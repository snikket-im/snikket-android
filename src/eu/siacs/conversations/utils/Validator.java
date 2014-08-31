package eu.siacs.conversations.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Validator {
	public static final Pattern VALID_JID = Pattern.compile(
			"^[^@/<>'\"\\s]+@[^@/<>'\"\\s]+$", Pattern.CASE_INSENSITIVE);

	public static boolean isValidJid(String jid) {
		Matcher matcher = VALID_JID.matcher(jid);
		return matcher.find();
	}
}
