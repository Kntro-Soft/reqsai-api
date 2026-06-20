package com.kntro.reqsai.workspace.application.handler;
 
import com.kntro.reqsai.workspace.application.command.AddGlossaryTermCommand;
import com.kntro.reqsai.workspace.application.port.GlossaryRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Glossary;
import com.kntro.reqsai.workspace.domain.model.GlossaryTerm;
import com.kntro.reqsai.workspace.domain.model.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
 
@Component
@RequiredArgsConstructor
public class AddGlossaryTermCommandHandler {
 
    private final GlossaryRepository glossaries;
    private final ProjectRepository projects;
 
    @Transactional
    public GlossaryTerm handle(AddGlossaryTermCommand command) {
        Project project = projects.findById(command.projectId())
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(command.projectId()));

        if (!project.getOrganizationId().equals(command.organizationId())) {
            throw WorkspaceExceptions.projectNotFound(command.projectId());
        }

        Glossary glossary = glossaries.findByProjectId(command.projectId())
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(command.projectId()));
 
        GlossaryTerm term = glossary.addTerm(command.term(), command.definition());
        glossaries.save(glossary);
        return term;
    }
}
