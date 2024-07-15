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
package inetsoft.uql.gdata;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.*;
import com.google.api.client.json.Json;
import com.google.api.client.util.ExponentialBackOff;
import inetsoft.uql.XFactory;
import inetsoft.uql.tabular.oauth.AuthorizationClient;
import inetsoft.uql.tabular.oauth.Tokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

public class GDataRequestInitializer implements HttpRequestInitializer {
   public GDataRequestInitializer(GDataDataSource dataSource, boolean saveTokens) {
      this.dataSource = dataSource;
      this.saveTokens = saveTokens;
   }

   @Override
   public void initialize(HttpRequest request) {
      if(Instant.now().isAfter(Instant.ofEpochMilli(dataSource.getTokenExpiration()))) {
         if(dataSource.getRefreshToken() == null || dataSource.getRefreshToken().isEmpty()) {
            throw new IllegalStateException("Refresh token is not set");
         }

         try {
            Tokens tokens =
               AuthorizationClient.refresh("google-sheets", dataSource.getRefreshToken(), null);
            dataSource.updateTokens(tokens);
         }
         catch(Exception e) {
            throw new RuntimeException("Failed to refresh the access token", e);
         }

         if(saveTokens && dataSource.getFullName() != null) {
            try {
               XFactory.getRepository().updateDataSource(dataSource, dataSource.getFullName());
            }
            catch(Exception e) {
               LOG.warn("Failed to save data source after refreshing token", e);
            }
         }
      }

      if(dataSource.getAccessToken() == null || dataSource.getAccessToken().isEmpty()) {
         throw new IllegalStateException("Access token is not set");
      }

      request.getHeaders().setAuthorization("Bearer " + dataSource.getAccessToken());

      if(dataSource.getConnectTimeout() > 0) {
         request.setConnectTimeout(dataSource.getConnectTimeout());
      }

      if(dataSource.getReadTimeout() > 0) {
         request.setReadTimeout(dataSource.getReadTimeout());
      }

      HttpBackOffUnsuccessfulResponseHandler handler =
         new HttpBackOffUnsuccessfulResponseHandler(new ExponentialBackOff());
      handler.setBackOffRequired(backOffRequired);
      request.setUnsuccessfulResponseHandler(handler);
   }

   private final GDataDataSource dataSource;
   private final boolean saveTokens;

   private static final Logger LOG = LoggerFactory.getLogger(GDataRequestInitializer.class);
   private static final Set<String> retryErrors = new HashSet<>(Arrays.asList(
      "userRateLimitExceeded", "quotaExceeded", "internalServerError", "backendError"));
   private static final HttpBackOffUnsuccessfulResponseHandler.BackOffRequired backOffRequired = response -> {
      try {
         if(!response.isSuccessStatusCode()
            && HttpMediaType.equalsIgnoreParameters(Json.MEDIA_TYPE, response.getContentType())
            && response.getContent() != null)
         {
            GoogleJsonResponseException ex =
               GoogleJsonResponseException.from(GDataRuntime.JSON_FACTORY, response);
            GoogleJsonError details = ex.getDetails();
            return details.getErrors().stream()
               .map(GoogleJsonError.ErrorInfo::getReason)
               .anyMatch(retryErrors::contains);
         }
      }
      catch(IOException e) {
         LOG.warn("Failed to parse response", e);
      }

      return false;
   };
}
