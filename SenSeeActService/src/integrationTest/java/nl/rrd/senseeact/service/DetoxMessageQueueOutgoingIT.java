package nl.rrd.senseeact.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DetoxMessageQueueOutgoingIT {
	private static final String PROP_OUTGOING_URL =
			"senseeact.detox.outgoing.url";
	private static final String PROP_OUTGOING_MTLS =
			"senseeact.detox.outgoing.mtls";

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
	void buildsBloodPressureCompositionPayloadFromNewInputFormat()
			throws Exception {
		DetoxMessageQueueListener listener =
				new DetoxMessageQueueListener("detox", false);
		String compactPayload = "{\"type\":\"blood_pressure\","
				+ "\"requestId\":\"task_2_blood_pressure\","
				+ "\"inputDefinitionId\":\"blood_pressure\","
				+ "\"systolic\":120,"
				+ "\"diastolic\":90,"
				+ "\"pulseBpm\":72,"
				+ "\"timestampUtcMillis\":1777463845145,"
				+ "\"timeZone\":\"Europe/Amsterdam\","
				+ "\"localTime\":\"2026-04-29T13:57:25.145879\"}";
		String fallbackTimestamp = "2026-04-29T11:57:25+00:00";
		Method method = DetoxMessageQueueListener.class.getDeclaredMethod(
				"buildProcessedPayload", String.class, String.class, String.class,
				int.class, String.class, java.time.ZoneId.class);
		method.setAccessible(true);
		String payload = (String)method.invoke(listener, "bloodpressure",
				compactPayload, "ssa-1", 123, fallbackTimestamp, ZoneOffset.UTC);
		ObjectMapper mapper = new ObjectMapper();
		Map<?,?> payloadMap = mapper.readValue(payload, Map.class);
		assertEquals(123, payloadMap.get("clientId"));
		assertEquals("openEHR-EHR-COMPOSITION.blood_pressure_report.v1.0.0",
				payloadMap.get("archetypeId"));
		Object pathsAndValuesObj = payloadMap.get("pathsAndValues");
		assertNotNull(pathsAndValuesObj);
		Map<?,?> pathsMap = mapper.readValue(pathsAndValuesObj.toString(), Map.class);
		assertEquals(90,
				pathsMap.get("/content[id0.0.2,1]/data[id2,1]/events[id7,1]/data[id4,1]/items[id5,1]/value[id9010,1]/magnitude"));
		assertEquals(120,
				pathsMap.get("/content[id0.0.2,1]/data[id2,1]/events[id7,1]/data[id4,1]/items[id6,2]/value[id9011,1]/magnitude"));
		// MAP is derived from systolic/diastolic when it is not sent.
		assertEquals(100,
				pathsMap.get("/content[id0.0.2,1]/data[id2,1]/events[id7,1]/data[id4,1]/items[id1007,3]/value[id9012,1]/magnitude"));
	}

	@Test
	void buildsBloodPressureAndHeartRatePayloadsFromPulseBpm() throws Exception {
		DetoxMessageQueueListener listener =
				new DetoxMessageQueueListener("detox", false);
		String compactPayload = "{\"type\":\"blood_pressure\","
				+ "\"requestId\":\"task_4_blood_pressure\","
				+ "\"inputDefinitionId\":\"blood_pressure\","
				+ "\"systolic\":88,"
				+ "\"diastolic\":99,"
				+ "\"pulseBpm\":88,"
				+ "\"timestampUtcMillis\":1778154900518,"
				+ "\"timeZone\":\"Europe/Amsterdam\","
				+ "\"localTime\":\"2026-05-07T13:55:00.518853\"}";
		String fallbackTimestamp = "2026-05-07T11:55:00+00:00";
		Method method = DetoxMessageQueueListener.class.getDeclaredMethod(
				"buildProcessedPayloads", String.class, String.class, String.class,
				int.class, String.class, java.time.ZoneId.class);
		method.setAccessible(true);
		@SuppressWarnings("unchecked")
		java.util.List<Object> payloads = (java.util.List<Object>)method.invoke(
				listener, "bloodpressure", compactPayload, "ssa-1", 123,
				fallbackTimestamp, ZoneOffset.UTC);
		assertEquals(2, payloads.size());

		Object first = payloads.get(0);
		java.lang.reflect.Field firstTypeField =
				first.getClass().getDeclaredField("type");
		firstTypeField.setAccessible(true);
		java.lang.reflect.Field firstPayloadField =
				first.getClass().getDeclaredField("payload");
		firstPayloadField.setAccessible(true);
		assertEquals("bloodpressure", firstTypeField.get(first));
		String bpPayload = (String)firstPayloadField.get(first);

		Object second = payloads.get(1);
		java.lang.reflect.Field secondTypeField =
				second.getClass().getDeclaredField("type");
		secondTypeField.setAccessible(true);
		java.lang.reflect.Field secondPayloadField =
				second.getClass().getDeclaredField("payload");
		secondPayloadField.setAccessible(true);
		assertEquals("heartrate", secondTypeField.get(second));
		String hrPayload = (String)secondPayloadField.get(second);

		ObjectMapper mapper = new ObjectMapper();
		Map<?,?> bpPayloadMap = mapper.readValue(bpPayload, Map.class);
		assertEquals("openEHR-EHR-COMPOSITION.blood_pressure_report.v1.0.0",
				bpPayloadMap.get("archetypeId"));

		Map<?,?> hrPayloadMap = mapper.readValue(hrPayload, Map.class);
		assertEquals("openEHR-EHR-COMPOSITION.pulse_report.v1.0.0",
				hrPayloadMap.get("archetypeId"));
		Object hrPathsAndValuesObj = hrPayloadMap.get("pathsAndValues");
		assertNotNull(hrPathsAndValuesObj);
		Map<?,?> hrPathsMap = mapper.readValue(hrPathsAndValuesObj.toString(),
				Map.class);
		assertEquals(88,
				hrPathsMap.get("/content[id0.0.2,1]/data[id3,1]/events[id4,1]/data[id2,1]/items[id5,1]/value[id9004,1]/magnitude"));
	}

	@Test
	void buildsAfwijkingsbeoordelingCompositionPayload() throws Exception {
		DetoxMessageQueueListener listener =
				new DetoxMessageQueueListener("detox", false);
		String compactPayload = "{\"openingsvraag\":\"at4\","
				+ "\"hebJeHulpNodig\":true,"
				+ "\"waarHebJeHulpBijNodig\":\"Ik heb nu begeleiding nodig\","
				+ "\"gebruik\":\"Ja, ik ben van plan te gebruiken\","
				+ "\"alleenOfNiet\":\"at21\","
				+ "\"hoeSnelHulp\":\"Bij de volgende afspraak met mijn zorgverlener\"}";
		String fallbackTimestamp = "2026-05-07T11:26:00+00:00";
		Method method = DetoxMessageQueueListener.class.getDeclaredMethod(
				"buildProcessedPayload", String.class, String.class, String.class,
				int.class, String.class, java.time.ZoneId.class);
		method.setAccessible(true);
		String payload = (String)method.invoke(listener, "afwijkingsbeoordeling",
				compactPayload, "ssa-1", 123, fallbackTimestamp, ZoneOffset.UTC);
		ObjectMapper mapper = new ObjectMapper();
		Map<?,?> payloadMap = mapper.readValue(payload, Map.class);
		assertEquals(123, payloadMap.get("clientId"));
		assertEquals(
				"nl.Detoxhome::openEHR-EHR-COMPOSITION.afwijkingsbeoordeling_report.v1.0.0",
				payloadMap.get("archetypeId"));
		Object pathsAndValuesObj = payloadMap.get("pathsAndValues");
		assertNotNull(pathsAndValuesObj);
		Map<?,?> pathsMap = mapper.readValue(pathsAndValuesObj.toString(), Map.class);
		assertEquals("at4",
				pathsMap.get(
						"/content[id0.0.100.1,1]/data[id5,1]/events[id6,1]/data[id7,1]/items[id8,1]/value[id9,1]/defining_code[1]/code_string"));
		assertEquals("ac2",
				pathsMap.get(
						"/content[id0.0.100.1,1]/data[id5,1]/events[id6,1]/data[id7,1]/items[id8,1]/value[id9,1]/defining_code[1]/terminology_id[1]/value"));
		assertEquals("Het gaat goed met mij",
				pathsMap.get(
						"/content[id0.0.100.1,1]/data[id5,1]/events[id6,1]/data[id7,1]/items[id8,1]/value[id9,1]/value"));
		assertEquals(true,
				pathsMap.get(
						"/content[id0.0.100.1,1]/data[id5,1]/events[id6,1]/data[id7,1]/items[id10,2]/value[id11,1]/value"));
		assertEquals("Ik heb nu begeleiding nodig",
				pathsMap.get(
						"/content[id0.0.100.1,1]/data[id5,1]/events[id6,1]/data[id7,1]/items[id12,3]/value[id13,1]/value"));
		assertEquals("at16",
				pathsMap.get(
						"/content[id0.0.100.1,1]/data[id5,1]/events[id6,1]/data[id7,1]/items[id18,4]/value[id19,1]/defining_code[1]/code_string"));
		assertEquals("ac14",
				pathsMap.get(
						"/content[id0.0.100.1,1]/data[id5,1]/events[id6,1]/data[id7,1]/items[id18,4]/value[id19,1]/defining_code[1]/terminology_id[1]/value"));
		assertEquals("Ja, ik ben van plan te gebruiken",
				pathsMap.get(
						"/content[id0.0.100.1,1]/data[id5,1]/events[id6,1]/data[id7,1]/items[id18,4]/value[id19,1]/value"));
		assertEquals("at21",
				pathsMap.get(
						"/content[id0.0.100.1,1]/data[id5,1]/events[id6,1]/data[id7,1]/items[id24,5]/value[id25,1]/defining_code[1]/code_string"));
		assertEquals("ac20",
				pathsMap.get(
						"/content[id0.0.100.1,1]/data[id5,1]/events[id6,1]/data[id7,1]/items[id24,5]/value[id25,1]/defining_code[1]/terminology_id[1]/value"));
		assertEquals("Ja, ik ben alleen",
				pathsMap.get(
						"/content[id0.0.100.1,1]/data[id5,1]/events[id6,1]/data[id7,1]/items[id24,5]/value[id25,1]/value"));
		assertEquals("at29",
				pathsMap.get(
						"/content[id0.0.100.1,1]/data[id5,1]/events[id6,1]/data[id7,1]/items[id30,6]/value[id31,1]/defining_code/code_string"));
		assertEquals("ac26",
				pathsMap.get(
						"/content[id0.0.100.1,1]/data[id5,1]/events[id6,1]/data[id7,1]/items[id30,6]/value[id31,1]/defining_code/terminology_id/value"));
		assertEquals("Bij de volgende afspraak met mijn zorgverlener",
				pathsMap.get(
						"/content[id0.0.100.1,1]/data[id5,1]/events[id6,1]/data[id7,1]/items[id30,6]/value[id31,1]/value"));
		assertEquals(fallbackTimestamp,
				pathsMap.get(
						"/content[id0.0.100.1,1]/data[id5,1]/events[id6,1]/time[1]/value"));
	}

	@Test
	void buildsAfwijkingsbeoordelingWithoutConditionalHelpFields()
			throws Exception {
		DetoxMessageQueueListener listener =
				new DetoxMessageQueueListener("detox", false);
		String compactPayload = "{\"openingsvraag\":\"at4\","
				+ "\"hebJeHulpNodig\":false,"
				+ "\"timestampUtcMillis\":1778153160000,"
				+ "\"timeZone\":\"Europe/Amsterdam\"}";
		String fallbackTimestamp = "2026-05-07T11:26:00+00:00";
		Method method = DetoxMessageQueueListener.class.getDeclaredMethod(
				"buildProcessedPayload", String.class, String.class, String.class,
				int.class, String.class, java.time.ZoneId.class);
		method.setAccessible(true);
		String payload = (String)method.invoke(listener, "afwijkingsbeoordeling",
				compactPayload, "ssa-1", 123, fallbackTimestamp, ZoneOffset.UTC);
		ObjectMapper mapper = new ObjectMapper();
		Map<?,?> payloadMap = mapper.readValue(payload, Map.class);
		Object pathsAndValuesObj = payloadMap.get("pathsAndValues");
		assertNotNull(pathsAndValuesObj);
		Map<?,?> pathsMap = mapper.readValue(pathsAndValuesObj.toString(), Map.class);
		assertEquals(false,
				pathsMap.get(
						"/content[id0.0.100.1,1]/data[id5,1]/events[id6,1]/data[id7,1]/items[id10,2]/value[id11,1]/value"));
		assertEquals(null,
				pathsMap.get(
						"/content[id0.0.100.1,1]/data[id5,1]/events[id6,1]/data[id7,1]/items[id12,3]/value[id13,1]/value"));
		assertEquals(null,
				pathsMap.get(
						"/content[id0.0.100.1,1]/data[id5,1]/events[id6,1]/data[id7,1]/items[id30,6]/value[id31,1]/value"));
	}
}
