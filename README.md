#Conversations
Conversations is an open source XMPP (formerly known as Jabber) client for
Android 4.0+ smart phones.
[![Google Play](http://developer.android.com/images/brand/en_generic_rgb_wo_45.png)](https://play.google.com/store/apps/details?id=eu.siacs.conversations)

![screenshots](https://raw.githubusercontent.com/siacs/Conversations/master/screenshots.png)

##Design principles
* Be as beautiful and easy to use as possible without sacrificing security or
  privacy
* Rely on existing, well established protocols
* Do not require a Google Account or specifically Google Cloud Messaging (GCM)
* Require as little permissons as possible

##Features
* End-to-end encryption with either OTR or openPGP
* Sending and receiving images
* Holo UI
* Syncs with your desktop client
* Group Chats
* Address book integration
* Multiple Accounts / unified inbox

###XMPP Features
Conversations works with every XMPP server out there. However XMPP is an extensible
protocol. These extensions are standardized as well in so called XEP’s.
Conversations supports a couple of those to make the overall userexperience better. There is a
chance that your current XMPP server does not support these extensions.
Therefore to get the most out of Conversations you should consider either switching to an
XMPP server that does or - even better - run your own XMPP server for you and
your friends.
These XEPs are - as of now:
* XEP-0065: SOCKS5 Bytestreams - or rather mod_proxy65. Will be used to tranfer files if both parties are behind a firewall (NAT).
* XEP-0138: Stream Compression saves bandwith
* XEP-0198: Stream Management allows XMPP to surive small network outages and changes of the underlying TCP connection.
* XEP-0280: Message Carbons which automatically syncs the messages you send to
  your desktop client and thus allows you to switch seamlessly from your mobile
  client to your desktop client and back within one conversation.
* XEP-0237: Roster Versioning mainly to save bandwith on poor mobile connections

##Contributors
(In order of appearance)

###Code
* Rene Treffer @rtreffer
* Andreas Straub @strb

###Translations
* @beriain (Spanish and Basque)

##FAQ
###General
####How do I install Conversations?
Conversations is entirely open source and licensed under GPLv3. So if you are a
software developer you can check out the sources from github and use ant to
build your apk file.

The more convenient way - which not only gives you automatic updates but also
supports the further development of Conversations - is to buy the App in the Google
[Play Store](https://play.google.com/store/apps/details?id=eu.siacs.conversations).


####How do I create an account?
XMPP like email for example is a federated protocol which means that there is
not one company you can create your 'official xmpp account' with but there are
hundreds or even thousands of provider out there. To find one use a web search
engine of your choice. Or maybe your univeristy has one. Or you can run your own.
Or ask a friend to run one. Once you found one you can use Conversations to
create an account. Just select 'register new account on server' within the
create account dialog.
####How does the address book integration work?
The address bock integration was designed to protect your privacy. Conversations
neither uploads contacts from your address book to your server nor fills your
address book with unnecessary contacts from your online roster. If you manually
add a Jabber ID to your phones address book Conversations will use the name and
the profile picture of this contact. To make the process of adding Jabber IDs to
your address book easier you can click on the profile picture in the contact
detais within Conversations. This will start an add to address book intent with the jabber ID
as payload. This doesn’t require Conversations to have write permissions on your
address book but also doesn’t require you to copy past Jabber ID from one app to
another.
####How can I change my status
You can set an account offline by long pressing on it and select temporarily
disable account from the context menu. Other statuses like away, DND and N/A are
not supported for simplicity reasons. Users tend to forget their status, other
users ignore them and setting the status automatically would mean too much of an
impact on privacy.
###Security
####Why are there to end-to-end encryption methods and which one should I choose?
In most cases OTR should be the encryption method of choice. It works out of the box with most contacts as long as they are online.
However PGP can be in some cases (carbonated messages to multiple clients) be
more flexible.
####How do I use openPGP
Before you continue reading you should notice that the openPGP support in
Conversations is marked as experimental. This is not because it will make the app
unstable but because the fundamental concepts of PGP aren't ready for a
widespread use. The way PGP works is that you trust Key IDs instead of XMPP- or email addresses. So in theory your contact list should consist of Public-Key-IDs instead of email addresses. But of course no email or xmpp client out there implements these concepts. Plus PGP in the context of instant messaging has a couple of downsides. It is vulnerable to replay attacs, it is rather verbose, decryping and encrypting takes longer than OTR. It is however asynchronous and works well with carbonated messages.

To use openpgp you have to install the opensource app OpenKeychain (www.openkeychain.org) and then long press on the account in manage accounts and choose renew PGP announcement from the contextual menu.
###Development
####How do I build Conversations
Make sure to have ANDROID_HOME point to your Android SDK
```
git clone https://github.com/siacs/Conversations.git
cd Conversations
git submodule update --init --recursive
ant clean
ant debug
```
