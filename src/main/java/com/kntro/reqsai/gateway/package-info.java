/**
 * Gateway — external integrations bounded context.
 * <p>
 * Jira integration (OAuth connection + export of user stories). Owner: <strong>Marcelo</strong>.
 * <p>
 * Layers: {@code api}, {@code domain}, {@code application}, {@code infrastructure}, {@code interfaces}.
 * Depends only on the OPEN {@code shared} module.
 */
@org.springframework.modulith.ApplicationModule
package com.kntro.reqsai.gateway;
