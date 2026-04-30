package nl.detoxathome.remotecaremanager.client.model.detox;

import nl.detoxathome.remotecaremanager.dao.Database;
import nl.detoxathome.remotecaremanager.dao.DatabaseTableDef;
import nl.rrd.utils.exception.DatabaseException;

public class DetoxProcessedMessageQueueTable extends
		DatabaseTableDef<DetoxProcessedMessageQueue> {
	public static final String NAME = "detox_processed_message_queue";

	private static final int VERSION = 1;

	public DetoxProcessedMessageQueueTable() {
		super(NAME, DetoxProcessedMessageQueue.class, VERSION, false);
	}

	@Override
	public int upgradeTable(int version, Database db, String physTable)
			throws DatabaseException {
		if (version == 0)
			return upgradeTableV0(db, physTable);
		else
			return 1;
	}

	private int upgradeTableV0(Database db, String physTable)
			throws DatabaseException {
		return 1;
	}
}
