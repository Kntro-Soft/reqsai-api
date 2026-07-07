package com.kntro.reqsai.workspace.infrastructure.persistence.converters;

import com.kntro.reqsai.workspace.domain.valueobjects.Slug;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.jspecify.annotations.Nullable;

/** Maps {@link Slug} to/from a plain {@code varchar} column. */
@Converter
public class SlugConverter implements AttributeConverter<Slug, String> {

    @Override
    public @Nullable String convertToDatabaseColumn(@Nullable Slug attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public @Nullable Slug convertToEntityAttribute(@Nullable String dbData) {
        return (dbData == null || dbData.isBlank()) ? null : Slug.of(dbData);
    }
}
