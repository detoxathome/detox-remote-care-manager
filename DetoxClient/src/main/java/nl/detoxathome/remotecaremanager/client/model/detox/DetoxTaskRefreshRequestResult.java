package nl.detoxathome.remotecaremanager.client.model.detox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import nl.rrd.utils.json.JsonObject;

@JsonIgnoreProperties(ignoreUnknown=true)
public class DetoxTaskRefreshRequestResult extends JsonObject {
	private String requestToken;

	public String getRequestToken() {
		return requestToken;
	}

	public void setRequestToken(String requestToken) {
		this.requestToken = requestToken;
	}
}
