package com.kntro.reqsai.iam.infrastructure.persistence.converters;

import com.kntro.reqsai.shared.domain.valueobjects.Email;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.jspecify.annotations.Nullable;

/** Maps the {@link Email} value object to/from a plain {@code varchar} column. */
@Converter
public class EmailConverter implements AttributeConverter<Email, String> {

    @Override
    public @Nullable String convertToDatabaseColumn(@Nullable Email attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public @Nullable Email convertToEntityAttribute(@Nullable String dbData) {
        return (dbData == null || dbData.isBlank()) ? null : Email.of(dbData);
    }
}
