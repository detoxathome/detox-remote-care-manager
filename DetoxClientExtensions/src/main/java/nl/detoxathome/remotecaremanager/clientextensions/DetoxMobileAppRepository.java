package nl.detoxathome.remotecaremanager.clientextensions;

import nl.rrd.utils.AppComponent;
import nl.detoxathome.remotecaremanager.client.MobileApp;
import nl.detoxathome.remotecaremanager.client.MobileAppRepository;

import java.util.ArrayList;
import java.util.List;

@AppComponent
public class DetoxMobileAppRepository extends MobileAppRepository {
	@Override
	protected List<MobileApp> getMobileApps() {
		List<MobileApp> apps = new ArrayList<>();
		apps.add(new MobileApp(
				"senseeact",
				"Detox",
				"nl.detoxathome.remotecaremanager"));
		return apps;
	}
}
