/**
 * Discovery — capture sessions, user stories and the AI pipeline bounded context.
 * <p>
 * Live capture sessions, transcript segments, AI-assisted requirement/user-story generation and
 * pgvector embeddings. Owners: <strong>Jhosepmyr + Erick</strong>.
 * <p>
 * Layers: {@code api}, {@code domain}, {@code application}, {@code infrastructure}, {@code interfaces}.
 * Depends on the OPEN {@code shared} module, the {@code workspace::api} named interface for project
 * context enrichment in realtime suggestions, and the {@code billing::api} named interface to meter
 * AI token consumption against the organization's plan quota.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"shared", "workspace::api", "billing::api"})
package com.kntro.reqsai.discovery;
