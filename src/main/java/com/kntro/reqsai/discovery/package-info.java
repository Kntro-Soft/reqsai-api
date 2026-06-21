/**
 * Discovery — capture sessions, user stories and the AI pipeline bounded context.
 * <p>
 * Live capture sessions, transcript segments, AI-assisted requirement/user-story generation and
 * pgvector embeddings. Owners: <strong>Jhosepmyr + Erick</strong>.
 * <p>
 * Layers: {@code api}, {@code domain}, {@code application}, {@code infrastructure}, {@code interfaces}.
 * Depends on the OPEN {@code shared} module and the {@code workspace::api} named interface for
 * project context enrichment in realtime suggestions.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"shared", "workspace::api"})
package com.kntro.reqsai.discovery;
