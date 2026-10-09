/**
 * Codebase — the client's source repositories connected to a project, indexed into a map of modules
 * (what each part of the system does and which business rules it implements) so the requirements
 * copilot knows what is already built.
 * <p>
 * Only summaries, symbol names and the detected technical profile are stored; raw source code is read
 * once while indexing and never persisted. Access tokens of private repositories are encrypted at rest.
 * <p>
 * Layers: {@code domain}, {@code application}, {@code infrastructure}, {@code interfaces}. Other modules
 * read it only through the {@code codebase::api} named interface ({@link com.kntro.reqsai.codebase.api}).
 * Depends on the OPEN {@code shared} module and {@code billing::api} (AI token metering).
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"shared", "billing::api"})
package com.kntro.reqsai.codebase;
