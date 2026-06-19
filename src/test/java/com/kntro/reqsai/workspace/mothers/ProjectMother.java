package com.kntro.reqsai.workspace.mothers;

public final class ProjectMother {

    private ProjectMother() {
    }

    public static ProjectBuilder standard() {
        return ProjectBuilder.aProject();
    }
}
