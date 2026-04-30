package nl.detoxathome.remotecaremanager.service.model;

import nl.detoxathome.remotecaremanager.dao.BaseDatabaseObject;
import nl.detoxathome.remotecaremanager.dao.DatabaseField;
import nl.detoxathome.remotecaremanager.dao.DatabaseType;

public class GroupMember extends BaseDatabaseObject {
	@DatabaseField(value=DatabaseType.STRING, index=true)
	private String groupId;

	@DatabaseField(value=DatabaseType.STRING, index=true)
	private String user;
	
	@DatabaseField(value=DatabaseType.STRING)
	private String label;

	public String getGroupId() {
		return groupId;
	}

	public void setGroupId(String groupId) {
		this.groupId = groupId;
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}
}
