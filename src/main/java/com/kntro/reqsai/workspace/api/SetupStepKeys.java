package com.kntro.reqsai.workspace.api;

import java.util.List;

/** The steps of a project's setup, in the order the bar shows them. */
public final class SetupStepKeys {

    /** The Jira project and issue type are saved as the project's target. */
    public static final String JIRA_LINK = "JIRA_LINK";

    /** The Jira project's existing issues are imported as stories. */
    public static final String JIRA_IMPORT = "JIRA_IMPORT";

    /** The repositories are connected and indexed. */
    public static final String GITHUB_REPOS = "GITHUB_REPOS";

    /** Every step, in display order. */
    public static final List<String> ORDER = List.of(JIRA_LINK, JIRA_IMPORT, GITHUB_REPOS);

    private SetupStepKeys() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static boolean isKnown(String key) {
        return ORDER.contains(key);
    }
}
