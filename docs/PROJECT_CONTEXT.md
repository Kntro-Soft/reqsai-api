# ReqsAI Project Context

## 1. Purpose and Scope
This document centralizes the **business, product, market, and validation context** for ReqsAI.

For complementary technical material, use:

* [docs/DESING.md](DESING.md) for visual direction, UX guidelines, colors, typography, navigation patterns, and public-facing metatags.
* [docs/ARCHITECTURE.md](ARCHITECTURE.md) for DDD, bounded contexts, EventStorming outputs, multi-tenancy constraints, naming conventions, and architectural decisions.

---

## 2. Executive Summary and Startup Profile
* **Company Name:** Kntro-Soft (Lima, Peru)
* **Product Name:** ReqsAI
* **Core Mission:** Eliminate information loss during software requirements elicitation by leveraging real-time Generative AI and Natural Language Processing (NLP).
* **Target Market:** Tech startups, software factories, and enterprise IT departments operating under Agile methodologies within Latin America.

### 2.1. Core Value Propositions
* **Instant Documentation:** Automatic generation of structured User Stories accompanied by functional acceptance criteria written in **Gherkin syntax** (`Given-When-Then`) via state-of-the-art LLMs.
* **Real-Time Consultative Assistance:** Live acoustic/text processing during discovery meetings to prompt analysts with strategic questions, revealing hidden edge cases and logic gaps *while* the client is present.
* **Intelligent Context Integration (RAG):** Utilization of Retrieval-Augmented Generation to surface previous project context, preventing requirement duplication and enforcing consistent architectural alignments.
* **Enterprise Privacy:** A multi-tenant architecture utilizing a rigid **schema-per-tenant isolation strategy** to guarantee strict boundaries around organizational data and intellectual property.

---

## 3. Problem Statement and Market Analysis (5W2H)

* **WHO:** Affects Systems Analysts, Product Owners, Business Analysts, Technical Leaders, and downstream engineering squads across LATAM software organizations.
* **WHAT:** Critical data loss during client discovery meetings. Human cognitive limits prevent analysts from practicing active listening while simultaneously documenting comprehensive edge cases. This translates to an immediate accumulation of functional technical debt.
* **WHERE:** Remote, hybrid, or in-person discovery and scoping sessions within software development environments.
* **WHEN:** Occurs during the **Requirements Elicitation Phase**. The problem compounds post-meeting when analysts attempt to retroactively reconstruct agreements from raw memory or long videos.
* **WHY:** Over-reliance on passive, artisanal documentation workflows in a highly automated ecosystem. Communication issues remain a primary contributor to IT project failure.
* **HOW:** Materializes as upstream rework, infinite feedback loops, and delayed sprints because developers are forced to freeze tasks to ask for structural definitions.
* **HOW MUCH:** Industry data indicates fixing a requirement flaw during development costs 10x more than during design, skyrocketing to 100x if it slips into production. The targeted operational benchmark for ReqsAI is a **40% reduction in requirements-focused follow-up meetings** and a **first-pass User Story acceptance rate exceeding 80%**.

---

## 4. Target User Personas and Segmentation

### 4.1. Alex: The Technical Leader (Startup Segment)
* **Demographics:** 30–35 years old, engineering/computer science background, native digital tools user.
* **Context:** Operates in high-velocity startup environments, scaling hybrid roles between product architecture and team leadership.
* **Frictions:** High cognitive load, friction moving from conversations to clean backlogs, detests manual formatting documentation tasks.
* **Desired Outcome:** Code-ready Gherkin criteria synchronized into backlogs immediately post-meeting so the sprint velocity never drops.

### 4.2. Claudia: The Enterprise Systems Analyst (Corporate Segment)
* **Demographics:** 30–45 years old, standard enterprise tooling ecosystems (Azure DevOps, Enterprise Architect), corporate governance frameworks (SAFe/CMMI).
* **Context:** Acts as a strict institutional bridge between non-technical corporate business stakeholders and specialized dev teams.
* **Frictions:** Overwhelmed by massive multi-hour recordings, prone to missing nested edge cases, bound to rigorous documentation and strict compliance regulations.
* **Desired Outcome:** Automated synthesis of vast requirements, immediate alignment with existing architectures via RAG, and bulletproof security isolation.

---

## 5. Product Metric Framework (AARRR Focus)
* **Acquisition:** Target a 25% conversion rate from outbound/inbound loops to a 14-day free trial.
* **Activation:** 40% of trial accounts must process $\ge 3$ live meetings and generate complete User Story sets within week one.
* **Retention:** Stabilize a 60% conversion rate from active trial startups into standard monthly subscribers.
* **Revenue:** Focus on a $49 average MRR for the startup tier and custom annual contracts ($\$4,000+$) for mid-market/enterprise levels.
* **Referral:** 25% of active power users referring the product to peer Product Managers and Engineers.

---

## 6. Product Hypotheses and Validation Priorities

1. **Live Advisory Value (High Risk/High Value):** Providing live, contextual recommendations on missed edge cases to the Systems Analyst drives exhaustive discovery. *Metric: Analysts must manually or vocally adopt $\ge 60\%$ of real-time AI suggestions during live client interaction.*
2. **Delivery Velocity (High Risk/High Value):** Contextual RAG execution saves processing hours for Tech Leads. *Metric: Average human modification or corrective manual editing on generated User Stories falls below 15 minutes per session.*
3. **Backlog Ecosystem Retainers:** Seamless workspace exporting directly impacts user stickiness. *Metric: $\ge 80\%$ of approved platform assets are synched out to Jira / third-party tools within 10 minutes of meeting completion.*
4. **Data Isolation as Trust Catalyst:** Schema-level infrastructure reduces enterprise privacy friction. *Metric: Enterprise legal/compliance teams bypass manual review friction directly upon reading tenant-isolation technical documentation.*
