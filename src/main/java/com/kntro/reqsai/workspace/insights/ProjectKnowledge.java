package com.kntro.reqsai.workspace.insights;

/**
 * What a project has written down about the client's domain.
 *
 * @param glossaryTerms
 *            terms in the project glossary
 * @param constraints
 *            project constraints
 * @param documents
 *            active client documents
 * @param assignedMembers
 *            members assigned to the project (organization owners and admins
 *            reach every project without being assigned, so they are not
 *            counted)
 */
public record ProjectKnowledge(long glossaryTerms, long constraints, long documents, long assignedMembers) {
}
