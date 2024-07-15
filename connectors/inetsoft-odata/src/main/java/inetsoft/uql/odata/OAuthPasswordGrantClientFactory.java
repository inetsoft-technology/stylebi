/*
 * inetsoft-odata - StyleBI is a business intelligence web application.
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
package inetsoft.uql.odata;

import inetsoft.uql.XFactory;
import inetsoft.uql.tabular.oauth.AuthorizationClient;
import inetsoft.uql.tabular.oauth.Tokens;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.olingo.client.core.http.DefaultHttpClientFactory;
import org.apache.olingo.commons.api.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.net.URI;

public class OAuthPasswordGrantClientFactory extends DefaultHttpClientFactory {
   public OAuthPasswordGrantClientFactory(ODataDataSource dataSource, boolean saveTokens) {
      this.dataSource = dataSource;
      this.saveTokens = saveTokens;
   }

   @Override
   public DefaultHttpClient create(HttpMethod method, URI uri) {
      DefaultHttpClient client = super.create(method, uri);
      client.addRequestInterceptor((request, context) -> {
         Tokens tokens = AuthorizationClient.refreshPasswordGrantToken(dataSource);

         if(tokens != null) {
            dataSource.updateTokens(tokens);

            if(saveTokens && dataSource.getFullName() != null) {
               try {
                  XFactory.getRepository().updateDataSource(dataSource, dataSource.getFullName());
               }
               catch(Exception ex) {
                  LOG.warn("Failed to save data source after refreshing token", ex);
               }
            }
         }

         if(dataSource.getAccessToken() == null || dataSource.getAccessToken().isEmpty()) {
            throw new IllegalStateException("Access token is not set");
         }

         request.setHeader("Authorization", "Bearer " + dataSource.getAccessToken());
      });
      return client;
   }

   private final ODataDataSource dataSource;
   private final boolean saveTokens;

   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
}
