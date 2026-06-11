# Test JWT keys — THROWAWAY, NOT SECRET

These RSA-2048 PEM files exist **only** so `./gradlew build` and CI work out-of-the-box on a clean
checkout (no manual key generation). They are used exclusively by the `test` Spring profile.

- `private_key.pem` — PKCS#8, used by `TestJwtFactory` to **sign** test access tokens.
- `public_key.pem` — X.509, loaded by `JwtTokenVerifier` to **verify** them.

They sign nothing real and grant access to nothing. Committing them is safe and intentional.

## Production keys are different

Real signing keys are **never** committed. In prod, they come from env / a mounted secret
(`JWT_PUBLIC_KEY_PATH` / `JWT_PRIVATE_KEY_PATH`); the dev keys under `src/main/resources/certs/`
are git-ignored. Regenerate this test pair anytime with:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private_key.pem
openssl rsa -pubout -in private_key.pem -out public_key.pem
```
