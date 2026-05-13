package nl.detoxathome.remotecaremanager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DetoxMessageQueueOutgoingIT {
	private static final String PROP_OUTGOING_URL =
			"detoxrcm.outgoing.url";
	private static final String PROP_OUTGOING_MTLS =
			"detoxrcm.outgoing.mtls";

	@Test
	void sendsPayloadToOverrideEndpoint() throws Exception {
		MockWebServer server = new MockWebServer();
		server.enqueue(new MockResponse().setResponseCode(200));
		server.start();
		try {
			String url = server.url("/ons").toString();
			System.setProperty(PROP_OUTGOING_URL, url);
			System.setProperty(PROP_OUTGOING_MTLS, "false");

			DetoxMessageQueueListener listener =
					new DetoxMessageQueueListener("detox", false);
			String payload = "{\"hello\":\"world\"}";

			Method method = DetoxMessageQueueListener.class.getDeclaredMethod(
					"sendProcessedToOns", String.class, String.class, int.class,
					String.class, String.class);
			method.setAccessible(true);
			boolean ok = (boolean) method.invoke(listener, "rid-1",
					"ssa-1", 123, null, payload);

			assertTrue(ok);

			RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
			assertNotNull(request);
			assertEquals("POST", request.getMethod());
			assertEquals(payload, request.getBody().readUtf8());
		} finally {
			System.clearProperty(PROP_OUTGOING_URL);
			System.clearProperty(PROP_OUTGOING_MTLS);
			server.shutdown();
		}
	}

	@Test
	void buildsDetoxDagstartCompositionPayload() throws Exception {
		DetoxMessageQueueListener listener =
				new DetoxMessageQueueListener("detox", false);
		String compactPayload = "{\"hoeGaatHetVanochtend\":\"Goed\","
				+ "\"hebJeVertrouwenInVandaag\":\"Ja\","
				+ "\"wilJeVandaagContactMetJouwHulpverlener\":false}";
		String timestamp = "2026-04-27T09:15:01+00:00";
		Method method = DetoxMessageQueueListener.class.getDeclaredMethod(
				"buildProcessedPayload", String.class, String.class, String.class,
				int.class, String.class, java.time.ZoneId.class);
		method.setAccessible(true);
		String payload = (String)method.invoke(listener, "detox_dagstart",
				compactPayload, "ssa-1", 123, timestamp, ZoneOffset.UTC);
		ObjectMapper mapper = new ObjectMapper();
		Map<?,?> payloadMap = mapper.readValue(payload, Map.class);
		assertEquals(123, payloadMap.get("clientId"));
		assertEquals(
				"nl.Detoxhome::openEHR-EHR-COMPOSITION.detox_dagstart_vragen_report.v0.1.0",
				payloadMap.get("archetypeId"));
		Object pathsAndValuesObj = payloadMap.get("pathsAndValues");
		assertNotNull(pathsAndValuesObj);
		Map<?,?> pathsMap = mapper.readValue(pathsAndValuesObj.toString(), Map.class);
		assertEquals(5, pathsMap.size());
		assertEquals("Goed",
				pathsMap.get(
						"/content[id0.0.100.1,1]/data[id2,1]/events[id3,1]/data[id4,1]/items[id5,1]/value[id6,1]/value"));
		assertEquals("Ja",
				pathsMap.get(
						"/content[id0.0.100.1,1]/data[id2,1]/events[id3,1]/data[id4,1]/items[id7,2]/value[id8,1]/value"));
		assertEquals(false,
				pathsMap.get(
						"/content[id0.0.100.1,1]/data[id2,1]/events[id3,1]/data[id4,1]/items[id9,3]/value[id10,1]/value"));
		assertEquals(timestamp,
				pathsMap.get(
						"/content[id0.0.100.1,1]/data[id2,1]/events[id3,1]/time[1]/value"));
		assertEquals("",
				pathsMap.get(
						"/content[id0.0.101,2]/data[id2,1]/items[id3.1,1]/value[id4,1]/value"));
	}

	@Test
	void buildsVasCompositionPayloadFromWrappedInput() throws Exception {
		DetoxMessageQueueListener listener =
				new DetoxMessageQueueListener("detox", false);
		String compactPayload = "{\"v\":1,\"values\":{\"craving_vas\":{"
				+ "\"inputDefinitionId\":\"craving_vas\","
				+ "\"result\":{\"type\":\"vas\",\"requestId\":\"1\","
				+ "\"inputDefinitionId\":\"craving_vas\",\"value\":7}}},"
				+ "\"timestampUtcMillis\":1778485630394,"
				+ "\"timeZone\":\"Europe/Amsterdam\","
				+ "\"localTime\":\"2026-05-11T09:47:10.394455\"}";
		String payload = buildProcessedPayload(listener, "vas", compactPayload,
				"2026-01-01T00:00:00+00:00");
		ObjectMapper mapper = new ObjectMapper();
		Map<?,?> payloadMap = mapper.readValue(payload, Map.class);
		assertEquals(
				"nl.Detoxhome::openEHR-EHR-COMPOSITION.vas_report.v0.2.0",
				payloadMap.get("archetypeId"));
		Map<?,?> pathsMap = mapper.readValue(payloadMap.get("pathsAndValues")
				.toString(), Map.class);
		assertEquals("7", pathsMap.get(
				"/content[id0.0.100.1,1]/data[id2,1]/events[id3,1]/data[id4,1]/items[id5,1]/value[id6,1]/magnitude"));
		assertEquals("2026-05-11T09:47:10+02:00", pathsMap.get(
				"/content[id0.0.100.1,1]/data[id2,1]/events[id3,1]/time[1]/value"));
		assertEquals("", pathsMap.get(
				"/content[id0.0.101,2]/data[id2,1]/items[id3.1,1]/value[id4,1]/value"));
	}

	@Test
	void buildsSosCompositionPayloadFromWrappedInput() throws Exception {
		DetoxMessageQueueListener listener =
				new DetoxMessageQueueListener("detox", false);
		ObjectMapper mapper = new ObjectMapper();
		Map<String,Object> answers = new LinkedHashMap<>();
		for (int i = 1; i <= 33; i++) {
			answers.put(String.format("q%02d", i), i % 5);
		}
		Map<String,Object> result = new LinkedHashMap<>();
		result.put("type", "questionnaire");
		result.put("requestId", "1");
		result.put("inputDefinitionId", "questionnaire/sos");
		result.put("answers", answers);
		Map<String,Object> valueEntry = new LinkedHashMap<>();
		valueEntry.put("inputDefinitionId", "questionnaire/sos");
		valueEntry.put("result", result);
		Map<String,Object> values = new LinkedHashMap<>();
		values.put("sos", valueEntry);
		Map<String,Object> wrapped = new LinkedHashMap<>();
		wrapped.put("v", 1);
		wrapped.put("values", values);
		wrapped.put("timestampUtcMillis", 1778486565740L);
		wrapped.put("timeZone", "Europe/Amsterdam");
		String payload = buildProcessedPayload(listener, "sos",
				mapper.writeValueAsString(wrapped), "2026-01-01T00:00:00+00:00");
		Map<?,?> payloadMap = mapper.readValue(payload, Map.class);
		assertEquals(
				"nl.Detoxhome::openEHR-EHR-COMPOSITION.subjetieve_onthoudingsschaal_report.v1.0.0",
				payloadMap.get("archetypeId"));
		Map<?,?> pathsMap = mapper.readValue(payloadMap.get("pathsAndValues")
				.toString(), Map.class);
		assertEquals(68, pathsMap.size());
		assertEquals("at6", pathsMap.get(
				"/content[id0.0.100.1,1]/data[id8,1]/events[id9,1]/data[id10,1]/items[id11,1]/value[id12,1]/symbol[1]/defining_code[1]/code_string"));
		assertEquals("1", pathsMap.get(
				"/content[id0.0.100.1,1]/data[id8,1]/events[id9,1]/data[id10,1]/items[id11,1]/value[id12,1]/value"));
		assertEquals("2026-05-11T10:02:45+02:00", pathsMap.get(
				"/content[id0.0.100.1,1]/data[id8,1]/events[id9,1]/time[1]/value"));
	}

	@Test
	void buildsBacCompositionPayloadFromWrappedInput() throws Exception {
		DetoxMessageQueueListener listener =
				new DetoxMessageQueueListener("detox", false);
		String compactPayload = "{\"v\":1,\"values\":{\"bac\":{"
				+ "\"inputDefinitionId\":\"bac\","
				+ "\"result\":{\"type\":\"numeric\",\"requestId\":\"1\","
				+ "\"inputDefinitionId\":\"bac\",\"value\":3.0,"
				+ "\"unit\":\"\\u2030\",\"comment\":\"aa\"}}},"
				+ "\"timestampUtcMillis\":1778520687639,"
				+ "\"timeZone\":\"Europe/Amsterdam\","
				+ "\"localTime\":\"2026-05-11T19:31:27.639976\"}";
		String payload = buildProcessedPayload(listener, "bac", compactPayload,
				"2026-01-01T00:00:00+00:00");
		ObjectMapper mapper = new ObjectMapper();
		Map<?,?> payloadMap = mapper.readValue(payload, Map.class);
		assertEquals(
				"nl.DetoxAtHome::openEHR-EHR-COMPOSITION.bac_report.v0.1.0",
				payloadMap.get("archetypeId"));
		Map<?,?> pathsMap = mapper.readValue(payloadMap.get("pathsAndValues")
				.toString(), Map.class);
		assertEquals("%", pathsMap.get(
				"/content[id0.0.100.1,1]/data[id2,1]/events[id3,1]/data[id4,1]/items[id5,1]/value[id6,1]/units"));
		assertEquals("0,30", pathsMap.get(
				"/content[id0.0.100.1,1]/data[id2,1]/events[id3,1]/data[id4,1]/items[id5,1]/value[id6,1]/magnitude"));
		assertEquals(2, pathsMap.get(
				"/content[id0.0.100.1,1]/data[id2,1]/events[id3,1]/data[id4,1]/items[id5,1]/value[id6,1]/precision"));
		assertEquals("2026-05-11T19:31:27+02:00", pathsMap.get(
				"/content[id0.0.100.1,1]/data[id2,1]/events[id3,1]/time[1]/value"));
		assertEquals("aa", pathsMap.get(
				"/content[id0.0.101,2]/data[id2,1]/items[id3.1,1]/value[id4,1]/value"));
	}

	@Test
	void buildsTemperatureCompositionPayloadFromDirectNumericInput()
			throws Exception {
		DetoxMessageQueueListener listener =
				new DetoxMessageQueueListener("detox", false);
		String compactPayload = "{\"type\":\"numeric\","
				+ "\"inputDefinitionId\":\"temperature\","
				+ "\"value\":40.0,\"unit\":\"°C\","
				+ "\"comment\":\"mijjnopmerking\","
				+ "\"timestampUtcMillis\":1778490768782,"
				+ "\"timeZone\":\"Europe/Amsterdam\"}";
		String payload = buildProcessedPayload(listener, "temperature",
				compactPayload, "2026-01-01T00:00:00+00:00");
		ObjectMapper mapper = new ObjectMapper();
		Map<?,?> payloadMap = mapper.readValue(payload, Map.class);
		assertEquals("openEHR-EHR-COMPOSITION.body_temperature_report.v1.0.0",
				payloadMap.get("archetypeId"));
		Map<?,?> pathsMap = mapper.readValue(payloadMap.get("pathsAndValues")
				.toString(), Map.class);
		assertEquals("Cel", pathsMap.get(
				"/content[id0.0.2,1]/data[id3,1]/events[id4,1]/data[id2,1]/items[id5.1,1]/value[id9003,1]/units"));
		assertEquals("40,0", pathsMap.get(
				"/content[id0.0.2,1]/data[id3,1]/events[id4,1]/data[id2,1]/items[id5.1,1]/value[id9003,1]/magnitude"));
		assertEquals("mijjnopmerking", pathsMap.get(
				"/content[id0.0.2,1]/data[id3,1]/events[id4,1]/data[id2,1]/items[id64,2]/value[id9004,1]/value"));
		assertEquals("2026-05-11T11:12:48+02:00", pathsMap.get(
				"/content[id0.0.2,1]/data[id3,1]/events[id4,1]/time[1]/value"));
	}

	@Test
	void usesIndexedPathsForAfwijkingsbeoordelingHoeSnel() throws Exception {
		DetoxMessageQueueListener listener =
				new DetoxMessageQueueListener("detox", false);
		String compactPayload = "{\"openingsvraag\":\"at3\","
				+ "\"hebJeHulpNodig\":true,"
				+ "\"waarHebJeHulpBijNodig\":\"trek\","
				+ "\"hoeSnelHulp\":\"at27\","
				+ "\"timestampUtcMillis\":1778486600000,"
				+ "\"timeZone\":\"Europe/Amsterdam\"}";
		String payload = buildProcessedPayload(listener, "afwijkingsbeoordeling",
				compactPayload, "2026-01-01T00:00:00+00:00");
		ObjectMapper mapper = new ObjectMapper();
		Map<?,?> payloadMap = mapper.readValue(payload, Map.class);
		Map<?,?> pathsMap = mapper.readValue(payloadMap.get("pathsAndValues")
				.toString(), Map.class);
		assertEquals("at27", pathsMap.get(
				"/content[id0.0.100.1,1]/data[id5,1]/events[id6,1]/data[id7,1]/items[id30,7]/value[id31,1]/defining_code[1]/code_string"));
		assertEquals("ac26", pathsMap.get(
				"/content[id0.0.100.1,1]/data[id5,1]/events[id6,1]/data[id7,1]/items[id30,7]/value[id31,1]/defining_code[1]/terminology_id[1]/value"));
		assertFalse(pathsMap.containsKey(
				"/content[id0.0.100.1,1]/data[id5,1]/events[id6,1]/data[id7,1]/items[id30,6]/value[id31,1]/defining_code/code_string"));
	}

	@Test
	void buildsAfwijkingsbeoordelingFromWrappedDeviationValues() throws Exception {
		DetoxMessageQueueListener listener =
				new DetoxMessageQueueListener("detox", false);
		String compactPayload = "{\"v\":1,\"values\":{"
				+ "\"deviation_how_are_you\":{\"inputDefinitionId\":\"deviation_how_are_you\",\"result\":{\"type\":\"free_text\",\"text\":\"niet_goed\"}},"
				+ "\"deviation_needs_help\":{\"inputDefinitionId\":\"deviation_needs_help\",\"result\":{\"type\":\"free_text\",\"text\":\"ja\"}},"
				+ "\"deviation_help_request\":{\"inputDefinitionId\":\"deviation_help_request\",\"result\":{\"type\":\"free_text\",\"text\":\"Dit is een test\"}},"
				+ "\"deviation_substance_use_risk\":{\"inputDefinitionId\":\"deviation_substance_use_risk\",\"result\":{\"type\":\"free_text\",\"text\":\"nee\"}},"
				+ "\"deviation_is_alone\":{\"inputDefinitionId\":\"deviation_is_alone\",\"result\":{\"type\":\"free_text\",\"text\":\"ja\"}},"
				+ "\"deviation_trusted_environment\":{\"inputDefinitionId\":\"deviation_trusted_environment\",\"result\":{\"type\":\"boolean\",\"value\":false}},"
				+ "\"deviation_help_urgency\":{\"inputDefinitionId\":\"deviation_help_urgency\",\"result\":{\"type\":\"free_text\",\"text\":\"binnen_dagdeel\"}}},"
				+ "\"timestampUtcMillis\":1778519030000,\"timeZone\":\"UTC\"}";
		String payload = buildProcessedPayload(listener, "afwijkingsbeoordeling",
				compactPayload, "2026-01-01T00:00:00+00:00");
		ObjectMapper mapper = new ObjectMapper();
		Map<?,?> payloadMap = mapper.readValue(payload, Map.class);
		assertEquals(
				"nl.Detoxhome::openEHR-EHR-COMPOSITION.afwijkingsbeoordeling_report.v1.1.0",
				payloadMap.get("archetypeId"));
		Map<?,?> pathsMap = mapper.readValue(payloadMap.get("pathsAndValues")
				.toString(), Map.class);
		assertEquals("at3", pathsMap.get(
				"/content[id0.0.100.1,1]/data[id5,1]/events[id6,1]/data[id7,1]/items[id8,1]/value[id9,1]/defining_code[1]/code_string"));
		assertEquals(false, pathsMap.get(
				"/content[id0.0.100.1,1]/data[id5,1]/events[id6,1]/data[id7,1]/items[id32,6]/value[id33,1]/value"));
		assertEquals("at28", pathsMap.get(
				"/content[id0.0.100.1,1]/data[id5,1]/events[id6,1]/data[id7,1]/items[id30,7]/value[id31,1]/defining_code[1]/code_string"));
	}

	private String buildProcessedPayload(DetoxMessageQueueListener listener,
			String type, String compactPayload, String fallbackTimestamp)
			throws Exception {
		Method method = DetoxMessageQueueListener.class.getDeclaredMethod(
				"buildProcessedPayload", String.class, String.class, String.class,
				int.class, String.class, java.time.ZoneId.class);
		method.setAccessible(true);
		return (String)method.invoke(listener, type, compactPayload, "ssa-1", 123,
				fallbackTimestamp, ZoneOffset.UTC);
	}
}
