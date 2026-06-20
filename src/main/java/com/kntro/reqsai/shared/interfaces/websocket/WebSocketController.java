package com.kntro.reqsai.shared.interfaces.websocket;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Stereotype annotation for Spring WebSocket handlers that form the inbound adapter layer.
 *
 * <p>Plays the same architectural role as {@code @RestController} for HTTP: it marks a class as an
 * entry point that translates protocol events (WebSocket frames, connection lifecycle) into
 * application commands or queries, and it registers the class as a Spring bean via
 * the composed {@link Component @Component}.
 *
 * <p>Use this annotation on classes that extend {@code BinaryWebSocketHandler} or
 * {@code TextWebSocketHandler} and belong in a {@code interfaces/websocket/} package. Infrastructure
 * concerns (tenant context, JWT auth, CORS) are handled by the shared base classes; the annotated
 * class should contain only protocol-to-command mapping logic.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface WebSocketController {

    @AliasFor(annotation = Component.class)
    String value() default "";
}
