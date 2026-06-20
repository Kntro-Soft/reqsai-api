package com.kntro.reqsai.workspace.infrastructure.persistence.converters;

import com.kntro.reqsai.workspace.domain.model.Permission;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Converter
public class PermissionSetConverter implements AttributeConverter<Set<Permission>, String> {

    @Override
    public String convertToDatabaseColumn(Set<Permission> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "";
        }
        return attribute.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    @Override
    public Set<Permission> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(dbData.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Permission::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
