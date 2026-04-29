package nl.detoxathome.remotecaremanager.client.model.detox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import nl.rrd.utils.json.JsonObject;

@JsonIgnoreProperties(ignoreUnknown=true)
public class DetoxLinkedSubjectSummary extends JsonObject {
	private String userId;
	private String displayName;
	private boolean active;
	private int onsId;
	private String onsInstance;
	private boolean pushReady;
	private int pushRegisteredDeviceCount;

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public int getOnsId() {
		return onsId;
	}

	public void setOnsId(int onsId) {
		this.onsId = onsId;
	}

	public String getOnsInstance() {
		return onsInstance;
	}

	public void setOnsInstance(String onsInstance) {
		this.onsInstance = onsInstance;
	}

	public boolean isPushReady() {
		return pushReady;
	}

	public void setPushReady(boolean pushReady) {
		this.pushReady = pushReady;
	}

	public int getPushRegisteredDeviceCount() {
		return pushRegisteredDeviceCount;
	}

	public void setPushRegisteredDeviceCount(int pushRegisteredDeviceCount) {
		this.pushRegisteredDeviceCount = pushRegisteredDeviceCount;
	}
}
