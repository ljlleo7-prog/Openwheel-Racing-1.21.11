package com.openwheelracing.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public final class OWRKeyMappings {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath("openwheelracing", "controls"));

    // Driving keys — default to WASD so players don't need to rebind
    public static final KeyMapping THROTTLE    = new KeyMapping("key.openwheelracing.throttle",    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_W,            CATEGORY);
    public static final KeyMapping BRAKE       = new KeyMapping("key.openwheelracing.brake",       InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_S,            CATEGORY);
    public static final KeyMapping STEER_LEFT  = new KeyMapping("key.openwheelracing.steer_left",  InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_A,            CATEGORY);
    public static final KeyMapping STEER_RIGHT = new KeyMapping("key.openwheelracing.steer_right", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_D,            CATEGORY);
    public static final KeyMapping SHIFT_UP    = new KeyMapping("key.openwheelracing.shift_up",    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I,            CATEGORY);
    public static final KeyMapping SHIFT_DOWN  = new KeyMapping("key.openwheelracing.shift_down",  InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K,            CATEGORY);
    public static final KeyMapping EXIT_CAR    = new KeyMapping("key.openwheelracing.exit_car",    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R,            CATEGORY);
    public static final KeyMapping TOGGLE_ABS  = new KeyMapping("key.openwheelracing.toggle_abs",  InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V,            CATEGORY);
    public static final KeyMapping TOGGLE_TC   = new KeyMapping("key.openwheelracing.toggle_tc",   InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C,            CATEGORY);
    public static final KeyMapping TOGGLE_DRS  = new KeyMapping("key.openwheelracing.toggle_drs",  InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_0,            CATEGORY);
    public static final KeyMapping ERS_MODE_PREVIOUS = new KeyMapping("key.openwheelracing.ers_mode_previous", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);
    public static final KeyMapping ERS_MODE_NEXT = new KeyMapping("key.openwheelracing.ers_mode_next", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_L, CATEGORY);
    public static final KeyMapping MOUNT_CAR   = new KeyMapping("key.openwheelracing.mount_car",   InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G,            CATEGORY);
    public static final KeyMapping TRACK_EDITOR = new KeyMapping("key.openwheelracing.track_editor", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B,            CATEGORY);

    private OWRKeyMappings() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(THROTTLE);
        event.register(BRAKE);
        event.register(STEER_LEFT);
        event.register(STEER_RIGHT);
        event.register(SHIFT_UP);
        event.register(SHIFT_DOWN);
        event.register(EXIT_CAR);
        event.register(TOGGLE_ABS);
        event.register(TOGGLE_TC);
        event.register(TOGGLE_DRS);
        event.register(ERS_MODE_PREVIOUS);
        event.register(ERS_MODE_NEXT);
        event.register(MOUNT_CAR);
        event.register(TRACK_EDITOR);
    }
}
