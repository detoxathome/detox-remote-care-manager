package nl.detoxathome.remotecaremanager.service.access;

import nl.detoxathome.remotecaremanager.dao.Database;
import nl.detoxathome.remotecaremanager.dao.DatabaseTableDef;
import nl.detoxathome.remotecaremanager.service.model.User;
import nl.rrd.utils.exception.DatabaseException;

import java.util.List;

public interface ProjectUserAccessControl {
	List<DatabaseTableDef<?>> getTables();
	List<User> findAccessibleUsers(Database authDb, User user,
			List<User> projectUsers) throws DatabaseException;
	boolean isAccessibleUser(Database authDb, User user, User subject)
			throws DatabaseException;
}
