package nl.detoxathome.remotecaremanager.service.detox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DetoxDigitalGuideDialogueRegistry {
	private static final List<String> DEFAULT_DIALOGUE_IDS = List.of(
			"bac",
			"blood_pressure",
			"craving_vas",
			"demo_links",
			"deviation_assessment_questions",
			"morning_start",
			"questionnaire_dass21",
			"questionnaire_sos",
			"saturation",
			"starter_contact",
			"starter_unavailable",
			"temperature"
	);

	private static final Map<String,Integer> DEFAULT_ORDER = createDefaultOrder();

	private DetoxDigitalGuideDialogueRegistry() {
	}

	public static List<String> getDefaultDialogueIds() {
		return new ArrayList<>(DEFAULT_DIALOGUE_IDS);
	}

	public static List<String> normalizeDialogueIds(List<String> dialogueIds) {
		LinkedHashSet<String> normalized = new LinkedHashSet<>();
		if (dialogueIds != null) {
			for (String dialogueId : dialogueIds) {
				if (dialogueId == null)
					continue;
				String trimmed = dialogueId.trim();
				if (!trimmed.isEmpty())
					normalized.add(trimmed);
			}
		}
		List<String> result = new ArrayList<>(normalized);
		result.sort((a, b) -> {
			Integer aOrder = DEFAULT_ORDER.get(a);
			Integer bOrder = DEFAULT_ORDER.get(b);
			if (aOrder != null && bOrder != null)
				return Integer.compare(aOrder, bOrder);
			if (aOrder != null)
				return -1;
			if (bOrder != null)
				return 1;
			return a.compareToIgnoreCase(b);
		});
		return result;
	}

	public static List<String> intersectDialogueIds(
			List<? extends List<String>> dialogueIdLists) {
		if (dialogueIdLists == null || dialogueIdLists.isEmpty())
			return new ArrayList<>();
		Set<String> intersection = null;
		for (List<String> dialogueIds : dialogueIdLists) {
			LinkedHashSet<String> normalized = new LinkedHashSet<>(
					normalizeDialogueIds(dialogueIds));
			if (intersection == null) {
				intersection = normalized;
			} else {
				intersection.retainAll(normalized);
			}
		}
		if (intersection == null)
			return new ArrayList<>();
		return normalizeDialogueIds(new ArrayList<>(intersection));
	}

	private static Map<String,Integer> createDefaultOrder() {
		Map<String,Integer> result = new LinkedHashMap<>();
		for (int i = 0; i < DEFAULT_DIALOGUE_IDS.size(); i++) {
			result.put(DEFAULT_DIALOGUE_IDS.get(i), i);
		}
		return result;
	}
}
