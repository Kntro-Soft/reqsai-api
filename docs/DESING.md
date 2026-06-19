# ReqsAI Design

## 1. Purpose and Scope
This document centralizes the **visual direction, UX guidelines, navigation patterns, and public-facing presentation rules** for ReqsAI.

For complementary documentation, use:

* [docs/PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) for business and product context.
* [docs/ARCHITECTURE.md](ARCHITECTURE.md) for DDD, bounded contexts, and system architecture.

---

## 2. UX and Design System Guidelines
The user interface draws inspiration from highly scannable, clean productivity tools (e.g., *Notion*), favoring vast negative space, soft structural shadows, and light/neutral foundational blocks.

### 2.1. Color and Visual Direction
* **Primary Controls / States:** Corporate Blues for actions, navigation focus, and main buttons.
* **Semantic Status Indication:** Greens for completed/approved, yellows for warnings/drafts, and reds for errors/failures.
* **AI Features Context:** Vibrant Violet / Neon Blue accents to distinguish AI-generated insights from human-authored content.

### 2.2. Typography and Layout Hierarchy
* Use high-contrast sans-serif typography.
* Maintain clear scaling from page titles down to tags, labels, and analytical metadata.
* Favor clarity and scanability over dense enterprise dashboards.

### 2.3. Navigation Blueprint
* **Sidebar (Persistent Global Nav):** `Dashboard -> Projects -> Sessions -> User Stories -> Integrations -> Billing -> Settings`
* **Topbar (Contextual Utilities):** workspace switcher, global omnibox search, rapid actions, and user profile/session controls.
* **Responsive In-App Drawers:** editing surfaces and inspection panels should prefer slide-out drawers over deeply nested windows.

### 2.4. Interaction Style
* The product should feel like a productivity workspace, not a bulky admin panel.
* AI-specific outputs should be visually distinct, but not visually noisy.
* Review flows such as Gherkin editing, confidence inspection, duplication handling, and export confirmation should be easy to scan and reversible where possible.

---

## 3. Public Metatags and Marketing Surface
For public-facing views, landing pages, and authentication checkpoints, use these semantic metadata markers:

* **Title:** `Reqs-AI | AI Requirements Elicitation Platform`
* **Description:** `Convert discovery meetings into structured user stories, Gherkin acceptance criteria and Jira-ready backlog items using AI.`
* **Keywords:** `requirements elicitation, user stories, Gherkin, Jira integration, AI assistant, software requirements`
