/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.uql.odata;

import inetsoft.test.*;
import inetsoft.uql.tabular.*;
import inetsoft.uql.util.Config;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #78242: the data source listing and editor build the OData layout with
 * LayoutCreator.createLayout(), which threw "Property missing for view: user" when the
 * Introspector reported ODataDataSource's own unannotated override of an OAuthDataSource default
 * accessor (JDK-8347826, OpenJDK 21.0.9+). Note: this only catches the regression on a JDK that
 * includes JDK-8347826; on older JDKs the annotated interface default is reported and this
 * passes even without the fix.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, ODataDataSourceLayoutTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
class ODataDataSourceLayoutTest {
   @Test
   void listingLayoutIsCreated() {
      TabularView layout = new LayoutCreator().createLayout(new ODataDataSource());
      Set<String> editors = new HashSet<>();
      collectEditors(layout, editors);

      assertTrue(editors.containsAll(
                    List.of("user", "password", "accessToken", "refreshToken", "tokenExpiration")),
                 "Java " + System.getProperty("java.version") + ": " + editors);
   }

   private static void collectEditors(TabularView view, Set<String> editors) {
      if(view.getEditor() != null) {
         editors.add(view.getValue());
      }

      if(view.getViews() != null) {
         for(TabularView child : view.getViews()) {
            collectEditors(child, editors);
         }
      }
   }

   @Configuration
   static class Beans {
      // the layout reads the labels through Config, a missing bundle keeps the default labels
      @Bean
      public Config config() {
         return mock(Config.class);
      }

      // new ODataDataSource() creates its credential through the CredentialService bean
      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }
   }
}
