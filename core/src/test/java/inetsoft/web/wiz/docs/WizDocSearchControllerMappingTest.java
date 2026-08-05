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
package inetsoft.web.wiz.docs;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the request-mapping prefix of the doc-search endpoint.
 *
 * <p>The prefix is load-bearing, not convention: {@code WizServiceAuthenticationFilter} validates
 * the plugin's bearer token only under {@code /api/wiz/**}, and {@code CSRFFilter} exempts only
 * that prefix. Moved elsewhere, the endpoint would not authenticate the plugin at all.</p>
 */
@Tag("core")
class WizDocSearchControllerMappingTest {
   @Test
   void everyEndpointLivesUnderTheWizPrefix() {
      List<String> paths = getMappingPaths(WizDocSearchController.class);
      assertFalse(paths.isEmpty(), "expected @PostMapping annotations on WizDocSearchController");

      for(String path : paths) {
         assertTrue(path.startsWith(WIZ_PREFIX), "endpoint '" + path + "' must live under '" +
            WIZ_PREFIX + "' or the plugin cannot authenticate to it");
      }
   }

   @Test
   void mappingsMatchThePluginContract() {
      assertEquals(Set.of("/api/wiz/v1/docs/search"),
                   new HashSet<>(getMappingPaths(WizDocSearchController.class)));
   }

   private static List<String> getMappingPaths(Class<?> controller) {
      List<String> paths = new ArrayList<>();

      for(Method method : controller.getDeclaredMethods()) {
         PostMapping mapping = method.getAnnotation(PostMapping.class);

         if(mapping != null) {
            paths.addAll(Arrays.asList(mapping.value()));
         }
      }

      return paths;
   }

   private static final String WIZ_PREFIX = "/api/wiz/";
}
