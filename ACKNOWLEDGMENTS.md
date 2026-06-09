# Acknowledgments — Reqs-AI API (Backend)

The **Kntro-Soft** team thanks the people, tools, and open-source projects that make this backend
possible.

## Instructor and Institution

- **Christian Luis De Los Rios Fernández** — Instructor for course 1ASI0732, whose feedback shaped
  the architecture and design decisions.
- **Universidad Peruana de Ciencias Aplicadas (UPC)** — For the academic framework and resources.

## Technologies and Frameworks

| Project                                                                     | Use in the backend                              |
|-----------------------------------------------------------------------------|-------------------------------------------------|
| [Spring Boot](https://spring.io/projects/spring-boot)                       | Application framework                           |
| [Spring Modulith](https://spring.io/projects/spring-modulith)               | Modular monolith with verified module boundaries |
| [Spring AI](https://spring.io/projects/spring-ai)                           | Google Gemini (LLM/embeddings) + pgvector store |
| [Hibernate ORM](https://hibernate.org)                                      | Persistence + schema-per-tenant routing         |
| [Flyway](https://flywaydb.org)                                              | Database migrations (common + per-tenant)       |
| [PostgreSQL](https://www.postgresql.org) + [pgvector](https://github.com/pgvector/pgvector) | Relational DB + vector embeddings |
| [JJWT](https://github.com/jwtk/jjwt)                                        | JWT (RS256) signing and verification            |
| [uuid-creator](https://github.com/f4b6a3/uuid-creator)                      | UUID v7 identifier generation                   |
| [Caffeine](https://github.com/ben-manes/caffeine)                           | In-memory caching (tenant schema resolution)    |
| [Testcontainers](https://testcontainers.com)                                | Integration testing against real PostgreSQL     |
| [Lombok](https://projectlombok.org)                                         | Boilerplate reduction                           |

## Reference Methodologies

- [Domain-Driven Design](https://www.domainlanguage.com/ddd/) — Eric Evans
- [Hexagonal Architecture](https://alistair.cockburn.us/hexagonal-architecture/) — Alistair Cockburn
- [C4 Model](https://c4model.com) — Simon Brown
- [Architecture Decision Records](https://adr.github.io/) — Michael Nygard
- [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/)
- [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
- [Semantic Versioning](https://semver.org/)
