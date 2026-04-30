package nl.detoxathome.remotecaremanager.client.model;

import com.fasterxml.jackson.core.type.TypeReference;
import nl.rrd.utils.exception.ParseException;
import nl.rrd.utils.json.JsonMapper;
import nl.detoxathome.remotecaremanager.dao.BaseDatabaseObject;
import nl.detoxathome.remotecaremanager.dao.DatabaseField;
import nl.detoxathome.remotecaremanager.dao.DatabaseType;

import java.util.LinkedHashMap;
import java.util.Map;

public class AppSettings extends BaseDatabaseObject {
	@DatabaseField(value=DatabaseType.STRING, index=true)
	private String user;
	@DatabaseField(value=DatabaseType.TEXT)
	private String settings;
	private Map<String,Object> settingsObject = new LinkedHashMap<>();

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	/**
	 * Returns the JSON code for the settings. This method is used for the DAO.
	 * Users can call {@link #getSettingsObject() getSettingsObject()}.
	 *
	 * @return the JSON code for the settings
	 */
	public String getSettings() {
		return JsonMapper.generate(settingsObject);
	}

	/**
	 * Sets the JSON code for the settings. This method is used for the DAO.
	 * Users can call {@link #setSettingsObject setSettingsObj()}.
	 *
	 * @param settings the JSON code for the settings
	 * @throws ParseException if the JSON code is invalid
	 */
	public void setSettings(String settings) throws ParseException {
		settingsObject = JsonMapper.parse(settings,
				new TypeReference<Map<String,Object>>() {});
	}

	public Map<String, Object> getSettingsObject() {
		return settingsObject;
	}

	public void setSettingsObject(Map<String, Object> settingsObject) {
		this.settingsObject = settingsObject;
	}
}
