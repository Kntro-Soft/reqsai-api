package com.kntro.reqsai.discovery.application.command;

import java.util.UUID;

/** "Analizar ahora": analyze a live session's recent conversation right away (US46). */
public record AnalyzeSessionNowCommand(UUID projectId, UUID sessionId) {
}
