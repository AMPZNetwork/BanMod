package com.ampznetwork.test.banmod;

import com.ampznetwork.libmod.api.model.model.convert.UuidBinary16Converter;
import com.ampznetwork.libmod.api.model.model.convert.UuidVarchar36Converter;
import org.junit.jupiter.api.Test;

import java.util.UUID;

public class ConverterTest {
    final UUID Uuid = UUID.fromString("30c408a8-31df-4c13-bf92-416288300613");
    final byte[] UuidBytes = new byte[]{ 48, -60, 8, -88, 49, -33, 76, 19, -65, -110, 65, 98, -120, 48, 6, 19 };

    @Test
    public void testUuidFillDashes() {
        var uuidNoDash = "30c408a831df4c13bf92416288300613";
        var uuidDash   = "30c408a8-31df-4c13-bf92-416288300613";
        assertEquals(uuidDash, UuidVarchar36Converter.fillDashes(uuidNoDash));
        assertEquals(Uuid, new UuidVarchar36Converter().convertToEntityAttribute(uuidDash));
    }

    //@Test
    public void testUuidToBinary16() {
        assertEquals(UuidBytes, new UuidBinary16Converter().convertToDatabaseColumn(Uuid));
        assertEquals(Uuid, new UuidBinary16Converter().convertToEntityAttribute(UuidBytes));
    }
}
