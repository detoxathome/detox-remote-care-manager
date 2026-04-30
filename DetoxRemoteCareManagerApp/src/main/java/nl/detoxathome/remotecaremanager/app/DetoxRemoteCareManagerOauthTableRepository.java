package nl.detoxathome.remotecaremanager.app;

import nl.detoxathome.remotecaremanager.dao.DatabaseTableDef;
import nl.detoxathome.remotecaremanager.service.OAuthTableRepository;
import nl.rrd.utils.AppComponent;

import java.util.ArrayList;
import java.util.List;

@AppComponent
public class DetoxRemoteCareManagerOauthTableRepository extends OAuthTableRepository {
	@Override
	public List<DatabaseTableDef<?>> getOAuthTables() {
		return new ArrayList<>();
	}
}
