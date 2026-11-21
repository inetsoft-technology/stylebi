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
package inetsoft.uql.rest.datasource.seomonitor;

import inetsoft.test.*;
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
 * OFFSET paging of the SEOmonitor endpoints (page types 2 and 3): the offset starts at 0 and
 * advances by the number of records read, and the query ends on a page without records. Every
 * test here needs the end of the query, so they all wait for Bug #81958.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, EndpointsTestConfig.class,
               SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SEOmonitorPaginationTest {
   private static SEOmonitorQuery keywordData() {
      SEOmonitorDataSource dataSource = new SEOmonitorDataSource();
      dataSource.setApiToken("token");
      return ReplaySupport.query(dataSource, new SEOmonitorQuery(), "Keyword Data", "$",
                                 Map.of("Site ID", "1", "Start Date", "2024-01-01",
                                        "End Date", "2024-01-31"));
   }

   private static SEOmonitorQuery ranks() {
      SEOmonitorDataSource dataSource = new SEOmonitorDataSource();
      dataSource.setApiToken("token");
      return ReplaySupport.query(dataSource, new SEOmonitorQuery(), "Ranks", "$[0]",
                                 Map.of("Site ID", "1", "Start Date", "2024-01-01",
                                        "End Date", "2024-01-31"));
   }

   @Disabled("Bug #81958: an empty last page does not end the query, the same offset is requested forever; enable when it is fixed")
   @Test
   void keywordDataEndsOnAnEmptyPage() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("keyword_data", ReplaySupport.array(1, 3), "[]");
      List<Map<String, Object>> rows = ReplaySupport.run(keywordData(), http);

      assertEquals(3, rows.size());
      assertEquals(2, http.requests().size(), http.requests().toString());
   }

   @Disabled("Bug #81958: an empty last page does not end the query, the same offset is requested forever; enable when it is fixed")
   @Test
   void keywordDataWithNoRecordsMakesOneRequest() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler().on("keyword_data", "[]");
      ReplaySupport.runTable(keywordData(), http);

      assertEquals(1, http.requests().size(), http.requests().toString());
   }

   @Disabled("Bug #81958: an empty last page does not end the query, the same offset is requested forever; enable when it is fixed")
   @Test
   void ranksEndOnAnEmptyPage() throws Exception {
      // the records of a page are the first element of the array
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("/ranks/", "[" + ReplaySupport.array(1, 3) + "]", "[[]]");
      List<Map<String, Object>> rows = ReplaySupport.run(ranks(), http);

      assertEquals(3, rows.size());
      assertEquals(2, http.requests().size(), http.requests().toString());
   }
}
