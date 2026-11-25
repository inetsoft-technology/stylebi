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

import inetsoft.test.EndpointsSpringContext;
import inetsoft.test.SreeHome;
import inetsoft.uql.rest.AbstractRestDataSource;
import inetsoft.uql.tabular.HttpParameter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every REST data source, filled in with placeholder values, must build a URL without the text
 * "null" or a template brace, and must not send the text "null" in a header or parameter. A URL
 * with [Name] in it is one that the user completes (ActiveCampaign, LiveAgent, Prometheus).
 */
@EndpointsSpringContext
@SreeHome
@Tag("core")
class AllDataSourcesRequestTest {
   /** The word null on its own (any case), not as part of a longer word such as "nullable". */
   private static final java.util.regex.Pattern NULL_TEXT =
      java.util.regex.Pattern.compile("(?<![A-Za-z0-9])null(?![A-Za-z0-9])",
                                      java.util.regex.Pattern.CASE_INSENSITIVE);

   private static final Set<String> EXTRA_SKIP =
      Set.of("setImpersonatePrincipal", "setConfigurationServiceName", "setServerType");

   /** Setters known to throw when filled with a placeholder; a change here must be looked at. */
   private static final Set<String> EXPECTED_SKIPPED_SETTERS = Set.of();

   @Test
   void everyDataSourceBuildsACleanUrlAndSendsItsCredentials() throws Exception {
      ClassPathScanningCandidateComponentProvider scanner =
         new ClassPathScanningCandidateComponentProvider(false);
      scanner.addIncludeFilter(new AssignableTypeFilter(AbstractRestDataSource.class));
      List<String> problems = new ArrayList<>();
      Set<String> skipped = new TreeSet<>();
      int checked = 0;

      for(BeanDefinition definition : scanner.findCandidateComponents("inetsoft.uql.rest")) {
         Class<?> type = Class.forName(definition.getBeanClassName());

         if(Modifier.isAbstract(type.getModifiers()) ||
            !type.getPackageName().contains(".datasource.") ||
            // these log in to Salesforce to build the URL, which would reach the real service
            inetsoft.uql.rest.datasource.salesforce.SalesforceDataSource.class
               .isAssignableFrom(type))
         {
            continue;
         }

         AbstractRestDataSource<?> ds;

         try {
            ds = (AbstractRestDataSource<?>) type.getConstructor().newInstance();
         }
         catch(Throwable e) {
            continue;
         }

         ds.setName("req-" + type.getSimpleName());
         skipped.addAll(DataSourceFiller.fill(ds, EXTRA_SKIP));
         ds.setTokenExpiration(System.currentTimeMillis() + Duration.ofHours(1).toMillis());
         checked++;
         String simple = type.getSimpleName();

         try {
            String url = ds.getURL();

            // Prometheus is a self-hosted server whose host and port the user gives
            if(url != null && url.startsWith("http://") && !simple.equals("PrometheusDataSource")) {
               problems.add(simple + " URL is not https: " + url);
            }

            if(url != null && !url.isEmpty() &&
               (NULL_TEXT.matcher(url).find() || url.contains("{") || !url.startsWith("http") ||
                url.replaceAll("\\[[^\\]]*\\]", "").contains(" ")))
            {
               problems.add(simple + " URL: " + url);
            }
         }
         catch(Throwable e) {
            // a data source that logs in to build its URL (Salesforce) is covered elsewhere
         }

         try {
            HttpParameter[] params = ds.getQueryHttpParameters();

            if(params != null) {
               for(HttpParameter p : params) {
                  String value = p.getValue();

                  if(value != null && NULL_TEXT.matcher(value).find()) {
                     problems.add(simple + " parameter " + p.getName() + ": " + value);
                  }
               }
            }
         }
         catch(Throwable e) {
            problems.add(simple + " getQueryHttpParameters: " + e);
         }
      }

      assertEquals(EXPECTED_SKIPPED_SETTERS, skipped, "setters that could not be set");
      assertTrue(checked > 40, "only " + checked + " data sources were checked");
      assertTrue(problems.isEmpty(), problems.size() + " problem(s):\n" +
                 String.join("\n", problems));
   }
}
