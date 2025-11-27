package eu.siacs.conversations.utils;

import org.junit.Assert;
import org.junit.Test;

public class EmoticonsTest {

    @Test
    public void testUpAndDown() {
        Assert.assertTrue(Emoticons.isEmoji("↕\uFE0F"));
    }

    @Test
    public void testHeadShakingVertically() {
        Assert.assertTrue(Emoticons.isEmoji("\uD83D\uDE42\u200D↕\uFE0F"));
    }

    @Test
    public void rightArrowCurvingLeft() {
        Assert.assertTrue(Emoticons.isEmoji("↩\uFE0F"));
    }
}
