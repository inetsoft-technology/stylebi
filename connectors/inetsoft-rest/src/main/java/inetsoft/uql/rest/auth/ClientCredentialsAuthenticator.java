/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.uql.rest.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.uql.rest.auth.ClientCredentialsTokenCache.ClientCredentialsRequest;
import inetsoft.uql.rest.auth.ClientCredentialsTokenCache.TokenResponse;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.nio.client.HttpAsyncClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * Authenticates the request with an access token obtained with the OAuth 2.0 client credentials
 * grant (RFC 6749 section 4.4). The token is requested directly from the token endpoint by the
 * server, no browser or authorization relay is involved, and is cached in memory until shortly
 * before it expires.
 */
public class ClientCredentialsAuthenticator implements RestAuthenticator {
   ClientCredentialsAuthenticator(ClientCredentialsRequest tokenRequest, HttpAsyncClient client) {
      this(tokenRequest, client, ClientCredentialsTokenCache.getInstance());
   }

   ClientCredentialsAuthenticator(ClientCredentialsRequest tokenRequest, HttpAsyncClient client,
                                  ClientCredentialsTokenCache cache)
   {
      this.tokenRequest = tokenRequest;
      this.client = client;
      this.cache = cache;
   }

   @Override
   public void authenticateRequest(HttpRequestBase request, HttpClientContext context)
      throws IOException, InterruptedException
   {
      final String accessToken = cache.getAccessToken(tokenRequest, this::requestToken);
      appliedToken = accessToken;
      request.removeHeaders("Authorization");
      request.addHeader("Authorization", "Bearer " + accessToken);
   }

   /**
    * The authorization server can revoke a token before it expires, so a rejected token is
    * discarded and a new one requested instead of being used until its expiration.
    */
   @Override
   public boolean credentialsRejected() throws InterruptedException {
      if(appliedToken == null) {
         return false;
      }

      final boolean retry = cache.invalidate(tokenRequest, appliedToken);
      appliedToken = null;

      if(retry) {
         LOG.debug("Access token rejected, retrying with a new one for {}", tokenRequest);
      }

      return retry;
   }

   /**
    * Requests a new token from the token endpoint to verify the client credentials. The cached
    * token is neither used nor replaced, so the queries using it are not affected.
    */
   public void verifyCredentials() throws IOException, InterruptedException {
      cache.verify(tokenRequest, this::requestToken);
   }

   private TokenResponse requestToken(ClientCredentialsRequest tokenRequest)
      throws IOException, InterruptedException
   {
      final HttpPost post = new HttpPost(tokenRequest.tokenUri());
      final List<NameValuePair> form = new ArrayList<>();
      form.add(new BasicNameValuePair("grant_type", "client_credentials"));

      if(tokenRequest.clientAuthMethod() == ClientAuthMethod.POST) {
         form.add(new BasicNameValuePair("client_id", tokenRequest.clientId()));
         form.add(new BasicNameValuePair("client_secret", tokenRequest.clientSecret()));
      }
      else {
         post.addHeader("Authorization", "Basic " + getBasicCredentials(tokenRequest));
      }

      if(!isEmpty(tokenRequest.scope())) {
         form.add(new BasicNameValuePair("scope", tokenRequest.scope().trim()));
      }

      if(!isEmpty(tokenRequest.audience())) {
         form.add(new BasicNameValuePair("audience", tokenRequest.audience().trim()));
      }

      post.setEntity(new UrlEncodedFormEntity(form, StandardCharsets.UTF_8));
      post.addHeader("Accept", "application/json");

      final Future<HttpResponse> future = client.execute(post, null);
      final HttpResponse response;

      try {
         response = future.get(TOKEN_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      }
      catch(TimeoutException e) {
         future.cancel(true);
         throw new IOException(
            "Timed out requesting an access token from " + tokenRequest.tokenUri(), e);
      }
      catch(InterruptedException e) {
         // the query was cancelled, don't leave the request running on the shared client
         future.cancel(true);
         throw e;
      }
      catch(ExecutionException e) {
         if(e.getCause() instanceof IOException) {
            throw (IOException) e.getCause();
         }

         throw new IOException(
            "Failed to request an access token from " + tokenRequest.tokenUri(), e);
      }

      final int status = response.getStatusLine().getStatusCode();
      final String body = response.getEntity() == null ?
         "" : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

      if(status < 200 || status >= 300) {
         throw new IOException(
            "Failed to get an access token from " + tokenRequest.tokenUri() +
            ", the server responded with " + status + getErrorDescription(body));
      }

      final JsonNode json;

      try {
         json = MAPPER.readTree(body);
      }
      catch(IOException e) {
         throw new IOException(
            "The token endpoint " + tokenRequest.tokenUri() + " did not return a JSON response",
            e);
      }

      final JsonNode accessToken = json == null ? null : json.get("access_token");
      final JsonNode expiresIn = json == null ? null : json.get("expires_in");

      if(accessToken == null || !accessToken.isTextual()) {
         throw new IOException(
            "The token endpoint " + tokenRequest.tokenUri() + " did not return an access token");
      }

      LOG.debug("Received a client credentials access token for {}", tokenRequest);
      // some servers return expires_in as a string
      return new TokenResponse(
         accessToken.asText(), expiresIn == null ? 0L : expiresIn.asLong(0L));
   }

   /**
    * Gets the OAuth error from a token error response (RFC 6749 section 5.2), if there is one.
    */
   private static String getErrorDescription(String body) {
      try {
         final JsonNode json = MAPPER.readTree(body);

         if(json != null && json.hasNonNull("error")) {
            final StringBuilder description =
               new StringBuilder(": ").append(json.get("error").asText());

            if(json.hasNonNull("error_description")) {
               description.append(" - ").append(json.get("error_description").asText());
            }

            return description.toString();
         }
      }
      catch(Exception ignore) {
         // not a JSON error response
      }

      return "";
   }

   /**
    * The client ID and secret are form URL encoded before they are joined with a colon and
    * base 64 encoded (RFC 6749 section 2.3.1).
    */
   private static String getBasicCredentials(ClientCredentialsRequest tokenRequest) {
      final String credentials = formEncode(tokenRequest.clientId()) + ":" +
         formEncode(tokenRequest.clientSecret());
      return Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
   }

   private static String formEncode(String value) {
      return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
   }

   private static boolean isEmpty(String value) {
      return value == null || value.trim().isEmpty();
   }

   private final ClientCredentialsRequest tokenRequest;
   private final HttpAsyncClient client;
   private final ClientCredentialsTokenCache cache;
   private String appliedToken;

   private static final long TOKEN_REQUEST_TIMEOUT_SECONDS = 60L;
   private static final ObjectMapper MAPPER = new ObjectMapper();
   private static final Logger LOG = LoggerFactory.getLogger(ClientCredentialsAuthenticator.class);
}
