package com.openwheelracing.content.block.entity;

import com.openwheelracing.content.block.RaceLightBlock;
import com.openwheelracing.content.menu.RaceLightMenu;
import com.openwheelracing.content.race.OWRRaceControlState;
import com.openwheelracing.content.race.PitLightMode;
import com.openwheelracing.content.race.RaceLightType;
import com.openwheelracing.content.race.RaceSignal;
import com.openwheelracing.content.track.TrackDefinition;
import com.openwheelracing.content.track.TrackDefinitionsData;
import com.openwheelracing.registry.OWRBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class RaceLightBlockEntity extends BlockEntity implements MenuProvider {
    public static final int FLAG_FLASH_HALF_PERIOD_TICKS = 5;
    private final RaceLightType type;
    private int sector;
    private int minisector = -1;
    private boolean automaticSector = true;
    private int startOrder = 1;
    private PitLightMode pitMode = PitLightMode.ENTRY;

    public RaceLightBlockEntity(BlockPos pos, BlockState state, RaceLightType type) {
        super(OWRBlockEntities.typeFor(type).get(), pos, state);
        this.type = type;
    }

    public RaceLightType getLightType() { return type; }
    public int getSector() { return sector; }
    public int getMinisector() { return minisector; }
    public boolean isAutomaticSector() { return automaticSector; }
    public int getStartOrder() { return startOrder; }
    public PitLightMode getPitMode() { return pitMode; }
    public void setSector(int value) { sector = Math.max(0, value); automaticSector = false; setChanged(); }
    public void setMinisector(int value) { minisector = Math.max(-1, value); automaticSector = false; setChanged(); }
    public void setStartOrder(int value) { startOrder = Math.max(1, Math.min(5, value)); setChanged(); }
    public void setPitMode(PitLightMode value) { pitMode = value == null ? PitLightMode.ENTRY : value; setChanged(); }

    public void autoDetectSector() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        automaticSector = true;
        TrackDefinitionsData.get(serverLevel).activeTrack(serverLevel.dimension().identifier().toString()).ifPresent(track -> {
            double best = Double.MAX_VALUE;
            int closest = -1;
            for (TrackDefinition.Checkpoint checkpoint : track.checkpoints()) {
                double x = (checkpoint.left().x() + checkpoint.right().x()) * 0.5;
                double z = (checkpoint.left().z() + checkpoint.right().z()) * 0.5;
                double distance = worldPosition.distSqr(BlockPos.containing(x, worldPosition.getY(), z));
                if (distance < best) { best = distance; closest = checkpoint.index(); }
            }
            int checkpoint = closest;
            track.sectors().stream().filter(s -> checkpoint >= s.startCheckpointIndex() && checkpoint <= s.endCheckpointIndex()).findFirst().ifPresent(s -> sector = s.index());
            minisector = closest;
        });
        setChanged();
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel) || serverLevel.getGameTime() % FLAG_FLASH_HALF_PERIOD_TICKS != 0L) return;
        RaceSignal signal = resolveSignal(OWRRaceControlState.get(serverLevel));
        if (type == RaceLightType.FLAG && signal != RaceSignal.OFF
                && serverLevel.getGameTime() / FLAG_FLASH_HALF_PERIOD_TICKS % 2L != 0L) {
            signal = RaceSignal.OFF;
        }
        BlockState state = getBlockState();
        if (state.hasProperty(RaceLightBlock.SIGNAL) && state.getValue(RaceLightBlock.SIGNAL) != signal) {
            serverLevel.setBlock(worldPosition, state.setValue(RaceLightBlock.SIGNAL, signal), 3);
        }
    }

    private RaceSignal resolveSignal(OWRRaceControlState control) {
        return switch (type) {
            case FLAG -> control.getSectorSignal(sector, minisector);
            case START -> control.getStartPhase() >= startOrder && control.getStartPhase() <= 5 ? RaceSignal.RED : RaceSignal.OFF;
            case PIT -> control.getPitSignal(pitMode);
        };
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Sector", sector); output.putInt("Minisector", minisector); output.putBoolean("AutomaticSector", automaticSector);
        output.putInt("StartOrder", startOrder); output.putInt("PitMode", pitMode.ordinal());
    }
    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        sector = Math.max(0, input.getIntOr("Sector", 0)); minisector = Math.max(-1, input.getIntOr("Minisector", -1));
        automaticSector = input.getBooleanOr("AutomaticSector", true); startOrder = Math.max(1, Math.min(5, input.getIntOr("StartOrder", 1)));
        pitMode = PitLightMode.fromOrdinal(input.getIntOr("PitMode", 0));
    }
    @Override public Component getDisplayName() { return Component.translatable("container.openwheelracing." + type.name().toLowerCase() + "_light"); }
    @Override public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new RaceLightMenu(id, inventory, this); }
}
