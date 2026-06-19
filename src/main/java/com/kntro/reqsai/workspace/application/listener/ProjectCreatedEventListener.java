package com.kntro.reqsai.workspace.application.listener;

import com.kntro.reqsai.workspace.application.port.GlossaryRepository;
import com.kntro.reqsai.workspace.domain.event.ProjectCreatedEvent;
import com.kntro.reqsai.workspace.domain.model.Glossary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectCreatedEventListener {

    private final GlossaryRepository glossaryRepository;

    @EventListener
    @Transactional
    public void onProjectCreated(ProjectCreatedEvent event) {
        log.info("ProjectCreatedEvent received for project {}", event.projectId());
        Glossary glossary = new Glossary(event.aggregateId());
        glossaryRepository.save(glossary);
        log.info("Glossary created for project {}", event.projectId());
    }
}
