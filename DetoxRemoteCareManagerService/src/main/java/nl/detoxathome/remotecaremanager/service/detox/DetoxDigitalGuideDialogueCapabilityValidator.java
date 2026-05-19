package nl.detoxathome.remotecaremanager.service.detox;

import nl.detoxathome.remotecaremanager.client.model.detox.DetoxDigitalGuideDialogueCapability;
import nl.detoxathome.remotecaremanager.service.model.User;

import java.util.List;

public class DetoxDigitalGuideDialogueCapabilityValidator {
	private DetoxDigitalGuideDialogueCapabilityValidator() {
	}

	public static void validateDialogueCapabilityRecord(
			DetoxDigitalGuideDialogueCapability record, User actor, User subject)
			throws DialogueCapabilityValidationException {
		if (subject == null || actor == null ||
				!actor.getUserid().equals(subject.getUserid())) {
			throw new DialogueCapabilityValidationException("user",
					"Dialogue capability snapshots can only be uploaded by the subject user");
		}
		List<String> normalizedDialogueIds =
				DetoxDigitalGuideDialogueRegistry.normalizeDialogueIds(
						record.getDialogueIdsList());
		record.setDialogueIdsList(normalizedDialogueIds);
		if (normalizedDialogueIds.isEmpty()) {
			throw new DialogueCapabilityValidationException("dialogueIds",
					"Dialogue capability snapshots must contain at least one dialogue id");
		}
		record.setLanguage(normalizeRequired(record.getLanguage(), "language"));
		record.setSourceDeviceId(normalizeRequired(record.getSourceDeviceId(),
				"sourceDeviceId"));
		record.setAppVersion(normalizeNullable(record.getAppVersion()));
	}

	private static String normalizeRequired(String value, String field)
			throws DialogueCapabilityValidationException {
		String normalized = normalizeNullable(value);
		if (normalized == null) {
			throw new DialogueCapabilityValidationException(field,
					"Dialogue capability field \"" + field +
							"\" must be a non-empty string");
		}
		return normalized;
	}

	private static String normalizeNullable(String value) {
		if (value == null)
			return null;
		String normalized = value.trim();
		return normalized.isEmpty() ? null : normalized;
	}

	public static class DialogueCapabilityValidationException
			extends Exception {
		private final String field;

		public DialogueCapabilityValidationException(String field,
				String message) {
			super(message);
			this.field = field;
		}

		public String getField() {
			return field;
		}
	}
}
