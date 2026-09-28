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
package inetsoft.web.admin.security.user;

/*
 * Issue #77080: an EM group edit whose body names a different group or organization than the
 * permission-checked path group is rejected by UserTreeService.editGroup before anything is
 * written. This pins the HTTP side of that rejection through the real GroupController and
 * AdminExceptionHandler: the client gets a sanitized 403 (not a 500), and no identity,
 * permission, theme or provider call is made. (Adapted from PR #5657.)
 */

import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.security.*;
import inetsoft.util.IndexedStorage;
import inetsoft.util.Tool;
import inetsoft.util.log.LogManager;
import inetsoft.web.admin.AdminExceptionHandler;
import inetsoft.web.admin.security.AuthenticationProviderService;
import inetsoft.web.admin.security.IdentityService;
import inetsoft.web.factory.DecodePathVariableResolver;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("core")
class GroupControllerEditGroupMismatchHttpTest {
   private IdentityService identityService;
   private IdentityThemeService themeService;
   private SecurityEngine securityEngine;
   private AuthenticationProviderService providerService;
   private IndexedStorage indexedStorage;
   private MockMvc mvc;

   @BeforeEach
   void setUp() {
      identityService = mock(IdentityService.class);
      themeService = mock(IdentityThemeService.class);
      securityEngine = mock(SecurityEngine.class);
      providerService = mock(AuthenticationProviderService.class);
      indexedStorage = mock(IndexedStorage.class);
      UserTreeService service = new UserTreeService(
         providerService, mock(SystemAdminService.class), identityService, null, securityEngine,
         themeService, null, null, mock(DataCycleManager.class), null, null, indexedStorage,
         null, null, null, null, null);
      mvc = MockMvcBuilders.standaloneSetup(new GroupController(service))
         // WebConfig.configurePathMatch sets a UrlPathHelper, which selects AntPathMatcher matching
         .setPatternParser(null)
         .setCustomArgumentResolvers(new DecodePathVariableResolver())
         .setControllerAdvice(new AdminExceptionHandler(mock(LogManager.class)))
         .build();
   }

   @Test
   void bodyOrgDiffersFromPathOrg_returnsSanitizedForbiddenAndWritesNothing() throws Exception {
      postEdit(new IdentityID("sales", "organizationA"),
               "{\"name\":\"sales\",\"oldName\":\"sales\"," +
                  "\"organization\":\"organizationB\",\"members\":[]}");
   }

   @Test
   void bodyGroupDiffersFromPathGroup_returnsSanitizedForbiddenAndWritesNothing() throws Exception {
      postEdit(new IdentityID("sales", "organizationA"),
               "{\"name\":\"finance\",\"oldName\":\"finance\"," +
                  "\"organization\":\"organizationA\",\"members\":[]}");
   }

   private void postEdit(IdentityID pathGroup, String body) throws Exception {
      // the uri template encodes the ';' of the key, as the EM client does
      String pathKey = Tool.byteEncode(pathGroup.convertToKey());

      mvc.perform(post("/api/em/security/providers/Primary/groups/{group}/", pathKey)
                     .contentType(MediaType.APPLICATION_JSON)
                     .accept(MediaType.APPLICATION_JSON)
                     .content(body))
         .andExpect(status().isForbidden())
         .andExpect(jsonPath("$.type").value("SecurityException"))
         // sanitized: no group or organization names from the request are echoed back
         .andExpect(jsonPath("$.message", not(containsString("organization"))))
         .andExpect(jsonPath("$.message", not(containsString("sales"))))
         .andExpect(jsonPath("$.message", not(containsString("finance"))));

      verifyNoInteractions(identityService, themeService, securityEngine, providerService,
                           indexedStorage);
   }
}
