package im.conversations.android.xmpp;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNull;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.manager.DiscoManager;
import im.conversations.android.xml.XmlElementReader;
import im.conversations.android.xmpp.model.disco.info.InfoQuery;
import im.conversations.android.xmpp.model.stanza.Iq;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class EntityCapabilitiesTest {

    @Test
    public void entityCaps() throws IOException {
        final String xml =
                """
                <query xmlns='http://jabber.org/protocol/disco#info'
                         node='http://code.google.com/p/exodus#QgayPKawpkPSDYmwT/WM94uAlu0='>
                    <identity category='client' name='Exodus 0.9.1' type='pc'/>
                    <feature var='http://jabber.org/protocol/caps'/>
                    <feature var='http://jabber.org/protocol/disco#items'/>
                    <feature var='http://jabber.org/protocol/disco#info'/>
                    <feature var='http://jabber.org/protocol/muc'/>
                  </query>""";
        final Element element = XmlElementReader.read(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(element, instanceOf(InfoQuery.class));
        final InfoQuery info = (InfoQuery) element;
        final String var = EntityCapabilities.hash(info).encoded();
        Assert.assertEquals("QgayPKawpkPSDYmwT/WM94uAlu0=", var);
    }

    @Test
    public void entityCapsComplexExample() throws IOException {
        final String xml =
                """
                <query xmlns='http://jabber.org/protocol/disco#info'
                         node='http://psi-im.org#q07IKJEyjvHSyhy//CH0CxmKi8w='>
                    <identity xml:lang='en' category='client' name='Psi 0.11' type='pc'/>
                    <identity xml:lang='el' category='client' name='Ψ 0.11' type='pc'/>
                    <feature var='http://jabber.org/protocol/caps'/>
                    <feature var='http://jabber.org/protocol/disco#info'/>
                    <feature var='http://jabber.org/protocol/disco#items'/>
                    <feature var='http://jabber.org/protocol/muc'/>
                    <x xmlns='jabber:x:data' type='result'>
                      <field var='FORM_TYPE' type='hidden'>
                        <value>urn:xmpp:dataforms:softwareinfo</value>
                      </field>
                      <field var='ip_version' type='text-multi' >
                        <value>ipv4</value>
                        <value>ipv6</value>
                      </field>
                      <field var='os'>
                        <value>Mac</value>
                      </field>
                      <field var='os_version'>
                        <value>10.5.1</value>
                      </field>
                      <field var='software'>
                        <value>Psi</value>
                      </field>
                      <field var='software_version'>
                        <value>0.11</value>
                      </field>
                    </x>
                  </query>""";
        final Element element = XmlElementReader.read(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(element, instanceOf(InfoQuery.class));
        final InfoQuery info = (InfoQuery) element;
        final String var = EntityCapabilities.hash(info).encoded();
        Assert.assertEquals("q07IKJEyjvHSyhy//CH0CxmKi8w=", var);
    }

    @Test
    public void entityCapsOpenFireOrg() throws IOException {
        final String xml =
                """
  <iq type="result" xmlns="jabber:client" to="inputmice3@igniterealtime.org/Conversations.cI4W" from="igniterealtime.org" id="L3xl8X8_kzvx">
  <query node="https://www.igniterealtime.org/projects/openfire/#Cd91QBSG4JGOCEvRsSz64xeJPMk=" xmlns="http://jabber.org/protocol/disco#info">
    <identity name="Openfire Server" type="im" category="server" xmlns="http://jabber.org/protocol/disco#info"/>
    <identity type="pep" category="pubsub" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="urn:xmpp:raa:0#embed-presence-directed" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/caps" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/pubsub#retrieve-default" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/pubsub#purge-nodes" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/pubsub#subscription-options" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="urn:xmpp:raa:0#embed-message " xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/pubsub#outcast-affiliation" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="msgoffline" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/pubsub#delete-nodes" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="jabber:iq:register" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/pubsub#config-node" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/pubsub#retrieve-items" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/pubsub#auto-create" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/disco#items" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/pubsub#delete-items" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="urn:xmpp:mam:0" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="urn:xmpp:mam:1" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="urn:xmpp:mam:2" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="urn:xmpp:fulltext:0" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/pubsub#persistent-items" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/pubsub#create-and-configure" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/pubsub#retrieve-affiliations" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="urn:xmpp:time" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/pubsub#manage-subscriptions" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="urn:xmpp:bookmarks-conversion:0" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/pubsub#auto-subscribe" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/offline" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/pubsub#publish-options" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="urn:xmpp:carbons:2" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/address" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/pubsub#collections" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/pubsub#retrieve-subscriptions" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="vcard-temp" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/pubsub#subscribe" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/pubsub#create-nodes" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/pubsub#get-pending" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="urn:xmpp:blocking" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/pubsub#multi-subscribe" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/pubsub#presence-notifications" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="urn:xmpp:ping" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="urn:xmpp:archive:manage" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/pubsub#filtered-notifications" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="urn:xmpp:push:0" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/pubsub#meta-data" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/pubsub#multi-items" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/pubsub#item-ids" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="jabber:iq:roster" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/pubsub#instant-nodes" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/pubsub#modify-affiliations" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="urn:xmpp:raa:0#embed-presence-sub" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/pubsub" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/pubsub#publisher-affiliation" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/pubsub#access-open" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="jabber:iq:version" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/pubsub#retract-items" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="urn:xmpp:extdisco:1" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="jabber:iq:privacy" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="urn:xmpp:extdisco:2" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/commands" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="jabber:iq:last" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="urn:xmpp:raa:0" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/pubsub#publish" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="urn:xmpp:serverinfo:0" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="urn:xmpp:archive:auto" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/disco#info" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="jabber:iq:private" xmlns="http://jabber.org/protocol/disco#info"/>
    <feature var="http://jabber.org/protocol/rsm" xmlns="http://jabber.org/protocol/disco#info"/>
    <x type="result" xmlns="jabber:x:data">
      <field var="FORM_TYPE" type="hidden" xmlns="jabber:x:data">
        <value xmlns="jabber:x:data">urn:xmpp:dataforms:softwareinfo</value>
      </field>
      <field var="os" type="text-single" xmlns="jabber:x:data">
        <value xmlns="jabber:x:data">Linux</value>
      </field>
      <field var="os_version" type="text-single" xmlns="jabber:x:data">
        <value xmlns="jabber:x:data">4.14.355-276.618.amzn2.x86_64 amd64 - Java 17.0.14</value>
      </field>
      <field var="software" type="text-single" xmlns="jabber:x:data">
        <value xmlns="jabber:x:data">Openfire</value>
      </field>
      <field var="software_version" type="text-single" xmlns="jabber:x:data">
        <value xmlns="jabber:x:data">5.0.0 Alpha</value>
      </field>
    </x>
    <x type="result" xmlns="jabber:x:data">
      <field var="FORM_TYPE" type="hidden" xmlns="jabber:x:data">
        <value xmlns="jabber:x:data">http://jabber.org/network/serverinfo</value>
      </field>
      <field var="admin-addresses" type="list-multi" xmlns="jabber:x:data">
        <value xmlns="jabber:x:data">xmpp:dwd@dave.cridland.net</value>
        <value xmlns="jabber:x:data">xmpp:akrherz@igniterealtime.org</value>
        <value xmlns="jabber:x:data">xmpp:benjamin@igniterealtime.org</value>
        <value xmlns="jabber:x:data">mailto:benjamin@holyarmy.org</value>
        <value xmlns="jabber:x:data">xmpp:csh@igniterealtime.org</value>
        <value xmlns="jabber:x:data">xmpp:dan.caseley@igniterealtime.org</value>
        <value xmlns="jabber:x:data">mailto:dan.caseley@surevine.com</value>
        <value xmlns="jabber:x:data">xmpp:flow@igniterealtime.org</value>
        <value xmlns="jabber:x:data">xmpp:gdt@igniterealtime.org</value>
        <value xmlns="jabber:x:data">mailto:greg.d.thomas@gmail.com</value>
        <value xmlns="jabber:x:data">xmpp:guus.der.kinderen@igniterealtime.org</value>
        <value xmlns="jabber:x:data">mailto:guus.der.kinderen@gmail.com</value>
        <value xmlns="jabber:x:data">xmpp:lg@igniterealtime.org</value>
        <value xmlns="jabber:x:data">xmpp:rcollier@igniterealtime.org</value>
        <value xmlns="jabber:x:data">mailto:robincollier@hotmail.com</value>
      </field>
    </x>
  </query>
</iq>
""";
        final Element element = XmlElementReader.read(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(element, instanceOf(Iq.class));
        final var iq = (Iq) element;
        final InfoQuery info = iq.getExtension(InfoQuery.class);
        final String var = EntityCapabilities.hash(info).encoded();
        Assert.assertEquals("Cd91QBSG4JGOCEvRsSz64xeJPMk=", var);
    }

    @Test
    public void entityCapsOpenFireTestServer() throws IOException {
        final String xml =
                """
<iq type="result" id="779-6" to="jane@example.org" xmlns="jabber:client">
  <query xmlns="http://jabber.org/protocol/disco#info">
    <identity category="server" name="Openfire Server" type="im"/>
    <identity category="pubsub" type="pep"/>
    <feature var="http://jabber.org/protocol/caps"/>
    <feature var="http://jabber.org/protocol/pubsub#retrieve-default"/>
    <feature var="http://jabber.org/protocol/pubsub#purge-nodes"/>
    <feature var="http://jabber.org/protocol/pubsub#subscription-options"/>
    <feature var="http://jabber.org/protocol/pubsub#outcast-affiliation"/>
    <feature var="msgoffline"/>
    <feature var="jabber:iq:register"/>
    <feature var="http://jabber.org/protocol/pubsub#delete-nodes"/>
    <feature var="http://jabber.org/protocol/pubsub#config-node"/>
    <feature var="http://jabber.org/protocol/pubsub#retrieve-items"/>
    <feature var="http://jabber.org/protocol/pubsub#auto-create"/>
    <feature var="http://jabber.org/protocol/pubsub#delete-items"/>
    <feature var="http://jabber.org/protocol/disco#items"/>
    <feature var="http://jabber.org/protocol/pubsub#persistent-items"/>
    <feature var="http://jabber.org/protocol/pubsub#create-and-configure"/>
    <feature var="http://jabber.org/protocol/pubsub#retrieve-affiliations"/>
    <feature var="urn:xmpp:time"/>
    <feature var="http://jabber.org/protocol/pubsub#manage-subscriptions"/>
    <feature var="urn:xmpp:bookmarks-conversion:0"/>
    <feature var="http://jabber.org/protocol/offline"/>
    <feature var="http://jabber.org/protocol/pubsub#auto-subscribe"/>
    <feature var="http://jabber.org/protocol/pubsub#publish-options"/>
    <feature var="urn:xmpp:carbons:2"/>
    <feature var="http://jabber.org/protocol/address"/>
    <feature var="http://jabber.org/protocol/pubsub#collections"/>
    <feature var="http://jabber.org/protocol/pubsub#retrieve-subscriptions"/>
    <feature var="vcard-temp"/>
    <feature var="http://jabber.org/protocol/pubsub#subscribe"/>
    <feature var="http://jabber.org/protocol/pubsub#create-nodes"/>
    <feature var="http://jabber.org/protocol/pubsub#get-pending"/>
    <feature var="urn:xmpp:blocking"/>
    <feature var="http://jabber.org/protocol/pubsub#multi-subscribe"/>
    <feature var="http://jabber.org/protocol/pubsub#presence-notifications"/>
    <feature var="urn:xmpp:ping"/>
    <feature var="http://jabber.org/protocol/pubsub#filtered-notifications"/>
    <feature var="http://jabber.org/protocol/pubsub#item-ids"/>
    <feature var="http://jabber.org/protocol/pubsub#meta-data"/>
    <feature var="http://jabber.org/protocol/pubsub#multi-items"/>
    <feature var="jabber:iq:roster"/>
    <feature var="http://jabber.org/protocol/pubsub#instant-nodes"/>
    <feature var="http://jabber.org/protocol/pubsub#modify-affiliations"/>
    <feature var="http://jabber.org/protocol/pubsub"/>
    <feature var="http://jabber.org/protocol/pubsub#publisher-affiliation"/>
    <feature var="http://jabber.org/protocol/pubsub#access-open"/>
    <feature var="jabber:iq:version"/>
    <feature var="http://jabber.org/protocol/pubsub#retract-items"/>
    <feature var="jabber:iq:privacy"/>
    <feature var="jabber:iq:last"/>
    <feature var="http://jabber.org/protocol/commands"/>
    <feature var="http://jabber.org/protocol/pubsub#publish"/>
    <feature var="http://jabber.org/protocol/disco#info"/>
    <feature var="jabber:iq:private"/>
    <feature var="http://jabber.org/protocol/rsm"/>
    <x xmlns="jabber:x:data" type="result">
      <field var="FORM_TYPE" type="hidden">
        <value>http://jabber.org/network/serverinfo</value>
      </field>
      <field var="admin-addresses" type="list-multi">
        <value>xmpp:admin@example.org</value>
        <value>mailto:admin@example.com</value>
      </field>
    </x>
    <x xmlns="jabber:x:data" type="result">
      <field var="FORM_TYPE" type="hidden">
        <value>urn:xmpp:dataforms:softwareinfo</value>
      </field>
      <field type="text-single" var="os">
        <value>Linux</value>
      </field>
      <field type="text-single" var="os_version">
        <value>6.8.0-59-generic amd64 - Java 21.0.7</value>
      </field>
      <field type="text-single" var="software">
        <value>Openfire</value>
      </field>
      <field type="text-single" var="software_version">
        <value>5.0.0 Alpha</value>
      </field>
    </x>
  </query>
</iq>
""";
        final Element element = XmlElementReader.read(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(element, instanceOf(Iq.class));
        final var iq = (Iq) element;
        final InfoQuery info = iq.getExtension(InfoQuery.class);
        final String var = EntityCapabilities.hash(info).encoded();
        Assert.assertEquals("3wkXXN9QL/i/AyVoHaqaiTT8BFA=", var);
    }

    @Test
    public void caps2() throws IOException {
        final String xml =
                """
                <query xmlns="http://jabber.org/protocol/disco#info">
                  <identity category="client" name="BombusMod" type="mobile"/>
                  <feature var="http://jabber.org/protocol/si"/>
                  <feature var="http://jabber.org/protocol/bytestreams"/>
                  <feature var="http://jabber.org/protocol/chatstates"/>
                  <feature var="http://jabber.org/protocol/disco#info"/>
                  <feature var="http://jabber.org/protocol/disco#items"/>
                  <feature var="urn:xmpp:ping"/>
                  <feature var="jabber:iq:time"/>
                  <feature var="jabber:iq:privacy"/>
                  <feature var="jabber:iq:version"/>
                  <feature var="http://jabber.org/protocol/rosterx"/>
                  <feature var="urn:xmpp:time"/>
                  <feature var="jabber:x:oob"/>
                  <feature var="http://jabber.org/protocol/ibb"/>
                  <feature var="http://jabber.org/protocol/si/profile/file-transfer"/>
                  <feature var="urn:xmpp:receipts"/>
                  <feature var="jabber:iq:roster"/>
                  <feature var="jabber:iq:last"/>
                </query>""";
        final Element element = XmlElementReader.read(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(element, instanceOf(InfoQuery.class));
        final InfoQuery info = (InfoQuery) element;
        final String var = EntityCapabilities2.hash(info).encoded();
        Assert.assertEquals("kzBZbkqJ3ADrj7v08reD1qcWUwNGHaidNUgD7nHpiw8=", var);
    }

    @Test
    public void caps2complex() throws IOException {
        final String xml =
                """
                <query xmlns="http://jabber.org/protocol/disco#info">
                  <identity category="client" name="Tkabber" type="pc" xml:lang="en"/>
                  <identity category="client" name="Ткаббер" type="pc" xml:lang="ru"/>
                  <feature var="games:board"/>
                  <feature var="http://jabber.org/protocol/activity"/>
                  <feature var="http://jabber.org/protocol/activity+notify"/>
                  <feature var="http://jabber.org/protocol/bytestreams"/>
                  <feature var="http://jabber.org/protocol/chatstates"/>
                  <feature var="http://jabber.org/protocol/commands"/>
                  <feature var="http://jabber.org/protocol/disco#info"/>
                  <feature var="http://jabber.org/protocol/disco#items"/>
                  <feature var="http://jabber.org/protocol/evil"/>
                  <feature var="http://jabber.org/protocol/feature-neg"/>
                  <feature var="http://jabber.org/protocol/geoloc"/>
                  <feature var="http://jabber.org/protocol/geoloc+notify"/>
                  <feature var="http://jabber.org/protocol/ibb"/>
                  <feature var="http://jabber.org/protocol/iqibb"/>
                  <feature var="http://jabber.org/protocol/mood"/>
                  <feature var="http://jabber.org/protocol/mood+notify"/>
                  <feature var="http://jabber.org/protocol/rosterx"/>
                  <feature var="http://jabber.org/protocol/si"/>
                  <feature var="http://jabber.org/protocol/si/profile/file-transfer"/>
                  <feature var="http://jabber.org/protocol/tune"/>
                  <feature var="http://www.facebook.com/xmpp/messages"/>
                  <feature var="http://www.xmpp.org/extensions/xep-0084.html#ns-metadata+notify"/>
                  <feature var="jabber:iq:avatar"/>
                  <feature var="jabber:iq:browse"/>
                  <feature var="jabber:iq:dtcp"/>
                  <feature var="jabber:iq:filexfer"/>
                  <feature var="jabber:iq:ibb"/>
                  <feature var="jabber:iq:inband"/>
                  <feature var="jabber:iq:jidlink"/>
                  <feature var="jabber:iq:last"/>
                  <feature var="jabber:iq:oob"/>
                  <feature var="jabber:iq:privacy"/>
                  <feature var="jabber:iq:roster"/>
                  <feature var="jabber:iq:time"/>
                  <feature var="jabber:iq:version"/>
                  <feature var="jabber:x:data"/>
                  <feature var="jabber:x:event"/>
                  <feature var="jabber:x:oob"/>
                  <feature var="urn:xmpp:avatar:metadata+notify"/>
                  <feature var="urn:xmpp:ping"/>
                  <feature var="urn:xmpp:receipts"/>
                  <feature var="urn:xmpp:time"/>
                  <x xmlns="jabber:x:data" type="result">
                    <field type="hidden" var="FORM_TYPE">
                      <value>urn:xmpp:dataforms:softwareinfo</value>
                    </field>
                    <field var="software">
                      <value>Tkabber</value>
                    </field>
                    <field var="software_version">
                      <value>0.11.1-svn-20111216-mod (Tcl/Tk 8.6b2)</value>
                    </field>
                    <field var="os">
                      <value>Windows</value>
                    </field>
                    <field var="os_version">
                      <value>XP</value>
                    </field>
                  </x>
                </query>""";
        final Element element = XmlElementReader.read(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(element, instanceOf(InfoQuery.class));
        final InfoQuery info = (InfoQuery) element;
        final String var = EntityCapabilities2.hash(info).encoded();
        Assert.assertEquals("u79ZroNJbdSWhdSp311mddz44oHHPsEBntQ5b1jqBSY=", var);
    }

    @Test
    public void parseCaps2Node() {
        final var caps =
                DiscoManager.buildHashFromNode(
                        "urn:xmpp:caps#sha-256.u79ZroNJbdSWhdSp311mddz44oHHPsEBntQ5b1jqBSY=");
        assertThat(caps, instanceOf(EntityCapabilities2.EntityCaps2Hash.class));
    }

    @Test
    public void parseCaps2NodeMissingHash() {
        final var caps = DiscoManager.buildHashFromNode("urn:xmpp:caps#sha-256.");
        assertNull(caps);
    }

    @Test
    public void parseCaps2NodeInvalid() {
        final var caps = DiscoManager.buildHashFromNode("urn:xmpp:caps#-");
        assertNull(caps);
    }

    @Test
    public void parseCaps2NodeUnknownAlgo() {
        final var caps = DiscoManager.buildHashFromNode("urn:xmpp:caps#test.test");
        assertNull(caps);
    }
}
