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
 * Issue #77078: an EM group edit whose body identifies a different group (or organization) than
 * the permission-checked path is rejected by UserTreeService.editGroup. This pins the HTTP side of
 * that rejection: the request reaches the real controller and service, and the EM exception
 * handler turns the rejection into a 403, not a 500.
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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("core")
class GroupControllerEditGroupOrgStatusTest {
   @Test
   void editGroup_bodyOrgDiffersFromPathOrg_returnsForbidden() throws Exception {
      IdentityService identityService = mock(IdentityService.class);
      IdentityThemeService themeService = mock(IdentityThemeService.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      UserTreeService service = new UserTreeService(
         mock(AuthenticationProviderService.class), mock(SystemAdminService.class),
         identityService, null, securityEngine, themeService, null, null,
         mock(DataCycleManager.class), null, null, mock(IndexedStorage.class), null, null, null,
         null, null);
      MockMvc mvc = MockMvcBuilders.standaloneSetup(new GroupController(service))
         // WebConfig.configurePathMatch sets a UrlPathHelper, which selects AntPathMatcher matching
         .setPatternParser(null)
         .setCustomArgumentResolvers(new DecodePathVariableResolver())
         .setControllerAdvice(new AdminExceptionHandler(mock(LogManager.class)))
         .build();
      // the uri template encodes the ';' of the key, as the EM client does
      String pathGroup = Tool.byteEncode(new IdentityID("sales", "organizationA").convertToKey());

      mvc.perform(post("/api/em/security/providers/Primary/groups/{group}/", pathGroup)
                     .contentType(MediaType.APPLICATION_JSON)
                     .accept(MediaType.APPLICATION_JSON)
                     .content("{\"name\":\"sales\",\"oldName\":\"sales\"," +
                                 "\"organization\":\"organizationB\",\"members\":[]}"))
         .andExpect(status().isForbidden())
         .andExpect(jsonPath("$.type").value("SecurityException"));

      verifyNoInteractions(identityService, themeService, securityEngine);
   }
}
