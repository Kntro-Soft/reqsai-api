package com.kntro.reqsai.workspace.insights;

import java.util.UUID;

/**
 * An active project as a dashboard names it.
 *
 * @param id
 *            project id
 * @param name
 *            project name
 * @param demo
 *            whether it is the organization's sample project
 */
public record ProjectRef(UUID id, String name, boolean demo) {
}
