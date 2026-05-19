package nl.detoxathome.remotecaremanager.client.model.detox;

import nl.detoxathome.remotecaremanager.dao.Database;
import nl.detoxathome.remotecaremanager.dao.DatabaseTableDef;
import nl.rrd.utils.exception.DatabaseException;

public class DetoxDigitalGuideDialogueCapabilityTable extends
		DatabaseTableDef<DetoxDigitalGuideDialogueCapability> {
	public static final String NAME = "digital_guide_dialogue_capabilities";

	private static final int VERSION = 0;

	public DetoxDigitalGuideDialogueCapabilityTable() {
		super(NAME, DetoxDigitalGuideDialogueCapability.class, VERSION, false);
	}

	@Override
	public int upgradeTable(int version, Database db, String physTable)
			throws DatabaseException {
		return VERSION;
	}
}
