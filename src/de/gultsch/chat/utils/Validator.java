package de.gultsch.chat.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Validator {
	public static final Pattern VALID_JID = 
		    Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$", Pattern.CASE_INSENSITIVE);
	
	public static boolean isValidJid(String jid) {
		Matcher matcher = VALID_JID.matcher(jid);
		return matcher.find();
	}
}
