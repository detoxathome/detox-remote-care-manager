package nl.detoxathome.remotecaremanager.service.detox;

import nl.detoxathome.remotecaremanager.client.exception.HttpFieldError;
import nl.detoxathome.remotecaremanager.client.model.Role;
import nl.detoxathome.remotecaremanager.client.model.detox.DetoxTaskConfiguration;
import nl.detoxathome.remotecaremanager.dao.DatabaseFieldException;
import nl.detoxathome.remotecaremanager.dao.DatabaseObjectMapper;
import nl.detoxathome.remotecaremanager.service.controller.CommonCrudController;
import nl.detoxathome.remotecaremanager.service.exception.HttpException;
import nl.detoxathome.remotecaremanager.service.model.User;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DetoxTaskConfigurationValidator {
	private static final List<String> REQUIRED_TASK_FIELDS = List.of(
			"id",
			"name",
			"requestText",
			"description"
	);

	private DetoxTaskConfigurationValidator() {
	}

	public static Map<String,Object> normalizeTaskConfigurationActionData(
			Map<?,?> rawData, User actor, User subject)
			throws HttpException, DatabaseFieldException,
			TaskValidationException {
		DatabaseObjectMapper mapper = new DatabaseObjectMapper();
		DetoxTaskConfiguration record = mapper.mapToObject(rawData,
				DetoxTaskConfiguration.class, true);
		if (subject != null) {
			String recordUser = record.getUser();
			if (recordUser == null) {
				record.setUser(subject.getUserid());
			} else if (!recordUser.equals(subject.getUserid())) {
				throw new TaskValidationException("user",
						"Task snapshot user must match the sync subject");
			}
		}
		CommonCrudController.validateWriteRecordTime(
				subject != null ? subject.toTimeZone() : actor.toTimeZone(),
				record, rawData);
		validateTaskConfigurationRecord(record, actor, subject);
		return mapper.objectToMap(record, true);
	}

	public static void validateTaskConfigurationRecord(
			DetoxTaskConfiguration record, User actor, User subject)
			throws TaskValidationException {
		String normalizedSource = normalizeSource(record.getSource());
		if (normalizedSource == null) {
			throw new TaskValidationException("source",
					"Unsupported source. Allowed values: APP, WEB");
		}
		record.setSource(normalizedSource);
		record.setSourceDeviceId(normalizeNullable(record.getSourceDeviceId()));
		record.setRequestToken(normalizeNullable(record.getRequestToken()));
		record.setEditorUser(normalizeNullable(record.getEditorUser()));
		String xml = record.getXml();
		if (xml == null || xml.trim().isEmpty()) {
			throw new TaskValidationException("xml",
					"Task snapshot XML must be a non-empty string");
		}
		record.setXml(validateTaskXml(xml));
		if (DetoxTaskConfiguration.SOURCE_APP.equals(normalizedSource)) {
			validateAppSnapshot(record, actor, subject);
		} else {
			validateWebSnapshot(record, actor, subject);
		}
	}

	private static void validateAppSnapshot(DetoxTaskConfiguration record,
			User actor, User subject) throws TaskValidationException {
		if (subject == null || actor == null ||
				!actor.getUserid().equals(subject.getUserid())) {
			throw new TaskValidationException("source",
					"APP task snapshots can only be uploaded by the subject user");
		}
		record.setEditorUser(null);
	}

	private static void validateWebSnapshot(DetoxTaskConfiguration record,
			User actor, User subject) throws TaskValidationException {
		if (actor == null || actor.getRole() == Role.PATIENT) {
			throw new TaskValidationException("source",
					"WEB task snapshots can only be uploaded by professionals or admins");
		}
		if (subject == null) {
			throw new TaskValidationException("user",
					"WEB task snapshots require a subject user");
		}
		if (record.getSourceDeviceId() != null) {
			throw new TaskValidationException("sourceDeviceId",
					"WEB task snapshots must not set sourceDeviceId");
		}
		if (record.getRequestToken() != null) {
			throw new TaskValidationException("requestToken",
					"WEB task snapshots must not set requestToken");
		}
		record.setEditorUser(actor.getUserid());
	}

	private static String normalizeSource(String source) {
		if (source == null)
			return null;
		String normalized = source.trim().toUpperCase(Locale.ROOT);
		if (DetoxTaskConfiguration.SOURCE_APP.equals(normalized))
			return DetoxTaskConfiguration.SOURCE_APP;
		if (DetoxTaskConfiguration.SOURCE_WEB.equals(normalized))
			return DetoxTaskConfiguration.SOURCE_WEB;
		return null;
	}

	private static String normalizeNullable(String value) {
		if (value == null)
			return null;
		String normalized = value.trim();
		if (normalized.isEmpty())
			return null;
		return normalized;
	}

	private static String validateTaskXml(String xml)
			throws TaskValidationException {
		Document document = parseXml(xml);
		Element root = document.getDocumentElement();
		if (root == null || !"ArrayList".equals(root.getTagName())) {
			throw new TaskValidationException("xml",
					"Task XML root element must be ArrayList");
		}
		boolean changed = false;
		List<Element> taskElements = childElements(root);
		for (Element child : taskElements) {
			if (!"Task".equals(child.getTagName())) {
				throw new TaskValidationException("xml",
						"ArrayList may only contain Task elements");
			}
			changed |= validateTaskElement(child);
		}
		if (!changed)
			return xml;
		return serializeXml(document);
	}

	private static Document parseXml(String xml) throws TaskValidationException {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature(
					"http://apache.org/xml/features/disallow-doctype-decl",
					true);
			factory.setFeature(
					"http://xml.org/sax/features/external-general-entities",
					false);
			factory.setFeature(
					"http://xml.org/sax/features/external-parameter-entities",
					false);
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			DocumentBuilder builder = factory.newDocumentBuilder();
			return builder.parse(new InputSource(new StringReader(xml)));
		} catch (Exception ex) {
			throw new TaskValidationException("xml",
					"Task snapshot XML is invalid: " + ex.getMessage());
		}
	}

	private static String serializeXml(Document document)
			throws TaskValidationException {
		try {
			TransformerFactory factory = TransformerFactory.newInstance();
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			Transformer transformer = factory.newTransformer();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			transformer.setOutputProperty(OutputKeys.INDENT, "no");
			StringWriter writer = new StringWriter();
			transformer.transform(new DOMSource(document),
					new StreamResult(writer));
			return writer.toString();
		} catch (Exception ex) {
			throw new TaskValidationException("xml",
					"Task snapshot XML could not be normalized: " + ex.getMessage());
		}
	}

	private static boolean validateTaskElement(Element task)
			throws TaskValidationException {
		Map<String,String> values = new LinkedHashMap<>();
		Map<String,Element> elements = new LinkedHashMap<>();
		for (Element field : childElements(task)) {
			String name = field.getTagName();
			if (!values.containsKey(name)) {
				values.put(name, field.getTextContent());
				elements.put(name, field);
			}
		}
		for (String requiredField : REQUIRED_TASK_FIELDS) {
			String value = values.get(requiredField);
			if (value == null || value.trim().isEmpty()) {
				throw new TaskValidationException("xml",
						"Task field \"" + requiredField + "\" is required");
			}
		}
		validateIntField(values, "id", false);
		validateTimeField(values, "fixedTime");
		validateTimeField(values, "stateTimeRangeStart");
		validateTimeField(values, "stateTimeRangeEnd");
		validateBooleanField(values, "answerableOnWatch");
		validateIntField(values, "maximumRequestsPerDay", true);
		validateIntField(values, "minimumIntervalRequestInMinutes", true);
		return validateTriggerField(values, elements);
	}

	private static void validateTimeField(Map<String,String> values,
			String field) throws TaskValidationException {
		String value = normalizeNullable(values.get(field));
		if (value == null)
			return;
		try {
			LocalTime.parse(value);
		} catch (DateTimeParseException ex) {
			throw new TaskValidationException("xml",
					"Task field \"" + field + "\" must contain a valid local time");
		}
	}

	private static void validateBooleanField(Map<String,String> values,
			String field) throws TaskValidationException {
		String value = normalizeNullable(values.get(field));
		if (value == null)
			return;
		if (!"true".equalsIgnoreCase(value) &&
				!"false".equalsIgnoreCase(value)) {
			throw new TaskValidationException("xml",
					"Task field \"" + field + "\" must be true or false");
		}
	}

	private static void validateIntField(Map<String,String> values,
			String field, boolean allowEmpty) throws TaskValidationException {
		String value = normalizeNullable(values.get(field));
		if (value == null) {
			if (allowEmpty)
				return;
			throw new TaskValidationException("xml",
					"Task field \"" + field + "\" must contain an integer");
		}
		try {
			Integer.parseInt(value);
		} catch (NumberFormatException ex) {
			throw new TaskValidationException("xml",
					"Task field \"" + field + "\" must contain an integer");
		}
	}

	private static boolean validateTriggerField(Map<String,String> values,
			Map<String,Element> elements)
			throws TaskValidationException {
		String normalizedTrigger = null;
		for (String fieldName : List.of("TaskTriggerType",
				"combinedModePreferredTrigger")) {
			String value = normalizeNullable(values.get(fieldName));
			if (value == null)
				continue;
			String normalizedValue = normalizeTriggerType(value);
			if (normalizedValue == null) {
				throw new TaskValidationException("xml",
						"Task trigger type must be FixedTime, CurrentState or CombinedMode");
			}
			if (normalizedTrigger == null) {
				normalizedTrigger = normalizedValue;
			} else if (!normalizedTrigger.equals(normalizedValue)) {
				throw new TaskValidationException("xml",
						"Task trigger fields must agree on a single trigger type");
			}
		}
		if (normalizedTrigger == null)
			return false;
		boolean changed = false;
		for (String fieldName : List.of("TaskTriggerType",
				"combinedModePreferredTrigger")) {
			Element field = elements.get(fieldName);
			if (field == null)
				continue;
			String value = normalizeNullable(field.getTextContent());
			if (value == null)
				continue;
			if (!normalizedTrigger.equals(value)) {
				field.setTextContent(normalizedTrigger);
				changed = true;
			}
		}
		return changed;
	}

	private static String normalizeTriggerType(String value) {
		if (value == null)
			return null;
		String normalized = value.trim();
		if (normalized.isEmpty())
			return null;
		String key = normalized.toUpperCase(Locale.ROOT)
				.replace('-', '_');
		switch (key) {
		case "FIXEDTIME":
		case "FIXED_TIME":
			return "FixedTime";
		case "CURRENTSTATE":
		case "CURRENT_STATE":
		case "STATEBASED":
		case "STATE_BASED":
			return "CurrentState";
		case "COMBINEDMODE":
		case "COMBINED_MODE":
			return "CombinedMode";
		default:
			return null;
		}
	}

	private static List<Element> childElements(Element element) {
		List<Element> result = new ArrayList<>();
		for (Node child = element.getFirstChild(); child != null;
				child = child.getNextSibling()) {
			if (child instanceof Element)
				result.add((Element)child);
		}
		return result;
	}

	public static class TaskValidationException extends Exception {
		private final String field;

		public TaskValidationException(String field, String message) {
			super(message);
			this.field = field;
		}

		public HttpFieldError toFieldError() {
			return new HttpFieldError(field, getMessage());
		}
	}
}
