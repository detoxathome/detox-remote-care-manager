package nl.detoxathome.remotecaremanager.app;

import nl.detoxathome.remotecaremanager.service.sso.SSOToken;
import nl.detoxathome.remotecaremanager.service.sso.SSOTokenRepository;
import nl.rrd.utils.AppComponent;

import java.util.ArrayList;
import java.util.List;

@AppComponent
public class DetoxRemoteCareManagerSsoTokenRepository extends SSOTokenRepository {
	@Override
	public List<SSOToken> getTokens() {
		return new ArrayList<>();
	}
}
