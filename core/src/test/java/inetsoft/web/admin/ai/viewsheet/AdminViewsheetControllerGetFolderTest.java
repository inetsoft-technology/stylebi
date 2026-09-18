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
package inetsoft.web.admin.ai.viewsheet;

import inetsoft.web.admin.sheet.vs.ViewsheetService;
import inetsoft.sree.security.OrganizationManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * Regression test for the fix in 07-fix-r1-java.md: {@code GET .../viewsheets/folder} takes
 * {@code path} as a query parameter, not a {@code {path:.+}} path-variable segment.
 *
 * <p>Standalone {@link MockMvc} (no Spring context, matching {@code WizControllerErrorHandlerTest}'s
 * own precedent) -- real Spring MVC request dispatch/binding runs, but there is no real embedded
 * Tomcat connector underneath, so this does not, by itself, prove the original {@code
 * encodedSolidusHandling} rejection would not recur; it does prove the new query-parameter shape
 * correctly binds a slash-containing value end to end through Spring's own request processing,
 * including the {@code %2F}-encoded form the plugin's old (path-variable) request construction used,
 * which is exactly the byte sequence Tomcat rejected in the path but never restricts in a query
 * string.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class AdminViewsheetControllerGetFolderTest {
   @Mock private ViewsheetService viewsheetApiService;
   @Mock private ViewsheetFolderService folderService;
   @Mock private ViewsheetChangePlanService planService;
   @Mock private ViewsheetChangesetApplyService applyService;
   @Mock private OrganizationManager orgManager;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockMvc mvc;

   private static final java.security.Principal TEST_PRINCIPAL = () -> "test-user";

   @BeforeEach void setUp() {
      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.isSiteAdmin(TEST_PRINCIPAL)).thenReturn(true);

      mvc = standaloneSetup(
         new AdminViewsheetController(viewsheetApiService, folderService, planService, applyService))
         .setMessageConverters(new MappingJackson2HttpMessageConverter())
         .build();
   }

   @AfterEach void tearDown() {
      orgManagerStatic.close();
   }

   @Test void getFolderExtractsSlashContainingPathFromQueryParameter() throws Exception {
      when(folderService.getFolder("Examples/Old Folder", null))
         .thenReturn(new GetViewsheetFolderResult(true, "Examples/Old Folder", null, null, null));

      mvc.perform(get("/api/wiz/v1/admin/viewsheets/folder")
            .param("path", "Examples/Old Folder")
            .principal(TEST_PRINCIPAL)
            .header("Authorization", "Bearer test-token"))
         .andExpect(status().isOk())
         .andExpect(content().string(org.hamcrest.Matchers.containsString(
            "\"path\":\"Examples/Old Folder\"")));

      verify(folderService).getFolder("Examples/Old Folder", null);
   }

   /**
    * The exact byte sequence ({@code %2F}) the plugin's pre-fix request construction sent, now in
    * the query string rather than the path -- confirms Spring correctly percent-decodes it back to
    * a literal slash for a {@code @RequestParam}, unlike the path-segment shape this replaces.
    */
   @Test void getFolderAcceptsPercentEncodedSlashInQueryString() throws Exception {
      when(folderService.getFolder("Examples/Old Folder", null))
         .thenReturn(new GetViewsheetFolderResult(true, "Examples/Old Folder", null, null, null));

      mvc.perform(get(URI.create(
            "/api/wiz/v1/admin/viewsheets/folder?path=Examples%2FOld%20Folder"))
            .principal(TEST_PRINCIPAL)
            .header("Authorization", "Bearer test-token"))
         .andExpect(status().isOk())
         .andExpect(content().string(org.hamcrest.Matchers.containsString(
            "\"path\":\"Examples/Old Folder\"")));

      verify(folderService).getFolder("Examples/Old Folder", null);
   }

   @Test void getFolderNoLongerMapsTheOldPathVariableShape() throws Exception {
      mvc.perform(get("/api/wiz/v1/admin/viewsheets/folders/Examples%2FOld%20Folder")
            .header("Authorization", "Bearer test-token"))
         .andExpect(status().isNotFound());

      verifyNoInteractions(folderService);
   }
}
