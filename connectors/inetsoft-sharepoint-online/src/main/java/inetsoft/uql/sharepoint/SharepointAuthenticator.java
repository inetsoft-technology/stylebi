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
package inetsoft.uql.sharepoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.graph.authentication.BaseAuthenticationProvider;
import inetsoft.uql.XRepository;
import inetsoft.util.credential.CloudCredential;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

class SharepointAuthenticator extends BaseAuthenticationProvider {
   public SharepointAuthenticator(SharepointOnlineDataSource dataSource, boolean saveTokens) {
      this.dataSource = dataSource;
      this.saveTokens = saveTokens;
   }

   @Override
   public CompletableFuture<String> getAuthorizationTokenAsync(URL requestUrl) {
      if(shouldAuthenticateRequestWithUrl(Objects.requireNonNull(requestUrl, "requestUrl parameter cannot be null"))) {
         try {
            return CompletableFuture.completedFuture(getToken());
         }
         catch(IOException e) {
            throw new RuntimeException("Failed to authorized request", e);
         }
      }
      else {
         return CompletableFuture.completedFuture((String) null);
      }
   }

   /**
    * Gets the access token to send. Bug #77730, the token obtained by a token request is returned
    * directly, it is not read again from the data source, whose credential may not hold it.
    */
   private String getToken() throws IOException {
      String accessToken;
      String refreshToken;
      Instant expires;

      // the tokens are read and replaced together, so that a token is never sent with the
      // expiration of another token when the data source is used by more than one thread
      synchronized(dataSource) {
         accessToken = dataSource.getAccessToken();

         if(accessToken != null && !isExpired()) {
            return accessToken;
         }

         accessToken = requestToken();
         refreshToken = dataSource.getRefreshToken();
         expires = dataSource.getTokenExpires();
      }

      saveTokens(accessToken, refreshToken, expires);
      return accessToken;
   }

   /**
    * Requests a new token, by refreshing the token if there is a refresh token, or by signing in.
    */
   private String requestToken() throws IOException {
      if(dataSource.getRefreshToken() != null) {
         try {
            return refreshAccessToken();
         }
         catch(IOException e) {
            // the refresh token may have expired or been revoked, sign in with the password
            LOG.warn("Failed to refresh the access token of data source {}, signing in again: {}",
                     dataSource.getFullName(), e.getMessage());
            LOG.debug("Failed to refresh the access token", e);
         }
      }

      return requestPasswordGrant();
   }

   /**
    * Checks if the access token has expired or is about to expire, or if its expiration is unknown.
    */
   private boolean isExpired() {
      Instant expires = dataSource.getTokenExpires();
      return expires == null || !expires.isAfter(Instant.now().plus(EXPIRATION_MARGIN));
   }

   private String requestPasswordGrant() throws IOException {
      return authorize("client_id=" + dataSource.getClientId() +
         "&client_secret=" + URLEncoder.encode(dataSource.getClientSecret(), "UTF-8") +
         "&scope=Sites.Read.All%20offline_access" +
         "&username=" + URLEncoder.encode(dataSource.getUser(), "UTF-8") +
         "&password=" + URLEncoder.encode(dataSource.getPassword(), "UTF-8") +
         "&grant_type=password", null);
   }

   private String refreshAccessToken() throws IOException {
      String refreshToken = dataSource.getRefreshToken();
      return authorize("client_id=" + dataSource.getClientId() +
         "&refresh_token=" + URLEncoder.encode(refreshToken, "UTF-8") +
         "&grant_type=refresh_token" +
         "&client_secret=" + URLEncoder.encode(dataSource.getClientSecret(), "UTF-8"),
         refreshToken);
   }

