package ch.regdata.rps.engine.client.http;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * This class caches tokens to avoid to authenticate too often.
 * It calculates if the token has expired.
 * If the token is still valid it returns the valid token
 * Else it returns null
 */
public class TokenCache {

    /**
     * Map of tokens
     */
    private final Map<String, String> tokens = new HashMap<>();
    /**
     * Map of dates and times when the tokens have been created
     */
    private final Map<String, Date> expiries = new HashMap<>();
    /**
     * Stores the timeout duration for the tokens
     */
    private final Map<String, Long> timeouts = new HashMap<>();

    /**
     * Get a token value for a given token/application
     *
     * @param name
     * @return the access_token if the token is still valid, null otherwise
     */
    public String getToken(String name) {
        synchronized (tokens) {
            String token = name != null ? tokens.get(name) : null;
            if (token != null) {
                Date creationDate = expiries.get(name);
                if (creationDate == null) {
                    return null;
                } else {
                    // Check if the token has expired
                    Date now = new Date();
                    long diff = now.getTime() - creationDate.getTime();
                    Long timeout = timeouts.get(name);
                    if (timeout == null) {
                        return null;
                    }
                    if (diff > timeout * 1000) {       // Time_out is in seconds, compared to ms
                        cleanToken(name);
                        return null;
                    }
                }
            }
            // The token is valid
            return token;
        }
    }

    /**
     * Removes a token from the cache
     * Tokens are removed when invalid
     *
     * @param tokenName
     */
    private void cleanToken(String tokenName) {
        synchronized (tokens) {
            tokens.remove(tokenName);
            expiries.remove(tokenName);
            timeouts.remove(tokenName);
        }
    }

    /**
     * Add a token to the cache
     *
     * @param tokenName
     * @param tokenValue
     * @param timeout
     */
    public void addToken(String tokenName, String tokenValue, Long timeout) {
        // Make sure this token doesn't already exist
        cleanToken(tokenName);
        // Add the token
        synchronized (tokens) {
            tokens.put(tokenName, tokenValue);
            expiries.put(tokenName, new Date());
            timeouts.put(tokenName, timeout);
        }
    }
}
