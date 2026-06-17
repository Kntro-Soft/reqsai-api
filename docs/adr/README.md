# Architecture Decision Records

This directory records the significant architectural decisions for the Reqs-AI backend using
[Michael Nygard's ADR format](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions).

Each ADR is immutable once accepted. To change a decision, add a new ADR that **supersedes** the old
one (and update the old one's status).

## Index

| ADR                                                          | Title                                          | Status   |
|--------------------------------------------------------------|------------------------------------------------|----------|
| [0001](./0001-record-architecture-decisions.md)              | Record architecture decisions                  | Accepted |
| [0002](./0002-modular-monolith-with-spring-modulith.md)      | Modular monolith with Spring Modulith          | Accepted |
| [0003](./0003-schema-per-tenant-multitenancy.md)             | Schema-per-tenant multitenancy                 | Accepted |
| [0004](./0004-uuid-v7-identifiers.md)                        | UUID v7 identifiers                            | Accepted |
| [0005](./0005-rsa-jwt-authentication.md)                     | Stateless authentication with RS256 JWT        | Accepted |
| [0006](./0006-deploy-on-aws-ecs-fargate.md)                  | Deploy on AWS ECS Fargate                      | Accepted |
| [0007](./0007-realtime-stomp-switchable-broker.md)           | Real-time over STOMP with a switchable broker  | Accepted |
| [0008](./0008-testing-strategy-and-security-test-support.md) | Testing strategy and security test support     | Accepted |
| [0009](./0009-test-data-builders-and-parallel-execution.md)  | Test data builders and parallel test execution | Accepted |
| [0010](./0010-use-case-vertical-slice-workflow.md)           | Use-case (vertical slice) development workflow | Accepted |
| [0011](./0011-api-response-field-selection-strategy.md)      | API response field-selection strategy          | Accepted |
| [0012](./0012-rest-route-design-for-user-stories.md)         | REST route design for User Story endpoints     | Accepted |
| [0013](./0013-exception-handling-strategy.md)                | Exception handling strategy                    | Accepted |

## Template

```markdown
# NNNN. Title

- Status: Proposed | Accepted | Deprecated | Superseded by ADR-XXXX
- Date: YYYY-MM-DD
- Deciders: <names>

## Context
<the forces at play, the problem>

## Decision
<what we decided>

## Consequences
<positive, negative, and neutral outcomes>
```
