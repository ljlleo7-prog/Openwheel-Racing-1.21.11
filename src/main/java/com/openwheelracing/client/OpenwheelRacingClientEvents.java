package com.openwheelracing.client;

import com.openwheelracing.OpenwheelRacing;
import com.openwheelracing.client.camera.OWRCameraMode;
import com.openwheelracing.client.hud.CarHudOverlay;
import com.openwheelracing.client.hud.LapDeltaClient;
import com.openwheelracing.client.hud.LiveLapDeltaClient;
import com.openwheelracing.client.hud.SurveyRouteHud;
import com.openwheelracing.client.input.OWRClientInputHandler;
import com.openwheelracing.client.input.OWRKeyMappings;
import com.openwheelracing.client.render.StewardLineOverlay;
import com.openwheelracing.client.render.SurveyRouteOverlay;
import com.openwheelracing.client.render.AiRacingLineOverlay;
import com.openwheelracing.client.screen.OpenwheelSetupScreen;
import com.openwheelracing.client.sound.CarSoundManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderAvatarEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import org.lwjgl.glfw.GLFW;

public final class OpenwheelRacingClientEvents {
    private static final Identifier CAR_HUD = Identifier.fromNamespaceAndPath(OpenwheelRacing.MODID, "car_hud");
    private static final Identifier SURVEY_ROUTE_HUD = Identifier.fromNamespaceAndPath(OpenwheelRacing.MODID, "survey_route_hud");

    private OpenwheelRacingClientEvents() {
    }

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        OWRKeyMappings.register(event);
    }

    public static void onAddGuiOverlayLayers(AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().add(CAR_HUD, CarHudOverlay::render);
        event.getLayeredDraw().add(SURVEY_ROUTE_HUD, SurveyRouteHud::render);
    }

    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof PauseScreen)) {
            return;
        }
        event.addListener(Button.builder(Component.translatable("screen.openwheelracing.setup.open"), button -> Minecraft.getInstance().setScreen(new OpenwheelSetupScreen(screen)))
            .bounds(screen.width - 142, screen.height - 28, 134, 20)
            .build());
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        OWRClientInputHandler.onClientTick(event);
        OWRCameraMode.clearIfNotOnboard();
        CarSoundManager.onClientTick();
        boolean ridingCar = Minecraft.getInstance().player != null && Minecraft.getInstance().player.getVehicle() instanceof com.openwheelracing.content.entity.OpenwheelCarEntity;
        LapDeltaClient.tick(ridingCar);
        LiveLapDeltaClient.tick();
        SurveyRouteOverlay.tick();
        if (Minecraft.getInstance().level == null) {
            StewardLineOverlay.clear();
            SurveyRouteOverlay.clear();
            AiRacingLineOverlay.clear();
        }
    }

    public static boolean onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getAction() != GLFW.GLFW_PRESS || OWRClientInputHandler.onboardCar() == null) {
            return false;
        }
        boolean handled = switch (event.getButton()) {
            case GLFW.GLFW_MOUSE_BUTTON_LEFT -> OWRClientInputHandler.shiftDown();
            case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> OWRClientInputHandler.shiftUp();
            case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> OWRClientInputHandler.toggleDrs();
            default -> false;
        };
        return handled;
    }

    public static boolean onRenderPlayer(RenderAvatarEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && OWRCameraMode.isTCamera() && event.getState().id == mc.player.getId();
    }

    public static void renderWorldOverlays(net.minecraft.client.renderer.state.LevelRenderState state) {
        StewardLineOverlay.render(state);
        SurveyRouteOverlay.render(state);
        AiRacingLineOverlay.render(state);
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch(net.minecraft.client.renderer.rendertype.RenderTypes.lines());
    }
}
