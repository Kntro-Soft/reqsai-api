package com.kntro.reqsai.iam.infrastructure.email.strategy;

import jakarta.mail.BodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Infrastructure: SMTP email adapter — project templates")
@ExtendWith(MockitoExtension.class)
class SmtpEmailAdapterTest {

    private static final String APP_URL = "https://app.reqsai.com";

    @Mock
    private JavaMailSender mailSender;

    private SmtpEmailAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SmtpEmailAdapter(mailSender, APP_URL, "noreply@reqsai.com", "Mailpit");
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((jakarta.mail.Session) null));
    }

    @Test
    @DisplayName("project invitation email names the project and role and uses the tokenized accept link")
    void project_invitation_email_content() throws Exception {
        adapter.sendProjectInvitationEmail("invitee@example.com", "Invitee", "Acme", "MEMBER",
                "Apollo", "Analyst", "Owner", "raw-token");

        String body = sentBody();
        assertThat(body).contains("Apollo");
        assertThat(body).contains("Analyst");
        assertThat(body).contains("Acme");
        assertThat(body).contains(APP_URL + "/invitations/accept?token=raw-token");
    }

    @Test
    @DisplayName("project assignment notification names the project and role and links straight to the project")
    void project_assignment_email_content() throws Exception {
        adapter.sendProjectAssignmentEmail("member@example.com", "Member", "Apollo", "Analyst", "the-project-id");

        String body = sentBody();
        assertThat(body).contains("Apollo");
        assertThat(body).contains("Analyst");
        assertThat(body).contains(APP_URL + "/projects/the-project-id");
        // Notification, not an accept flow — no acceptance token link.
        assertThat(body).doesNotContain("/invitations/accept");
    }

    @Test
    @DisplayName("message carries both a plain-text part and an HTML part with matching content")
    void message_has_plain_text_and_html_parts() throws Exception {
        adapter.sendPasswordResetEmail("user@example.com", "User", "raw-token");

        List<String> leaves = leafContents();
        // Content-Type headers on an in-memory, never-transported MimeMessage aren't reliable in this
        // test harness (JavaMail only finalizes them on save/serialize), so parts are told apart by
        // their actual content instead — which is what a real client-side render depends on anyway.
        List<String> htmlLeaves = leaves.stream().filter(s -> s.contains("<html")).toList();
        List<String> plainLeaves = leaves.stream().filter(s -> !s.contains("<html")).toList();

        assertThat(htmlLeaves).hasSize(1);
        assertThat(plainLeaves).hasSize(1);
        assertThat(plainLeaves.getFirst()).contains(APP_URL + "/auth/reset-password?token=raw-token");
        assertThat(htmlLeaves.getFirst()).contains(APP_URL + "/auth/reset-password?token=raw-token");
    }

    @Test
    @DisplayName("dynamic values are HTML-escaped so a hostile display/org name can't inject markup")
    void dynamic_values_are_html_escaped() throws Exception {
        adapter.sendInvitationEmail("victim@example.com", "<img src=x onerror=alert(1)>",
                "<script>alert('org')</script>", "MEMBER", "Owner", "raw-token");

        String htmlPart = sentHtmlPart();
        assertThat(htmlPart).doesNotContain("<img src=x onerror=alert(1)>");
        assertThat(htmlPart).doesNotContain("<script>alert('org')</script>");
        assertThat(htmlPart).contains("&lt;script&gt;");
    }

    private String sentBody() throws Exception {
        return String.join("\n", leafContents());
    }

    private String sentHtmlPart() throws Exception {
        return leafContents().stream()
                .filter(s -> s.contains("<html"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No HTML part found in the sent message"));
    }

    /** Flattens the (possibly nested mixed/related/alternative) MIME tree into its leaf string bodies. */
    private List<String> leafContents() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        List<String> leaves = new ArrayList<>();
        collectLeaves(captor.getValue().getContent(), leaves);
        return leaves;
    }

    private void collectLeaves(Object content, List<String> leaves) throws Exception {
        if (content instanceof MimeMultipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart part = multipart.getBodyPart(i);
                collectLeaves(part.getContent(), leaves);
            }
        } else {
            leaves.add(content.toString());
        }
    }
}
