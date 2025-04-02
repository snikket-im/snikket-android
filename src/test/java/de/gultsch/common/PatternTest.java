package de.gultsch.common;

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.regex.MatchResult;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Test;

public class PatternTest {

    @Test
    public void shortImMessage() {
        final var message =
                "Hi. I'm refactoring how URIs are linked in Conversations. We now support more URI"
                    + " schemes like mailto:user@example.com and tel:+1-269-555-0107 and obviously"
                    + " maintain support for things like"
                    + " xmpp:conversations@conference.siacs.eu?join and https://example.com however"
                    + " we no longer link domains that aren't actual URIs like example.com to avoid"
                    + " some false positives.";

        final var matches =
                Patterns.URI_GENERIC
                        .matcher(message)
                        .results()
                        .map(MatchResult::group)
                        .collect(Collectors.toList());

        Assert.assertEquals(
                Arrays.asList(
                        "mailto:user@example.com",
                        "tel:+1-269-555-0107",
                        "xmpp:conversations@conference.siacs.eu?join",
                        "https://example.com"),
                matches);
    }

    @Test
    public void ambiguous() {
        final var message =
                "Please find more information in the corresponding page on Wikipedia"
                    + " (https://en.wikipedia.org/wiki/Ambiguity_(disambiguation)). Let me know if"
                    + " you have questions!";
        final var matches =
                Patterns.URI_GENERIC
                        .matcher(message)
                        .results()
                        .map(MatchResult::group)
                        .collect(Collectors.toList());

        Assert.assertEquals(
                ImmutableList.of("https://en.wikipedia.org/wiki/Ambiguity_(disambiguation)"),
                matches);
    }

    @Test
    public void parenthesis() {
        final var message = "Daniel is on Mastodon (https://gultsch.social/@daniel)";
        final var matches =
                Patterns.URI_GENERIC
                        .matcher(message)
                        .results()
                        .map(MatchResult::group)
                        .collect(Collectors.toList());

        Assert.assertEquals(ImmutableList.of("https://gultsch.social/@daniel"), matches);
    }

    @Test
    public void fullWidthSpace() {
        final var message = "\u3000https://conversations.im";
        final var matches =
                Patterns.URI_GENERIC
                        .matcher(message)
                        .results()
                        .map(MatchResult::group)
                        .collect(Collectors.toList());

        Assert.assertEquals(ImmutableList.of("https://conversations.im"), matches);
    }

    @Test
    public void fullWidthColon() {
        final var message = "\uFF1Ahttps://conversations.im";
        final var matches =
                Patterns.URI_GENERIC
                        .matcher(message)
                        .results()
                        .map(MatchResult::group)
                        .collect(Collectors.toList());

        Assert.assertEquals(ImmutableList.of("https://conversations.im"), matches);
    }
}
