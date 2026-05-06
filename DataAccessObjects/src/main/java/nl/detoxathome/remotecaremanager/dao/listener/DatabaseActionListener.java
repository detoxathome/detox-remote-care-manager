package nl.detoxathome.remotecaremanager.dao.listener;

import java.util.List;

import nl.detoxathome.remotecaremanager.dao.DatabaseAction;

/**
 * This listener can be notified when database actions are added. This is done
 * when action logging is enabled, for database synchronization. Listeners can
 * be registered in the {@link DatabaseListenerRepository
 * DatabaseListenerRepository}.
 * 
 * @author Dennis Hofs (RRD)
 */
public interface DatabaseActionListener {
	
	/**
	 * Called when one or more database actions are added. All actions in the
	 * specified list should have the same type {@link
	 * nl.detoxathome.remotecaremanager.dao.DatabaseAction.Action DatabaseAction.Action}.
	 * 
	 * @param database the database
	 * @param table the table
	 * @param actions the new database actions
	 */
	void onAddDatabaseActions(String database, String table,
			List<DatabaseAction> actions);
}
