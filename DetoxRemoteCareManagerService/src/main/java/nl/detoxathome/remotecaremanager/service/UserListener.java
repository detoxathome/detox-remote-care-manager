package nl.detoxathome.remotecaremanager.service;

import nl.detoxathome.remotecaremanager.client.model.Role;
import nl.detoxathome.remotecaremanager.service.model.User;

public interface UserListener {
	void userProfileUpdated(User user, User oldProfile);
	void userRoleChanged(User user, Role oldRole);
	void userActiveChanged(User user);
	void userAddedToProject(User user, String project, Role role);
	void userRemovedFromProject(User user, String project, Role role);
	void userAddedAsSubject(User user, User profUser);
	void userRemovedAsSubject(User user, User profUser);
}
