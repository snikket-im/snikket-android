package eu.siacs.conversations.entities;

import android.annotation.SuppressLint;

@SuppressLint("DefaultLocale")
public class MucOptions {
	public class User {
		public static final int ROLE_MODERATOR = 3;
		public static final int ROLE_NONE = 0;
		public static final int ROLE_PARTICIPANT = 2;
		public static final int ROLE_VISITOR = 1;
		public static final int AFFILIATION_ADMIN = 4;
		public static final int AFFILIATION_OWNER = 3;
		public static final int AFFILIATION_MEMBER = 2;
		public static final int AFFILIATION_OUTCAST = 1;
		public static final int AFFILIATION_NONE = 0;
		
		private int role;
		private int affiliation;
		public int getRole() {
			return this.role;
		}
		public void setRole(String role) {
			role = role.toLowerCase();
			if (role.equals("moderator")) {
				this.role = ROLE_MODERATOR;
			} else if (role.equals("participant")) {
				this.role = ROLE_PARTICIPANT;
			} else if (role.equals("visitor")) {
				this.role = ROLE_VISITOR;
			} else {
				this.role = ROLE_NONE;
			}
		}
		public int getAffiliation() {
			return this.affiliation;
		}
		public void setAffiliation() {
			
		}
	}
}
