package com.kntro.reqsai.discovery.domain.model;

/**
 * A quality characteristic a story is evaluated against, taken from the ISO/IEC/IEEE 29148 requirement
 * characteristics and the INVEST-style Quality User Story framework. Each one weighs the same in the overall
 * score, so no single characteristic can hide a story that fails the others.
 */
public enum QualityCharacteristic {

    /** Free of vague, subjective or open-ended terms ("fast", "easy", "etc."). */
    AMBIGUITY,

    /** Singular: one story describes one capability, small enough to be built and verified on its own. */
    ATOMICITY,

    /** Each expected outcome can be checked: acceptance criteria exist and state an observable result. */
    VERIFIABILITY,

    /** Has a role, a goal and a benefit, and the acceptance criteria that frame it. */
    COMPLETENESS;

    /** Relative weight in the overall score. */
    public int weight() {
        return 1;
    }
}
