package com.kntro.reqsai.discovery.infrastructure.ai.transcription.streaming.strategy;

import java.io.IOException;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * In-memory provider {@link WebSocket}: records every frame the adapter sends and lets the test play the
 * provider's side (transcripts, close, error). Like the JDK socket, it fails a send issued while another
 * is still pending, so a test notices if the adapter ever sends concurrently.
 */
final class FakeProviderSocket implements WebSocket {

    private final WebSocket.Listener listener;
    private final List<Object> sent = new ArrayList<>();
    private final List<CompletableFuture<WebSocket>> heldSends = new ArrayList<>();
    private boolean pending;
    private boolean failSends;
    private boolean holdSends;
    private boolean aborted;
    private Integer closeCode;

    FakeProviderSocket(WebSocket.Listener listener) {
        this.listener = listener;
    }

    // ----- what the adapter sent -----

    List<byte[]> binaryFrames() {
        return sent.stream().filter(byte[].class::isInstance).map(byte[].class::cast).toList();
    }

    List<String> textFrames() {
        return sent.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    /** Every frame in send order: {@code byte[]} for audio, {@code String} for text. */
    List<Object> frames() {
        return List.copyOf(sent);
    }

    boolean aborted() {
        return aborted;
    }

    Integer closeCode() {
        return closeCode;
    }

    // ----- send behaviour -----

    /** Every later send fails, as on a broken pipe. */
    void failSends() {
        this.failSends = true;
    }

    /** Sends stay pending until {@link #completeHeldSend()}. */
    void holdSends() {
        this.holdSends = true;
    }

    void completeHeldSend() {
        CompletableFuture<WebSocket> held = heldSends.removeFirst();
        pending = false;
        held.complete(this);
    }

    // ----- the provider's side -----

    void providerSends(String json) {
        listener.onText(this, json, true);
    }

    void providerCloses(int code, String reason) {
        listener.onClose(this, code, reason);
    }

    void providerFails(Throwable error) {
        listener.onError(this, error);
    }

    // ----- WebSocket -----

    @Override
    public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
        return record(data.toString());
    }

    @Override
    public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) {
        byte[] bytes = new byte[data.remaining()];
        data.get(bytes);
        return record(bytes);
    }

    private CompletableFuture<WebSocket> record(Object frame) {
        if (pending) {
            return CompletableFuture.failedFuture(new IllegalStateException("Send pending"));
        }
        if (failSends || aborted) {
            return CompletableFuture.failedFuture(new IOException("Broken pipe"));
        }
        sent.add(frame);
        if (holdSends) {
            pending = true;
            CompletableFuture<WebSocket> held = new CompletableFuture<>();
            heldSends.add(held);
            return held;
        }
        return CompletableFuture.completedFuture(this);
    }

    @Override
    public CompletableFuture<WebSocket> sendPing(ByteBuffer message) {
        return CompletableFuture.completedFuture(this);
    }

    @Override
    public CompletableFuture<WebSocket> sendPong(ByteBuffer message) {
        return CompletableFuture.completedFuture(this);
    }

    /** Records the code and, like a well-behaved server, answers with its own close. */
    @Override
    public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
        closeCode = statusCode;
        listener.onClose(this, statusCode, "");
        return CompletableFuture.completedFuture(this);
    }

    @Override
    public void request(long n) {
        // flow control is irrelevant in memory
    }

    @Override
    public String getSubprotocol() {
        return "";
    }

    @Override
    public boolean isOutputClosed() {
        return aborted || closeCode != null;
    }

    @Override
    public boolean isInputClosed() {
        return aborted;
    }

    @Override
    public void abort() {
        aborted = true;
        heldSends.forEach(held -> held.completeExceptionally(new IOException("aborted")));
        heldSends.clear();
        pending = false;
    }
}
