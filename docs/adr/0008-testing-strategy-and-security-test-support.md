# 0008. Testing strategy and security test support

- Status: Accepted
- Date: 2026-06-11
- Deciders: Kntro-Soft team

## Context

Every HTTP route and WebSocket connection passes through the JWT filter chain, so **any** test that
touches a secured endpoint must present an authenticated principal or it gets `401`/`403`. As the test
suite grows across bounded contexts we need a consistent, low-friction way to do this that:

1. works on a **clean checkout and in CI with zero manual setup** (no "generate keys first" step),
2. keeps **production signing keys out of git**,
3. exercises the **real RS256 verification path** when we want end-to-end confidence, but stays cheap
   for slice tests,
4. doesn't pollute the Spring Modulith module model with test-only classes.

The alternatives for getting a key into CI were: generate an ephemeral keypair in a workflow step (CI
goes green but a local `git clone` still can't run the ITs), or mock the `TokenVerifier` everywhere
(never exercises real verification). Neither gives "clone → build → green" out of the box.

## Decision

**Test pyramid** (also in [CONTRIBUTING](../../.github/CONTRIBUTING.md#testing-pyramid)):

1. **Unit** — pure domain, no Spring/DB.
2. **Slice** — `@DataJpaTest` (adapters), `@WebMvcTest` (controllers).
3. **Integration** — `@SpringBootTest` + Testcontainers PostgreSQL (`jdbc:tc:postgresql` URL, or a
   `pgvector/pgvector:pg16` `@ServiceConnection` when pgvector/schema-switching is needed). Never H2.
4. **Architecture** — `ModularityTests` (`@Tag("modularity")`) on every build.

**Security test support** — a committed **throwaway RSA keypair** under `src/test/resources/certs`
(documented as non-secret), bound by the `test` profile (`src/test/resources/application-test.yml`, on
the **test** classpath only — never in the production jar). Production keys stay git-ignored and come
from env/secrets. Two helpers in the `com.kntro.reqsai.testsupport` package (excluded from the Modulith
model) cover the two needs:

- `@WithMockReqsaiUser` — populates the `SecurityContext` like `JwtAuthenticationFilter` does
  (principal = userId, one authority = role) **without** a token. For slice/method-security tests.
- `TestJwtFactory` — mints a real RS256 token signed with the test key. For end-to-end tests that must
  cross the actual filter / WebSocket interceptor.

### Usage examples

Slice / `@WebMvcTest` (BDD style — given an admin, when listing, then 200):

```java
@Test
@WithMockReqsaiUser(role = "ROLE_ADMIN")          // given: an authenticated admin
void admin_lists_workspaces() throws Exception {
    mockMvc.perform(get("/api/v1/workspaces"))    // when
           .andExpect(status().isOk());           // then
}
```

End-to-end / `@SpringBootTest` through the real JWT filter:

```java
@Test
void valid_token_passes_the_security_chain() {            // given: a signed RS256 token
    var res = client.get().uri("/api/v1/whoami")
        .header("Authorization", TestJwtFactory.bearer(userId, orgId, "ROLE_USER"))  // when
        .retrieve().toBodilessEntity();
    assertThat(res.getStatusCode()).isNotIn(UNAUTHORIZED, FORBIDDEN);                 // then
}
```

WebSocket (CONNECT auth) — set the same bearer as a native STOMP header on CONNECT; see
[REALTIME.md](../REALTIME.md). The live example is `SmokeTest.validJwtClearsTheSecurityChain`.

## Consequences

- `git clone && ./gradlew build` is green with no key generation; CI needs no secret to run ITs.
- Production keys never enter the repository; the test keypair signs nothing real.
- The genuine RS256 path is covered (`TestJwtFactory`) while slice tests stay fast (`@WithMockReqsaiUser`).
- Test-only helpers live in `testsupport`, kept out of the module model via an ignore predicate in
  `ModularityTests`, so they never look like a stray bounded context.
- If we ever adopt Cucumber/Gherkin, the same helpers back the step definitions unchanged — the BDD
  given/when/then above is already the shape of the assertions.
