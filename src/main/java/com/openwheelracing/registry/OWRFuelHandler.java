package com.openwheelracing.registry;

import net.minecraftforge.event.furnace.FurnaceFuelBurnTimeEvent;

public final class OWRFuelHandler {
    private OWRFuelHandler() {
    }

    public static void onFuelBurnTime(FurnaceFuelBurnTimeEvent event) {
        if (event.getItemStack().is(OWRItems.GAS.get())) {
            event.setBurnTime(400);
        } else if (event.getItemStack().is(OWRItems.PETROL_CAN.get())) {
            event.setBurnTime(1200);
        } else if (event.getItemStack().is(OWRItems.DIESEL_CAN.get())) {
            event.setBurnTime(1600);
        }
    }
}
