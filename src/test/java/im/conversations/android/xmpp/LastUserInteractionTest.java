package im.conversations.android.xmpp;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

import im.conversations.android.xmpp.model.idle.LastUserInteraction;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;

public class LastUserInteractionTest {

    @Test
    public void onlineAndIdle() {
        Assert.assertTrue(
                LastUserInteraction.max(
                                Arrays.asList(
                                        LastUserInteraction.online(),
                                        LastUserInteraction.idle(Instant.now())))
                        instanceof LastUserInteraction.Online);
    }

    @Test
    public void idleAndIdle() {
        final var idleNow = Instant.now();
        final var idleFiveMinutesAgo = idleNow.minus(5, ChronoUnit.MINUTES);
        final var last =
                LastUserInteraction.max(
                        Arrays.asList(
                                LastUserInteraction.none(),
                                LastUserInteraction.idle(idleNow),
                                LastUserInteraction.idle(idleFiveMinutesAgo)));
        assertThat(last, instanceOf(LastUserInteraction.Idle.class));
        Assert.assertEquals(idleNow, ((LastUserInteraction.Idle) last).getSince());
    }
}
