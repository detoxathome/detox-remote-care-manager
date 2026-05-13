package nl.detoxathome.remotecaremanager.client.model.detox;

import nl.detoxathome.remotecaremanager.client.model.sample.UTCSample;
import nl.detoxathome.remotecaremanager.dao.DatabaseField;
import nl.detoxathome.remotecaremanager.dao.DatabaseType;

import java.time.ZonedDateTime;

/**
 * Append-only request asking the patient app to upload its current task set.
 */
public class DetoxTaskRefreshRequest extends UTCSample {
	@DatabaseField(value=DatabaseType.STRING, index=true)
	private String requestToken;

	@DatabaseField(value=DatabaseType.STRING, index=true)
	private String requestedByUser;

	public DetoxTaskRefreshRequest() {
	}

	public DetoxTaskRefreshRequest(String user, ZonedDateTime tzTime,
			String requestToken, String requestedByUser) {
		super(user, tzTime);
		this.requestToken = requestToken;
		this.requestedByUser = requestedByUser;
	}

	public String getRequestToken() {
		return requestToken;
	}

	public void setRequestToken(String requestToken) {
		this.requestToken = requestToken;
	}

	public String getRequestedByUser() {
		return requestedByUser;
	}

	public void setRequestedByUser(String requestedByUser) {
		this.requestedByUser = requestedByUser;
	}
}
