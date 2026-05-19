package nl.detoxathome.remotecaremanager.client.model.detox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import nl.rrd.utils.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown=true)
public class DetoxDigitalGuideDialogueOptions extends JsonObject {
	private List<String> defaultDialogueIds = new ArrayList<>();
	private List<String> effectiveDialogueIds = new ArrayList<>();
	private List<String> reportedDialogueIds = new ArrayList<>();
	private List<String> sourceDeviceIds = new ArrayList<>();
	private boolean deviceReported;
	private int deviceSnapshotCount;
	private String language;
	private String appVersion;
	private String reportedAt;

	public List<String> getDefaultDialogueIds() {
		return defaultDialogueIds;
	}

	public void setDefaultDialogueIds(List<String> defaultDialogueIds) {
		this.defaultDialogueIds = defaultDialogueIds;
	}

	public List<String> getEffectiveDialogueIds() {
		return effectiveDialogueIds;
	}

	public void setEffectiveDialogueIds(List<String> effectiveDialogueIds) {
		this.effectiveDialogueIds = effectiveDialogueIds;
	}

	public List<String> getReportedDialogueIds() {
		return reportedDialogueIds;
	}

	public void setReportedDialogueIds(List<String> reportedDialogueIds) {
		this.reportedDialogueIds = reportedDialogueIds;
	}

	public List<String> getSourceDeviceIds() {
		return sourceDeviceIds;
	}

	public void setSourceDeviceIds(List<String> sourceDeviceIds) {
		this.sourceDeviceIds = sourceDeviceIds;
	}

	public boolean isDeviceReported() {
		return deviceReported;
	}

	public void setDeviceReported(boolean deviceReported) {
		this.deviceReported = deviceReported;
	}

	public int getDeviceSnapshotCount() {
		return deviceSnapshotCount;
	}

	public void setDeviceSnapshotCount(int deviceSnapshotCount) {
		this.deviceSnapshotCount = deviceSnapshotCount;
	}

	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public String getAppVersion() {
		return appVersion;
	}

	public void setAppVersion(String appVersion) {
		this.appVersion = appVersion;
	}

	public String getReportedAt() {
		return reportedAt;
	}

	public void setReportedAt(String reportedAt) {
		this.reportedAt = reportedAt;
	}
}
