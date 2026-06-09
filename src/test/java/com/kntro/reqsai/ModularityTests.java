package com.kntro.reqsai;

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
 */
@Tag("modularity")
class ModularityTests {

    static final ApplicationModules MODULES = ApplicationModules.of(BackendReqsaiApplication.class);

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
