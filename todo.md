##GSOC teaser tasks

####update Contacts last seen for muc messages as well
The contact class (entities/Contact) has the ability to save the last time that Conversations
  received a message from that contact. Currently this time only gets updated for one-on-one
  messages. In non-anonymous mucs messages from a contact should also update the last seen
  time.

####Select multiple Contact in Choose Contact Activity
Currently the choose Contact activity allows only for one contact to be selected. A long
press on one contact should bring the activity in a mode where the user can select multiple
contacts.
The Activity should then return an array of contacts instead of just one

####Request and respond to message receipts in MUC PNs
Private MUC messages either dont request message receipts or dont respond to them. The source
of error should be determined and eliminated. A rather small tasks that just teaches you a bit
about the stanza parser and generator in Conversations

####Edit dynamic tags / groups
The context menu for the contact list (StartConversationActivity) should offer the ability to
edit groups. (Any UI decissions are left to you) Quick suggestion though: Dialog with
checkboxes for existing groups and some way to enter new tags.
