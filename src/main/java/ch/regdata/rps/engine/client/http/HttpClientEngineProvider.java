package ch.regdata.rps.engine.client.http;

import ch.regdata.rps.engine.client.engine.IRPSEngineProvider;
import ch.regdata.rps.engine.client.model.api.connect.Token;
import ch.regdata.rps.engine.client.model.api.request.RequestBody;
import ch.regdata.rps.engine.client.model.api.response.ResponseBody;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * This class provides an implementation of the IRPSEngineProvider for an http client
 */
public class HttpClientEngineProvider implements IRPSEngineProvider {

    private static final String ENGINE_TOKEN = "RPSEngine";     // Name of the token used for the engine

    private String engineHost;
    private String tokenTimeOut = "300";                // Default - 300 minutes
    private String identityHost;
    private String apiKey;
    private String secretKey;
    private boolean acceptSelSigned = false;
    private final TokenCache tokenCache = new TokenCache();

    public HttpClientEngineProvider() {

    }

    /**
     * For testing purpose tells if we accept self-signed certificates
     *
     * @param accept
     */
    public void setAcceptSelSigned(boolean accept) {
        this.acceptSelSigned = accept;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getEngineHost() {
        return engineHost;
    }

    public void setEngineHost(String engineHost) {
        this.engineHost = engineHost;
    }

    public String getTokenTimeOut() {
        return tokenTimeOut;
    }

    public void setTokenTimeOut(String tokenTimeOut) {
        this.tokenTimeOut = tokenTimeOut;
    }

    public String getIdentityHost() {
        return identityHost;
    }

    public void setIdentityHost(String identityHost) {
        this.identityHost = identityHost;
    }

    /**
     * Calls the RPS Engine transform API
     *
     * @param requestBody
     * @return
     */
    @Override
    public ResponseBody transformAsync(RequestBody requestBody) {
        try {
            // Get authentication token
            String bearer = authenticate();

            CloseableHttpClient httpClient = getHttpClient(acceptSelSigned);
            String authorizationString = "Bearer " + bearer;    // Authorization string

            HttpPost post = new HttpPost(engineHost + "/api/transform");
            post.setHeader("Authorization", authorizationString);
            post.setHeader("Content-Type", "application/json");
            // Get Json request
            ObjectMapper mapper = new ObjectMapper();
            mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            String jsonRequest = mapper.writeValueAsString(requestBody);
            post.setEntity(new StringEntity(jsonRequest, "UTF-8"));

            // Handle the response as a string
            ResponseHandler<String> responseHandler = response -> {
                int status = response.getStatusLine().getStatusCode();
                if (status == 200) {
                    HttpEntity responseEntity = response.getEntity();
                    return responseEntity != null ? EntityUtils.toString(responseEntity) : null;
                } else {
                    System.out.println("ERROR: Transformation method returned " + response.getStatusLine().getStatusCode() +
                            " " + response.getStatusLine().getReasonPhrase());
                    return null;
                }
            };

            try {
                String responseBody = httpClient.execute(post, responseHandler);
                // Deserialize JSon response to ResponseBody object
                ResponseBody body = responseBody != null ? mapper.readValue(responseBody, ResponseBody.class)
                        : new ResponseBody();
                return body;
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                closeConnection(httpClient);
            }

            return null;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Authenticates against RPSIdentity and returns the access_token
     *
     * @return java.lang.String access token
     */
    public String authenticate() {

        String cachedToken = tokenCache.getToken(ENGINE_TOKEN);

        if (cachedToken != null)
            return cachedToken;

        // Uses a form application/x-www-form-urlencoded
        List<NameValuePair> formparams = new ArrayList<>();
        formparams.add(new BasicNameValuePair("grant_type", "client_credentials"));
        formparams.add(new BasicNameValuePair("client_id", apiKey));
        formparams.add(new BasicNameValuePair("client_secret", secretKey));
        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(formparams, Consts.UTF_8);
        // Set http request
        CloseableHttpClient httpClient = getHttpClient(acceptSelSigned);
        HttpPost httpPost = new HttpPost(identityHost + "/connect/token");
        httpPost.setEntity(entity);

        // Handle the response as a string
        ResponseHandler<String> responseHandler = response -> {
            int status = response.getStatusLine().getStatusCode();
            if (status == 200) {
                HttpEntity responseEntity = response.getEntity();
                return responseEntity != null ? EntityUtils.toString(responseEntity) : null;
            } else {
                System.out.println("ERROR Authentication method returned " + status);
                throw new ClientProtocolException("Unexpected response status: " + status);
            }
        };

        try {
            String responseBody = httpClient.execute(httpPost, responseHandler);
            ObjectMapper mapper = new ObjectMapper();
            mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            Token token = mapper.readValue(responseBody, Token.class);
            // Add the token to the cache
            tokenCache.addToken(ENGINE_TOKEN, token.getAccess_token(), Long.valueOf(token.getExpires_in()));
            // Then return the token
            return token.getAccess_token();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeConnection(httpClient);
        }
        return null;
    }

    /**
     * Closes an HttpClient once used
     *
     * @param httpClient
     */
    private void closeConnection(CloseableHttpClient httpClient) {
        try {
            httpClient.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates the http connection. This method can accept self signed certificates for testing.
     * This method is usefull when test certificates are self-signed and production ones are signed
     * In test we can set this value to true
     * While it has to be set to false in production
     *
     * @param withSelfSigned true if a self signed certificate can be accepted
     * @return
     */
    private CloseableHttpClient getHttpClient(boolean withSelfSigned) {
        if (withSelfSigned) {   // Accept self-signed certificates
            TrustStrategy acceptingTrustStrategy = (cert, authType) -> true;
            SSLContext sslContext = null;
            try {
                sslContext = SSLContexts.custom().loadTrustMaterial(null, acceptingTrustStrategy).build();
            } catch (Exception e) {
                e.printStackTrace();
            }
            SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext,
                    NoopHostnameVerifier.INSTANCE);

            Registry<ConnectionSocketFactory> socketFactoryRegistry =
                    RegistryBuilder.<ConnectionSocketFactory>create()
                            .register("https", sslsf)
                            .register("http", new PlainConnectionSocketFactory())
                            .build();

            BasicHttpClientConnectionManager connectionManager =
                    new BasicHttpClientConnectionManager(socketFactoryRegistry);
            CloseableHttpClient httpClient = HttpClients.custom().setSSLSocketFactory(sslsf)
                    .setConnectionManager(connectionManager).build();
            return httpClient;
        } else {    // Signed certificates
            return HttpClients.createDefault();
        }
    }
}
