package com.openwheelracing.client.livery;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PictureLiveryTest {
    @Test
    void fitsWideAndTallPicturesWithoutStretchingOrLeavingThePanel() {
        assertEquals(new PictureLivery.Placement(10, 45, 100, 50), PictureLivery.fit(400, 200, 10, 20, 100, 100));
        assertEquals(new PictureLivery.Placement(35, 20, 50, 100), PictureLivery.fit(200, 400, 10, 20, 100, 100));
        assertEquals(new PictureLivery.Placement(0, 0, 1, 1), PictureLivery.fit(4096, 1, 0, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> PictureLivery.fit(0, 20, 0, 0, 100, 100));
    }

    @Test
    void transparentPixelsPreserveTheBaseAndSemitransparentPixelsBlendOnce() {
        assertEquals(0xFF123456, PictureLivery.composite(0x00FFFFFF, 0xFF123456));
        assertEquals(0xFFABCDEF, PictureLivery.composite(0xFFABCDEF, 0xFF123456));
        assertEquals(0xFF80007F, PictureLivery.composite(0x80FF0000, 0xFF0000FF));
    }
}
