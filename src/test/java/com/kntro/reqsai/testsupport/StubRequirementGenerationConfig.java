package com.kntro.reqsai.testsupport;

import com.kntro.reqsai.discovery.application.port.GenerationResult;
import com.kntro.reqsai.discovery.application.port.RequirementGenerationPort;
import com.kntro.reqsai.discovery.domain.model.Priority;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * Deterministic stand-in for the Gemini generation adapter, shared by all integration tests that
 * exercise the process-transcript path.
 * <p>
 * Always returns the same hardcoded stories so tests can assert on story extraction without a
 * running Gemini API key.
 * <p>
 * Usage: {@code @Import(StubRequirementGenerationConfig.class)} on any {@code @SpringBootTest}
 * that calls {@code POST /sessions/{id}/process}.
 */
@TestConfiguration
public class StubRequirementGenerationConfig {

    public static final GenerationResult STUB_RESULT = new GenerationResult(List.of(
            new GenerationResult.GeneratedStory(
                    "Login con Google",
                    "usuario registrado",
                    "iniciar sesión con mi cuenta de Google",
                    "no necesito recordar otra contraseña",
                    Priority.HIGH, 3,
                    List.of(new GenerationResult.GeneratedCriterion(
                            "Given el usuario en la pantalla de login",
                            "When hace clic en 'Iniciar con Google'",
                            "Then es redirigido al OAuth de Google"))),
            new GenerationResult.GeneratedStory(
                    "Gestión de perfil de usuario",
                    "usuario autenticado",
                    "editar mi nombre y foto de perfil",
                    "mi información esté actualizada",
                    Priority.MEDIUM, 2,
                    List.of())));

    @Bean
    @Primary
    public RequirementGenerationPort stubRequirementGenerationPort() {
        return new RequirementGenerationPort() {
            @Override
            public boolean isAvailable() { return true; }

            @Override
            public GenerationResult generate(String transcript, String language) { return STUB_RESULT; }
        };
    }
}