   /**
    * Requests a token and keeps it in the data source.
    *
    * @param body         the body of the token request.
    * @param refreshToken the refresh token to keep if the response does not have a new one.
    *
    * @return the access token.
    */
   private String authorize(String body, String refreshToken) throws IOException {
      HttpPost post = new HttpPost(
         "https://login.microsoftonline.com/" + dataSource.getTenantId() + "/oauth2/v2.0/token");
      ByteArrayEntity entity = new ByteArrayEntity(
         body.getBytes(StandardCharsets.UTF_8), ContentType.APPLICATION_FORM_URLENCODED);
      post.setEntity(entity);
      HttpClient client = HttpClients.createDefault();
      AuthorizationResponse tokens = client.execute(post, this::handleTokenResponse);

      if(tokens.getAccessToken() == null) {
         throw new ClientProtocolException("The token response has no access token");
      }

      dataSource.setAccessToken(tokens.getAccessToken());
      dataSource.setRefreshToken(
         tokens.getRefreshToken() != null ? tokens.getRefreshToken() : refreshToken);
      dataSource.setTokenExpires(Instant.now().plus(tokens.getExpiresIn(), ChronoUnit.SECONDS));
      return tokens.getAccessToken();
   }

   private AuthorizationResponse handleTokenResponse(ClassicHttpResponse response)
      throws IOException, ParseException
   {
      int responseCode = response.getCode();

      if(responseCode < 200 || responseCode >= 300) {
         throw new ClientProtocolException(
            "Authorization request failed with response code [" + responseCode + "]: " +
            EntityUtils.toString(response.getEntity()));
      }

      return new ObjectMapper()
         .readValue(response.getEntity().getContent(), AuthorizationResponse.class);
   }

   private void saveTokens(String accessToken, String refreshToken, Instant expires) {
      // Bug #77730, the tokens of a cloud credential are kept only by this runtime instance, the
      // stored definition holds only the id of the secret and can't keep them (Bug #77699).
      // Tokens obtained for another account than that of the stored definition, e.g. when the
      // variables of the credential were replaced with the values of the query, are not kept
      // either, and the stored definition is then not written at all
      if(!saveTokens || dataSource.getCredential() instanceof CloudCredential ||
         !isSameAccount(getStoredDataSource()))
      {
         return;
      }

      try {
         // Bug #77699, save onto the stored definition, not this runtime instance whose
         // variables may have been replaced with the values of the query
         XRepository.getRepository().updateDataSourceTokens(dataSource, stored -> {
            // checked again, the stored definition may have changed in the meantime
            if(!isSameAccount(stored)) {
               return;
            }

            SharepointOnlineDataSource sharepoint = (SharepointOnlineDataSource) stored;
            sharepoint.setAccessToken(accessToken);
            sharepoint.setRefreshToken(refreshToken);
            sharepoint.setTokenExpires(expires);
         });
      }
      catch(Exception e) {
         LOG.error("Failed to save access token", e);
      }
   }

   /**
    * Checks if a stored definition is that of the account the tokens were obtained for.
    */
   private boolean isSameAccount(Object stored) {
      return stored instanceof SharepointOnlineDataSource sharepoint &&
         sharepoint.isSameAccount(dataSource);
   }

   /**
    * Gets the stored definition of the data source, without copying it.
    */
   private Object getStoredDataSource() {
      try {
         XRepository repository = XRepository.getRepository();
         SharepointOnlineDataSource base = dataSource.getBaseDatasource();
         String name = dataSource.getFullName();

         // an additional connection is stored under its parent, not at its bare name
         if(base != null && name != null && name.indexOf('/') < 0) {
            return repository.getDataSource(base.getFullName(), false)
               instanceof SharepointOnlineDataSource parent ? parent.getDataSource(name) : null;
         }

         return repository.getDataSource(name, false);
      }
      catch(Exception e) {
         LOG.debug("Failed to get the stored data source {}", dataSource.getFullName(), e);
         return null;
      }
   }

   private final SharepointOnlineDataSource dataSource;
   private final boolean saveTokens;
   // a token that expires within this time is not used, so that it does not expire in transit
   private static final Duration EXPIRATION_MARGIN = Duration.ofMinutes(1);
   private static final Logger LOG = LoggerFactory.getLogger(SharepointAuthenticator.class);
}
