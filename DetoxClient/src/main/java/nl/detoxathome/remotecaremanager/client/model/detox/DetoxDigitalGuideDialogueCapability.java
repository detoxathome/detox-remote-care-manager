package nl.detoxathome.remotecaremanager.client.model.detox;

import com.fasterxml.jackson.core.type.TypeReference;
import nl.detoxathome.remotecaremanager.client.model.sample.UTCSample;
import nl.detoxathome.remotecaremanager.dao.DatabaseField;
import nl.detoxathome.remotecaremanager.dao.DatabaseType;
import nl.rrd.utils.exception.ParseException;
import nl.rrd.utils.json.JsonMapper;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Append-only capability snapshot listing which Digital Guide dialogues an
 * installed patient app can run.
 */
public class DetoxDigitalGuideDialogueCapability extends UTCSample {
	@DatabaseField(value=DatabaseType.TEXT, json=true)
	private String dialogueIds;

	@DatabaseField(value=DatabaseType.STRING, index=true)
	private String language;

	@DatabaseField(value=DatabaseType.STRING, index=true)
	private String appVersion;

	@DatabaseField(value=DatabaseType.STRING, index=true)
	private String sourceDeviceId;

	private List<String> dialogueIdsList = new ArrayList<>();

	public DetoxDigitalGuideDialogueCapability() {
	}

	public DetoxDigitalGuideDialogueCapability(String user, ZonedDateTime tzTime) {
		super(user, tzTime);
	}

	public String getDialogueIds() {
		return JsonMapper.generate(dialogueIdsList);
	}

	public void setDialogueIds(String dialogueIds) throws ParseException {
		this.dialogueIdsList = JsonMapper.parse(dialogueIds,
				new TypeReference<>() {});
	}

	public List<String> getDialogueIdsList() {
		return dialogueIdsList;
	}

	public void setDialogueIdsList(List<String> dialogueIdsList) {
		this.dialogueIdsList = dialogueIdsList;
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

	public String getSourceDeviceId() {
		return sourceDeviceId;
	}

	public void setSourceDeviceId(String sourceDeviceId) {
		this.sourceDeviceId = sourceDeviceId;
	}
}
