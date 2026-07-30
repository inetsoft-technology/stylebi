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
package inetsoft.web.admin.ai;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the request-mapping prefix of the admin-chat endpoints.
 *
 * <p>The wiz-services broker calls these endpoints server-to-server, carrying the operator's SSO
 * bearer token and no browser session. That makes the {@code /api/wiz/**} prefix load-bearing
 * rather than cosmetic:
 *
 * <ul>
 *   <li>{@code WizServiceAuthenticationFilter} validates the bearer JWT only when
 *       {@code isWizRequest()} holds — the path matches {@code /api/wiz/**}, or a
 *       {@code wiz_auth} cookie is present, which a server-to-server caller does not have.
 *       Off that prefix the token is ignored and the request falls through to session auth.</li>
 *   <li>{@code CSRFFilter} exempts {@code /api/wiz/**}; every other {@code /api/**} path requires
 *       an {@code X-XSRF-TOKEN} that the broker has no way to obtain.</li>
 * </ul>
 *
 * <p>Moving these mappings off the prefix breaks the broker asymmetrically — loudly for the POSTs
 * (CSRF 403) and silently for the GETs (CSRF-safe methods), which is the harder failure to
 * diagnose. {@code AdminAiEndpointPrefixTest} is the executable proof of both filter behaviours;
 * this test pins the mappings that depend on them.
 */
@Tag("core")
class AdminAiControllerMappingTest {
   @Test
   void everyEndpointLivesUnderTheWizPrefix() {
      List<String> paths = postMappingPaths(AdminAiController.class);
      assertFalse(paths.isEmpty(), "expected @PostMapping annotations on AdminAiController");

      for(String path : paths) {
         assertTrue(path.startsWith(WIZ_PREFIX), "endpoint '" + path + "' must live under '" +
            WIZ_PREFIX + "' or the wiz-services broker cannot authenticate to it");
      }
   }

   @Test
   void mappingsMatchTheFrozenContract() {
      assertEquals(
         Set.of("/api/wiz/v1/admin/backup",
                "/api/wiz/v1/admin/restore",
                "/api/wiz/v1/admin/preview",
                "/api/wiz/v1/admin/apply"),
         new HashSet<>(postMappingPaths(AdminAiController.class)));
   }

   @Test
   void singlePropertyChangeEndpointIsNotExposed() {
      // POST /change bypassed the preview/apply review gate entirely. AdminChangeService.applyChange
      // remains as the internal primitive, but it must not be reachable over HTTP.
      assertFalse(postMappingPaths(AdminAiController.class).stream()
         .anyMatch(path -> path.endsWith("/change")),
         "POST /change must not be mapped: it bypasses the plan-hash review gate");
   }

   private static List<String> postMappingPaths(Class<?> controller) {
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
