/**
 * Overview — read-only dashboards of a project and of an organization.
 * <p>
 * Composes the figures each bounded context publishes through its
 * {@code insights} (or {@code api}) named interface — backlog, sessions and AI
 * suggestions from discovery, glossary, constraints, documents and people from
 * workspace, connected code from codebase — into the two pages the web app
 * shows as "Overview". Owns no tables: every number is a SQL aggregate computed
 * by the context that owns the data, never a loaded entity.
 * <p>
 * Layers: {@code domain} (errors), {@code application} (the composing services)
 * and {@code interfaces} (REST). Depends on the OPEN {@code shared} module and
 * the named interfaces above; it never reaches into another module's
 * repositories or entities. Authorization follows the data: the project page
 * returns only the sections the caller's project permissions allow, and the
 * organization page only covers the projects the caller may see.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"shared", "workspace::api",
        "workspace::insights", "discovery::insights", "codebase::api"})
package com.kntro.reqsai.overview;
