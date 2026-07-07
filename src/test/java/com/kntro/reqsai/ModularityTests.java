package com.kntro.reqsai;

import com.tngtech.archunit.core.domain.JavaClass;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Verifies the Spring Modulith architecture (no Spring context — pure bytecode analysis).
 * <p>
 * Tagged {@code modularity} so the dedicated {@code verifyModularity} Gradle task can run it in
 * isolation (it also runs as part of the normal {@code test} task). Fails the build if any module
 * violates its boundaries (e.g. reaching into another module's internal packages).
 * <p>
 * The {@code testsupport} package holds test-only helpers (not a bounded context), so it is excluded
 * from the module model — otherwise Modulith would treat it as a stray application module.
 */
@Tag("modularity")
class ModularityTests {

    static final ApplicationModules MODULES = ApplicationModules.of(
            BackendReqsaiApplication.class,
            JavaClass.Predicates.resideInAPackage("com.kntro.reqsai.testsupport.."));

    @Test
    void verifiesModuleBoundaries() {
        MODULES.verify();
    }

    @Test
    void writesDocumentation() {
        new Documenter(MODULES)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml();
    }
}
