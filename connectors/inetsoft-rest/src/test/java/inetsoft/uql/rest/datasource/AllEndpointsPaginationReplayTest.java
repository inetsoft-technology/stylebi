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
package inetsoft.uql.rest.datasource;

import inetsoft.test.*;
import inetsoft.uql.rest.AbstractRestDataSource;
import inetsoft.uql.rest.json.EndpointJsonQuery;
import inetsoft.uql.rest.json.RestQuotaManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * For every paged endpoint of every data source, generates two pages from the endpoint's own
 * pagination settings (see {@link PaginationReplay}) and checks that the query reads both and
 * stops. Endpoints whose settings are not supported by the generator (a total count or has-next
 * value in a header, for example) are not checked.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, EndpointsTestConfig.class,
               SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AllEndpointsPaginationReplayTest {
   /**
    * OFFSET paging that starts at offset 1 instead of 0: HubSpot #81836, ServiceNow #80276.
    */
   private static final Set<String> OFFSET_STARTS_AT_ONE =
      Set.of("HubSpotQuery", "ServiceNowQuery");

   /** OFFSET paging that never ends at an empty page; none since Bug #81958 and #81879. */
   private static final Set<String> NEVER_ENDS = Set.of();

   /**
    * Endpoints whose records are the first element of the response array, so the sweep's flat
    * pages give no table; SEOmonitorPaginationTest covers their paging.
    */
   private static final Set<String> NESTED_RECORDS =
      Set.of("SEOmonitorQuery/Ranks", "SEOmonitorQuery/Group Ranks");

   /**
    * YouTube Analytics has its own test; the Salesforce ones log in to the real service to build
    * their URL.
    */
   private static final Set<String> NOT_CONFIGURED =
      Set.of("YouTubeAnalyticsQuery", "SalesforceReportsQuery", "RemedyforceQuery");

   /** Setters known to throw when filled with a placeholder; a change here must be looked at. */
   private static final Set<String> EXPECTED_SKIPPED_SETTERS = Set.of();

   /** What a sweep found, besides the problems themselves. */
   private record Sweep(List<String> problems, Set<String> neverEndsHit, Set<String> skipped) {
   }

   /**
    * The sweep sends thousands of requests to a local fake server. Without this, the vendors'
    * requests-per-second limits (Xero 1, Asana 2.5, ...) make it run for over five minutes, and
    * the CI monitor kills the fork when no test class has finished for that long.
    */
   @BeforeAll
   static void disableRateLimit() {
      previousRateLimitFlag = System.getProperty(RestQuotaManager.DISABLE_RATE_LIMIT_PROPERTY);
      System.setProperty(RestQuotaManager.DISABLE_RATE_LIMIT_PROPERTY, "true");
   }

   @AfterAll
   static void restoreRateLimit() {
      if(previousRateLimitFlag == null) {
         System.clearProperty(RestQuotaManager.DISABLE_RATE_LIMIT_PROPERTY);
      }
      else {
         System.setProperty(RestQuotaManager.DISABLE_RATE_LIMIT_PROPERTY, previousRateLimitFlag);
      }
   }

   private static String previousRateLimitFlag;

   private static List<String> sweep(boolean offsetValues, Set<String> only) throws Exception {
      return sweepAll(offsetValues, only).problems();
   }

   private static Sweep sweepAll(boolean offsetValues, Set<String> only) throws Exception {
      List<String> problems = new ArrayList<>();
      Set<String> neverEndsHit = new TreeSet<>();
      Set<String> skipped = new TreeSet<>();
      int checked = 0;

      for(Class<?> type : EndpointDefinitionsTest.queryTypes()) {
         String simple = type.getSimpleName();

         if(NOT_CONFIGURED.contains(simple) || only != null && !only.contains(simple)) {
            continue;
         }

         EndpointJsonQuery<?> probe = EndpointDefinitionsTest.newQuery(type);

         for(String name : new ArrayList<>(probe.getEndpointMap().keySet())) {
            EndpointJsonQuery<?> query = EndpointDefinitionsTest.newQuery(type);
            DataSourceFiller.fillUrl(query.getDataSource());
            skipped.addAll(DataSourceFiller.fill(query.getDataSource(), Set.of()));
            String where = simple + "/" + name;
            boolean checkOffsets = offsetValues || !OFFSET_STARTS_AT_ONE.contains(simple);

            try {
               PaginationReplay.Result result = PaginationReplay.run(
                  (AbstractRestDataSource<?>) query.getDataSource(), query, name, checkOffsets);

               if(result == null) {
                  continue;
               }

               checked++;

               for(String problem : result.problems()) {
                  problems.add(where + " [" + result.type() + "]: " + problem);
               }
            }
            catch(Throwable e) {
               String message = String.valueOf(e);

               if(NEVER_ENDS.contains(simple) && message.contains("does not stop")) {
                  neverEndsHit.add(simple);
                  continue;
               }

               if(NESTED_RECORDS.contains(where) && message.contains("returned no table")) {
                  continue;
               }

               problems.add(where + ": " + message.substring(0, Math.min(250, message.length())));
            }
         }
      }

      if(only == null) {
         assertTrue(checked > 400, "only " + checked + " endpoints were checked");
      }

      return new Sweep(problems, neverEndsHit, skipped);
   }

   @Test
   @Order(1)
   void everyPagedEndpointReadsAllGeneratedPagesAndStops() throws Exception {
      Sweep sweep = sweepAll(false, null);
      assertTrue(sweep.problems().isEmpty(), sweep.problems().size() + " problem(s):\n" +
                 String.join("\n", sweep.problems()));
      assertEquals(EXPECTED_SKIPPED_SETTERS, sweep.skipped(), "setters that could not be set");

      Set<String> stale = new TreeSet<>(NEVER_ENDS);
      stale.removeAll(sweep.neverEndsHit());
      assertTrue(stale.isEmpty(), "no longer fail to stop, remove from NEVER_ENDS and enable " +
                 "their tests: " + stale);
   }

   // An exemption must go when its bug is fixed, otherwise the sweep stays blind to the source.
   // This runs after the full sweep: replaying the failing OFFSET sources first slows it down.
   @Test
   @Order(2)
   void offsetStartsAtOneExemptionsAreStillNeeded() throws Exception {
      Set<String> failing = new TreeSet<>();

      for(String problem : sweep(true, OFFSET_STARTS_AT_ONE)) {
         int slash = problem.indexOf('/');
         assertTrue(slash > 0, "unexpected problem format, no 'source/endpoint' prefix: " + problem);
         failing.add(problem.substring(0, slash));
      }

      Set<String> stale = new TreeSet<>(OFFSET_STARTS_AT_ONE);
      stale.removeAll(failing);
      assertTrue(stale.isEmpty(), "OFFSET paging is now zero-based, remove from " +
                 "OFFSET_STARTS_AT_ONE and enable offsetPagingStartsAtZero: " + stale);
   }

   // OFFSET paging is zero-based here: the first record is at offset 0
   @Test
   @Disabled("Bug #82126 (HubSpot), #82127 (ServiceNow), #82124 (TeamDesk): OFFSET paging starts at offset 1 and skips the first record; already fixed in 13.8 as #81836, #80276, #81880, not yet in 2.1.0, enable when the fix reaches main")
   void offsetPagingStartsAtZero() throws Exception {
      List<String> problems = sweep(true, OFFSET_STARTS_AT_ONE);
      assertTrue(problems.isEmpty(), problems.size() + " problem(s):\n" +
                 String.join("\n", problems));
   }
}
