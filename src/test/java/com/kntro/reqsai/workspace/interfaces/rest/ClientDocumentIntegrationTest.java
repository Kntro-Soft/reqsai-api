package com.kntro.reqsai.workspace.interfaces.rest;

import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import com.kntro.reqsai.testsupport.AbstractIntegrationTest;
import com.kntro.reqsai.testsupport.StubDocumentClassificationConfig;
import com.kntro.reqsai.testsupport.TestDocuments;
import com.kntro.reqsai.testsupport.TestJwtFactory;
import com.kntro.reqsai.workspace.api.ProjectDocumentSnapshot;
import com.kntro.reqsai.workspace.api.ProjectSnapshot;
import com.kntro.reqsai.workspace.api.WorkspaceModuleApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US22 end to end over HTTP and the tenant schema: a client PDF and a Word document are uploaded, their
 * text is extracted (real PDFBox / POI on generated files) and classified (deterministic stub), the
 * analyst applies the review (glossary terms and constraints added, duplicates skipped), discards an
 * unwanted upload, and executables or empty files are refused with a clear code. The applied summary
 * then reaches the AI project context through {@link WorkspaceModuleApi}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(StubDocumentClassificationConfig.class)
@Tag("integration")
@DisplayName("Integration: Client documents (US22)")
class ClientDocumentIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER_USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String MEMBER_USER_ID = "00000000-0000-0000-0000-000000000008";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PDF = "application/pdf";
    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WorkspaceModuleApi workspaceApi;

    @Test
    @DisplayName("a client PDF is analyzed, reviewed and applied into glossary, constraints and AI context")
    void upload_review_and_apply() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        String schema = "\"tenant_" + slug + "\"";
        UUID orgId = createOrganization(suffix, slug);
        UUID projectId = createProject(orgId, slug);
        String documents = "/api/organizations/" + orgId + "/projects/" + projectId + "/documents";
        assertThat(post(OWNER_USER_ID, orgId, "/api/organizations/" + orgId + "/projects/" + projectId + "/glossary",
                Map.of("term", "Comensal", "definition", "Cliente del restaurante.")).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        byte[] pdf = TestDocuments.pdf("Términos de referencia del sistema de reservas.",
                "El comensal reserva una mesa indicando fecha, hora y personas.",
                "El sistema debe cumplir la Ley 29733 de protección de datos personales.");
        ResponseEntity<String> uploaded = upload(OWNER_USER_ID, orgId, documents, "Terminos de referencia.pdf", PDF, pdf);

        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode analysis = JSON.readTree(uploaded.getBody());
        assertThat(analysis.path("fileName").asString()).isEqualTo("Terminos de referencia.pdf");
        assertThat(analysis.path("mediaType").asString()).isEqualTo(PDF);
        assertThat(analysis.path("sizeBytes").asLong()).isEqualTo(pdf.length);
        assertThat(analysis.path("classified").asBoolean()).isTrue();
        assertThat(analysis.path("truncated").asBoolean()).isFalse();
        assertThat(analysis.path("documentType").asString()).isEqualTo("TECHNICAL_SPEC");
        assertThat(analysis.path("context").asString()).isEqualTo(StubDocumentClassificationConfig.SUMMARY);
        assertThat(analysis.path("glossary").size()).isEqualTo(2);
        assertThat(analysis.path("glossary").get(0).path("term").asString()).isEqualTo("Comensal");
        assertThat(analysis.path("glossary").get(0).path("exists").asBoolean()).isTrue();
        assertThat(analysis.path("glossary").get(1).path("exists").asBoolean()).isFalse();
        assertThat(analysis.path("constraints").size()).isEqualTo(2);
        assertThat(StubDocumentClassificationConfig.LAST_REQUEST.get().text())
                .contains("El comensal reserva una mesa indicando fecha, hora y personas.");
        String documentId = analysis.path("documentId").asString();

        // Pending: stored with its text, but not listed until applied.
        Map<String, Object> pending = jdbcTemplate.queryForMap(
                "SELECT d.status, d.extracted_chars, c.body FROM " + schema + ".project_documents d "
                        + "JOIN " + schema + ".project_document_contents c ON c.id = d.content_id WHERE d.id = ?::uuid",
                documentId);
        assertThat(pending.get("status")).isEqualTo("PENDING");
        assertThat((String) pending.get("body")).contains("Ley 29733");
        assertThat(JSON.readTree(get(OWNER_USER_ID, orgId, documents).getBody()).size()).isZero();

        ResponseEntity<String> applied = post(OWNER_USER_ID, orgId, documents + "/" + documentId + "/apply", Map.of(
                "name", "Términos de referencia",
                "documentType", "TECHNICAL_SPEC",
                "summary", StubDocumentClassificationConfig.SUMMARY,
                "glossaryTerms", List.of(
                        Map.of("term", "Comensal", "definition", "Cliente que reserva una mesa en el restaurante."),
                        Map.of("term", "Reserva", "definition", "Mesa apartada para una fecha, hora y número de personas.")),
                "constraints", List.of("El sistema debe cumplir la Ley 29733 de protección de datos personales.")));

        assertThat(applied.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode result = JSON.readTree(applied.getBody());
        assertThat(result.path("glossaryTermsAdded").asInt()).isEqualTo(1);
        assertThat(result.path("glossaryTermsSkipped").asInt()).isEqualTo(1);
        assertThat(result.path("constraintsAdded").asInt()).isEqualTo(1);
        assertThat(result.path("constraintsSkipped").asInt()).isZero();
        assertThat(result.path("document").path("status").asString()).isEqualTo("ACTIVE");
        assertThat(result.path("document").path("summary").asString()).isEqualTo(StubDocumentClassificationConfig.SUMMARY);

        assertThat(jdbcTemplate.queryForList("SELECT t.term FROM " + schema + ".glossary_terms t JOIN " + schema
                + ".glossaries g ON g.id = t.glossary_id WHERE g.project_id = ?::uuid ORDER BY t.term", String.class, projectId))
                .containsExactly("Comensal", "Reserva");
        assertThat(jdbcTemplate.queryForList("SELECT description FROM " + schema
                + ".project_constraints WHERE project_id = ?::uuid", String.class, projectId))
                .containsExactly("El sistema debe cumplir la Ley 29733 de protección de datos personales.");

        JsonNode listed = JSON.readTree(get(OWNER_USER_ID, orgId, documents).getBody());
        assertThat(listed.size()).isEqualTo(1);
        assertThat(listed.get(0).path("name").asString()).isEqualTo("Términos de referencia");
        assertThat(listed.get(0).path("fileName").asString()).isEqualTo("Terminos de referencia.pdf");
        assertThat(listed.get(0).path("extractedChars").asInt()).isPositive();

        // Applying twice is a conflict.
        ResponseEntity<String> again = post(OWNER_USER_ID, orgId, documents + "/" + documentId + "/apply",
                Map.of("documentType", "REFERENCE"));
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(JSON.readTree(again.getBody()).path("code").asString()).isEqualTo("PROJECT_DOCUMENT_NOT_PENDING");

        // The applied summary is now part of the project context the AI reads.
        TenantContext.setCurrentTenant(orgId.toString());
        TenantContext.setCurrentSchema("tenant_" + slug);
        try {
            ProjectSnapshot snapshot = workspaceApi.findProjectSnapshot(projectId).orElseThrow();
            assertThat(snapshot.documents()).containsExactly(new ProjectDocumentSnapshot(
                    "Términos de referencia", "TECHNICAL_SPEC", StubDocumentClassificationConfig.SUMMARY));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("a Word upload can be discarded, re-uploads replace the pending one, and bad files are refused")
    void discard_replace_and_reject() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        String schema = "\"tenant_" + slug + "\"";
        UUID orgId = createOrganization(suffix, slug);
        UUID projectId = createProject(orgId, slug);
        String documents = "/api/organizations/" + orgId + "/projects/" + projectId + "/documents";
        byte[] docx = TestDocuments.docx("Acta de la reunión de inicio.", "La aplicación debe funcionar en Android e iOS.");

        ResponseEntity<String> first = upload(OWNER_USER_ID, orgId, documents, "acta.docx", DOCX, docx);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(JSON.readTree(first.getBody()).path("mediaType").asString()).isEqualTo(DOCX);
        ResponseEntity<String> second = upload(OWNER_USER_ID, orgId, documents, "acta.docx", "application/octet-stream", docx);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String secondId = JSON.readTree(second.getBody()).path("documentId").asString();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM " + schema
                + ".project_documents WHERE status = 'PENDING'", Integer.class)).isEqualTo(1);

        // Discarding the analysis deletes the document and its extracted text.
        assertThat(delete(OWNER_USER_ID, orgId, documents + "/" + secondId).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM " + schema + ".project_documents", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM " + schema + ".project_document_contents", Integer.class))
                .isZero();

        // Executables, disguised or not, other formats and empty files are refused with a code.
        assertRejected(upload(OWNER_USER_ID, orgId, documents, "setup.exe", "application/x-msdownload",
                TestDocuments.executable()), HttpStatus.UNSUPPORTED_MEDIA_TYPE, "DOCUMENT_TYPE_NOT_ALLOWED");
        assertRejected(upload(OWNER_USER_ID, orgId, documents, "factura.pdf", PDF, TestDocuments.executable()),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "DOCUMENT_TYPE_NOT_ALLOWED");
        assertRejected(upload(OWNER_USER_ID, orgId, documents, "notas.txt", "text/plain", "hola".getBytes()),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "DOCUMENT_TYPE_NOT_ALLOWED");
        assertRejected(upload(OWNER_USER_ID, orgId, documents, "escaneo.pdf", PDF, TestDocuments.blankPdf()),
                HttpStatus.UNPROCESSABLE_CONTENT, "DOCUMENT_EMPTY");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM " + schema + ".project_documents", Integer.class)).isZero();

        // A member with only the read floor cannot upload.
        assertThat(post(OWNER_USER_ID, orgId, "/api/organizations/" + orgId + "/members", Map.of(
                "userId", MEMBER_USER_ID, "email", "reader@example.com", "displayName", "Reader", "role", "MEMBER"))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(upload(MEMBER_USER_ID, orgId, documents, "acta.docx", DOCX, docx).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ----- helpers -----

    private static void assertRejected(ResponseEntity<String> response, HttpStatus status, String code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(JSON.readTree(response.getBody()).path("code").asString()).isEqualTo(code);
    }

    private ResponseEntity<String> upload(String userId, UUID orgId, String documents, String fileName,
                                          String contentType, byte[] content) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", new ByteArrayResource(content))
                .filename(fileName)
                .contentType(MediaType.parseMediaType(contentType));
        return client().post().uri(documents + "/upload")
                .header("Authorization", TestJwtFactory.bearer(userId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body.build())
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }

    private ResponseEntity<String> get(String userId, UUID orgId, String uri) {
        return client().get().uri(uri)
                .header("Authorization", TestJwtFactory.bearer(userId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }

    private ResponseEntity<String> delete(String userId, UUID orgId, String uri) {
        return client().delete().uri(uri)
                .header("Authorization", TestJwtFactory.bearer(userId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }

    private ResponseEntity<String> post(String userId, UUID orgId, String uri, Map<String, Object> body) {
        return client().post().uri(uri)
                .header("Authorization", TestJwtFactory.bearer(userId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }

    private UUID createOrganization(String suffix, String slug) {
        ResponseEntity<String> res = client().post().uri("/api/organizations")
                .header("Authorization", TestJwtFactory.bearer(OWNER_USER_ID, UUID.randomUUID().toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Acme " + suffix))
                .exchange((req, r) -> ResponseEntity.status(r.getStatusCode()).body(r.bodyTo(String.class)));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT id::text FROM public.organizations WHERE slug = ?", String.class, slug));
    }

    private UUID createProject(UUID orgId, String slug) {
        ResponseEntity<String> res = post(OWNER_USER_ID, orgId, "/api/organizations/" + orgId + "/projects", Map.of(
                "name", "Documents Project",
                "programmingLanguages", List.of("Java"),
                "frameworks", List.of("Spring Boot"),
                "clientPlatforms", List.of("Web"),
                "databases", List.of("PostgreSQL"),
                "architecture", "Hexagonal",
                "domain", "Gastronomía"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT id::text FROM \"tenant_" + slug + "\".projects WHERE organization_id = ?::uuid AND name = ?",
                String.class, orgId.toString(), "Documents Project"));
    }
}
