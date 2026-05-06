package nl.detoxathome.remotecaremanager.service;

import nl.rrd.utils.AppComponent;
import nl.detoxathome.remotecaremanager.dao.DatabaseTableDef;

import java.util.List;

@AppComponent
public abstract class OAuthTableRepository {
	public abstract List<DatabaseTableDef<?>> getOAuthTables();
}
