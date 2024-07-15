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
package inetsoft.uql.onedrive;

import com.microsoft.graph.authentication.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

class OneDriveAuthenticator extends BaseAuthenticationProvider {
   public OneDriveAuthenticator(OneDriveDataSource dataSource) {
      this.dataSource = dataSource;
   }

   @Override
   public CompletableFuture<String> getAuthorizationTokenAsync(URL requestUrl) {
      if(shouldAuthenticateRequestWithUrl(Objects.requireNonNull(requestUrl, "requestUrl parameter cannot be null"))) {

         if(dataSource.getRefreshToken() != null &&
            Instant.now().isAfter(Instant.ofEpochMilli(dataSource.getTokenExpiration())))
         {
            try {
               dataSource.refreshTokens();
            }
            catch(Exception e) {
               throw new RuntimeException("Failed to refresh the access token", e);
            }
         }


         return CompletableFuture.completedFuture(dataSource.getAccessToken());
      }
      else {
         return CompletableFuture.completedFuture((String) null);
      }
   }

   private final OneDriveDataSource dataSource;
   private static final Logger LOG = LoggerFactory.getLogger(OneDriveAuthenticator.class);
}
