package com.kntro.reqsai.shared.interfaces.websocket;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Stereotype for Spring WebSocket handlers that form the inbound interface layer.
 * Equivalent role to {@code @RestController} but for binary/text WebSocket endpoints.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface WebSocketController {

    @AliasFor(annotation = Component.class)
    String value() default "";
}
