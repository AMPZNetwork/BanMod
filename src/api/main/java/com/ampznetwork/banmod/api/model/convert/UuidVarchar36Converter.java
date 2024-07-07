package com.ampznetwork.banmod.api.model.convert;

import lombok.Value;
import org.comroid.api.info.Constraint;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.util.UUID;

@Value
@Converter(autoApply = true)
public class UuidVarchar36Converter implements AttributeConverter<UUID, String> {
    public static String fillDashes(String uuid) {
        Constraint.notNull(uuid, "uuid string").run();
        if (uuid.length() > 36)
            uuid = uuid.replaceAll("-", "");
        return uuid.length() == 36 ? uuid
                : uuid.substring(0, 8) +
                '-' + uuid.substring(8, 12) +
                '-' + uuid.substring(12, 16) +
                '-' + uuid.substring(16, 20) +
                '-' + uuid.substring(20);
    }

    @Override
    public String convertToDatabaseColumn(UUID attribute) {
        return attribute.toString();
    }

    @Override
    public UUID convertToEntityAttribute(String dbData) {
        return UUID.fromString(fillDashes(dbData));
    }
}
