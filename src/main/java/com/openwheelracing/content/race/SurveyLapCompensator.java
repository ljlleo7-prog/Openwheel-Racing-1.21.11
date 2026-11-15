package com.openwheelracing.content.race;

import com.openwheelracing.content.track.TrackDefinition;
import com.openwheelracing.content.track.survey.SurveyRoute;
import com.openwheelracing.content.track.survey.SurveyRouteGeometry;
import com.openwheelracing.content.track.survey.SurveyRouteLocalizer;
import com.openwheelracing.content.track.survey.SurveyRouteModel;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.world.phys.Vec3;

/** Independent of racing-line learning and its ordered/profile sample coverage. */
public final class SurveyLapCompensator {
    private final SurveyRouteModel model;
    private final SurveyRouteLocalizer.State localizer = new SurveyRouteLocalizer.State();
    private final ContinuousLapCoverage coverage;
    private Vec3 lastPosition;
    private double lastTick = Double.NEGATIVE_INFINITY;

    public SurveyLapCompensator(SurveyRoute route, TrackDefinition.StartFinishLine line) {
        model = route.toModel();
        var midpoint = new SurveyRouteModel.Point((line.left().x() + line.right().x()) / 2,
            (line.left().y() + line.right().y()) / 2, (line.left().z() + line.right().z()) / 2);
        double finish = SurveyRouteGeometry.candidates(model, midpoint, line.headingRadians(), -1, model.nodes().size()).stream()
            .min(Comparator.comparingDouble(candidate -> candidate.horizontalDistance() * candidate.horizontalDistance()
                + candidate.verticalDelta() * candidate.verticalDelta()))
            .map(SurveyRouteGeometry.Candidate::distanceAlongRoute).orElse(0.0);
        coverage = new ContinuousLapCoverage(model.length(), finish);
    }

    public double coverage() { return coverage.coverage(); }
    public void reset() { coverage.reset(); localizer.reset(); lastPosition = null; lastTick = Double.NEGATIVE_INFINITY; }

    private ContinuousLapCoverage.Sample locate(Vec3 position, double tick, double heading) {
        var result = SurveyRouteLocalizer.locate(model, new SurveyRouteModel.Point(position.x, position.y, position.z), heading, localizer);
        boolean reliable = result.best().isPresent() && (result.status() == SurveyRouteLocalizer.Status.TRACKED
            || result.status() == SurveyRouteLocalizer.Status.LOW_CONFIDENCE);
        return new ContinuousLapCoverage.Sample(result.best().map(SurveyRouteGeometry.Candidate::distanceAlongRoute).orElse(0.0),
            tick, position.x, position.y, position.z, reliable);
    }

    public Optional<ContinuousLapCoverage.Crossing> observeMovement(Vec3 before, Vec3 current, double tick, double heading) {
        if (lastPosition == null || lastPosition.distanceToSqr(before) > 1e-12) {
            double at = Math.max(lastTick, tick - 1);
            coverage.sample(locate(before, at, heading));
            lastTick = at;
        }
        lastPosition = current;
        lastTick = Math.max(lastTick, tick);
        return coverage.sample(locate(current, lastTick, heading));
    }

    public void restartAtFinish(Vec3 position, double tick, double heading) {
        localizer.reset();
        coverage.restartAtFinish(locate(position, tick, heading));
        lastPosition = position;
        lastTick = tick;
    }
}
