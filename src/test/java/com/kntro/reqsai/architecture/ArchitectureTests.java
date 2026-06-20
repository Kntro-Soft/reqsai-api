package com.kntro.reqsai.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Tag;

@Tag("architecture")
@AnalyzeClasses(packages = "com.kntro.reqsai", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTests {

	@ArchTest
	static final ArchRule domain_must_not_use_spring = noClasses().that()
			.resideInAPackage("..domain..").and()
			.resideOutsideOfPackages(
					"..shared.domain.model..",
					"..shared.domain.exception..",
					"..discovery.domain.exception..",
					"..workspace.domain.exception..")
			.should().dependOnClassesThat().resideInAPackage("org.springframework..")
			.because("domain layer must be framework-agnostic; "
					+ "shared.domain.model uses Spring Data auditing intentionally, "
					+ "*.domain.exception uses HttpStatus as typed error carrier");

	@ArchTest
	static final ArchRule domain_must_not_use_jpa = noClasses().that()
			.resideInAPackage("..domain..").and()
			.resideOutsideOfPackages(
					"..shared.domain.model..",
					"..discovery.domain.model..",
					"..discovery.domain.valueobjects..",
					"..workspace.domain.model..",
					"..workspace.domain.valueobjects..")
			.should().dependOnClassesThat().resideInAPackage("jakarta.persistence..")
			.because("domain must not depend on JPA — use ports; "
					+ "Active Record pattern exempts model and value-object packages");

	@ArchTest
	static final ArchRule interfaces_must_not_access_infrastructure = noClasses().that()
			.resideInAPackage("..interfaces..").and()
			.resideOutsideOfPackage("..interfaces.rest.swagger..").and()
			.resideOutsideOfPackage("..interfaces.websocket..")
			.should().dependOnClassesThat().resideInAPackage("..infrastructure..")
			.because("interfaces layer must not bypass the application layer; "
					+ "interfaces.rest.swagger may use shared OpenAPI annotations (compile-time metadata only); "
					+ "interfaces.websocket handlers extend TenantAwareBinaryWebSocketHandler and WebSocketQueryParams "
					+ "from shared.infrastructure.web.websocket (Spring WS base classes for multi-tenant adapters)");

	@ArchTest
	static final ArchRule bounded_contexts_must_not_directly_import_each_other = noClasses().that()
			.resideInAPackage("com.kntro.reqsai.iam..").should()
			.dependOnClassesThat().resideInAPackage("com.kntro.reqsai.billing..").orShould()
			.dependOnClassesThat().resideInAPackage("com.kntro.reqsai.workspace..").orShould()
			.dependOnClassesThat().resideInAPackage("com.kntro.reqsai.discovery..")
			.because("bounded contexts must communicate through shared module APIs only");
}
