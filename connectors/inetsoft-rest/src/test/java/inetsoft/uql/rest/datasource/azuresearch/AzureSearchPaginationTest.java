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
package inetsoft.uql.rest.datasource.azuresearch;

import inetsoft.test.*;
import inetsoft.uql.tabular.RestParameter;
import inetsoft.uql.tabular.RestParameters;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Azure AI Search returns the continuation URL of a search in the "@odata.nextLink" property of
 * the response (Search Documents REST API reference).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, EndpointsTestConfig.class,
               SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AzureSearchPaginationTest {
   private static AzureSearchQuery documentSearch() {
      AzureSearchDataSource dataSource = new AzureSearchDataSource();
      dataSource.setApiKey("key");
      dataSource.setServiceName("acct1");
      AzureSearchQuery query = new AzureSearchQuery();
      query.setDataSource(dataSource);
      query.setJsonPath("$.value");
      query.setEndpoint("Document Search");
      RestParameters parameters = query.getParameters();

      for(RestParameter parameter : parameters.getParameters()) {
         if(parameter.isRequired()) {
            parameter.setValue("1");
         }
      }

      query.setParameters(parameters);
      return query;
   }

   @Test
   void searchFollowsTheOdataNextLink() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler().on(
         url -> !url.contains("skiptoken"),
         "{\"value\":" + ReplaySupport.array(1, 3) +
         ",\"@odata.nextLink\":\"https://acct1.search.windows.net/indexes/1/docs?skiptoken=abc\"}")
         .on(url -> url.contains("skiptoken"), "{\"value\":" + ReplaySupport.array(4, 2) + "}");
      List<Map<String, Object>> rows = ReplaySupport.run(documentSearch(), http);

      assertEquals(5, rows.size(), rows.toString());
      assertEquals(2, http.requests().size(), http.requests().toString());
      assertTrue(http.requests().get(1).contains("skiptoken=abc"), http.requests().get(1));
   }
}
