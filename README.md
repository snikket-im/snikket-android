#Conversations
Conversations is an open source XMPP client for Android 4.0+ smart phones

![alt tag](https://raw.githubusercontent.com/siacs/Conversations/master/screenshots.png)

##Design principles
* Be as beautiful and easy to use as possible without sacrificing security or
  privacy
* Rely on existing, well established protocols
* Do not require a Google Account or specifically Google Cloud Messaging (GCM)
* Require as little permissons as possible

##Features
* End-to-end encryption with either OTR or openPGP
* Holo UI
* Multiple Accounts
* Group Chats
* Contact list integration

###XMPP Features
Conversations works with every XMPP server out there. However XMPP is an extensible
protocol. These extensions are standardized as well in so called XEP’s.
Conversations supports a couple of those to make the overall userexperience better. There is a
chance that your current XMPP server does not support these extensions.
Therefore to get the most out of Conversations you should consider either switching to an
XMPP server that does or - even better - run your own XMPP server for you and
your friends.
These XEPs are - as of now:
* XEP-0198: Stream Management allows XMPP to surive small network outages and changes of the underlying TCP connection.
* XEP-0280: Message Carbons which automatically syncs the messages you send to
  your desktop client and thus allows you to switch seamlessly from your mobile
  client to your desktop client and back within one conversation.
* XEP-0237: Roster Versioning mainly to save bandwith on poor mobile connections

##FAQ
###Security
####Why are there to end-to-end encryption methods and which one should I choose?
In most cases OTR should be the encryption method of choice. It works out of the box with most contacts as long as they are online.
