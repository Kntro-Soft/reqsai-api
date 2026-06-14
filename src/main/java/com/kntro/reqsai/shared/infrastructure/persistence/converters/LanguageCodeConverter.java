package com.kntro.reqsai.shared.infrastructure.persistence.converters;

import com.kntro.reqsai.shared.domain.valueobjects.LanguageCode;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.jspecify.annotations.Nullable;

/**
 * Maps the single-value {@link LanguageCode} value object to/from a plain {@code varchar} column.
 * {@code autoApply = true} so every {@code LanguageCode} field across all bounded contexts (e.g. an
 * organization's {@code meetingLanguage}, a discovery session's {@code language}) persists as the bare
 * BCP-47 string without per-field {@code @Convert}.
 */
@Converter(autoApply = true)
public class LanguageCodeConverter implements AttributeConverter<LanguageCode, String> {

    @Override
    public @Nullable String convertToDatabaseColumn(@Nullable LanguageCode attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public @Nullable LanguageCode convertToEntityAttribute(@Nullable String dbData) {
        return (dbData == null || dbData.isBlank()) ? null : LanguageCode.of(dbData);
    }
}
