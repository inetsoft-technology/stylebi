/*
 * inetsoft-sharepoint-online - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.sharepoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.graph.authentication.BaseAuthenticationProvider;
import inetsoft.uql.XFactory;
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
            if(dataSource.getRefreshToken() != null &&
               dataSource.getTokenExpires().isBefore(Instant.now()))
            {
               refreshAccessToken();
            }
            else if(dataSource.getAccessToken() == null) {
               getAccessToken();
            }

            return CompletableFuture.completedFuture(dataSource.getAccessToken());
         }
         catch(IOException e) {
            throw new RuntimeException("Failed to authorized request", e);
         }
      }
      else {
         return CompletableFuture.completedFuture((String) null);
      }
   }

   private void getAccessToken() throws IOException {
      authorize("client_id=" + dataSource.getClientId() +
         "&client_secret=" + URLEncoder.encode(dataSource.getClientSecret(), "UTF-8") +
         "&scope=Sites.Read.All%20offline_access" +
         "&username=" + URLEncoder.encode(dataSource.getUsername(), "UTF-8") +
         "&password=" + URLEncoder.encode(dataSource.getPassword(), "UTF-8") +
         "&grant_type=password");
   }

   private void refreshAccessToken() throws IOException {
      authorize("client_id=" + dataSource.getClientId() +
         "&refresh_token=" + URLEncoder.encode(dataSource.getRefreshToken(), "UTF-8") +
         "&grant_type=refresh_token" +
         "&client_secret=" + URLEncoder.encode(dataSource.getClientSecret(), "UTF-8"));

   }

   private void authorize(String body) throws IOException {
      HttpPost post = new HttpPost(
         "https://login.microsoftonline.com/" + dataSource.getTenantId() + "/oauth2/v2.0/token");
      ByteArrayEntity entity = new ByteArrayEntity(
         body.getBytes(StandardCharsets.UTF_8), ContentType.APPLICATION_FORM_URLENCODED);
      post.setEntity(entity);
      HttpClient client = HttpClients.createDefault();
      AuthorizationResponse tokens = client.execute(post, this::handleTokenResponse);

      dataSource.setAccessToken(tokens.getAccessToken());
      dataSource.setRefreshToken(tokens.getRefreshToken());
      dataSource.setTokenExpires(Instant.now().plus(tokens.getExpiresIn(), ChronoUnit.SECONDS));

      saveTokens();
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

   private void saveTokens() {
      if(saveTokens) {
         try {
            XFactory.getRepository().updateDataSource(dataSource, dataSource.getFullName());
         }
         catch(Exception e) {
            LOG.error("Failed to save access token", e);
         }
      }
   }

   private final SharepointOnlineDataSource dataSource;
   private final boolean saveTokens;
   private static final Logger LOG = LoggerFactory.getLogger(SharepointAuthenticator.class);
}
