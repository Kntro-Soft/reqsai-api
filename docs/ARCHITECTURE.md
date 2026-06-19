# ReqsAI Architecture

## 1. Purpose and Scope
This document centralizes the **system architecture, domain model, EventStorming outcomes, bounded contexts, and implementation constraints** for ReqsAI.

For complementary documentation, use:

* [docs/PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) for business and product context.
* [docs/DESING.md](DESING.md) for visual direction and UX/design guidelines.

---

## Table of Contents

1. [Purpose and Scope](#1-purpose-and-scope)
2. [Technical Architecture Constraints](#2-technical-architecture-constraints)
   1. [Multi-Tenancy Design](#21-multi-tenancy-design)
   2. [Core Structural Data Domains and Labeling Systems](#22-core-structural-data-domains-and-labeling-systems)
3. [Domain-Driven Design Context](#3-domain-driven-design-context)
   1. [EventStorming Baseline](#31-eventstorming-baseline)
   2. [Actors, Systems, and Decision Makers](#32-actors-systems-and-decision-makers)
   3. [Event Inventory and Lifecycle Signals](#33-event-inventory-and-lifecycle-signals)
   4. [Candidate Bounded Contexts](#34-candidate-bounded-contexts)
   5. [Read Models and UX-Relevant Views](#35-read-models-and-ux-relevant-views)
   6. [Pain Points and Product Risks Surfaced by EventStorming](#36-pain-points-and-product-risks-surfaced-by-eventstorming)
   7. [Policies and Automation Signals](#37-policies-and-automation-signals)
   8. [Bounded Context Responsibilities](#38-bounded-context-responsibilities)
   9. [Ubiquitous Language](#39-ubiquitous-language)
   10. [Key Message Flows](#310-key-message-flows)
   11. [Context Map and Integration Patterns](#311-context-map-and-integration-patterns)
   12. [Implementation Implications](#312-implementation-implications)
   13. [Assumptions, Verification Metrics, and Open Questions](#313-assumptions-verification-metrics-and-open-questions)
4. [Software Architecture Views (C4 Model)](#4-software-architecture-views-c4-model)
   1. [C4 Scope and Intent](#41-c4-scope-and-intent)
   2. [System Landscape View](#42-system-landscape-view)
   3. [System Context View](#43-system-context-view)
   4. [Container View](#44-container-view)
   5. [Deployment View](#45-deployment-view)
   6. [Consolidated Architecture Decisions](#46-consolidated-architecture-decisions)
   7. [Module-Level Component Views](#47-module-level-component-views)
   8. [Tactical Domain Model Views](#48-tactical-domain-model-views)
   9. [Persistence Views by Bounded Context](#49-persistence-views-by-bounded-context)
   10. [Diagram Source Strategy](#410-diagram-source-strategy)

---

## 2. Technical Architecture Constraints

### 2.1. Multi-Tenancy Design
* **Isolation Model:** **Schema-per-tenant approach**. Every workspace or organization must have its data physically or logically separated at the schema level to avoid cross-tenant leaks.
* **Context Engine:** Retains vectorized data mapping previous project iterations (RAG architecture) to provide historical comparison and deduplication alerts.
* **Vector Storage Strategy:** Embeddings are persisted in PostgreSQL using `pgvector`, avoiding the need for a separate vector database in the current architecture stage.

### 2.2. Core Structural Data Domains and Labeling Systems
When implementing database structures, UI components, or payload endpoints, use the following standardized English naming conventions and state values:

| Domain Namespace | Functional Scope                                                 | Valid Semantic Lifecycle States                   |
|:-----------------|:-----------------------------------------------------------------|:--------------------------------------------------|
| **Workspace**    | High-level organization or tenant hub.                           | `Active`, `Suspended`, `Trial`                    |
| **Projects**     | Categorization of user efforts per product or client.            | `Active`, `Archived`                              |
| **Sessions**     | Live stream or audio recording file processing node.             | `Live`, `Processing`, `Completed`, `Failed`       |
| **User Stories** | AI-generated requirements containing Gherkin syntax.             | `Draft`, `Pending Review`, `Approved`, `Exported` |
| **Integrations** | Webhooks and authentication nodes for third-party Jira / DevOps. | `Connected`, `Disconnected`                       |

---

## 3. Domain-Driven Design Context

### 3.1. EventStorming Baseline
A **Design-Level EventStorming** exercise materially sharpens the domain model. The resulting timeline, command flow, and aggregate map are sufficiently clear to extract implementation-relevant conclusions.

The EventStorming session models ReqsAI as a business timeline with four major macro-flows:

* **Identity and workspace bootstrap:** first login attempt, pending account creation, email verification, account validation, organization creation, and free-plan assignment.
* **Commercial lifecycle:** payment started, plan upgrade accepted/rejected, subscription termination, and plan degradation.
* **Project and collaboration setup:** project creation, technology stack registration, project data upload/update, role creation, invitation sending, role assignment, and client approval capture.
* **Requirement elicitation and delivery:** live session start/pause/resume/close/abort, audio upload and segmentation, transcript/suggestion generation, duplicate detection, story approval/rating, and export to an external project-management board.

### 3.2. Actors, Systems, and Decision Makers
The EventStorming artifacts reinforce that the platform is not only analyst-facing; it coordinates several human and automated participants:

* **User / Analyst / Product-oriented operator:** initiates sessions, uploads files, updates stories, approves stories, links projects, and triggers exports.
* **Tech Lead / workspace administrator:** creates projects, defines roles, assigns permissions, and manages integration setup.
* **System policies:** generate derived actions such as demonstration project creation, duplicate alerts, processing status updates, and cleanup of temporary stories after aborted sessions.
* **External systems:** `Email Service`, `Speech-to-Text Service`, `LLM Service`, `Payment Gateway`, and `Project Management Service`.

From the command images, the most stable business commands appear to be:

* `Create Pending Account`, `Send Verification Email`, `Validate Account`, `Log In`
* `Create Organization`, `Assign Free Plan`, `Start Payment`, `Terminate Subscription`, `Degrade Plan`
* `Create Project`, `Register Technological Stack`, `Upload Project Data File`, `Update Project Data`
* `Create Role`, `Assign Role Permissions`, `Send Invitation Email`, `Assign Role`, `Save Client Approval`
* `Start Session`, `Pause Session`, `Resume Session`, `Close Session`, `Save Session`, `Abort Session`, `Terminate Session`
* `Upload Audio File`, `Validate Audio File`, `Divide Audio`, `Process Audio Chunks`
* `Generate User Story`, `Update Session Backlog`, `Hide User Story`, `Display Duplication Alert`, `Solve Duplication Alert`, `Approve User Story`, `Register User Story Rating`
* `Link Project`, `Link Board and Project`, `Export Approved User Stories`

### 3.3. Event Inventory and Lifecycle Signals
The EventStorming boards also make explicit a broader set of domain events than the later message-flow section. These are useful as candidate event names or state transitions for internal modules:

* **Identity events:** `Pending Account Created`, `Verification Email Sent`, `Account Validated`, `Logged In`, `Password Reset`.
* **Workspace events:** `Organization Created`, `Organization Data Updated`, `Current Organization Changed`, `Free Plan Assigned`.
* **Billing events:** `Payment Started`, `Payment Confirmed`, `Payment Failed`, `Upgraded to Pro Plan`, `Upgraded to Team Plan`, `Upgrade Rejected`, `Subscription Terminated`, `Plan Degraded`.
* **Project and collaboration events:** `Project Created`, `Technological Stack Registered`, `Project Data File Uploaded`, `Project Data Updated`, `Role Created`, `Role Permissions Assigned`, `Email Invitation Sent`, `Role Assigned`, `Member Removed`, `Client Approval Saved`.
* **Session events:** `Session Started`, `Audio Recording Started`, `Audio Segmented`, `Speech Segment Identified`, `Session Paused`, `Session Resumed`, `Session Closed`, `Session Saved`, `Session Aborted`, `Session Terminated`, `Final Session Metrics Displayed`.
* **Story-generation events:** `User Story Generated`, `Session Backlog Updated`, `User Story Hidden`, `Session Questions Generated`, `Suggestions Generated`, `Duplication Alert Displayed`, `Duplication Alert Solved`, `User Story Updated`, `User Story Approved`, `User Story Rating Registered`.
* **File-processing and export events:** `Audio File Uploaded`, `Audio File Validated`, `Audio Chunks Processed`, `Processing Status Displayed`, `Project Linked`, `Board and Project Linked`, `Approved User Stories Exported`.

Not all names above need to become persisted events immediately, but they are strong candidates for:

* internal domain events,
* audit trail entries,
* enum state transitions,
* UI activity feed labels,
* webhook/event-bus contracts.

### 3.4. Candidate Bounded Contexts
The collaborative DDD workshop consolidated the product into **five bounded contexts**. This is the current architectural lens that should guide package boundaries, module ownership, API contracts, and event naming.

| Bounded Context | Subdomain Type | Strategic Role | Main Aggregates / Concepts | Primary Responsibility |
|:----------------|:---------------|:---------------|:----------------------------|:-----------------------|
| **Requirement Discovery** | Core Domain | Revenue, custom-built | `Session`, `UserStory`, `Transcript`, `GherkinFormat` | Capture live meetings, orchestrate STT + LLM, apply RAG/project context, and generate structured user stories. |
| **Workspace Management** | Supporting / Generic operational domain | Compliance, product | `Tenant`, `Organization`, `Project`, `Glossary`, `ProjectDocument` | Enforce schema-per-tenant isolation, manage organization/project hierarchy, and provide project context to AI workflows. |
| **IAM** | Generic Subdomain | Compliance, commodity | `User`, `Credentials`, `Token` | Registration, authentication, email validation, session authorization, and credential lifecycle management. |
| **Billing & Subscription** | Generic Subdomain | Revenue, product | `Subscription`, `PlanTier`, `Quota`, `PaymentToken` | Payment processing orchestration, plan upgrades/downgrades, and quota/token usage enforcement. |
| **Integration Gateway** | Supporting Subdomain | Engagement, custom-built | `ExternalConnection`, `OAuthToken`, `Mapping`, `AgileTicket` | Anti-Corruption Layer for OAuth integrations and one-way export of approved stories to external PM systems. |

### 3.4.1. Aggregate Discovery From EventStorming
The aggregate board complements the bounded contexts by exposing the likely **consistency boundaries** inside each context. Based on the readable aggregate map, the system currently revolves around the following aggregate candidates:

| Bounded Context | Aggregate Candidates | Notes |
|:----------------|:---------------------|:------|
| **Requirement Discovery** | `Session`, `UserStory` | The `Session` aggregate governs recording and lifecycle transitions. `UserStory` governs generation, review, approval, rating, duplication handling, and backlog state. |
| **Workspace Management** | `Organization`, `Project`, `Glossary`, `ProjectMember`, `MemberRole`, `ProjectDocument` | Workspace concerns are split between tenant/org setup, project metadata, glossary/document ingestion, and collaboration/membership rules. |
| **IAM** | `User`, `Account`, `RefreshToken` | The event boards distinguish user profile concerns from account verification and token/session renewal. |
| **Billing & Subscription** | `Subscription` | The subscription aggregate appears to own plan tier transitions, payment outcomes, and quota-related entitlement state. |
| **Integration Gateway** | `ExternalConnection`, `ExportJob` | One aggregate handles connection/linking, while another likely models export execution and retries/failures. |

This aggregate view suggests that implementation should avoid treating each bounded context as a single flat service. There are already smaller business consistency clusters inside each context that deserve separate entities, policies, and test coverage.

### 3.5. Read Models and UX-Relevant Views
The EventStorming `Read Models` layer is especially useful for backend/API design because it shows what the UI must retrieve before a decision can be made. Readable views from the board include:

* account status and email/password verification cues,
* organization plan summary,
* tools/frameworks metadata for a project,
* invitation expiration date,
* project and session status indicators,
* processing-time usage and processing-progress views,
* duplicate-resolution guidance,
* story description plus prioritization metadata,
* rating feedback,
* linked board/project names.

These should influence query-model design. In practice that means the system likely needs dedicated projection endpoints for:

* workspace dashboard and ROI/usage summaries,
* project context detail and glossary readiness,
* live session processing state,
* user story review queue with duplication annotations,
* integration linkage state and export history.

### 3.6. Pain Points and Product Risks Surfaced by EventStorming
Even with limited image sharpness, several pain points and risk markers are visible in the EventStorming boards:

* **Latency in email reception** during account validation.
* **Ambiguity around accepted project-data formats**, especially around compatibility versus AI extraction quality.
* **Session interruption risk**, particularly whether a paused session can safely resume and whether partial recordings are preserved.
* **Audio-processing quality risk**, especially around long recordings, chunking accuracy, and transcript continuity.
* **Upgrade outcome ambiguity**, including how the platform reacts when the payment gateway rejects an upgrade.

Those risks support a few engineering priorities:

* retries and expiration management around email verification and invitations,
* strict validation plus user feedback for uploaded context/audio files,
* resumable or recoverable session state for interrupted recordings,
* explicit failed-payment and failed-export paths,
* background processing observability for chunked audio ingestion.

### 3.7. Policies and Automation Signals
The EventStorming `Policies` layer shows that the domain relies on automated reactions, not only direct commands. The following policy patterns appear repeatedly and should be preserved in architecture decisions:

* when an account is created, verification mail is dispatched automatically;
* when an organization is created, a free plan is assigned and a demo project may be provisioned;
* when payment succeeds or fails, plan state is transitioned without manual intervention;
* when a session or audio upload advances, downstream processing steps such as chunking, transcription, suggestion generation, and progress updates are triggered;
* when a story is generated or updated, duplication checks and review-state changes are triggered;
* when a session is aborted, temporary user stories are deleted automatically.

This strongly favors an implementation style with:

* domain events plus internal subscribers,
* background job orchestration for slow AI/audio tasks,
* idempotent handlers for retries,
* explicit compensation logic for aborted or failed flows.

### 3.8. Bounded Context Responsibilities

#### Requirement Discovery
* Owns the **core value stream** from session start to user story generation.
* Receives `Start Session` from the analyst, coordinates audio segmentation with STT, requests project context, and sends `Generate User Story` to the active LLM provider.
* Publishes domain outcomes such as `Story Approved` and `Story Exported`.
* Business rules captured in the canvas:
  * Sessions shorter than a minimum threshold should not trigger story generation.
  * Generated stories must comply with **Gherkin syntax**.
  * If a generated story reaches a **similarity score > 80%**, the user should be warned to avoid duplicates.

#### Workspace Management
* Owns tenant/workspace isolation, organizational hierarchy, project membership, and glossary/document context.
* Supplies `Project data sent` to Requirement Discovery as the canonical RAG payload.
* Reacts to `User Registered` and `Upgraded to Pro Plan` events from upstream contexts.
* Business rules captured in the canvas:
  * A project belongs to exactly one organization.
  * Glossary uploads are constrained to text-extractable formats such as **PDF** or **TXT**.

#### IAM
* Owns authentication and user identity lifecycle.
* Emits `User Registered` so that downstream workspace provisioning can react.
* Business rules captured in the canvas:
  * Passwords must be stored with strong hashing.
  * Auth tokens expire after **24 hours**.

#### Billing & Subscription
* Owns recurring subscription state and quota enforcement.
* Integrates with a PCI-compliant external payment gateway instead of storing card data locally.
* Emits `Upgraded to Pro Plan` so that workspace limits can be recalculated asynchronously.
* Business rules captured in the canvas:
  * Access to AI features is blocked when quota is exhausted for the current plan.
  * Payment execution is delegated to an external PCI-compliant provider.

#### Integration Gateway
* Owns outbound agile-tool integration concerns and protects the core domain from third-party API drift.
* Reacts to `Story Approved`, translates internal user story semantics to external ticket schemas, and issues `Create Issue` to Jira-like PM services.
* Returns export confirmation through `Story Exported`.
* Business rules captured in the canvas:
  * Failures in third-party tools must not break the core ReqsAI workflow.
  * External schemas are translated into a generic internal agile ticket model before export.

### 3.9. Ubiquitous Language
Use these terms consistently in code, docs, APIs, and events:

| Term | Meaning |
|:-----|:--------|
| **Session** | A time-bounded elicitation meeting recorded by an analyst to capture requirements. |
| **Transcript** | Consolidated spoken content obtained from the session, optionally segmented by timestamps/speakers. |
| **User Story** | A structured requirement expressed from the end-user perspective. |
| **Gherkin Format** | Acceptance criteria syntax using `Given / When / Then`. |
| **Tenant** | A logically isolated client organization under the schema-per-tenant model. |
| **Project** | A bounded initiative inside one organization that owns its glossary and requirement history. |
| **Glossary** | Background information and domain knowledge uploaded to contextualize AI generation. |
| **Subscription** | The recurring commercial agreement defining plan tier and quotas. |
| **Quota** | The allowed consumption envelope for AI features, such as session minutes or generation tokens. |
| **External Connection** | The configured bridge between a ReqsAI workspace and a third-party PM tool. |
| **Agile Ticket** | The external representation of an approved story in Jira/Trello-like systems. |

### 3.10. Key Message Flows

#### Flow A. Requirements Capture and Generation
1. The analyst issues `Start Session` with `projectId`, `analystId`, and `timestamp`.
2. Requirement Discovery records `Recording Started`.
3. Requirement Discovery sends `Divide Audio` to the STT service with audio stream metadata.
4. STT returns `Speech Segments Identified` including transcript fragments, timestamps, and speaker identifiers.
5. Requirement Discovery requests `Project Data` from Workspace Management.
6. Workspace Management returns `Project Data Sent` including project context and technology stack/glossary.
7. Requirement Discovery sends `Generate User Story` to the LLM service with transcript body, project context, and system prompt.
8. The LLM response is normalized into `User Story Generated` with story id, Gherkin content, and acceptance criteria.

#### Flow B. Subscription and Organization Upgrade
1. A tech lead requests `Pro Plan Upgrade` for an organization and billing cycle.
2. Billing & Subscription initiates `Start Payment` against the payment gateway.
3. The gateway confirms `Payment Validated` with transaction and receipt details.
4. Billing & Subscription emits `Upgraded to Pro Plan`.
5. Workspace Management reacts by updating organization limits and available capabilities.

#### Flow C. Agile Synchronization
1. A tech lead approves a generated story via `Approve Story`.
2. Requirement Discovery emits `Story Approved`.
3. Integration Gateway translates the story and sends `Create Issue` to the external PM service.
4. The external service confirms issue creation with external id and URL.
5. Integration Gateway emits `Story Exported` back into the domain.

### 3.11. Context Map and Integration Patterns
The DDD context map defines the intended relationships between bounded contexts and external systems:

* **Requirement Discovery -> STT Service:** `ACL`
  Keeps speech-to-text provider contracts out of the core domain.
* **Requirement Discovery -> LLM Service:** `ACL`
  Prevents vendor lock-in and isolates prompt/model/provider drift.
* **Requirement Discovery -> Workspace Management:** `Customer / Supplier`
  The core domain negotiates the shape and freshness of project context required for RAG.
* **Workspace Management -> IAM:** `Conformist`
  Workspace adopts the identity model and role semantics emitted by IAM.
* **Workspace Management -> Billing & Subscription:** `Conformist`
  Workspace mirrors subscription/plan state needed for quota-aware behavior without synchronous coupling on every request.
* **Requirement Discovery -> Integration Gateway:** `Open Host Service + Published Language`
  Requirement Discovery publishes stable domain events that downstream consumers, beginning with Integration Gateway, can subscribe to.
* **Integration Gateway -> External PM Service:** `ACL`
  Maps internal story semantics to Jira/Trello-specific ticket schemas.

### 3.12. Implementation Implications
These DDD artifacts imply several implementation constraints that should remain explicit:

* The system should behave as a **modular monolith with clear bounded-context seams** unless there is a proven reason to split deployment units later.
* `Requirement Discovery` must stay insulated from vendor-specific STT/LLM SDKs through ports/adapters.
* `Integration Gateway` should support **one-way export first**. Bidirectional sync is an open roadmap concern, not an MVP requirement.
* `Workspace Management` is the source of truth for tenant/project/glossary context, but may maintain denormalized subscription facts required for fast policy checks.
* Public domain events should include enough metadata for multitenancy and traceability, especially `tenantId`, `projectId`, `sessionId`, `storyId`, event version, and timestamps.
* Session processing should be modeled as a **long-running workflow**, not as a single request/response transaction.
* Audio upload, segmentation, transcription, suggestion generation, and duplicate detection should be observable as separate pipeline stages.
* Story review state should distinguish at least: generated, updated, hidden, approved, exported, and temporarily discarded.
* Export integration should model both the connection state (`linked project/board`) and the execution state (`export started`, success, failure, retryable failure).

### 3.13. Assumptions, Verification Metrics, and Open Questions
The DDD canvases surfaced product assumptions that matter for engineering planning:

* **Requirement Discovery assumptions:** browser-based WebSocket audio streaming is stable for meetings up to roughly 2 hours; the LLM can infer business rules from transcript + glossary without excessive hallucination.
* **Workspace assumptions:** clients already possess PDFs/TXT documents that can be uploaded as glossary material; a user may need access to multiple organizations.
* **IAM assumptions:** JWT-based authentication is sufficient for the MVP; MFA is not mandatory on day one.
* **Billing assumptions:** fixed monthly tiers are enough initially; recurring billing complexity remains delegated to the payment provider.
* **Integration assumptions:** Jira and Trello cover most early integration demand; exports are one-way in the MVP.

Suggested metrics from the canvases to preserve as engineering targets:

* `Requirement Discovery`: 90% of generated user stories approved without manual edits; generation time after session end under 20 seconds.
* `Workspace Management`: project context retrieval under 500ms; zero cross-tenant leaks in security audits.
* `IAM`: 100% of unauthorized API requests blocked.
* `Billing & Subscription`: 100% of payments processed without storing local credit card data.
* `Integration Gateway`: external API schema changes should require zero modifications in the core Requirement Discovery module.

Open product/architecture questions still visible in the DDD artifacts:

* How should partial audio recovery work if WebSockets or the STT provider fail mid-session?
* Should the platform support the same project across multiple organizations in the future?
* Is SSO with Google/Microsoft a day-one requirement or a later enterprise feature?
* Should billing include a grace period after failed recurring payments before access is locked?
* Should the integration model evolve toward bidirectional synchronization or remain export-only?

---

## 4. Software Architecture Views (C4 Model)

### 4.1. C4 Scope and Intent
The software architecture is additionally described through the **C4 model**, moving from the surrounding ecosystem down to deployable containers and infrastructure topology. These views are aligned with previously established quality attributes and constraints, especially:

* modular monolith as the current backend architecture,
* secure multitenancy,
* in-memory fault-tolerant modular communication,
* AI-assisted processing with RAG,
* operational simplicity and fast time-to-market.

### 4.2. System Landscape View
At the landscape level, ReqsAI sits inside the broader Kntro-Soft enterprise ecosystem and collaborates with both end users and the external tools they already use in day-to-day work.

#### 4.2.1. Core Actors
* **Technical Lead**
* **Enterprise Analyst**

#### 4.2.2. Core System
* **ReqsAI System:** central system that processes transcripts, analyzes requirements, and structures them into user stories.

#### 4.2.3. External Ecosystem Dependencies
* **STT API:** receives audio fragments or streams and returns transcript segments.
* **Embedding API:** converts text into vectors for the RAG index.
* **LLM API:** generates high-value language outputs and helps structure uploaded knowledge and inferred stories.
* **Payment Gateway:** processes subscription transactions.

#### 4.2.4. Customer Landscape Tools
* **Video Conferencing Tools:** Google Meet, Zoom, Microsoft Teams, or similar systems where discovery meetings occur. These are the upstream source of recorded audio.
* **Project Management Tools:** Jira, Trello, Linear, and similar backlog systems where approved stories are exported.
* **Email Service Provider:** channel for invitations, validation, and transactional communication.
* **Customer Support Channel:** support contact path for users to resolve doubts and report issues.

#### 4.2.5. Landscape-Level Interpretation
ReqsAI does not replace the customer ecosystem. It acts as the **AI-powered bridge** between requirement conversations and execution tooling, absorbing audio/context on the upstream side and exporting approved backlog assets on the downstream side.

### 4.3. System Context View
At the context level, ReqsAI is treated as a single system boundary that orchestrates all meaningful interactions with actors and external services.

#### 4.3.1. Inbound Responsibilities
* **Technical Lead**
  * starts live recording sessions,
  * reviews and approves generated stories.
* **Enterprise Analyst**
  * manages workspaces and organizations,
  * uploads glossary/documents,
  * manages B2B payments and related administrative setup.

#### 4.3.2. Outbound Operational Systems
* **Payment Gateway:** receives payment requests to activate higher-value subscription capabilities.
* **Email Service Provider:** receives requests for account validation and password-recovery email delivery.

#### 4.3.3. Outbound Core Technology Systems
* **STT API:** receives real-time audio and returns segmented text with low latency.
* **Embedding API:** used to vectorize glossary entries, uploaded project knowledge, and generated stories.
* **LLM API:** used both for document understanding/structuring and for Gherkin-oriented user story generation.
* **Project Management Tool:** receives approved and transformed stories as external issues.

#### 4.3.4. Context-Level Responsibility Split
The context view makes a key architectural distinction explicit:

* **Workspace Management** uses AI services to process and enrich project knowledge for RAG.
* **Requirement Discovery** uses AI services to process meeting audio, retrieve context, and generate backlog artifacts.

### 4.4. Container View
The container view decomposes ReqsAI into user-facing applications, entry-point infrastructure, backend execution, and persistence.

#### 4.4.1. User Interface Containers
* **Web Application**
  * primary desktop/browser experience,
  * optimized for deep analysis, project configuration, and story review,
  * technology direction: **Angular**.
* **Mobile App**
  * mobile access to capture meetings and review progress on the go,
  * technology direction: **Flutter** for cross-platform delivery.

#### 4.4.2. Edge and Traffic Management Containers
* **CDN & Reverse Proxy:** **Amazon CloudFront**
  * serves static SPA assets,
  * caches edge content,
  * mitigates DDoS exposure,
  * routes dynamic web API traffic toward the API gateway.
* **API Gateway:** **AWS API Gateway**
  * unified entry point for REST and WebSocket traffic,
  * serves both web and mobile clients,
  * handles throttling, TLS termination, and top-level consumption metrics.

#### 4.4.3. Backend Container
* **ReqsAI Backend Application**
  * technology direction: **Java 25 + Spring Boot 4**,
  * deployed as a single Dockerized backend unit,
  * structured internally as a **Spring Modulith-based modular monolith**,
  * bounded contexts communicate through public interfaces and in-memory events instead of internal network calls.

This reinforces the earlier DDD decision: the architecture prioritizes strong module boundaries without incurring distributed-system latency and operational complexity too early.

#### 4.4.4. Persistence Container
* **Database:** **AWS RDS PostgreSQL + pgvector**
  * relational source of truth,
  * vector-capable storage for semantic retrieval,
  * shared persistence layer for the current modular-monolith phase.

#### 4.4.5. Container-Level Communication Model
* **Web channel:** Browser -> CloudFront -> API Gateway -> Backend
* **Mobile channel:** Mobile App -> API Gateway -> Backend
* **Internal backend channel:** bounded contexts communicate through in-memory domain events and module interfaces
* **External integrations:** each bounded context calls the external systems it needs directly through its own adapters

#### 4.4.6. Bounded Context to Integration Responsibility Mapping
* **IAM** -> Email Service Provider
* **Billing & Subscription** -> Payment Gateway
* **Workspace Management** -> Embedding API and LLM API for document/glossary ingestion and structuring
* **Requirement Discovery** -> STT API, Embedding API, and LLM API for meeting processing and story generation
* **Integration Gateway** -> Project Management API

### 4.5. Deployment View
The deployment view maps the software containers to cloud infrastructure and runtime nodes.

#### 4.5.1. Client-Side Runtime Nodes
* **User computers / browsers** run the Angular web application.
* **Mobile devices (iOS/Android)** run the Flutter mobile application.

#### 4.5.2. Edge Layer
* **AWS Edge Locations / CloudFront**
  * caches and serves static web assets near the user,
  * acts as the first inbound layer for web traffic,
  * forwards dynamic requests toward the main AWS region.

#### 4.5.3. Core Processing Region
The main execution environment is placed in **AWS us-east-1 (Virginia)**.

Core nodes:

* **AWS API Gateway**
  * receives dynamic traffic from CloudFront for web,
  * receives direct HTTPS traffic from the mobile app.
* **AWS ECS + Fargate Cluster**
  * runs the backend as serverless containers,
  * supports elastic execution without direct EC2 host management,
  * encapsulates the backend runtime as a task definition.

#### 4.5.4. Observability Topology
The deployment design introduces a more detailed observability model than what existed in `ARCHITECTURE.md` previously:

* **Grafana Alloy** runs as a sidecar alongside the backend container.
* A dedicated **Observability Server** on **AWS EC2 + Docker Compose** hosts:
  * **Prometheus** for metrics,
  * **Loki** for logs,
  * **Tempo** for distributed traces,
  * **Grafana** for unified dashboards.

Telemetry flow:

* backend metrics are exposed through `/actuator/prometheus`,
* logs are collected from container stdout,
* traces are exported via OTLP,
* Grafana Alloy pushes telemetry to the observability server,
* Grafana queries Prometheus, Loki, and Tempo to visualize operational state.

#### 4.5.5. Persistence Layer
* **AWS RDS PostgreSQL + pgvector**
  * handles transactional persistence for all bounded contexts,
  * stores vector embeddings for the RAG workflow,
  * supports managed backups, patching, and failover concerns required by the platform.

### 4.6. Consolidated Architecture Decisions
The consolidated architecture views sharpen several concrete architectural decisions:

* the product architecture is documented explicitly with **C4 views**,
* the web frontend is expected to be **Angular**,
* the mobile application is expected to be **Flutter**,
* static web assets are served through **CloudFront**,
* API ingress is centralized through **AWS API Gateway**,
* backend runtime is deployed on **AWS ECS + Fargate**,
* relational and vector persistence are consolidated in **AWS RDS PostgreSQL + pgvector**,
* observability is treated as a first-class concern through **Grafana Alloy + Prometheus/Loki/Tempo/Grafana**,
* `Embedding API` is a distinct external dependency, separate from the `LLM API`,
* both **Workspace Management** and **Requirement Discovery** participate in RAG preparation, but at different stages and with different responsibilities.

### 4.7. Module-Level Component Views
The component views add **C4 Level 3 component detail** for each bounded context. Together, they make the internal module structure much more explicit.

#### 4.7.1. Common Internal Module Pattern
Across all five bounded contexts, the component diagrams consistently show the same architectural shape:

* **REST Controllers** as the inbound HTTP interface layer
* **Command Handlers** for state-changing use cases
* **Query Handlers** for read-only access patterns
* **Domain Model** containing business invariants and domain logic
* **JPA Repositories** as persistence adapters
* **External Adapters** for infrastructure or third-party integration
* **Module API Facades** as the only in-process surface exposed to other bounded contexts
* **Application listeners** reacting to in-memory Spring application events

This is important because it confirms the intended implementation style is not only "modular monolith" in the abstract, but specifically a **ports-and-adapters / CQRS-leaning modular architecture** inside each Spring Modulith module.

#### 4.7.2. Requirement Discovery Component View
The discovery component diagram introduces the following internal pieces:

* `DiscoverySessionController`
* `UserStoryController`
* session lifecycle command handlers
* story review command handlers
* read-only query handlers
* `SessionCompletedEventListener`
* `LlmTranscriptProcessingAdapter` and `GherkinGenerationAdapter`
* `DiscoveryModuleApiImpl`

This also sharpens the discovery responsibilities:

* sessions and stories are managed independently but within the same module,
* story generation is event-driven after session completion,
* the AI processing boundary is adapter-based,
* the module publishes usable outputs to other contexts through a dedicated module API.

#### 4.7.3. Workspace Management Component View
The workspace component diagram adds:

* separate controllers for workspaces, projects, members, documents, and glossary terms,
* specialized command handlers by sub-area,
* `S3FileStorageAdapter`,
* `SubscriptionActivatedEventListener`,
* `WorkspaceModuleApiImpl`.

This shows Workspace Management is broader than a simple tenant registry. It is also:

* the owner of project documents,
* the owner of glossary maintenance,
* the owner of collaboration roles and membership assignment,
* the consumer of subscription activation signals from Billing.

#### 4.7.4. IAM Component View
The IAM component diagram makes several tactical decisions explicit:

* `AuthenticationController` and `UserController` split authentication from profile management,
* session lifecycle is separated into dedicated token/refresh handlers,
* email verification is triggered by `EmailVerificationRequestedEventListener`,
* infrastructure adapters include JWT, BCrypt, OTP generation, and SMTP,
* `WebSecurityConfiguration` and `BearerAuthorizationRequestFilter` are part of the module boundary,
* `IamModuleApiImpl` is the in-process identity surface for other contexts.

This confirms IAM is intended to be **stateless JWT-based security** with refresh-token rotation and explicit verification flows.

#### 4.7.5. Billing & Subscription Component View
The billing component diagram introduces:

* `PlanController`
* `SubscriptionController`
* subscription lifecycle command handlers
* read-only plan/subscription query handlers
* `BillingModuleApiImpl`
* provider adapters for both `Stripe` and `Culqi`

This extends the earlier architecture by documenting that the payment abstraction is already designed for **multiple payment providers**, not a single gateway implementation.

#### 4.7.6. Integration Gateway Component View
The gateway component diagram adds:

* `IntegrationController`
* `SyncController`
* OAuth connection command handlers
* sync/retry command handlers
* `UserStoryApprovedEventListener`
* `JiraCloudApiAdapter`

This view makes two things explicit:

* approved-story export is event-driven from Discovery into Gateway,
* synchronization and retry behavior are first-class use cases, not incidental infrastructure details.

### 4.8. Tactical Domain Model Views
The new `*-class.puml` diagrams enrich the DDD documentation with tactical class design details that were not previously documented in the architecture file.

#### 4.8.1. Requirement Discovery Tactical Model
Newly visible tactical elements include:

* `DiscoverySession` aggregate root
* `UserStory` aggregate root
* `ShareLink` aggregate root
* `AcceptanceCriterion` and `TranscriptSegment` entities
* enums such as `SessionStatus`, `StoryStatus`, `Priority`, and `CriterionType`
* domain events such as `SessionProcessingStartedEvent`, `UserStoriesGeneratedEvent`, `UserStoryApprovedEvent`, `ShareLinkCreatedEvent`, and `SpeakerLabelUpdatedEvent`
* API-level event `AiTokensConsumedEvent`

This reveals several new architectural concepts that were not explicit before:

* transcript segments are modeled explicitly, not just flattened into one transcript blob,
* public or semi-public sharing is planned through `ShareLink`,
* discovery publishes token-consumption information outward,
* speaker relabeling is a domain-level concern.

#### 4.8.2. Workspace Management Tactical Model
The workspace class diagram adds richer aggregates and value objects:

* aggregate roots: `Organization`, `Member`, `Project`, `ProjectRole`, `ProjectMember`, `ProjectDocument`, `Glossary`
* value objects: `PlanLimits`, `TechnicalProfile`
* project constraints as explicit domain concepts
* permission model with values such as `READ_PROJECT`, `WRITE_PROJECT`, `MANAGE_MEMBERS`, `UPLOAD_DOCUMENTS`, `MANAGE_GLOSSARY`, `RUN_DISCOVERY`

This matters because it shows:

* organization membership and project assignment are separate concepts,
* role assignment exists at both organization and project scope,
* project technical metadata is a first-class value object, not just unstructured text,
* glossary terms and project constraints are part of the contextual RAG model.

#### 4.8.3. IAM Tactical Model
The IAM tactical model confirms three main aggregate roots:

* `Account`
* `User`
* `RefreshToken`

It also adds:

* `Email` and `UserPreferences` as value objects
* account states such as `PENDING_VERIFICATION`, `ACTIVE`, `SUSPENDED`, `DELETED`
* events like `AccountCreatedEvent`, `EmailVerificationRequestedEvent`, `PasswordResetRequestedEvent`, `TermsAcceptedEvent`, `AccountVerifiedEvent`, and `SessionRevokedEvent`

Newly explicit concerns include:

* terms acceptance/versioning,
* password reset token lifecycle,
* last visited organization/project preferences,
* refresh-token hashing and revocation as modeled domain behavior.

#### 4.8.4. Billing Tactical Model
The billing tactical class view confirms:

* `Subscription` as the key aggregate root
* `PaymentProviderRef` as a value object
* `PlanType`, `SubscriptionStatus`, and `PaymentProvider` enums
* events such as `SubscriptionAssignedEvent`, `SubscriptionUpgradedEvent`, `SubscriptionCancelledEvent`, `TokenQuotaExceededEvent`, `SubscriptionReactivatedEvent`, and `QuotaResetEvent`

This formalizes quota usage as a real business concept rather than a loose metric.

#### 4.8.5. Gateway Tactical Model
The gateway tactical class view introduces:

* aggregate roots: `OAuthState`, `Integration`, `ExportRecord`
* value object: `IntegrationConfig`
* enums: `IntegrationProvider`, `IntegrationStatus`, `ExportStatus`
* events: `OAuthConnectionEstablishedEvent`, `StoryExportedEvent`, `ExportFailedEvent`, `IntegrationActivatedEvent`

This clarifies that the gateway does not only store credentials. It also models:

* OAuth anti-CSRF state,
* per-project integration configuration,
* export lifecycle history,
* retryable synchronization records.

### 4.9. Persistence Views by Bounded Context
The new `*-database.puml` diagrams add concrete persistence details that sharpen the current data architecture.

#### 4.9.1. General Persistence Conventions
Across the database diagrams, several conventions are consistent:

* all tables include audit columns: `created_at`, `updated_at`, `created_by`, `updated_by`
* cross-bounded-context references are often **logical references without physical foreign keys**
* value objects are frequently embedded into table columns
* several structured values are serialized into text/JSON-like fields in the current design

#### 4.9.2. Requirement Discovery Persistence
The discovery database view introduces these tables:

* `discovery_sessions`
* `user_stories`
* `acceptance_criteria`
* `transcript_segments`
* `share_links`

Important persistence signals:

* `project_id` is a logical reference back to Workspace without a physical FK
* transcript segment ordering is persisted through `sequence`
* acceptance criteria are stored as child rows, not embedded into the story row
* share links use a unique indexed `token`

#### 4.9.3. Workspace Persistence
The workspace database view adds:

* `organizations`
* `members`
* `projects`
* `project_constraints`
* `project_roles`
* `project_members`
* `project_documents`
* `glossaries`
* `glossary_terms`

Important persistence signals:

* `organizations` embeds `PlanLimits` as `max_*` columns
* `projects` embeds `TechnicalProfile`
* `description_embedding`, constraint embeddings, and glossary-term embeddings are persisted directly
* each project owns exactly one glossary
* project membership is materialized separately from organization membership

#### 4.9.4. IAM Persistence
The IAM database view confirms:

* `accounts`
* `users`
* `refresh_tokens`

Important persistence signals:

* `accounts` and `users` are a strict one-to-one relationship
* `email_value` is unique and indexed
* refresh tokens are stored by `token_hash`, not plaintext
* user preferences are embedded inside the `users` table

#### 4.9.5. Billing Persistence
The billing database view confirms a compact persistence model centered on:

* `subscriptions`

Important persistence signals:

* `organization_id` is unique, enforcing one subscription per organization
* `payment_provider` and `payment_external_id` embed the provider reference
* token consumption is persisted directly in `token_quota_used`

#### 4.9.6. Gateway Persistence
The gateway database view introduces:

* `oauth_states`
* `integrations`
* `export_records`

Important persistence signals:

* OAuth `state` values are unique and indexed
* integration config is stored through `base_url`, `project_key`, and serialized `issue_type_mapping`
* external provider tokens are explicitly modeled as encrypted-at-rest values
* export history is stored separately from integration configuration

### 4.10. Diagram Source Strategy
The architecture documentation strategy itself is worth preserving:

* diagrams are maintained as **PlantUML source files** (`.puml`) plus rendered `.png` artifacts
* C4 helper includes are kept locally inside the repository
* visual consistency is centralized under reusable style definitions

This is valuable because it means the architecture is not only documented visually, but also treated as **versioned architecture-as-code** that can evolve alongside the implementation.
