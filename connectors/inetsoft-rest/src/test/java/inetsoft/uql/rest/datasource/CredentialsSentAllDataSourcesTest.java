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
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A key or token that the user enters must be sent: it has to appear in a header or query
 * parameter of the data source (the user name and password of Basic authentication are added by
 * the authenticator and are not looked at here).
 */
@EndpointsSpringContext
@SreeHome
@Tag("core")
class CredentialsSentAllDataSourcesTest {
   private static final Pattern SECRET = Pattern.compile(
      "^set(ApiKey|ApiToken|AccessToken|AuthorizationToken|ApiSecret)$");

   @Test
   void everyKeyAndTokenIsSent() throws Exception {
      ClassPathScanningCandidateComponentProvider scanner =
         new ClassPathScanningCandidateComponentProvider(false);
      scanner.addIncludeFilter(new AssignableTypeFilter(AbstractRestDataSource.class));
      List<String> problems = new ArrayList<>();
      int checked = 0;

      for(BeanDefinition definition : scanner.findCandidateComponents("inetsoft.uql.rest")) {
         Class<?> type = Class.forName(definition.getBeanClassName());

         if(Modifier.isAbstract(type.getModifiers()) ||
            !type.getPackageName().contains(".datasource.") ||
            inetsoft.uql.rest.datasource.salesforce.SalesforceDataSource.class
               .isAssignableFrom(type))
         {
            continue;
         }

         for(Method m : type.getMethods()) {
            if(!SECRET.matcher(m.getName()).matches() || m.getParameterCount() != 1 ||
               m.getParameterTypes()[0] != String.class)
            {
               continue;
            }

            try {
               AbstractRestDataSource<?> ds =
                  (AbstractRestDataSource<?>) type.getConstructor().newInstance();
               ds.setName("cred-" + type.getSimpleName());

               try {
                  // InfluxDB sends its token only with the token scheme
                  type.getMethod("setAuthScheme", String.class).invoke(ds, "TOKEN");
               }
               catch(NoSuchMethodException e) {
                  // no authentication scheme
               }

               m.invoke(ds, "SENT456");
               ds.setRefreshToken("SENT-REFRESH");
               ds.setTokenExpiration(System.currentTimeMillis() + Duration.ofHours(1).toMillis());
               checked++;
               boolean sent = false;
               HttpParameter[] params = ds.getQueryHttpParameters();

               if(params != null) {
                  for(HttpParameter p : params) {
                     sent |= p.getValue() != null && p.getValue().contains("SENT456");
                  }
               }

               HttpParameter[] request = ds instanceof inetsoft.uql.rest.datasource.graphql.AbstractGraphQLDataSource<?> g ?
                  g.getRequestParameters() : null;

               if(request != null) {
                  for(HttpParameter p : request) {
                     sent |= p.getValue() != null && p.getValue().contains("SENT456");
                  }
               }

               if(!sent && ds.getAccessToken() != null && !"SENT456".equals(ds.getAccessToken()) &&
                  !"SENT456".equals(ds.getUser()) && !"SENT456".equals(ds.getPassword()))
               {
                  problems.add(type.getSimpleName() + "." + m.getName() + ": not sent");
               }
               else if(!sent && !"SENT456".equals(ds.getUser()) &&
                       !"SENT456".equals(ds.getPassword()) && !"SENT456".equals(ds.getAccessToken()))
               {
                  problems.add(type.getSimpleName() + "." + m.getName() + ": not sent");
               }
            }
            catch(Throwable e) {
               // not settable in this state
            }
         }
      }

      assertTrue(checked > 20, "only " + checked + " properties were checked");
      assertTrue(problems.isEmpty(), problems.size() + " problem(s):\n" +
                 String.join("\n", problems));
   }
}
