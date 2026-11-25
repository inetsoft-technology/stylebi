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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java's contract: objects that are equal have the same hash code. Two data sources that differ
 * in one text property are made, and if they are equal their hash codes must match.
 */
@EndpointsSpringContext
@SreeHome
@Tag("core")
class EqualsHashCodeContractTest {
   @Test
   void equalDataSourcesHaveEqualHashCodes() throws Exception {
      ClassPathScanningCandidateComponentProvider scanner =
         new ClassPathScanningCandidateComponentProvider(false);
      scanner.addIncludeFilter(new AssignableTypeFilter(AbstractRestDataSource.class));
      List<String> problems = new ArrayList<>();
      int checked = 0;

      for(BeanDefinition definition : scanner.findCandidateComponents("inetsoft.uql.rest")) {
         Class<?> type = Class.forName(definition.getBeanClassName());

         if(Modifier.isAbstract(type.getModifiers())) {
            continue;
         }

         Object base;

         try {
            base = type.getConstructor().newInstance();
         }
         catch(Throwable e) {
            continue;
         }

         checked++;

         for(Method m : type.getMethods()) {
            if(m.getName().equals("setName") || !m.getName().startsWith("set") || m.getParameterCount() != 1 ||
               m.getParameterTypes()[0] != String.class)
            {
               continue;
            }

            try {
               Object a = type.getConstructor().newInstance();
               Object b = type.getConstructor().newInstance();
               // a data source without a name hashes by identity
               ((AbstractRestDataSource<?>) a).setName("same");
               ((AbstractRestDataSource<?>) b).setName("same");
               m.invoke(a, "value-one");
               m.invoke(b, "value-two");

               if(a.equals(b) && a.hashCode() != b.hashCode()) {
                  problems.add(type.getSimpleName() + "." + m.getName() +
                               ": equal objects with different hash codes");
               }
            }
            catch(Throwable e) {
               // not settable in the default state
            }
         }
      }

      assertTrue(checked > 40, "only " + checked + " data sources were checked");
      assertTrue(problems.isEmpty(), problems.size() + " problem(s):\n" +
                 String.join("\n", problems));
   }

   @Test
   void zohoDataSourcesThatDifferInAccountDomainAreNotEqual() {
      inetsoft.uql.rest.datasource.zohocrm.ZohoCRMDataSource a =
         new inetsoft.uql.rest.datasource.zohocrm.ZohoCRMDataSource();
      inetsoft.uql.rest.datasource.zohocrm.ZohoCRMDataSource b =
         new inetsoft.uql.rest.datasource.zohocrm.ZohoCRMDataSource();
      a.setName("same");
      b.setName("same");
      a.setAccountDomain("https://accounts.zoho.com");
      b.setAccountDomain("https://accounts.zoho.com.eu");
      assertTrue(!a.equals(b) || a.hashCode() == b.hashCode());
      assertTrue(!a.equals(b));
   }
}
