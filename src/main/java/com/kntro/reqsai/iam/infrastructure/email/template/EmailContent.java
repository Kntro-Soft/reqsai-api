package com.kntro.reqsai.iam.infrastructure.email.template;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Structured content for a transactional email, rendered into both an HTML and a plain-text body
 * by {@link EmailTemplateRenderer} — a single source of truth so the two versions never drift out
 * of sync (required for a proper {@code multipart/alternative} message).
 *
 * @param preheader   short inbox-preview snippet (40-90 chars); hidden in the rendered body, shown
 *                    by the mail client next to the subject line
 * @param heading     the card's visible title
 * @param paragraphs  body copy, one entry per paragraph, in reading order
 * @param ctaText     the button/link label, or {@code null} for a notice-only email with no action
 * @param ctaUrl      the button/link target, required when {@code ctaText} is set
 * @param footnote    small print under the button (e.g. an expiry or "if this wasn't you" notice),
 *                    or {@code null}
 */
public record EmailContent(
        String preheader,
        String heading,
        List<String> paragraphs,
        @Nullable String ctaText,
        @Nullable String ctaUrl,
        @Nullable String footnote
) {}
