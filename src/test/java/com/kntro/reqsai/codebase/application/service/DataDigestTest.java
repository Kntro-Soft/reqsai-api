package com.kntro.reqsai.codebase.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DataDigest")
class DataDigestTest {

    @Test
    @DisplayName("keeps numbers and figures with their path, naming array items by their name")
    void flattensValues() {
        String json = """
                {"pricing": {
                   "title": "Planes",
                   "subtitle": "Elige el plan que mejor se adapte a tu equipo",
                   "plans": [
                     {"name": "Starter", "monthly": 0, "features": ["1 proyecto", "Soporte por correo"]},
                     {"name": "Pro", "monthly": 49, "annual": "39 USD al mes, facturado anualmente"}
                   ],
                   "trial": true,
                   "sections": [{"title": "3. Cuentas", "body": "Cada cuenta es personal"}]}}""";

        String digest = DataDigest.flatten(json);

        assertThat(digest).contains(
                "pricing.plans[Starter].monthly = 0",
                "pricing.plans[Starter].features[0] = \"1 proyecto\"",
                "pricing.plans[Pro].monthly = 49",
                "pricing.plans[Pro].annual = \"39 USD al mes, facturado anualmente\"");
        assertThat(digest).doesNotContain("subtitle", "Soporte por correo", "trial", "= \"3. Cuentas\"");
        assertThat(digest.indexOf("pricing.plans[Pro].monthly = 49"))
                .as("numbers come before texts").isLessThan(digest.indexOf("1 proyecto"));
    }

    @Test
    @DisplayName("returns null for content that is not a JSON document or holds no values")
    void notJson() {
        assertThat(DataDigest.flatten("plans:\n  pro: 49")).isNull();
        assertThat(DataDigest.flatten("\"just text\"")).isNull();
        assertThat(DataDigest.flatten("{\"hero\": {\"title\": \"Bienvenido\"}}")).isNull();
    }
}
