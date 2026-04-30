package nl.detoxathome.remotecaremanager.service;

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
}
