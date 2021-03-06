/**
* This MicroStrategy class implements MicroStrategyLibrary API methods required
* to push data to a MicroStrategy Cube
*
* @author  Alex Fernandez
* @version 0.1
* @since   2018-08-01 
*
*/

package com.microstrategy.sample.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.*;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.*;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.apache.kafka.connect.sink.SinkRecord;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MicroStrategy {

	public static final String X_MSTR_AUTH_TOKEN = "X-MSTR-AuthToken";
	public static final String X_MSTR_PROJECT_ID = "X-MSTR-ProjectID";
	private String baseUrl;
	private String username;
	private String password;
	private String loginMode;
	private CookieStore cookieStore;
	private CloseableHttpClient httpClient;
	private HttpClientContext httpContext;
	private Collection<Header> headers;
	private ObjectMapper mapper;
	private String projectId;
	private Header authToken;
	private String datasetName;
	private String folderId;
	private String tableName;
	private String datasetId;

	// method for testing purposes only
	public static void main(String[] args) throws Exception {
		
		ArrayList<Header> headers1 = new ArrayList<Header>();
		headers1.add(new BasicHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON));
		headers1.add(new BasicHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON));
		HttpPost request = new HttpPost("/auth/login");
		request.setHeaders(headers1.toArray(new Header[headers1.size()]));
		
		for (Header header : request.getAllHeaders()) {
			System.out.println(header.getName()+":"+header.getValue());
		}
		
		System.exit(0);
		
		String libraryUrl = "https://xxx.microstrategy.com/MicroStrategyLibrary";
		String username = "usename";
		String password = "password";
		String loginMode = "1";

		MicroStrategy mstr = new MicroStrategy(libraryUrl, username, password, loginMode);
		mstr.connect();

		mstr.setProject("MicroStrategy Tutorial");
		mstr.setTarget("Streaming", "topic", "D3C7D461F69C4610AA6BAA5EF51F4125");
		Collection<SinkRecord> records = null;
		mstr.put(records);

	}

	private void put(Collection<SinkRecord> records) {
		for (SinkRecord record : records) {
			record.value();
		}
	}

	public MicroStrategy(String libraryUrl, String username, String password, String loginMode) {
		this.baseUrl = libraryUrl + "/api";
		this.username = username;
		this.password = password;
		this.loginMode = loginMode;

		CookieStore cookieStore = new BasicCookieStore();
		httpContext = HttpClientContext.create();
		httpContext.setCookieStore(this.cookieStore);
		httpClient = HttpClients.custom().setDefaultCookieStore(cookieStore).build();

		this.headers = new ArrayList<Header>();
		this.headers.add(new BasicHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON));
		this.headers.add(new BasicHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON));
		
		mapper = new ObjectMapper();
	}

	public void connect() throws MicroStrategyException {
		Map<String, String> payload = new Hashtable<String, String>();
		payload.put("username", username);
		payload.put("password", password);
		payload.put("loginMode", loginMode);

		try {
			HttpPost request = new HttpPost(baseUrl + "/auth/login");
			request.setEntity(new StringEntity(mapper.writeValueAsString(payload)));
			request.setHeaders(this.headers.toArray(new Header[this.headers.size()]));
			CloseableHttpResponse response = httpClient.execute(request, httpContext);
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NO_CONTENT) {
				this.authToken = response.getFirstHeader(X_MSTR_AUTH_TOKEN);
				this.headers.add(this.authToken);
			} else {
				throw new MicroStrategyException(response.getStatusLine().toString());
			}
		} catch (Exception e) {
			throw new MicroStrategyException(e);
		}			
	}
	
	public void disconnect () throws MicroStrategyException {
		Map<String, String> payload = new Hashtable<String, String>();
		payload.put(X_MSTR_AUTH_TOKEN, this.authToken.getValue());
		try {
			HttpPost request = new HttpPost(baseUrl + "/auth/logout");
			request.setEntity(new StringEntity(mapper.writeValueAsString(payload)));
			request.setHeaders(this.headers.toArray(new Header[this.headers.size()]));
			CloseableHttpResponse response = httpClient.execute(request, httpContext);
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NO_CONTENT) {
			} else {
				throw new MicroStrategyException(response.getStatusLine().toString());
			}
		} catch (Exception e) {
			throw new MicroStrategyException(e);
		}					
	}
	
	public CookieStore getCookieStore() {
		return this.cookieStore;
	}
	
	public Collection<Header> getHeaders () {
		return this.headers;
	}

	public void setProject(String projectName) throws MicroStrategyException {
		HttpGet request = new HttpGet(baseUrl + "/projects");
		request.setHeaders(this.headers.toArray(new Header[this.headers.size()]));
		try {
			CloseableHttpResponse response = httpClient.execute(request, httpContext);
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				List<Map<String, Object>> json = mapper.readValue(response.getEntity().getContent(),
						new TypeReference<ArrayList<Object>>() {
						});
				for (Map<String, Object> project : json) {
					if (projectName.equals(project.get("name"))) {
						projectId = (String) project.get("id");
						this.headers.add(new BasicHeader(X_MSTR_PROJECT_ID, projectId));
						return;
					}
				}
				throw new MicroStrategyException("Project " + projectName + " was not found.");
			} else {
				throw new MicroStrategyException(response.getStatusLine().toString());
			}
		} catch (IOException e) {
			throw new MicroStrategyException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public void setTarget(String datasetName, String tableName, String folderId) throws MicroStrategyException {
		this.datasetName = datasetName;
		this.tableName = tableName;
		this.folderId = folderId;

		try {
			URIBuilder uriBuilder = new URIBuilder(baseUrl + "/searches/results");
			uriBuilder.addParameter("limit", "-1");
			uriBuilder.addParameter("name", datasetName);
			uriBuilder.addParameter("type", "3"); // OBJECT_TYPE_REPORT_DEFINITION
			uriBuilder.addParameter("pattern", "2"); // SEARCH_TYPE_EXACTLY

			HttpGet request = new HttpGet(uriBuilder.build());
			request.setHeaders(this.headers.toArray(new Header[this.headers.size()]));
			CloseableHttpResponse response = httpClient.execute(request, httpContext);

			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				Map<String, Object> json = mapper.readValue(response.getEntity().getContent(),
						new TypeReference<Map<String, Object>>() {
						});
				if (((Integer) json.get("totalItems")) > 0) {
					datasetId = (String) ((List<Map<String, Object>>) json.get("result")).get(0).get("id");
				}
			} else {
				throw new MicroStrategyException(response.getStatusLine().toString());
			}
		} catch (Exception e) {
			throw new MicroStrategyException(e);
		}
	}

	public void push(Map<String, Object> tableDefinition, String updatePolicy) throws MicroStrategyException {
		try {
			Map<String, Object> payload = null;
			HttpEntityEnclosingRequestBase request = null;
			if (datasetId != null) { // Upsert
				request = new HttpPatch(baseUrl + "/datasets/" + datasetId + "/tables/" + tableName);
				request.setHeaders(this.headers.toArray(new Header[this.headers.size()]));
				System.out.println("Update policy: " + updatePolicy);
				request.setHeader("updatePolicy", updatePolicy);
				payload = tableDefinition;
			} else { // Create dataset
				request = new HttpPost(baseUrl + "/datasets");
				request.setHeaders(this.headers.toArray(new Header[this.headers.size()]));
				payload = new HashMap<String, Object>();
				payload.put("name", datasetName);
				payload.put("tables", Collections.singletonList(tableDefinition));
				payload.put("folderId", this.folderId); // create dataset in Shared Reports
			}
			// System.out.println(request.getURI());
			request.setEntity(new StringEntity(mapper.writeValueAsString(payload)));
			// System.out.println(mapper.writeValueAsString(payload));
			CloseableHttpResponse response = httpClient.execute(request, httpContext);
			if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
				throw new MicroStrategyException(response.getStatusLine().toString());
			}
		} catch (IOException e) {
			throw new MicroStrategyException(e);
		}
	}

	private void printResponse(CloseableHttpResponse response) throws ParseException, IOException {
		HttpEntity entity = response.getEntity();
		System.out.println(EntityUtils.toString(entity, StandardCharsets.UTF_8));
	}

}
