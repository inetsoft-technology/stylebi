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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A data source property that names where the service is (domain, subdomain, instance, account,
 * shop, region, host, site, tenant) must end up in the URL.
 */
@EndpointsSpringContext
@SreeHome
@Tag("core")
class UrlPropertiesAllDataSourcesTest {
   private static final Pattern WHERE = Pattern.compile(
      "(?i)^set(Domain|Subdomain|Instance|AccountName|Shop|Region|Host|Site|Tenant|Server|Pod)" +
      "(Name|Id)?$");

   @Test
   void locationPropertiesAppearInTheUrl() throws Exception {
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
            if(!WHERE.matcher(m.getName()).matches() || m.getParameterCount() != 1 ||
               m.getParameterTypes()[0] != String.class)
            {
               continue;
            }

            try {
               AbstractRestDataSource<?> ds =
                  (AbstractRestDataSource<?>) type.getConstructor().newInstance();
               ds.setName("url-" + type.getSimpleName());
               m.invoke(ds, "sentinel123");
               checked++;
               String url = ds.getURL();

               if(url == null || !url.contains("sentinel123")) {
                  problems.add(type.getSimpleName() + "." + m.getName() + ": URL is " + url);
               }
            }
            catch(Throwable e) {
               // not settable or no URL in this state
            }
         }
      }

      assertTrue(checked > 8, "only " + checked + " properties were checked");
      assertTrue(problems.isEmpty(), problems.size() + " problem(s):\n" +
                 String.join("\n", problems));
   }
}
