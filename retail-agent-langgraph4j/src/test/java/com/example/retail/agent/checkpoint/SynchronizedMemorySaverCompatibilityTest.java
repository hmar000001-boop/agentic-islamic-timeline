package com.example.retail.agent.checkpoint;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.retail.agent.RetailState;
import java.util.Map;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.junit.jupiter.api.Test;

/** Guards the expanded LangGraph4j 1.9 saver contract without changing graph execution policy. */
class SynchronizedMemorySaverCompatibilityTest {

    @Test
    void forwardsMessageAwareReleaseAndVersionedTagLookup() throws Exception {
        SynchronizedMemorySaver saver = new SynchronizedMemorySaver();
        RunnableConfig config = config("message-release-thread");

        BaseCheckpointSaver.Tag released = saver.release(config, "completed for compatibility test");

        assertThat(released.version()).contains(1);
        var loaded = saver.tag(config, null);
        assertThat(loaded).isPresent();
        assertThat(loaded.orElseThrow()).isSameAs(released);
        assertThat(saver.lastTag(config)).isPresent();
    }

    @Test
    void forwardsFailureRelease() throws Exception {
        SynchronizedMemorySaver saver = new SynchronizedMemorySaver();
        RunnableConfig config = config("error-release-thread");

        BaseCheckpointSaver.Tag released = saver.releaseOnError(config, new IllegalStateException("expected test failure"));

        assertThat(released.version()).contains(1);
        assertThat(saver.tag(config, null)).contains(released);
    }

    @Test
    void forwardsInterruptionMetadataToTheDelegate() {
        SynchronizedMemorySaver saver = new SynchronizedMemorySaver();
        RunnableConfig config = config("interruption-thread");
        RetailState state = new RetailState(Map.of());
        InterruptionMetadata<RetailState> interruption =
                InterruptionMetadata.builder("await_approval", state).reason("approval required").build();

        InterruptionMetadata<RetailState> returned = saver.registerInterruption(config, interruption).join();

        assertThat(returned).isSameAs(interruption);
        assertThat(returned.nodeId()).isEqualTo("await_approval");
        assertThat(returned.reason()).contains("approval required");
        assertThat(returned.state()).isSameAs(state);
    }

    @Test
    void forwardsSubgraphSaverRegistrationAndLookup() {
        SynchronizedMemorySaver parent = new SynchronizedMemorySaver();
        SynchronizedMemorySaver child = new SynchronizedMemorySaver();
        RunnableConfig parentConfig = config("parent-thread");
        RunnableConfig childConfig = config("child-thread");

        parent.putSubGraphSaver(parentConfig, childConfig, child);

        assertThat(parent.listSubGraphSaver(parentConfig))
                .containsExactly(new BaseCheckpointSaver.SubGraphSaver("child-thread", child));
    }

    private static RunnableConfig config(String threadId) {
        return RunnableConfig.builder().threadId(threadId).build();
    }
}
