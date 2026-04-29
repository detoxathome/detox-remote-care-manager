package nl.detoxathome.remotecaremanager.client.model.detox;

import nl.detoxathome.remotecaremanager.client.model.sample.UTCSample;
import nl.detoxathome.remotecaremanager.dao.DatabaseField;
import nl.detoxathome.remotecaremanager.dao.DatabaseType;

import java.time.ZonedDateTime;

/**
 * Append-only task configuration snapshot for a Detox patient.
 */
public class DetoxTaskConfiguration extends UTCSample {
	public static final String SOURCE_APP = "APP";
	public static final String SOURCE_WEB = "WEB";

	@DatabaseField(value=DatabaseType.TEXT)
	private String xml;

	@DatabaseField(value=DatabaseType.STRING, index=true)
	private String source;

	@DatabaseField(value=DatabaseType.STRING, index=true)
	private String sourceDeviceId;

	@DatabaseField(value=DatabaseType.STRING, index=true)
	private String requestToken;

	@DatabaseField(value=DatabaseType.STRING, index=true)
	private String editorUser;

	public DetoxTaskConfiguration() {
	}

	public DetoxTaskConfiguration(String user, ZonedDateTime tzTime,
			String xml, String source) {
		super(user, tzTime);
		this.xml = xml;
		this.source = source;
	}

	public String getXml() {
		return xml;
	}

	public void setXml(String xml) {
		this.xml = xml;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public String getSourceDeviceId() {
		return sourceDeviceId;
	}

	public void setSourceDeviceId(String sourceDeviceId) {
		this.sourceDeviceId = sourceDeviceId;
	}

	public String getRequestToken() {
		return requestToken;
	}

	public void setRequestToken(String requestToken) {
		this.requestToken = requestToken;
	}

	public String getEditorUser() {
		return editorUser;
	}

	public void setEditorUser(String editorUser) {
		this.editorUser = editorUser;
	}
}
