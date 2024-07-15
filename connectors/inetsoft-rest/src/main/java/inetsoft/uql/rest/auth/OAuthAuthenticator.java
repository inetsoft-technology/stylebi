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

import inetsoft.uql.XFactory;
import inetsoft.uql.rest.AbstractRestDataSource;
import inetsoft.uql.tabular.oauth.*;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class OAuthAuthenticator<T extends AbstractRestDataSource & OAuthDataSource> implements RestAuthenticator {
   public OAuthAuthenticator(T dataSource) {
      this.dataSource = dataSource;
   }

   public static <T extends AbstractRestDataSource & OAuthDataSource> RestAuthenticator create(T dataSource) {
      return new OAuthAuthenticator<>(dataSource);
   }

   @Override
   public void authenticateRequest(HttpRequestBase request, HttpClientContext context) {
      final boolean basicAuth = dataSource.isBasicAuth();
      final Tokens tokens = AuthorizationClient.refreshTokens(dataSource, basicAuth);

      if(tokens != null) {
         dataSource.updateTokens(tokens);
      }

      if(dataSource.getFullName() != null) {
         try {
            XFactory.getRepository().updateDataSource(dataSource, dataSource.getFullName());
         }
         catch(Exception e) {
            LOG.warn("Failed to save data source after refreshing token", e);
         }
      }

      if(dataSource.getAccessToken() != null) {
         request.addHeader("Authorization", "Bearer " + dataSource.getAccessToken());
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   private final T dataSource;
}
