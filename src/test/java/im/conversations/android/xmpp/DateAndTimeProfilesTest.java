package im.conversations.android.xmpp;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

import eu.siacs.conversations.xml.Element;
import im.conversations.android.xml.XmlElementReader;
import im.conversations.android.xmpp.model.delay.Delay;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class DateAndTimeProfilesTest {

    @Test
    public void delayExample() throws IOException {
        final var xml =
                """
 <delay xmlns='urn:xmpp:delay'
     from='juliet@capulet.com/balcony'
     stamp='2002-09-10T23:41:07Z'/>
""";
        final Element element = XmlElementReader.read(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(element, instanceOf(Delay.class));
        final var delay = (Delay) element;
        Assert.assertEquals(Instant.ofEpochMilli(1031701267000L), delay.getStamp());
    }

    @Test
    public void delayExamplePlusSomeMilliseconds() throws IOException {
        final var xml =
                """
 <delay xmlns='urn:xmpp:delay'
     from='juliet@capulet.com/balcony'
     stamp='2002-09-10T23:41:07.123Z'/>
""";
        final Element element = XmlElementReader.read(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(element, instanceOf(Delay.class));
        final var delay = (Delay) element;
        Assert.assertEquals(Instant.ofEpochMilli(1031701267123L), delay.getStamp());
    }

    @Test
    public void firstStepsOneTheMoon() throws IOException {
        final var xml =
                """
 <delay xmlns='urn:xmpp:delay'
     from='juliet@capulet.com/balcony'
     stamp='1969-07-21T02:56:15Z'/>
""";
        final Element element = XmlElementReader.read(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(element, instanceOf(Delay.class));
        final var delay = (Delay) element;
        Assert.assertEquals(Instant.ofEpochMilli(-14159025000L), delay.getStamp());
    }

    @Test
    public void firstStepsOneTheMoonHustonTime() throws IOException {
        final var xml =
                """
 <delay xmlns='urn:xmpp:delay'
     from='juliet@capulet.com/balcony'
     stamp='1969-07-20T21:56:15-05:00'/>
""";
        final Element element = XmlElementReader.read(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(element, instanceOf(Delay.class));
        final var delay = (Delay) element;
        Assert.assertEquals(Instant.ofEpochMilli(-14159025000L), delay.getStamp());
    }
}
