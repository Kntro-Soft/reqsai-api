package com.kntro.reqsai.overview.application.result;

/**
 * How well the live stories are backed up.
 *
 * @param live
 *            stories still counting as work
 * @param withAcceptanceCriteria
 *            of them, those with at least one acceptance criterion
 * @param withOrigin
 *            of them, those quoting the conversation they came from
 * @param linkedToCode
 *            of them, those tied to a module of the client's code
 */
public record StoryCoverage(long live, long withAcceptanceCriteria, long withOrigin, long linkedToCode) {
}
