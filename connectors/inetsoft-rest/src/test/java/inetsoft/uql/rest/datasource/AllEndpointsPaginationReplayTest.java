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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

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
class AllEndpointsPaginationReplayTest {
   /** OFFSET paging that starts at offset 1 instead of 0: HubSpot #81836, ServiceNow #80276. */
   private static final Set<String> OFFSET_STARTS_AT_ONE =
      Set.of("HubSpotQuery", "ServiceNowQuery");

   /** OFFSET paging that never ends at an empty page: TeamDesk #81879, SEOmonitor #81958. */
   private static final Set<String> NEVER_ENDS = Set.of("TeamDeskQuery", "SEOmonitorQuery");

   /**
    * YouTube Analytics has its own test; the Salesforce ones log in to the real service to build
    * their URL.
    */
   private static final Set<String> NOT_CONFIGURED = Set.of("YouTubeAnalyticsQuery", "SalesforceReportsQuery", "RemedyforceQuery");

   private static final Set<String> SKIP = Set.of(
      "setName", "setURL", "setUrl", "setTokenUri", "setAuthorizationUri", "setScope",
      "setDescription", "setFolder", "setBody", "setContentType", "setOauthFlags", "setAuthType",
      "setAuthMethod", "setAuthURL", "setTokenPattern", "setServicePrincipalName", "setFullName",
      "setAudience", "setClientAuthMethod", "setCredentialId", "setLocale", "setTimeout");

   /** Gives the data source placeholder values for its string properties. */
   private static void fill(Object dataSource) {
      if(dataSource == null) {
         return;
      }

      try {
         String url = (String) dataSource.getClass().getMethod("getURL").invoke(dataSource);

         if(url == null || url.isEmpty()) {
            // the user gives the whole URL (Jive)
            dataSource.getClass().getMethod("setURL", String.class)
               .invoke(dataSource, "https://acct1.example.com");
         }
         else if(url.contains("[")) {
            // the URL holds the account name as a placeholder
            dataSource.getClass().getMethod("setURL", String.class)
               .invoke(dataSource, url.replaceAll("\\[[^\\]]*\\]", "acct1"));
         }
      }
      catch(Throwable e) {
         // no URL placeholder
      }

      for(java.lang.reflect.Method m : dataSource.getClass().getMethods()) {
         if(m.getName().startsWith("set") && m.getParameterCount() == 1 &&
            m.getParameterTypes()[0] == String.class && !SKIP.contains(m.getName()))
         {
            String n = m.getName().toLowerCase();
            String value = n.contains("token") || n.contains("key") || n.contains("secret") ||
               n.contains("password") ? "tok" : "acct1";

            try {
               m.invoke(dataSource, value);
            }
            catch(Throwable e) {
               // not settable in this state
            }
         }
      }
   }

   private static List<String> sweep(boolean offsetValues, Set<String> only) throws Exception {
      List<String> problems = new ArrayList<>();
      int checked = 0;

      for(Class<?> type : EndpointDefinitionsTest.queryTypes()) {
         String simple = type.getSimpleName();

         if(NOT_CONFIGURED.contains(simple) || only != null && !only.contains(simple)) {
            continue;
         }

         EndpointJsonQuery<?> probe = EndpointDefinitionsTest.newQuery(type);

         for(String name : new ArrayList<>(probe.getEndpointMap().keySet())) {
            EndpointJsonQuery<?> query = EndpointDefinitionsTest.newQuery(type);
            fill(query.getDataSource());
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
                  continue;
               }

               problems.add(where + ": " + message.substring(0, Math.min(250, message.length())));
            }
         }
      }

      if(only == null) {
         assertTrue(checked > 400, "only " + checked + " endpoints were checked");
      }

      return problems;
   }

   @Test
   void everyPagedEndpointReadsAllGeneratedPagesAndStops() throws Exception {
      List<String> problems = sweep(false, null);
      assertTrue(problems.isEmpty(), problems.size() + " problem(s):\n" +
                 String.join("\n", problems));
   }

   // OFFSET paging is zero-based here: the first record is at offset 0
   @Test
   @Disabled("Bug #81836 (HubSpot), #80276 (ServiceNow): OFFSET paging starts at offset 1 and skips the first record; enable when it is fixed")
   void offsetPagingStartsAtZero() throws Exception {
      List<String> problems = sweep(true, OFFSET_STARTS_AT_ONE);
      assertTrue(problems.isEmpty(), problems.size() + " problem(s):\n" +
                 String.join("\n", problems));
   }
}
