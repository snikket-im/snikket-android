###Changelog

####Version 1.15.0
* New [Blind Trust Before Verification](https://gultsch.de/trust.html) mode
* Easily share Barcode and XMPP uri from Account details
* Automatically deactivate own devices after 7 day of inactivity
* Improvements fo doze/push mode
* bug fixes

####Version 1.14.9
* warn in account details when data saver is enabled
* automatically enable foreground service after detecting frequent restarts
* bug fixes

####Version 1.14.8
* bug fixes

####Version 1.14.7
* error message accessible via context menu for failed messages
* don't include pgp signature in anonymous mucs
* bug fixes

####Version 1.14.6
* make error notification dismissable
* bug fixes


####Version 1.14.5
* expert setting to delete OMEMO identities
* bug fixes

####Version 1.14.4
* bug fixes

####Version 1.14.3
* XEP-0377: Spam Reporting
* fix rare start up crashes

####Version 1.14.2
* support ANONYMOUS SASL
* bug fixes

####Version 1.14.1
* Press lock icon to see why OMEMO is deactivated
* bug fixes

####Version 1.14.0
* Improvments for N
* Quick Reply to Notifications on N
* Don't download avatars and files when data saver is on
* bug fixes

####Version 1.13.9
* bug fixes

####Version 1.13.8
* show identities instead of resources in selection dialog
* allow TLS direct connect when port is set to 5223
* bug fixes

####Version 1.13.7
* bug fixes

####Version 1.13.6
* thumbnails for videos
* bug fixes

####Version 1.13.5
* bug fixes

####Version 1.13.4
* support jingle ft:4
* show contact as DND if one resource is
* bug fixes

####Version 1.13.3
* bug fixes

####Version 1.13.2
* new PGP decryption logic
* bug fixes

####Version 1.13.1
* changed some colors in dark theme
* fixed fall-back message for OMEMO

####Version 1.13.0
* configurable dark theme
* opt-in to share Last User Interaction

####Version 1.12.9
* make grace period configurable

####Version 1.12.8
* more bug fixes :-(

####Version 1.12.7
* bug fixes

####Version 1.12.6
* bug fixes

####Version 1.12.5
* new create conference dialog
* show first unread message on top
* show geo uri as links
* circumvent long message DOS

####Version 1.12.4
* show offline members in conference (needs server support)
* various bug fixes

####Version 1.12.3
* make omemo default when all resources support it
* show presence of other resources as template
* start typing in StartConversationsActivity to search
* various bug fixes and improvements

####Version 1.12.2
* fixed pgp presence signing

####Version 1.12.1
* small bug fixes

####Version 1.12.0
* new welcome screen that makes it easier to register account
* expert setting to modify presence

####Version 1.11.7
* Share xmpp uri from conference details
* add setting to allow quick sharing
* various bug fixes

####Version 1.11.6
* added preference to disable notification light
* various bug fixes

####Version 1.11.5
* check file ownership to not accidentally share private files

####Version 1.11.4
* fixed a bug where contacts are shown as offline
* improved broken PEP detection

####Version 1.11.3
* check maximum file size when using HTTP Upload
* properly calculate caps hash

####Version 1.11.2
* only add image files to media scanner
* allow to delete files
* various bug fixes

####Version 1.11.1
* fixed some bugs when sharing files with Conversations

####Version 1.11.0
* OMEMO encrypted conferences

####Version 1.10.1
* made message correction opt-in
* various bug fixes

####Version 1.10.0
* Support for XEP-0357: Push Notifications
* Support for XEP-0308: Last Message Correction
* introduced build flavors to make dependence on play-services optional

####Version 1.9.4
* prevent cleared Conversations from reloading history with MAM
* various MAM fixes

####Version 1.9.3
* expert setting that enables host and port configuration
* expert setting opt-out of bookmark autojoin handling
* offer to rejoin a conference after server sent unavailable
* internal rewrites

####Version 1.9.2
* prevent startup crash on Sailfish OS
* minor bug fixes

####Version 1.9.1
* minor bug fixes incl. a workaround for nimbuzz.com

####Version 1.9.0
* Per conference notification settings
* Let user decide whether to compress pictures
* Support for XEP-0368
* Ask user to exclude Conversations from battery optimizations

####Version 1.8.4
* prompt to trust own OMEMO devices
* fixed rotation issues in avatar publication
* invite non-contact JIDs to conferences

####Version 1.8.3
* brought text selection back

####Version 1.8.2
* fixed stuck at 'connecting...' bug
* make message box behave correctly with multiple links

####Version 1.8.1
* enabled direct share on Android 6.0
* ask for permissions on Android 6.0
* notify on MAM catchup messages
* bug fixes

####Version 1.8.0
* TOR/ORBOT support in advanced settings
* show vcard avatars of participants in a conference

####Version 1.7.3
* fixed PGP encrypted file transfer
* fixed repeating messages in slack conferences

####Version 1.7.2
* decode PGP messages in background


####Versrion 1.7.1
* performance improvements when opening a conversation

####Version 1.7.0
* CAPTCHA support
* SASL EXTERNAL (client certifiates)
* fetching MUC history via MAM
* redownload deleted files from HTTP hosts
* Expert setting to automatically set presence
* bug fixes

####Version 1.6.11
* tab completion for MUC nicks
* history export
* bug fixes

####Version 1.6.10
* fixed facebook login
* fixed bug with ejabberd mam
* use official HTTP File Upload namespace

####Version 1.6.9
* basic keyboard support

####Version 1.6.8
* reworked 'enter is send' setting
* reworked DNS server discovery on lolipop devices
* various bug fixes

####Version 1.6.7
* bug fixes

####Version 1.6.6
* best 1.6 release yet

####Version 1.6.5
* more OMEMO fixes

####Version 1.6.4
* setting to enable white chat bubbles
* limit OMEMO key publish attempts to work around broken PEP
* various bug fixes

####Version 1.6.3
* bug fixes

####Version 1.6.2
* fixed issues with connection time out when server does not support ping

####Version 1.6.1
* fixed crashes

####Version 1.6.0
* new multi-end-to-multi-end encryption method
* redesigned chat bubbles
* show unexpected encryption changes as red chat bubbles
* always notify in private/non-anonymous conferences

####Version 1.5.1
* fixed rare crashes
* improved otr support

####Version 1.5.0
* upload files to HTTP host and share them in MUCs. requires new [HttpUploadComponent](https://github.com/siacs/HttpUploadComponent) on server side

####Version 1.4.5
* fixes to message parser to not display some ejabberd muc status messages

####Version 1.4.4
* added unread count badges on supported devices
* rewrote message parser

####Version 1.4.0
* send button turns into quick action button to offer faster access to take photo, send location or record audio
* visually separate merged messages
* faster reconnects of failed accounts after network switches 
* r/o vcard avatars for contacts
* various bug fixes

####Version 1.3.0
* swipe conversations to end them
* quickly enable / disable account via slider
* share multiple images at once
* expert option to distrust system CAs
* mlink compatibility
* bug fixes

####Version 1.2.0
* Send current location. (requires [plugin](https://play.google.com/store/apps/details?id=eu.siacs.conversations.sharelocation))
* Invite multiple contacts at once
* performance improvements
* bug fixes

####Version 1.1.0
* Typing notifications (must be turned on in settings)
* Various UI performance improvements
* bug fixes

####Version 1.0.4
* load avatars asynchronously on start up
* support for XEP-0092: Software Version

####Version 1.0.3
* load messages asynchronously on start up
* bug fixes

####Version 1.0.2
* skipped

####Version 1.0.1
* accept more ciphers

####Version 1.0
* MUC controls (Affiliaton changes)
* Added download button to notification
* Added check box to hide offline contacts
* Use Material theme and icons on Android L
* Improved security
* bug fixes + code clean up

####Version 0.10
* Support for Message Archive Management
* Dynamically load message history
* Ability to block contacts
* New UI to verify fingerprints
* Ability to change password on server
* removed stream compression
* quiet hours
* fixed connection issues on ipv6 servers

####Version 0.9.3
* bug fixes

####Version 0.9.2
* more bug fixes

####Version 0.9.1
* bug fixes including some that caused Conversations to crash on start

####Version 0.9
* arbitrary file transfer
* more options to verify OTR (SMP, QR Codes, NFC)
* ability to create instant conferences
* r/o dynamic tags (presence and roster groups)
* optional foreground service (expert option)
* added SCRAM-SHA1 login method
* bug fixes

####Version 0.8.4
* bug fixes

####Version 0.8.3
* increased UI performance
* fixed rotation bugs

####Version 0.8.2
* Share contacts via QR codes or NFC
* Slightly improved UI
* minor bug fixes

####Version 0.8.1
* minor bug fixes

####Version 0.8
* Download HTTP images
* Show avatars in MUC tiles
* Disabled SSLv3
* Performance improvements
* bug fixes

####Version 0.7.3
* revised tablet ui
* internal rewrites
* bug fixes

####Version 0.7.2
* show full timestamp in messages
* brought back option to use JID to identify conferences
* optionally request delivery receipts (expert option)
* more languages
* bug fixes

####Version 0.7.1
* Optionally use send button as status indicator

####Version 0.7
* Ability to disable notifications for single conversations
* Merge messages in chat bubbles
* Fixes for OpenPGP and OTR (please republish your public key)
* Improved reliability on sending messages
* Join password protected Conferences
* Configurable font size
* Expert options for encryption

####Version 0.6
* Support for server side avatars
* save images in gallery
* show contact name and picture in non-anonymous conferences
* reworked account creation
* various bug fixes

####Version 0.5.2
* minor bug fixes

####Version 0.5.1
* couple of small bug fixes that have been missed in 0.5
* complete translations for Swedish, Dutch, German, Spanish, French, Russian

####Version 0.5
* UI overhaul
* MUC / Conference bookmarks
* A lot of bug fixes

####Version 0.4
* OTR file encryption
* keep OTR messages and files on device until both parties or online at the same time
* XEP-0333. Mark whether the other party has read your messages
* Delayed messages are now tagged properly
* Share images from the Gallery
* Infinit history scrolling
* Mark the last used presence in presence selection dialog

####Version 0.3
* Mostly bug fixes and internal rewrites
* Touch contact picture in conference to highlight
* Long press on received image to share
* made OTR more reliable
* improved issues with occasional message lost
* experimental conference encryption. (see FAQ)

####Version 0.2.3
* regression fix with receiving encrypted images

####Version 0.2.2
* Ability to take photos directly
* Improved openPGP offline handling
* Various bug fixes
* Updated Translations

####Version 0.2.1
* Various bug fixes
* Updated Translations

####Version 0.2
* Image file transfer
* Better integration with OpenKeychain (PGP encryption)
* Nicer conversation tiles for conferences
* Ability to clear conversation history
* A lot of bug fixes and code clean up

####Version 0.1.3
* Switched to minidns library to resolve SRV records
* Faster DNS in some cases
* Enabled stream compression
* Added permanent notification when an account fails to connect
* Various bug fixes involving message notifications
* Added support for DIGEST-MD5 auth

####Version 0.1.2
* Various bug fixes relating to conferences
* Further DNS lookup improvements

####Version 0.1.1
* Fixed the 'server not found' bug

####Version 0.1
* Initial release
