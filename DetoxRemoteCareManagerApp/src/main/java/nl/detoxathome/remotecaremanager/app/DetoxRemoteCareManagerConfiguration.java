package nl.detoxathome.remotecaremanager.app;

import nl.rrd.utils.AppComponent;
import nl.detoxathome.remotecaremanager.service.Configuration;

import java.net.URL;

/**
 * Configuration of the Detox Remote Care Manager service. This is
 * initialised from resource service.properties. Known property keys are
 * defined as constants in this class.
 * 
 * @author Dennis Hofs (RRD)
 */
@AppComponent
public class DetoxRemoteCareManagerConfiguration extends Configuration {
	private static final Object LOCK = new Object();
	private static DetoxRemoteCareManagerConfiguration instance = null;

	/**
	 * Returns the configuration. At startup of the service it should be
	 * initialised with {@link #loadProperties(URL) loadProperties()}.
	 * 
	 * @return the configuration
	 */
	public static DetoxRemoteCareManagerConfiguration getInstance() {
		synchronized (LOCK) {
			if (instance == null)
				instance = new DetoxRemoteCareManagerConfiguration();
			return instance;
		}
	}

	/**
	 * This private constructor is used in {@link #getInstance()
	 * getInstance()}.
	 */
	private DetoxRemoteCareManagerConfiguration() {
	}
}
