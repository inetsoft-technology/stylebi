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
 * Bug #77211: POST /api/em/security/users/create-organization/{provider} is gated only by the
 * EM component settings/security/users, which org admins hold, and
 * UserTreeService.createOrganization() never checked for a site admin. An org admin could create
 * organizations, and through the clone branch copy any other org (its users get a password the
 * caller chooses) and have their current org switched to the copy. In multi-tenant mode the
 * service must now reject a non site admin with a SecurityException (sanitized 403) before
 * anything is added, copied or switched.
 */

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.security.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.util.log.LogManager;
import inetsoft.web.admin.AdminExceptionHandler;
import inetsoft.web.admin.general.LocalizationSettingsService;
import inetsoft.web.admin.general.model.LocalizationSettingsModel;
import inetsoft.web.admin.security.AuthenticationProviderService;
import inetsoft.web.admin.security.IdentityService;
import inetsoft.web.factory.DecodePathVariableResolver;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.*;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("core")
class UserTreeServiceCreateOrganizationSiteAdminTest {
   @BeforeEach
   void setUp() {
      actionRecord = mock(ActionRecord.class);
      sUtilStatic = mockStatic(SUtil.class, withSettings().strictness(Strictness.LENIENT));
      sUtilStatic.when(() -> SUtil.getActionRecord(any(Principal.class), anyString(), any(), anyString()))
         .thenReturn(actionRecord);
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(true);
      auditStatic = mockStatic(Audit.class, withSettings().strictness(Strictness.LENIENT));
      auditStatic.when(Audit::getInstance).thenReturn(mock(Audit.class));
      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT));
      sreeEnvStatic.when(SreeEnv::getProperties).thenReturn(new Properties());

      orgManager = mock(OrganizationManager.class, withSettings().lenient());
      when(orgManager.getCurrentOrgID()).thenReturn(ORG_A);
      when(orgManager.getCurrentOrgID(any(Principal.class))).thenReturn(ORG_A);
      when(orgManager.isOrgAdmin(any(Principal.class))).thenReturn(true);
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(false);
      orgManagerStatic = mockStatic(OrganizationManager.class,
                                    withSettings().strictness(Strictness.LENIENT));
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      provider = mock(EditableAuthenticationProvider.class, withSettings().lenient());
      when(provider.getGroups()).thenReturn(new IdentityID[0]);
      when(provider.getRoles()).thenReturn(new IdentityID[0]);
      when(provider.getUsers()).thenReturn(new IdentityID[0]);
      when(provider.getOrganization(ORG_B)).thenAnswer(inv -> org(ORG_B, "Org B"));
      providerService = mock(AuthenticationProviderService.class, withSettings().lenient());
      when(providerService.getProviderByName("Primary")).thenReturn(provider);

      SecurityProvider securityProvider = mock(SecurityProvider.class, withSettings().lenient());
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[] { HOST, ORG_A, ORG_B });
      when(securityProvider.getOrganizationNames())
         .thenReturn(new String[] { "Host Organization", "Org A", "Org B" });
      SecurityEngine securityEngine = mock(SecurityEngine.class, withSettings().lenient());
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);

      LocalizationSettingsService localizationService =
         mock(LocalizationSettingsService.class, withSettings().lenient());
      when(localizationService.getModel()).thenReturn(mock(LocalizationSettingsModel.class));

      principal = mock(Principal.class, withSettings().lenient());
      when(principal.getName()).thenReturn(new IdentityID("adminA", ORG_A).convertToKey());

      service = new UserTreeService(
         providerService, null, mock(IdentityService.class), localizationService, securityEngine,
         mock(IdentityThemeService.class), null, null, null, mock(LicenseManager.class), null, null,
         mock(CustomThemesManager.class), null, null, null, null);
   }

   @AfterEach
   void tearDown() {
      sUtilStatic.close();
      auditStatic.close();
      sreeEnvStatic.close();
      orgManagerStatic.close();
   }

   @Test
   void orgAdmin_plainCreate_rejectedAndNothingAdded() {
      assertThrows(java.lang.SecurityException.class, () ->
         service.createOrganization(null, "Primary", null, null, principal, null));

      verify(provider, never()).addOrganization(any());
      verify(orgManager, never()).setCurrentOrgID(any());
      // the denied attempt is audited as a failure
      verify(actionRecord).setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
   }

   @Test
   void orgAdmin_cloneOtherOrg_rejectedAndNothingCopiedOrSwitched() {
      assertThrows(java.lang.SecurityException.class, () ->
         service.createOrganization(ORG_B, "Primary", null, null, principal, STRONG_PASSWORD));

      verify(provider, never()).copyOrganization(any(), any(), any(), any(), any(), any(), any(),
                                                 anyBoolean(), any());
      verify(provider, never()).addOrganization(any());
      verify(orgManager, never()).setCurrentOrgID(any());
   }

   @Test
   void siteAdmin_plainCreate_proceeds() {
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(true);

      EditOrganizationPaneModel model =
         service.createOrganization(null, "Primary", "New Org", "neworg", principal, null);

      assertNotNull(model);
      assertEquals("neworg", model.id());
      verify(provider).addOrganization(any());
      verify(orgManager).setCurrentOrgID("neworg");
   }

   @Test
   void siteAdmin_clone_proceeds() {
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(true);
      when(provider.getOrganization("neworg")).thenReturn(null, org("neworg", "New Org"));

      EditOrganizationPaneModel model =
         service.createOrganization(ORG_B, "Primary", "New Org", "neworg", principal, STRONG_PASSWORD);

      assertNotNull(model);
      verify(provider).copyOrganization(any(), eq("neworg"), any(), any(), any(), any(), any(),
                                        eq(false), eq(STRONG_PASSWORD));
   }

   @Test
   void multiTenantOff_nonSiteAdmin_proceeds() {
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(false);

      EditOrganizationPaneModel model =
         service.createOrganization(null, "Primary", "New Org", "neworg", principal, null);

      assertNotNull(model);
      verify(provider).addOrganization(any());
   }

   @Test
   void orgAdmin_createOrganizationEndpoint_returnsSanitizedForbidden() throws Exception {
      MockMvc mvc = MockMvcBuilders
         .standaloneSetup(new OrganizationController(service, null, providerService, null))
         .setPatternParser(null)
         .setCustomArgumentResolvers(new DecodePathVariableResolver())
         .setControllerAdvice(new AdminExceptionHandler(mock(LogManager.class)))
         .build();

      mvc.perform(post("/api/em/security/users/create-organization/Primary")
                     .principal(principal)
                     .contentType(MediaType.APPLICATION_JSON)
                     .accept(MediaType.APPLICATION_JSON)
                     .content("{\"parentGroup\":\"" + ORG_B + "\",\"defaultPassword\":\"" +
                                 STRONG_PASSWORD + "\"}"))
         .andExpect(status().isForbidden())
         .andExpect(jsonPath("$.type").value("SecurityException"))
         .andExpect(jsonPath("$.message", not(containsString(ORG_B))));

      verify(provider, never()).copyOrganization(any(), any(), any(), any(), any(), any(), any(),
                                                 anyBoolean(), any());
      verify(provider, never()).addOrganization(any());
      verify(orgManager, never()).setCurrentOrgID(any());
   }

   private static FSOrganization org(String id, String name) {
      FSOrganization org = new FSOrganization(id);
      org.setName(name);
      return org;
   }

   private static final String HOST = "host-org";
   private static final String ORG_A = "orga";
   private static final String ORG_B = "orgb";
   private static final String STRONG_PASSWORD = "Str0ng!Passw0rd";

   private ActionRecord actionRecord;
   private EditableAuthenticationProvider provider;
   private AuthenticationProviderService providerService;
   private OrganizationManager orgManager;
   private Principal principal;
   private UserTreeService service;
   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<Audit> auditStatic;
   private MockedStatic<SreeEnv> sreeEnvStatic;
   private MockedStatic<OrganizationManager> orgManagerStatic;
}
