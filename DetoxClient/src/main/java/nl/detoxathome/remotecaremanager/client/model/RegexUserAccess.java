package nl.detoxathome.remotecaremanager.client.model;

import nl.detoxathome.remotecaremanager.dao.DatabaseField;
import nl.detoxathome.remotecaremanager.dao.DatabaseType;
import nl.detoxathome.remotecaremanager.dao.UserDatabaseObject;

public class RegexUserAccess extends UserDatabaseObject {
	@DatabaseField(value= DatabaseType.STRING)
	private String emailRegex;

	public RegexUserAccess() {
	}

	public RegexUserAccess(String user, String emailRegex) {
		super(user);
		this.emailRegex = emailRegex;
	}

	public String getEmailRegex() {
		return emailRegex;
	}

	public void setEmailRegex(String emailRegex) {
		this.emailRegex = emailRegex;
	}
}
