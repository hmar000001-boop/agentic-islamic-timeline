package com.example.retail.agent.checkpoint;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.AgentState;

/**
 * Thread-safe in-process checkpoint saver for local execution and tests.
 * It is intentionally not durable across JVM restarts; production must inject a durable saver.
 */
public final class SynchronizedMemorySaver implements BaseCheckpointSaver {
    private final MemorySaver delegate = new MemorySaver();

    @Override public synchronized Collection<Checkpoint> list(RunnableConfig config) { return delegate.list(config); }
    @Override public synchronized Optional<Checkpoint> get(RunnableConfig config) { return delegate.get(config); }
    @Override public synchronized RunnableConfig put(RunnableConfig config, Checkpoint checkpoint) throws Exception {
        return delegate.put(config, checkpoint);
    }
    @Override public synchronized Tag release(RunnableConfig config) throws Exception { return delegate.release(config); }

    /** Forward the 1.9 message-aware release operation while retaining wrapper synchronization. */
    @Override public synchronized Tag release(RunnableConfig config, String message) throws Exception {
        return delegate.release(config, message);
    }

    /** Forward failure release so the delegate can retain its error tag metadata. */
    @Override public synchronized Tag releaseOnError(RunnableConfig config, Throwable exception) throws Exception {
        return delegate.releaseOnError(config, exception);
    }

    /** Keep interruption metadata behavior identical to the wrapped MemorySaver. */
    @Override
    public synchronized <State extends AgentState> CompletableFuture<InterruptionMetadata<State>> registerInterruption(
            RunnableConfig config, InterruptionMetadata<State> interruptionMetadata) {
        return delegate.registerInterruption(config, interruptionMetadata);
    }

    @Override public synchronized Optional<Tag> tag(RunnableConfig config, Integer version) throws Exception {
        return delegate.tag(config, version);
    }

    @Override public synchronized void putSubGraphSaver(
            RunnableConfig parentConfig, RunnableConfig subGraphConfig, BaseCheckpointSaver subGraphSaver) {
        delegate.putSubGraphSaver(parentConfig, subGraphConfig, subGraphSaver);
    }

    @Override public synchronized Collection<SubGraphSaver> listSubGraphSaver(RunnableConfig parentConfig) {
        return delegate.listSubGraphSaver(parentConfig);
    }
}
