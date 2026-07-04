package com.kntro.reqsai.iam.infrastructure.email.strategy;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

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

    private String sentBody() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        // Read the decoded content so quoted-printable soft line-breaks don't split the asserted URL.
        return captor.getValue().getContent().toString();
    }
}
