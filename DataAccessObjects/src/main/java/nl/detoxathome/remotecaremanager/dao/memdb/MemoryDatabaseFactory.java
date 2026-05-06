package nl.detoxathome.remotecaremanager.dao.memdb;

import java.io.IOException;

import nl.detoxathome.remotecaremanager.dao.DatabaseConnection;
import nl.detoxathome.remotecaremanager.dao.DatabaseFactory;

/**
 * This database factory provides access to a single {@link
 * MemoryDatabaseConnection MemoryDatabaseConnection}.
 * 
 * @author Dennis Hofs (RRD)
 */
public class MemoryDatabaseFactory extends DatabaseFactory {
	private MemoryDatabaseConnection connection =
			new MemoryDatabaseConnection();

	@Override
	protected DatabaseConnection doConnect() throws IOException {
		return connection;
	}
}
