package com.kntro.reqsai.iam.infrastructure.email.template;

import org.springframework.web.util.HtmlUtils;

import java.time.Year;
import java.util.regex.Pattern;

/**
 * Renders {@link EmailContent} into an HTML body and a matching plain-text body for
 * {@code multipart/alternative} delivery.
 * <p>
 * The HTML follows the conventions that keep transactional email readable across mail clients
 * (Outlook's Word-based renderer included): a table-based layout instead of flexbox/grid, every
 * visual rule inlined as a {@code style} attribute rather than a stylesheet, a fixed 600px content
 * width, and a hidden preheader so the inbox preview line is under our control instead of showing
 * raw HTML. Every dynamic value is HTML-escaped before insertion — email content built from
 * user-supplied strings (display names, organization/project names) is otherwise an HTML-injection
 * vector.
 */
public final class EmailTemplateRenderer {

    private static final String INK = "#0f172a";
    private static final String ACCENT = "#ef4444";
    private static final String BODY_TEXT = "#1f2937";
    private static final String MUTED_TEXT = "#6b7280";
    private static final String BORDER = "#e5e7eb";
    private static final String PANEL_BG = "#f9fafb";
    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");

    private EmailTemplateRenderer() {
    }

    public static String html(EmailContent content) {
        StringBuilder paragraphsHtml = new StringBuilder();
        for (String paragraph : content.paragraphs()) {
            paragraphsHtml.append("""
                    <tr><td style="padding:0 0 16px;font-size:15px;line-height:1.6;color:%s;">%s</td></tr>
                    """.formatted(BODY_TEXT, boldToHtml(escape(paragraph))));
        }

        String ctaHtml = "";
        if (content.ctaText() != null && content.ctaUrl() != null) {
            String url = escape(content.ctaUrl());
            ctaHtml = """
                    <tr>
                      <td style="padding:8px 0 24px;">
                        <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                          <tr>
                            <td style="border-radius:8px;background-color:%s;">
                              <a href="%s" target="_blank"
                                 style="display:inline-block;padding:14px 28px;font-size:15px;font-weight:700;
                                        color:#ffffff;text-decoration:none;border-radius:8px;min-height:20px;">
                                %s
                              </a>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:0 0 24px;font-size:13px;line-height:1.5;color:%s;">
                        Si el botón no funciona, copia y pega este enlace en tu navegador:<br/>
                        <a href="%s" target="_blank" style="color:%s;word-break:break-all;">%s</a>
                      </td>
                    </tr>
                    """.formatted(ACCENT, url, escape(content.ctaText()), MUTED_TEXT, url, ACCENT, url);
        }

        String footnoteHtml = "";
        if (content.footnote() != null) {
            footnoteHtml = """
                    <tr><td style="padding:0 0 4px;font-size:13px;line-height:1.5;color:%s;">%s</td></tr>
                    """.formatted(MUTED_TEXT, boldToHtml(escape(content.footnote())));
        }

        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <meta name="color-scheme" content="light" />
                  <meta name="supported-color-schemes" content="light" />
                  <title>%s</title>
                </head>
                <body style="margin:0;padding:0;background-color:%s;font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;">
                  <div style="display:none;max-height:0;overflow:hidden;opacity:0;">%s</div>
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background-color:%s;">
                    <tr>
                      <td align="center" style="padding:32px 16px;">
                        <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0"
                               style="width:600px;max-width:100%%;background-color:#ffffff;border-radius:12px;overflow:hidden;border:1px solid %s;">
                          <tr>
                            <td style="background-color:%s;padding:24px 32px;">
                              <span style="font-size:18px;font-weight:700;color:#ffffff;letter-spacing:0.02em;">Reqs<span style="color:%s;">AI</span></span>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:32px;">
                              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0">
                                <tr><td style="padding:0 0 16px;font-size:20px;font-weight:700;color:%s;">%s</td></tr>
                                %s
                                %s
                                %s
                              </table>
                            </td>
                          </tr>
                          <tr>
                            <td style="background-color:%s;padding:20px 32px;border-top:1px solid %s;">
                              <p style="margin:0;font-size:12px;line-height:1.5;color:%s;">
                                Este es un correo automático de ReqsAI, no respondas a esta dirección.<br/>
                                &copy; %d ReqsAI &mdash; elicitación de requisitos asistida por IA.
                              </p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                escape(content.heading()), PANEL_BG, escape(content.preheader()), PANEL_BG, BORDER,
                INK, ACCENT, BODY_TEXT, escape(content.heading()), paragraphsHtml, ctaHtml, footnoteHtml,
                PANEL_BG, BORDER, MUTED_TEXT, Year.now().getValue());
    }

    public static String plainText(EmailContent content) {
        StringBuilder sb = new StringBuilder();
        sb.append(stripBold(content.heading())).append("\n\n");
        for (String paragraph : content.paragraphs()) {
            sb.append(stripBold(paragraph)).append("\n\n");
        }
        if (content.ctaText() != null && content.ctaUrl() != null) {
            sb.append(stripBold(content.ctaText())).append(": ").append(content.ctaUrl()).append("\n\n");
        }
        if (content.footnote() != null) {
            sb.append(stripBold(content.footnote())).append("\n\n");
        }
        sb.append("---\nEste es un correo automático de ReqsAI, no respondas a esta dirección.\n");
        return sb.toString().strip();
    }

    private static String escape(String raw) {
        return HtmlUtils.htmlEscape(raw, "UTF-8");
    }

    private static String boldToHtml(String escapedText) {
        return BOLD.matcher(escapedText).replaceAll("<strong>$1</strong>");
    }

    private static String stripBold(String raw) {
        return BOLD.matcher(raw).replaceAll("$1");
    }
}
