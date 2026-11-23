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
package inetsoft.uql.rest.datasource.hubspot;

import inetsoft.test.*;
import inetsoft.uql.rest.datasource.PaginationReplay;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * For every paged HubSpot endpoint, builds two pages from the endpoint's own pagination settings
 * and checks that the query reads both and stops.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, EndpointsTestConfig.class,
               SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class HubSpotPaginationReplayTest {
   @Test
   void everyPagedEndpointReadsAllGeneratedPagesAndStops() throws Exception {
      assertNoProblems(false);
   }

   // zero-based offsets: the first record is at offset 0
   @Test
   void offsetEndpointsStartAtOffsetZero() throws Exception {
      assertNoProblems(true);
   }

   private static void assertNoProblems(boolean offsetValues) throws Exception {
      List<String> problems = new ArrayList<>();
      Map<String, Integer> checked = new TreeMap<>();
      HubSpotQuery names = new HubSpotQuery();

      for(String name : names.getEndpointMap().keySet()) {
         HubSpotDataSource dataSource = new HubSpotDataSource();
         dataSource.setAccessToken("token");
         HubSpotQuery query = new HubSpotQuery();
         PaginationReplay.Result result;

         try {
            result = PaginationReplay.run(dataSource, query, name, offsetValues);
         }
         catch(Throwable e) {
            problems.add(name + ": " + e);
            continue;
         }

         if(result == null) {
            continue;
         }

         checked.merge(result.type(), 1, Integer::sum);

         for(String problem : result.problems()) {
            problems.add(result.type() + " / " + name + ": " + problem);
         }
      }

      assertTrue(problems.isEmpty(), problems.size() + " problem(s) (checked " + checked + "):\n" +
                 String.join("\n", problems));
      assertTrue(checked.size() >= 3, checked.toString());
   }
}
