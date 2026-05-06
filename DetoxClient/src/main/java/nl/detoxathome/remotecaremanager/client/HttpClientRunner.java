package nl.detoxathome.remotecaremanager.client;

import nl.rrd.utils.http.HttpClient2;
import nl.rrd.utils.http.HttpClientException;

import java.io.IOException;

public interface HttpClientRunner {
	void run(HttpClient2 client) throws HttpClientException, IOException;
}
