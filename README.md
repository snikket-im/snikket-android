# Snikket Android client
<img src="https://codeberg.org/iNPUTmice/Conversations/raw/branch/master/fastlane/metadata/android/en-US/images/phoneScreenshots/01.png" width="216"/>
<img src="https://codeberg.org/iNPUTmice/Conversations/raw/branch/master/fastlane/metadata/android/en-US/images/phoneScreenshots/02.png" width="216"/>
<img src="https://codeberg.org/iNPUTmice/Conversations/raw/branch/master/fastlane/metadata/android/en-US/images/phoneScreenshots/03.png" width="216"/>
<img src="https://codeberg.org/iNPUTmice/Conversations/raw/branch/master/fastlane/metadata/android/en-US/images/phoneScreenshots/04.png" width="216"/>
<img src="https://codeberg.org/iNPUTmice/Conversations/raw/branch/master/fastlane/metadata/android/en-US/images/phoneScreenshots/05.png" width="216"/>
</p>

This is the source code for the Snikket Android client.
#### Conversations is consuming a lot of battery, what can I do?

Battery attribution on Android can be misleading. Conversations may appear to consume a lot of battery because it’s active, but this doesn’t necessarily mean it drains your battery significantly faster. For example, if your phone lasts 24 hours with Conversations and 25 hours without it, the impact is only about an hour, which is often negligible for most users who charge their phones nightly.

To check for potential issues, use the account server info screen in Conversations to verify whether server features are consistently available. Additionally, ensure your session age is appropriately long (e.g., several days or since the last time you restarted your phone). A session age of just minutes might indicate a problem unless you recently turned on your phone.

Battery usage percentages can also be deceptive. On low-usage days, Conversations might rank high simply because it’s running, even if its actual impact is minimal compared to something like taking a photo with the camera. Evaluating battery life with and without the app under similar conditions is the best way to assess its true effect.

Address book integration is only available in the F-Droid version.

longer necessary.

# License

#### Can I export my chats as plain text files?

There is a tool called [ceb2txt](https://codeberg.org/iNPUTmice/ceb2txt) that can convert backup file (.ceb) into txt files.

Snikket for Android is based on [Conversations](https://conversations.im/) by Daniel Gultsch.

The official Conversations repository is available at: https://codeberg.org/iNPUTmice/Conversations

Copyright (c) 2014-2024 Daniel Gultsch and Snikket Community Interest Company.

Snikket and the Snikket logo are trademarks of Snikket Community Interest Company.

Licensed under GPL License Version 3.
