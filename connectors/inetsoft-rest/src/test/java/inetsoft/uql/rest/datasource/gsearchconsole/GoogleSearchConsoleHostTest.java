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
package inetsoft.uql.rest.datasource.gsearchconsole;

import inetsoft.test.*;
import inetsoft.uql.tabular.RestParameter;
import inetsoft.uql.tabular.RestParameters;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The URL Inspection API is served from searchconsole.googleapis.com, the Search Console API
 * (sites, sitemaps, search analytics) from www.googleapis.com/webmasters (Google Search Console API
 * reference).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, EndpointsTestConfig.class,
               SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class GoogleSearchConsoleHostTest {
   @Test
   void urlInspectionHasItsOwnHost() throws Exception {
      Map<String, String> hosts = new TreeMap<>();

      for(String name : new GoogleSearchConsoleQuery().getEndpointMap().keySet()) {
         GoogleSearchConsoleDataSource dataSource = new GoogleSearchConsoleDataSource();
         dataSource.setAccessToken("g-token");
         dataSource.setRefreshToken("g-refresh");
         dataSource.setTokenExpiration(System.currentTimeMillis() + Duration.ofHours(1).toMillis());
         GoogleSearchConsoleQuery query = new GoogleSearchConsoleQuery();
         query.setDataSource(dataSource);
         query.setJsonPath("$");
         query.setEndpoint(name);
         RestParameters parameters = query.getParameters();

         for(RestParameter parameter : parameters.getParameters()) {
            if(parameter.isRequired()) {
               parameter.setValue("1");
            }
         }

         query.setParameters(parameters);

         if(query.isPostRequest()) {
            query.setRequestBody("{}");
         }

         ScriptedHttpHandler http = new ScriptedHttpHandler().on(url -> true, "{}");
         ReplaySupport.runTable(query, http);
         hosts.put(name, http.requests().isEmpty() ? "(no request)" :
                   URI.create(http.requests().get(0)).getHost());
      }

      Map<String, String> expected = new TreeMap<>();

      for(String name : hosts.keySet()) {
         expected.put(name, name.equals("URL Inspection") ? "searchconsole.googleapis.com" :
                      "www.googleapis.com");
      }

      assertEquals(expected, hosts);
   }
}
