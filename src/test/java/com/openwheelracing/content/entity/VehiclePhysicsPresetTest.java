package com.openwheelracing.content.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VehiclePhysicsPresetTest {
    @Test
    void namesAreCaseInsensitiveAndUnknownValuesFallBackToDynamic() {
        assertEquals(VehiclePhysicsPreset.CLASSIC, VehiclePhysicsPreset.fromName("classic"));
        assertEquals(VehiclePhysicsPreset.DYNAMIC, VehiclePhysicsPreset.fromName("DYNAMIC"));
        assertEquals(VehiclePhysicsPreset.DYNAMIC, VehiclePhysicsPreset.fromName("unknown"));
    }

    @Test
    void onlyDynamicPresetEnablesChatPhysicsChanges() {
        assertTrue(VehiclePhysicsPreset.DYNAMIC.isDynamic());
        assertTrue(!VehiclePhysicsPreset.CLASSIC.isDynamic());
    }
}
