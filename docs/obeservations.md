Observations on implementing XMPP
=================================
After spending the last two and a half month basically writing my own XMPP
library from scratch I decided to share some of the observations I made in the
process. In part this article can be seen as a response to a blog post made by
Dr. Ing. Georg Lukas. The blog post introduces a couple of XEP (XMPP Extensions)
which make the life on mobile devices a lot easier but states that they are
currently very few implementations of those XEPs. So I went ahead and
implemented all of them in my Android XMPP client.

###General observations
The first thing I noticed is that XMPP is actually okish designed. If you were
to design a new chat protocol today you probably wouldn’t choose XML again
however the protocol basically consists of only three different packages which
are quickly hidden under some sort of abstraction layer within your library.
Getting from zero to sending messages to other users actually was very simple
and straight forward. But then came the XEPs.

###Multi-User Chat
The first one was XEP-0045 Multi-User Chat. This is the one XEP of the XEPs I’m
going to mention in my article which is actually wildly adopted. Most clients
and servers I know of support MUC. However the level of completeness varies.
MUC actually introduces access and permission roles which are far more complex
than what some of us are used to from IRC but a lot of clients just don’t
implement them. I’m not implementing them myself (at least for now) because I
somewhat doubt that someone would actually use them (however this might be some
sort of chicken or egg problem). I did find some strange bugs though which might
be interesting for other library developers. In theory a MUC server
implementation can allow a  single user (same jid) to join a conference room
multiple times with the same nick from different clients. This means if someone
wants to participate in a conference from two different devices (mobile and
desktop for example) one wouldn’t have to name oneself `userDesktop` and
`userMobile` but just `user`. Both ejabberd and prosody support this but with
strange side effects. Prosody for example doesn’t allow a user to change its
name once two clients are “merged” by having the same nick.

###Carbons and Stream Management
Two of the other XEPs Lukas mentions — Carbons (XEP-0280) and Stream Management
(XEP-0198) — were actually fairly easy to implement. The only challenges were to
find a server to support them (I ended up running my own Prosody server) and a
desktop client to test them with. For carbons there is a patched Mcabber version
and Gajim. After implementing stream management I had very good results on my
mobile device. I had sessions running for up to 24 hours with a walking outside,
loosing mobile coverage for a few minutes and so on. The only limitation was
that I had to keep on developing and reinstalling my app.

###Off the record
And then came OTR... This is were I spend the most time debugging stuff and
trying to get things right and compatible with other clients. This is the part
were I want to help other developers not to make the same mistakes and maybe
come to some sort of consent among XMPP developers to ultimately increase the
interoperability. OTR has some down sides which make it difficult or at times
even dangerous to implement within XMPP. First of all it is a synchronous
protocol which is tunneled through a different protocol (XMPP). Synchronous
means — among other things — auto replies. (An OTR session begins with “hi I’m
speaking otr give me your key” “ok cool here is my key”) And auto replies — we
know that since the first time an out of office auto responder went postal — are
dangerous. Things really start to get messy when you use one of the best
features of XMPP — multiple clients. The way XMPP works is that clients are
encouraged to send their messages to the raw jid and let the server decide what
full jid the messages are routed to. If in doubt even all of them. So what
happens when Alice sends a  start-otr-message to Bobs raw jid? Bob receives the
message on his notebook as well as his cell phone. Both of them answer. Alice
gets two different replies. Shit explodes. Even if Alice  sends the message to
bob/notebook chances are that Bob has carbon messages enabled and still receives
the messages on both devices. Now assuming that Bobs client is clever enough not
to auto reply to carbonated messages Bob/cellphone will still end up with a lot
of garbage messages. (Essentially the entire conversation between Alice and
Bob/notebook but unreadable of course) Therefor it should be good practice to
tag OTR messages as both private and no-copy (private is part of the carbons
XEP, no-copy is a general hint). I found that prosody for some reasons doesn’t
honor the private tag on outgoing messages. While this is easily fixed I presume
that having both the private and the no-copy tag will make it more compatible
with servers or clients I don’t know about yet.

####Rules to follow when implementing OTR
To summarize my observations on implementing OTR in XMPP let me make the
following three statements.

1. While it is good practice for unencrypted messages to be send to the raw jid
and have the receiving server or user decide how they should be routed OTR
messages must be send to a specific resource. To make this work the user should
be given the option to select the presence (which can be assisted with some
educated guessing by the client based on previous messages).  Furthermore a
client should encourage a user to choose meaningful presences instead of the
clients name or even random ones. Something like `/mobile`, `/notebook`,
`/desktop` is a greater assist to any one who wants to start an otr session then
`/Gajim`, `/mcabber` or `/pidgin`.

2. Messages should be tagged private and no-copy to avoid unnecessary traffic or
otr error loops with faulty clients. This tagging should be done even if your
own client doesn’t support carbons.

3. When dealing with “legacy clients” — meaning clients which don’t follow my
advise — a client should be extra careful not to create message loops. This
means to not respond with otr errors if a client is not 100% sure it is the only
client which received the message
