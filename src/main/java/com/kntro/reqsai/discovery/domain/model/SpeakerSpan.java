package com.kntro.reqsai.discovery.domain.model;

/**
 * When one diarized speaker talked: the speaker label and the time range of one final transcript segment,
 * in milliseconds from the start of the recording. Read in transcript order to build the
 * {@link SpeakerRoster} and to detect overlapping speech ({@link SpeakerOverlapDetector}).
 */
public record SpeakerSpan(String speakerLabel, long startMs, long endMs) {
}
