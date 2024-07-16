package com.ampznetwork.banmod.api.model.convert;

import lombok.Value;
import org.jetbrains.annotations.Contract;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.util.UUID;

@Value
@Converter(autoApply = true)
public class UuidVarchar36Converter implements AttributeConverter<UUID, String> {
    @Override
    public String convertToDatabaseColumn(UUID attribute) {
        return attribute.toString();
    }

    @Override
    @Contract("null->null;!null->!null")
    public UUID convertToEntityAttribute(String dbData) {
        return dbData == null ? null : UUID.fromString(fillDashes(dbData));
    }

    @Contract("null->null;!null->!null")
    public static String fillDashes(String uuid) {
        if (uuid == null)
            return null;
        if (uuid.length() > 36)
            uuid = uuid.replaceAll("-", "");
        return uuid.length() == 36 ? uuid
                                   : uuid.substring(0, 8) +
                       '-' + uuid.substring(8, 12) +
                       '-' + uuid.substring(12, 16) +
                       '-' + uuid.substring(16, 20) +
                       '-' + uuid.substring(20);
    }
}
