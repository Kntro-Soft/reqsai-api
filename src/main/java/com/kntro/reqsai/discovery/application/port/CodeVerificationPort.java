package com.kntro.reqsai.discovery.application.port;

import com.kntro.reqsai.codebase.api.CodeSnippet;

import java.util.List;

/**
 * Output port for the deep verification: ONE bounded model call that reads a request together with the passages
 * of the client's real source code found for it, and decides whether the code already does it, contradicts it,
 * has nothing about it, or is not enough to tell. Retrieve-then-read: the model never browses the code itself.
 */
public interface CodeVerificationPort {

    /** Whether a model is configured and reachable. */
    boolean isAvailable();

    /**
     * @param subject  what is verified: the story or requirement text, or the analyst's question (untrusted)
     * @param kind     whether {@code subject} is a requirement to check or a question to answer
     * @param snippets the passages of code to read (at least one), with their file and lines
     * @param language ISO-639-1 language the note is written in ({@code es} or {@code en})
     * @throws RuntimeException when the model cannot be reached or its reply cannot be read
     */
    CodeVerification verify(String subject, SubjectKind kind, List<CodeSnippet> snippets, String language);

    /** A requirement the code is compared with, or a question about the code the analyst wants answered. */
    enum SubjectKind {
        REQUIREMENT,
        QUESTION
    }
}
