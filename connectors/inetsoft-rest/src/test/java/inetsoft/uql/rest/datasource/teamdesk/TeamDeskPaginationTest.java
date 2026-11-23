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
package inetsoft.uql.rest.datasource.teamdesk;

import inetsoft.test.*;
import inetsoft.uql.tabular.RestParameter;
import inetsoft.uql.tabular.RestParameters;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OFFSET paging of the TeamDesk Select Table endpoint. TeamDesk's skip is the number of records
 * to skip, so the first request skips none and the next skips the records already read.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, EndpointsTestConfig.class,
               SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TeamDeskPaginationTest {
   private static TeamDeskQuery selectTable() {
      TeamDeskDataSource dataSource = new TeamDeskDataSource();
      dataSource.setDomain("acct1.example.com");
      dataSource.setApplicationId("app1");
      dataSource.setAuthorizationToken("token");
      TeamDeskQuery query = new TeamDeskQuery();
      query.setDataSource(dataSource);
      query.setJsonPath("$");
      query.setEndpoint("Select Table");
      RestParameters parameters = query.getParameters();

      for(RestParameter parameter : parameters.getParameters()) {
         parameter.setValue("1");
      }

      query.setParameters(parameters);
      return query;
   }

   @Test
   void selectTableEndsOnAnEmptyPage() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("select.json", ReplaySupport.array(1, 3), "[]");
      List<Map<String, Object>> rows = ReplaySupport.run(selectTable(), http);

      assertEquals(3, rows.size());
      assertEquals(2, http.requests().size(), http.requests().toString());
   }

   @Disabled("Bug #81880: the first page is requested with skip=1 and the first record is lost; fixed on epic-test-coverage, enable when that branch reaches main")
   @Test
   void selectTableSkipsNothingOnTheFirstPage() throws Exception {
      // the second request fails, so that the query ends whatever it does at an empty page
      ScriptedHttpHandler http = new ScriptedHttpHandler().onReplies(
         url -> true, new ScriptedHttpHandler.Reply(ReplaySupport.array(1, 3), List.of(), null),
         ScriptedHttpHandler.failure(new java.io.IOException("stop")));
      ReplaySupport.runTable(selectTable(), http);

      assertTrue(http.requests().get(0).contains("skip=0") ||
                 !http.requests().get(0).contains("skip="), http.requests().get(0));
      assertTrue(http.requests().get(1).contains("skip=3"), http.requests().get(1));
   }
}
