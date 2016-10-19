package com.cx.client.rest;


import com.cx.client.dto.LoginRequest;
import com.cx.client.exception.CxClientException;
import com.cx.client.rest.dto.CreateOSAScanResponse;
import com.cx.client.rest.dto.OSAScanStatus;
import com.cx.client.rest.dto.OSASummaryResults;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.media.multipart.file.FileDataBodyPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by: Dorg.
 * Date: 16/06/2016.
 */
public class CxRestClient {

    private final String username;
    private final String password;
    private Client client;
    private WebTarget root;

    public static final String OSA_SCAN_PROJECT_PATH = "projects/{projectId}/scans";
    public static final String OSA_SCAN_STATUS_PATH = "scans/{scanId}";
    public static final String OSA_SCAN_SUMMARY_PATH = "projects/{projectId}/summaryresults";
    public static final String OSA_SCAN_HTML_PATH = "projects/{projectId}/opensourceanalysis/htmlresults";
    public static final String OSA_SCAN_PDF_PATH = "projects/{projectId}/opensourceanalysis/pdfresults";
    private static final String AUTHENTICATION_PATH = "auth/login";
    public static final String OSA_ZIPPED_FILE_KEY_NAME = "OSAZippedSourceCode";
    private static final String ROOT_PATH = "CxRestAPI";
    public static final String CSRF_TOKEN_HEADER = "CXCSRFToken";
    private static ArrayList<Object> cookies;
    private static String csrfToken;
    ObjectMapper mapper = new ObjectMapper();

    private static final Logger log = LoggerFactory.getLogger(CxRestClient.class);


    private ClientResponseFilter clientResponseFilter = new ClientResponseFilter() {

        public void filter(ClientRequestContext clientRequestContext, ClientResponseContext clientResponseContext) throws IOException {
//            if (cookies != null) {
//                clientRequestContext.getHeaders().put("Cookie", cookies);
//            }
//
//            if (csrfToken != null) {
//                clientRequestContext.getHeaders().putSingle(CSRF_TOKEN_HEADER, csrfToken);
//            }

            // copy cookies
            if (clientResponseContext.getCookies() != null) {
                if (cookies == null) {
                    cookies = new ArrayList<Object>();
                }
                //cookies.clear();
                cookies.addAll(clientResponseContext.getCookies().values());

                if(clientResponseContext.getCookies().get(CSRF_TOKEN_HEADER) != null) {
                    csrfToken = clientResponseContext.getCookies().get(CSRF_TOKEN_HEADER).getValue();
                }
            }
        }

    };


    private ClientRequestFilter clientRequestFilter = new ClientRequestFilter() {
        public void filter(ClientRequestContext clientRequestContext) throws IOException {
            if (cookies != null) {
                clientRequestContext.getHeaders().put("Cookie", cookies);
            }

            if(csrfToken != null) {
                clientRequestContext.getHeaders().putSingle(CSRF_TOKEN_HEADER, csrfToken);
            }
        }
    };


    public CxRestClient(String hostname, String username, String password) {

        this.username = username;
        this.password = password;
        client = ClientBuilder.newBuilder().register(clientRequestFilter).register(clientResponseFilter).register(MultiPartFeature.class).build();
        root = client.target(hostname).path(ROOT_PATH);
    }

    public void destroy() {
        client.close();
    }


    public void login() throws CxClientException {
        cookies = null;
        csrfToken = null;
        LoginRequest credentials = new LoginRequest(username, password);
        Response response = root.path(AUTHENTICATION_PATH).request().post(Entity.entity(credentials, MediaType.APPLICATION_JSON));
        validateResponse(response, Response.Status.OK, "fail to perform login");
    }

    public CreateOSAScanResponse createOSAScan(long projectId, File zipFile) throws CxClientException {

        MultiPart multiPart = new MultiPart();
        multiPart.setMediaType(MediaType.MULTIPART_FORM_DATA_TYPE);

        FileDataBodyPart fileDataBodyPart = new FileDataBodyPart(OSA_ZIPPED_FILE_KEY_NAME , zipFile, MediaType.APPLICATION_OCTET_STREAM_TYPE);
        multiPart.bodyPart(fileDataBodyPart);

        Response response = root.path(OSA_SCAN_PROJECT_PATH).resolveTemplate("projectId", projectId).request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.entity(multiPart, multiPart.getMediaType()));

        validateResponse(response, Response.Status.ACCEPTED, "fail create OSA scan");

        return convertToObject(response, CreateOSAScanResponse.class);
    }

    public OSAScanStatus getOSAScanStatus(String scanId) throws CxClientException {
        Response response = root.path(OSA_SCAN_STATUS_PATH).resolveTemplate("scanId", scanId).request().get();
        validateResponse(response, Response.Status.OK, "fail get OSA scan status");
        return convertToObject(response, OSAScanStatus.class);
    }

    public OSASummaryResults getOSAScanSummaryResults(long projectId) throws CxClientException {
        Response response = root.path(OSA_SCAN_SUMMARY_PATH).resolveTemplate("projectId", projectId).request().get();
        validateResponse(response, Response.Status.OK, "fail get OSA scan summary results");
        return convertToObject(response, OSASummaryResults.class);
    }

    public String getOSAScanHtmlResults(long projectId) throws CxClientException {
        Response response = root.path(OSA_SCAN_HTML_PATH).resolveTemplate("projectId", projectId).request().get();
        validateResponse(response, Response.Status.OK, "fail get OSA scan html results");
        return response.readEntity(String.class);
    }

    public byte[] getOSAScanPDFResults(long projectId) throws CxClientException {
        Response response = root.path(OSA_SCAN_PDF_PATH).resolveTemplate("projectId", projectId).request().get();
        validateResponse(response, Response.Status.OK, "fail get OSA scan pdf results");
        return response.readEntity(byte[].class);

    }

    private void validateResponse(Response response, Response.Status expectedStatus, String message) throws CxClientException {
        if(response.getStatus() != expectedStatus.getStatusCode()) {
            throw new CxClientException(message + ": " + response.getStatusInfo().toString());
        }
    }

    private <T> T convertToObject(Response response, Class<T> valueType) throws CxClientException {
        String json = response.readEntity(String.class);
        T ret = null;
        try {
            ret = mapper.readValue(json, valueType);
        } catch (IOException e) {
            log.debug("fail to parse json response: ["+json+"]", e);
            throw new CxClientException("fail to parse json response: " +e.getMessage());
        }
        return ret;
    }
}