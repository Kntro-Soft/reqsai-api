package com.kntro.reqsai.discovery.application.handler;

/** One round of the assistant chat: what the analyst typed and ReqsAI's reply. */
public record AssistantExchange(AssistantChatEntry question, AssistantChatEntry answer) {
}
