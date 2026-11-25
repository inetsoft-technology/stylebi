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
package inetsoft.uql.rest.datasource.youtubeanalytics;

import inetsoft.test.*;
import inetsoft.uql.tabular.RestParameter;
import inetsoft.uql.tabular.RestParameters;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * YouTube Analytics reports: startIndex is 1-based (YouTube Analytics API reference), the next
 * startIndex is the index after the rows already read, and the query ends on a page without rows.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, EndpointsTestConfig.class,
               SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class YouTubeAnalyticsPaginationTest {
   private static YouTubeAnalyticsQuery report() {
      YouTubeAnalyticsDataSource dataSource = new YouTubeAnalyticsDataSource();
      dataSource.setAccessToken("yt-token");
      dataSource.setRefreshToken("yt-refresh");
      dataSource.setTokenExpiration(System.currentTimeMillis() + Duration.ofHours(1).toMillis());
      YouTubeAnalyticsQuery query = new YouTubeAnalyticsQuery();
      query.setDataSource(dataSource);
      query.setEndpoint("Reports Query");
      RestParameters parameters = query.getParameters();

      for(RestParameter parameter : parameters.getParameters()) {
         if(parameter.isRequired()) {
            parameter.setValue("1");
         }
      }

      query.setParameters(parameters);
      return query;
   }

   private static String page(int first, int count) {
      StringBuilder rows = new StringBuilder();

      for(int i = 0; i < count; i++) {
         rows.append(i == 0 ? "" : ",").append("[").append(first + i).append(",").append(i)
            .append("]");
      }

      return "{\"columnHeaders\":[{\"name\":\"views\"},{\"name\":\"likes\"}],\"rows\":[" +
         rows + "]}";
   }

   @Test
   void rowsAreReadAcrossPagesAndTheQueryEndsOnAPageWithoutRows() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler().on(
         "/v2/reports", page(1, 3), page(4, 2), page(0, 0));
      List<Map<String, Object>> rows = ReplaySupport.run(report(), http);

      assertEquals(5, rows.size(), rows.toString());
      assertEquals(3, http.requests().size(), http.requests().toString());
      assertTrue(http.requests().get(0).contains("startIndex=1"), http.requests().get(0));
      assertTrue(http.requests().get(1).contains("startIndex=4"), http.requests().get(1));
      assertTrue(http.requests().get(2).contains("startIndex=6"), http.requests().get(2));
      assertTrue(http.requests().get(0).contains("maxResults=100"), http.requests().get(0));
   }

   @Test
   void aLastPageWithoutARowsFieldEndsTheQuery() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler().on(
         "/v2/reports", page(1, 2), "{\"columnHeaders\":[{\"name\":\"views\"},{\"name\":\"likes\"}]}");
      List<Map<String, Object>> rows = ReplaySupport.run(report(), http);

      assertEquals(2, rows.size(), rows.toString());
      assertEquals(2, http.requests().size(), http.requests().toString());
   }

   @Test
   void columnsComeFromTheHeadersAndValuesFromTheRows() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler().on(
         "/v2/reports", page(10, 1), page(0, 0));
      List<Map<String, Object>> rows = ReplaySupport.run(report(), http);

      assertEquals(1, rows.size());
      assertEquals(List.of("views", "likes"), List.copyOf(rows.get(0).keySet()));
      assertEquals(10, ((Number) rows.get(0).get("views")).intValue());
   }
}
