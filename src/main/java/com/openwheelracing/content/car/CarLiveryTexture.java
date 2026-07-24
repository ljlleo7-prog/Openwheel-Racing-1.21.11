package com.openwheelracing.content.car;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record CarLiveryTexture(String id) {
    public static final CarLiveryTexture NONE = new CarLiveryTexture("");
    public static final Codec<CarLiveryTexture> CODEC = Codec.STRING.xmap(CarLiveryTexture::new, CarLiveryTexture::id);
    public static final StreamCodec<RegistryFriendlyByteBuf, CarLiveryTexture> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8,
        CarLiveryTexture::id,
        CarLiveryTexture::new
    );

    public CarLiveryTexture {
        id = sanitize(id);
    }

    public boolean isPresent() {
        return !id.isEmpty();
    }

    public static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sanitized = new StringBuilder();
        for (int i = 0; i < value.length() && sanitized.length() < 80; i++) {
            char c = Character.toLowerCase(value.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-') {
                sanitized.append(c);
            }
        }
        return sanitized.toString();
    }
}
