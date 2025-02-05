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
package inetsoft.uql.rest.datasource.fortytwomatters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.json.EndpointJsonRuntime;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.tabular.TabularQuery;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URLEncoder;

@SuppressWarnings("unused")
public class FortyTwoMattersRuntime extends EndpointJsonRuntime {
   @Override
   public XTableNode runQuery(TabularQuery tabularQuery, VariableTable params) {
      FortyTwoMattersQuery query = (FortyTwoMattersQuery) tabularQuery.clone();
      FortyTwoMattersDataSource dataSource = (FortyTwoMattersDataSource) query.getDataSource();
      FortyTwoMattersEndpoint endpoint = FortyTwoMattersQuery.getEndpoint(query.getEndpoint());
      return super.runQuery(query, params);
   }

   @Override
   public void testDataSource(TabularDataSource ds, VariableTable params) throws Exception {
      FortyTwoMattersDataSource dataSource = (FortyTwoMattersDataSource) ds;
      String url = dataSource.getURL();
      String accessToken = dataSource.getAccessToken();

      if(url == null || url.isEmpty()) {
         throw new IllegalStateException("The URL is not set");
      }

      if(accessToken == null || accessToken.isEmpty()) {
         throw new IllegalStateException("The access token is not set");
      }

      url += "/v2.0/account.json?access_token=" + URLEncoder.encode(accessToken, "UTF-8");

      HttpGet request = new HttpGet(url);

      try(CloseableHttpClient client = HttpClients.createDefault();
          CloseableHttpResponse response  = client.execute(request))
      {
         if(response.getStatusLine().getStatusCode() >= 200 && response.getStatusLine().getStatusCode() < 300) {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode data =
               (ObjectNode) mapper.readTree(EntityUtils.toString(response.getEntity()));
            String status = data.has("status") ? data.get("status").asText() : null;

            if(!"OK".equals(status)) {
               throw new Exception("Account status is " + status);
            }
         }
         else {
            throw new IOException(String.format(
               "Failed to connect to server, HTTP error %d: %s",
               response.getStatusLine().getStatusCode(),
               response.getStatusLine().getReasonPhrase()));
         }
      }
   }
}
