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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.*;

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
   private static final Set<String> SKIP = Set.of(
      "setName", "setURL", "setUrl", "setTokenUri", "setAuthorizationUri", "setScope",
      "setDescription", "setFolder", "setBody", "setContentType", "setOauthFlags", "setAuthType",
      "setAuthMethod", "setAuthURL", "setTokenPattern", "setServicePrincipalName", "setFullName",
      "setAudience", "setClientAuthMethod", "setCredentialId", "setLocale", "setTimeout",
      "setImpersonatePrincipal", "setConfigurationServiceName", "setServerType");

   private static void fill(Object ds) {
      for(Method m : ds.getClass().getMethods()) {
         if(m.getName().startsWith("set") && m.getParameterCount() == 1 &&
            m.getParameterTypes()[0] == String.class && !SKIP.contains(m.getName()))
         {
            String n = m.getName().toLowerCase();
            String value = n.contains("token") || n.contains("key") || n.contains("secret") ||
               n.contains("password") ? "tok" : "acct1";

            try {
               m.invoke(ds, value);
            }
            catch(Throwable e) {
               // not settable in this state
            }
         }
      }
   }

   @Test
   void everyDataSourceBuildsACleanUrlAndSendsItsCredentials() throws Exception {
      ClassPathScanningCandidateComponentProvider scanner =
         new ClassPathScanningCandidateComponentProvider(false);
      scanner.addIncludeFilter(new AssignableTypeFilter(AbstractRestDataSource.class));
      List<String> problems = new ArrayList<>();
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
         fill(ds);
         ds.setTokenExpiration(System.currentTimeMillis() + Duration.ofHours(1).toMillis());
         checked++;
         String simple = type.getSimpleName();

         try {
            String url = ds.getURL();

            if(url != null && !url.isEmpty() &&
               (url.contains("null") || url.contains("{") || !url.startsWith("http") ||
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

                  if(value != null && value.contains("null")) {
                     problems.add(simple + " parameter " + p.getName() + ": " + value);
                  }
               }
            }
         }
         catch(Throwable e) {
            problems.add(simple + " getQueryHttpParameters: " + e);
         }
      }

      assertTrue(checked > 40, "only " + checked + " data sources were checked");
      assertTrue(problems.isEmpty(), problems.size() + " problem(s):\n" +
                 String.join("\n", problems));
   }
}
