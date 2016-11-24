# Conversations

Conversations: the very last word in instant messaging

[![Google Play](https://conversations.im/images/en-play-badge.png)](https://play.google.com/store/apps/details?id=eu.siacs.conversations&referrer=utm_source%3Dgithub) [![Amazon App Store](https://images-na.ssl-images-amazon.com/images/G/01/AmazonMobileApps/amazon-apps-store-us-black.png)](http://www.amazon.com/dp/B00WD35AAC/)

![screenshots](https://raw.githubusercontent.com/siacs/Conversations/master/screenshots.png)

## Design principles

* Be as beautiful and easy to use as possible without sacrificing security or
  privacy
* Rely on existing, well established protocols (XMPP)
* Do not require a Google Account or specifically Google Cloud Messaging (GCM)
* Require as few permissions as possible

## Features

* End-to-end encryption with [OMEMO](http://conversations.im/omemo/), [OTR](https://otr.cypherpunks.ca/), or [OpenPGP](http://openpgp.org/about/)
* Send and receive images as well as other kind of files
* Share your location via an external [plug-in](https://play.google.com/store/apps/details?id=eu.siacs.conversations.sharelocation&referrer=utm_source%3Dgithub)
* Indication when your contact has read your message
* Intuitive UI that follows Android Design guidelines
* Pictures / Avatars for your Contacts
* Syncs with desktop client
* Conferences (with support for bookmarks)
* Address book integration
* Multiple accounts / unified inbox
* Very low impact on battery life


### XMPP Features

Conversations works with every XMPP server out there. However XMPP is an
extensible protocol. These extensions are standardized as well in so called
XEP's. Conversations supports a couple of these to make the overall user
experience better. There is a chance that your current XMPP server does not
support these extensions; therefore to get the most out of Conversations you
should consider either switching to an XMPP server that does or — even better —
run your own XMPP server for you and your friends. These XEP's are:

* [XEP-0065: SOCKS5 Bytestreams](http://xmpp.org/extensions/xep-0065.html) (or mod_proxy65). Will be used to transfer
  files if both parties are behind a firewall (NAT).
* [XEP-0163: Personal Eventing Protocol](http://xmpp.org/extensions/xep-0163.html) for avatars and OMEMO.
* [XEP-0191: Blocking command](http://xmpp.org/extensions/xep-0191.html) lets you blacklist spammers or block contacts
  without removing them from your roster.
* [XEP-0198: Stream Management](http://xmpp.org/extensions/xep-0198.html) allows XMPP to survive small network outages and
  changes of the underlying TCP connection.
* [XEP-0280: Message Carbons](http://xmpp.org/extensions/xep-0280.html) which automatically syncs the messages you send to
  your desktop client and thus allows you to switch seamlessly from your mobile
  client to your desktop client and back within one conversation.
* [XEP-0237: Roster Versioning](http://xmpp.org/extensions/xep-0237.html) mainly to save bandwidth on poor mobile connections
* [XEP-0313: Message Archive Management](http://xmpp.org/extensions/xep-0313.html) synchronize message history with the
  server. Catch up with messages that were sent while Conversations was
  offline.
* [XEP-0352: Client State Indication](http://xmpp.org/extensions/xep-0352.html) lets the server know whether or not
  Conversations is in the background. Allows the server to save bandwidth by
  withholding unimportant packages.
* [XEP-0363: HTTP File Upload](http://xmpp.org/extensions/xep-0363.html) allows you to share files in conferences
  and with offline contacts.

## Team

#### Head of Development

* [Daniel Gultsch](https://github.com/inputmice)

#### Code Contributions

(In order of appearance)

* [Rene Treffer](https://github.com/rtreffer) ([PRs](https://github.com/siacs/Conversations/pulls?utf8=%E2%9C%93&q=is%3Apr+author%3Artreffer+is%3Amerged))
* [Andreas Straub](https://github.com/strb) ([PRs](https://github.com/siacs/Conversations/pulls?utf8=%E2%9C%93&q=is%3Apr+author%3Astrb+is%3Amerged))
* [Alethea Butler](https://github.com/alethea) ([PRs](https://github.com/siacs/Conversations/pulls?utf8=%E2%9C%93&q=is%3Apr+author%3Aalethea+is%3Amerged))
* [M. Dietrich](https://github.com/emdete) ([PRs](https://github.com/siacs/Conversations/pulls?utf8=%E2%9C%93&q=is%3Apr+author%3Aemdete+is%3Amerged))
* [betheg](https://github.com/betheg) ([PRs](https://github.com/siacs/Conversations/pulls?utf8=%E2%9C%93&q=is%3Apr+author%3Abetheg+is%3Amerged))
* [Sam Whited](https://github.com/SamWhited) ([PRs](https://github.com/siacs/Conversations/pulls?utf8=%E2%9C%93&q=is%3Apr+author%3ASamWhited+is%3Amerged))
* [BrianBlade](https://github.com/BrianBlade) ([PRs](https://github.com/siacs/Conversations/pulls?utf8=%E2%9C%93&q=is%3Apr+author%3ABrianBlade+is%3Amerged))

#### Logo
* [Ilia Rostovtsev](https://github.com/qooob) (Progress)
* [Diego Turtulici](http://efesto.eigenlab.org/~diesys) (Original)
* [fiaxh](https://github.com/fiaxh) (OMEMO)

#### Translations
Translations are managed on [Transifex](https://www.transifex.com/projects/p/conversations/)

## FAQ

### General

#### How do I install Conversations?

Conversations is entirely open source and licensed under GPLv3. So if you are a
software developer you can check out the sources from GitHub and use Gradle to
build your apk file.

The more convenient way — which not only gives you automatic updates but also
supports the further development of Conversations — is to buy the App in the
Google [Play Store](https://play.google.com/store/apps/details?id=eu.siacs.conversations&referrer=utm_source%3Dgithub).

Buying the App from the Play Store will also give you access to our [beta test](#beta).

#### I don't have a Google Account but I would still like to make a contribution

I accept donations over PayPal, Bitcoin and Flattr. For donations via PayPal you
can use the email address `donate@siacs.eu` or the button below.

[![Donate with PayPal](https://www.paypalobjects.com/en_US/i/btn/btn_donate_LG.gif)](https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=CW3SYT3KG5PDL)

**Disclaimer:** I'm not a huge fan of PayPal and their business policies. For
larger contributions please get in touch with me beforehand and we can talk
about bank transfer (SEPA).

My Bitcoin Address is: `1NxSU1YxYzJVDpX1rcESAA3NJki7kRgeeu`


[![Flattr this!](http://api.flattr.com/button/flattr-badge-large.png)](https://flattr.com/submit/auto?user_id=inputmice&url=http%3A%2F%2Fconversations.siacs.eu&title=Conversations&tags=github&category=software)

#### How do I create an account?
XMPP, like email, is a federated protocol, which means that there is not one company you can create an *official XMPP account* with. Instead there are hundreds, or even thousands, of providers out there. One of those providers is our very own [conversations.im](https://account.conversations.im). If you don’t like to use *conversations.im* use a web search engine of your choice to find another provider. Or maybe your university has one. Or you can run your own. Or ask a friend to run one. Once you've found one, you can use Conversations to create an account. Just select *register new account* on server within the create account dialog.

##### Running your own
If you have a server somewhere and are willing to put some work in, the best alternative-in the spirit of federation-is to run your own. We recommend either [Prosody](https://prosody.im/) or [ejabberd](https://www.ejabberd.im/). Both of which have their own strengths. Ejabberd is slightly more mature nowadays but Prosody is arguably easier to set up.

For Prosody you need a couple of so called [community modules](https://modules.prosody.im/) most of which are maintained by the same people that develop Prosody.

If you pick ejabberd make sure you use the latest version. Linux Distributions might bundle some very old versions of it.

#### Where can I set up a custom hostname / port
Conversations will automatically look up the SRV records for your domain name
which can point to any hostname port combination. If your server doesn’t provide
those please contact your admin and have them read
[this](http://prosody.im/doc/dns#srv_records). If your server operator is unwilling
to fix this you can enable advanced server settings in the expert settings of
Conversations.

#### I get 'Incompatible Server'

As regular user you should be picking a different server. The server you selected
is probably insecure and/or very old.

If you are a server administrator you should make sure that your server provides
STARTTLS. XMPP over TLS (on a different port) is not sufficient.

On rare occasions this error message might also be caused by a server not providing
a login (SASL) mechanism that Conversations is able to handle. Conversations supports
SCRAM-SHA1, PLAIN, EXTERNAL (client certs) and DIGEST-MD5.

#### How do XEP-0357: Push Notifications work?
You need to be running the Play Store version of Conversations and your server needs to support push notifications.¹ Because *Google Cloud Notifications (GCM)* are tied with an API key to a specific app your server can not initiate the push message directly. Instead your server will send the push notification to the Conversations App server (operated by us) which then acts as a proxy and initiates the push message for you. The push message sent from our App server through GCM doesn’t contain any personal information. It is just an empty message which will wake up your device and tell Conversations to reconnect to your server. The information send from your server to our App server depends on the configuration of your server but can be limited to your account name. (In any case the Conversations App server won't redirect any information through GCM even if your server sends this information.)

In summary Google will never get hold of any personal information besides that *something* happened. (Which doesn’t even have to be a message but can be some automated event as well.) We - as the operator of the App server - will just get hold of your account name (without being able to tie this to your specific device).

If you don’t want this simply pick a server which does not offer Push Notifications or build Conversations yourself without support for push notifications. (This is available via a gradle build flavor.) Non-play store source of Conversations like the Amazon App store will also offer a version without push notifications. Conversations will just work as before and maintain its own TCP connection in the background.

 ¹ Your server only needs to support the server side of [XEP-0357: Push Notifications](http://xmpp.org/extensions/xep-0357.html). If you use the Play Store version you do **not** need to run your own app server. The server modules are called *mod_cloud_notify* on Prosody and *mod_push* on ejabberd.

#### Conversations doesn't work for me. Where can I get help?

You can join our conference room on `conversations@conference.siacs.eu`.
A lot of people in there are able to answer basic questions about the usage of
Conversations or can provide you with tips on running your own XMPP server. If
you found a bug or your app crashes please read the Developer / Report Bugs
section of this document.

#### I need professional support with Conversations or setting up my server

I'm available for hire. Contact me at `inputmice@siacs.eu`.

#### How does the address book integration work?

The address book integration was designed to protect your privacy. Conversations
neither uploads contacts from your address book to your server nor fills your
address book with unnecessary contacts from your online roster. If you manually
add a Jabber ID to your phones address book Conversations will use the name and
the profile picture of this contact. To make the process of adding Jabber IDs to
your address book easier you can click on the profile picture in the contact
details within Conversations. This will start an "add to address book" intent
with the JID as the payload. This doesn't require Conversations to have write
permissions on your address book but also doesn't require you to copy/paste a
JID from one app to another.

#### I get 'delivery failed' on my messages

If you get delivery failed on images it's probably because the recipient lost
network connectivity during reception. In that case you can try it again at a
later time.

For text messages the answer to your question is a little bit more complex.
When you see 'delivery failed' on text messages, it is always something that is
being reported by the server. The most common reason for this is that the
recipient failed to resume a connection. When a client loses connectivity for a
short time the client usually has a five minute window to pick up that
connection again. When the client fails to do so because the network
connectivity is out for longer than that all messages sent to that client will
be returned to the sender resulting in a delivery failed.

Instead of returning a message to the sender both ejabberd and prosody have the
ability to store messages in offline storage when the disconnecting client is
the only client. In prosody this is available via an extra module called
```mod_smacks_offline```. In ejabberd this is available via some configuration
settings.

Other less common reasons are that the message you sent didn't meet some
criteria enforced by the server (too large, too many). Another reason could be
that the recipient is offline and the server doesn't provide offline storage.

Usually you are able to distinguish between these two groups in the fact that
the first one happens always after some time and the second one happens almost
instantly.

#### Where can I see the status of my contacts? How can I set a status or priority?

Statuses are a horrible metric. Setting them manually to a proper value rarely
works because users are either lazy or just forget about them. Setting them
automatically does not provide quality results either. Keyboard or mouse
activity as indicator for example fails when the user is just looking at
something (reading an article, watching a movie). Furthermore automatic setting
of status always implies an impact on your privacy (are you sure you want
everybody in your contact list to know that you have been using your computer at
4am‽).

In the past status has been used to judge the likelihood of whether or not your
messages are being read. This is no longer necessary. With Chat Markers
(XEP-0333, supported by Conversations since 0.4) we have the ability to **know**
whether or not your messages are being read.  Similar things can be said for
priorities. In the past priorities have been used (by servers, not by clients!)
to route your messages to one specific client. With carbon messages (XEP-0280,
supported by Conversations since 0.1) this is no longer necessary. Using
priorities to route OTR messages isn't practical either because they are not
changeable on the fly. Metrics like last active client (the client which sent
the last message) are much better.

Unfortunately these modern replacements for legacy XMPP features are not widely
adopted. However Conversations should be an instant messenger for the future and
instead of making Conversations compatible with the past we should work on
implementing new, improved technologies and getting them into other XMPP clients
as well.

Making these status and priority optional isn't a solution either because
Conversations is trying to get rid of old behaviours and set an example for
other clients.

#### How do I backup / move Conversations to a new device?
On the one hand Conversations supports Message Archive Management to keep a server side history of your messages so when migrating to a new device that device can display your entire history. However that does not work if you enable OMEMO due to its forward secrecy. (Read [The State of Mobile XMPP in 2016](https://gultsch.de/xmpp_2016.html) especially the section on encryption.)

If you migrate to a new device and would still like to keep your history please use a third party backup tool like [oandbackup](https://github.com/jensstein/oandbackup) or ```adb backup``` from your computer.  It is important that your deactivate your account before backup and activate it only after a succesful restore. Otherwise OMEMO might not work afterwards. 

#### Conversations is missing a certain feature

I'm open for new feature suggestions. You can use the [issue tracker][issues] on
GitHub.  Please take some time to browse through the issues to see if someone
else already suggested it. Be assured that I read each and every ticket. If I
like it I will leave it open until it's implemented. If I don't like it I will
close it (usually with a short comment). If I don't comment on an feature
request that's probably a good sign because this means I agree with you.
Commenting with +1 on either open or closed issues won't change my mind, nor
will it accelerate the development.

#### You closed my feature request but I want it really really badly

Just write it yourself and send me a pull request. If I like it I will happily
merge it if I don't at least you and like minded people get to enjoy it.

#### I need a feature and I need it now!

I am available for hire. Contact me via XMPP: `inputmice@siacs.eu`

### Security

#### Why are there three end-to-end encryption methods and which one should I choose?

* OTR is a legacy encryption method. It works out of the box with most contacts as long as they are online.
* OMEMO works even when a contact is offline, and works with multiple devices. It also allows asynchronous file-transfer when the server has [HTTP File Upload](http://xmpp.org/extensions/xep-0363.html). However, OMEMO is not as widely supported as OTR and is currently implemented only by Conversations and Gajim. OMEMO should be preferred over OTR for contacts who use Conversations.
* OpenPGP (XEP-0027) is a very old encryption method that has some advantages over OTR but should only be used by experts who know what they are doing.

#### How do I use OpenPGP

Before you continue reading you should note that the OpenPGP support in
Conversations is experimental. This is not because it will make the app unstable
but because the fundamental concepts of PGP aren't ready for widespread use.
The way PGP works is that you trust Key IDs instead of JID's or email addresses.
So in theory your contact list should consist of Public-Key-IDs instead of
JID's. But of course no email or XMPP client out there implements these
concepts. Plus PGP in the context of instant messaging has a couple of
downsides: It is vulnerable to replay attacks, it is rather verbose, and
decrypting and encrypting takes longer than OTR. It is however asynchronous and
works well with message carbons.

To use OpenPGP you have to install the open source app
[OpenKeychain](http://www.openkeychain.org) and then long press on the account in
manage accounts and choose renew PGP announcement from the contextual menu.

#### OMEMO is grayed out. What do I do?
OMEMO has two requirments: Your server and the server of your contact need to support PEP. Both of you can verify that indivually by opening your account details and selecting ```Server info``` from the menu. The appearing table should list PEP as available. The second requirement is mutal presence subscription. You can verify that be going into the contact details and see if the both check boxes *Send presence updates* and *Receive presence updates* are checked.

#### How does the encryption for conferences work?

For conferences only OMEMO and OpenPGP are supported as encryption method. (OTR
does not work with multiple participants).

##### OMEMO

OMEMO encryption works only in private (members only) conferences that are non-anonymous.
You need to have presence subscription with every member of the conference.
You can verify that by going into the conference details, long press every member and start
a conversation with them. (Or select 'contact details' if they are already in your contact
list)

The owner of a conference can make a public conference private by going into the conference
details and hit the settings button (the one with the gears) and select both *private* and
*members only*.

If OMEMO is grayed out long pressing the lock icon will reveal some quick hints on why OMEMO
is disabled.

##### OpenPGP

Every participant has to announce their OpenPGP key (see answer above).
If you would like to send encrypted messages to a conference you have to make
sure that you have every participant's public key in your OpenKeychain.
Right now there is no check in Conversations to ensurethat.
You have to take care of that yourself. Go to the conference details and
touch every key id (The hexadecimal number below a contact). This will send you
to OpenKeychain which will assist you on adding the key.  This works best in
very small conferences with contacts you are already using OpenPGP with. This
feature is regarded experimental. Conversations is the only client that uses
XEP-0027 with conferences. (The XEP neither specifically allows nor disallows
this.)

#### Why is Conversations not end-to-end encrypted by default
We briefly had OMEMO as the default E2EE but it turned out to be a usabilty nightmare and thus we reverted that. You can find more information in [the commit message](https://github.com/siacs/Conversations/commit/035d0c79572d5981c53d1bff7f30b484c6542f17) of that change.

Quick reminder that Conversations **always** uses TLS to connect to your server. It won‘t even connect to a server without TLS.

#### What is Blind Trust Before Verification / why are messages marked with a red lock?

Read more about the concept on https://gultsch.de/trust.html

### What clients do I use on other platforms
There are XMPP Clients available for all major platforms.
####Windows / Linux
For your desktop computer we recommend that you use [Gajim](https://gajim.org). You need to install the plugins `OMEMO`, `HTTP Upload` and `URL image preview` to get the best compatibiltiy with Conversations. Plugins can be installed from within the app.
####iOS
Unfortunatly we don‘t have a recommendation for iPhones right now. There are two clients available [ChatSecure](https://chatsecure.org/) and [Monal](https://monal.im/). Both with their own pros and cons.


### Development

<a name="beta"></a>
#### Beta testing
If you bought the App on [Google Play](https://play.google.com/store/apps/details?id=eu.siacs.conversations)
you can get access to the latest beta version by joining the
[Conversations Beta Testers](https://plus.google.com/communities/107649347599361240873)
community on Google+ and then using [this link](https://play.google.com/apps/testing/eu.siacs.conversations)
to sign up for the beta test.

#### How do I build Conversations

Make sure to have ANDROID_HOME point to your Android SDK. Use the Android SDK Manager to install missing dependencies.

    git clone https://github.com/siacs/Conversations.git
    cd Conversations
    ./gradlew assembleFreeDebug

There are two build flavors available. *free* and *playstore*. Unless you know what you are doing you only need *free*.


[![Build Status](https://travis-ci.org/siacs/Conversations.svg?branch=development)](https://travis-ci.org/siacs/Conversations)

#### How do I update/add external libraries?

If the library you want to update is in Maven Central or JCenter (or has its own
Maven repo), add it or update its version in `build.gradle`. If the library is
in the `libs/` directory, you can update it using a subtree merge by doing the
following (using `minidns` as an example):

    git remote add minidns https://github.com/rtreffer/minidns.git
    git fetch minidns
    git merge -s subtree minidns master

To add a new dependency to the `libs/` directory (replacing "name", "branch" and
"url" as necessary):

    git remote add name url
    git merge -s ours --no-commit name/branch
    git read-tree --prefix=libs/name -u name/branch
    git commit -m "Subtree merged in name"

#### How do I debug Conversations

If something goes wrong Conversations usually exposes very little information in
the UI (other than the fact that something didn't work). However with adb
(android debug bridge) you can squeeze some more information out of Conversations.
These information are especially useful if you are experiencing trouble with
your connection or with file transfer.

To use adb you have to connect your mobile phone to your computer with an USB cable
and install `adb`. Most Linux systems have prebuilt packages for that tool. On
Debian/Ubuntu for example it is called `android-tools-adb`.

Furthermore you might have to enable 'USB debugging' in the Developer options of your
phone. After that you can just execute the following on your computer:

    adb -d logcat -v time -s conversations

If need be there are also some Apps on the PlayStore that can be used to show the logcat
directly on your rooted phone. (Search for logcat). However in regards to further processing
(for example to create an issue here on Github) it is more convenient to just use your PC.

#### I found a bug

Please report it to our [issue tracker][issues]. If your app crashes please
provide a stack trace. If you are experiencing misbehavior please provide
detailed steps to reproduce. Always mention whether you are running the latest
Play Store version or the current HEAD. If you are having problems connecting to
your XMPP server your file transfer doesn’t work as expected please always
include a logcat debug output with your issue (see above).

[issues]: https://github.com/siacs/Conversations/issues
