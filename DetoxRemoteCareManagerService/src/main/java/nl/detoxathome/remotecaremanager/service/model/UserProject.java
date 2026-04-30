package nl.detoxathome.remotecaremanager.service.model;

import nl.detoxathome.remotecaremanager.client.model.Role;
import nl.detoxathome.remotecaremanager.dao.BaseDatabaseObject;
import nl.detoxathome.remotecaremanager.dao.DatabaseField;
import nl.detoxathome.remotecaremanager.dao.DatabaseType;

public class UserProject extends BaseDatabaseObject {
	@DatabaseField(value=DatabaseType.STRING, index=true)
	private String user;

	@DatabaseField(value=DatabaseType.STRING, index=true)
	private String projectCode;
	
	@DatabaseField(value=DatabaseType.STRING)
	private Role asRole;

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public String getProjectCode() {
		return projectCode;
	}

	public void setProjectCode(String projectCode) {
		this.projectCode = projectCode;
	}

	public Role getAsRole() {
		return asRole;
	}

	public void setAsRole(Role asRole) {
		this.asRole = asRole;
	}
}
