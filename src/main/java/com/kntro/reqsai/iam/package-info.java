/**
 * IAM — Identity &amp; Access Management bounded context.
 * <p>
 * Authentication, users, roles and permissions. Owner: <strong>Paul</strong>.
 * <p>
 * Layers: {@code api} (events + interfaces exposed to other modules), {@code domain},
 * {@code application}, {@code infrastructure}, {@code interfaces} (REST). Depends only on the OPEN
 * {@code shared} module.
 */
@org.springframework.modulith.ApplicationModule
package com.kntro.reqsai.iam;
