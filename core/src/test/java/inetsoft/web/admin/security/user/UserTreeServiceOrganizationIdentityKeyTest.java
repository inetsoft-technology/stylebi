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
 * Bug #78046: an organization listed by GET /api/em/security/providers/{p}/identities/4 must open
 * in the org pane (GET .../organization/{key}/) when its key is passed back. The list was built as
 * IdentityID(id, id), and getOrganizationModel rejects a key whose name is not the org's stored
 * name, so the default host-org and any renamed org failed with "Organization ... doesn't exist".
 *
 * Drives the real chain: AuthenticationProviderService.getFilteredOrganizations -> convertToKey ->
 * IdentityID.getIdentityIDFromKey (what OrganizationController.getOrganization does) -> the real
 * UserTreeService.getOrganizationModel, with both services sharing one provider.
 */

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.security.*;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.ThreadContext;
import inetsoft.web.admin.general.LocalizationSettingsService;
import inetsoft.web.admin.general.model.LocalizationSettingsModel;
import inetsoft.web.admin.security.AuthenticationProviderService;
import inetsoft.web.admin.security.IdentityService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UserTreeServiceOrganizationIdentityKeyTest {
   @BeforeEach
   void setUp() {
      sUtilStatic = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(true);

      XSessionService mockSessionService = mock(XSessionService.class);
      lenient().when(mockSessionService.createSessionID(anyString(), any()))
         .thenAnswer(inv -> inv.getArgument(0, String.class) + "-session");
      xSessionServiceStatic = mockStatic(XSessionService.class);
      xSessionServiceStatic.when(XSessionService::getService).thenReturn(mockSessionService);

      AuthenticationProvider provider = mock(AuthenticationProvider.class, withSettings().lenient());
      when(provider.getGroups()).thenReturn(new IdentityID[0]);
      when(provider.getOrganizationIDs()).thenReturn(new String[] { HOST_ORG, RENAMED_ORG, SAME_ORG });
      when(provider.getOrganization(HOST_ORG)).thenReturn(org(HOST_ORG, "Host Organization"));
      when(provider.getOrganization(RENAMED_ORG)).thenReturn(org(RENAMED_ORG, "Renamed"));
      when(provider.getOrganization(SAME_ORG)).thenReturn(org(SAME_ORG, SAME_ORG));

      SecurityProvider securityProvider = mock(SecurityProvider.class, withSettings().lenient());
      when(securityProvider.checkPermission(any(Principal.class), any(ResourceType.class),
                                            anyString(), any(ResourceAction.class)))
         .thenReturn(true);
      SecurityEngine securityEngine = mock(SecurityEngine.class, withSettings().lenient());
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);

      providerService = spy(new AuthenticationProviderService(securityEngine, null, null, null));
      doReturn(provider).when(providerService).getProviderByName(PROVIDER);

      IdentityService identityService = mock(IdentityService.class, withSettings().lenient());
      when(identityService.getPermission(any(IdentityID.class), any(ResourceType.class),
                                          any(), any())).thenReturn(new ArrayList<>());
      when(identityService.getIdentityInfo(any(), anyInt(), any()))
         .thenReturn(new IdentityInfo());

      LocalizationSettingsService localizationSettingsService =
         mock(LocalizationSettingsService.class, withSettings().lenient());
      when(localizationSettingsService.getModel())
         .thenReturn(LocalizationSettingsModel.builder().build());

      CustomThemesManager customThemesManager =
         mock(CustomThemesManager.class, withSettings().lenient());
      when(customThemesManager.getCustomThemes()).thenReturn(new HashSet<>());

      service = new UserTreeService(
         providerService, null, identityService, localizationSettingsService, securityEngine,
         null, null, null, null, null, null, null, customThemesManager, null, null, null, null);

      principal = new SRPrincipal(new IdentityID("boss", HOST_ORG), new IdentityID[0],
                                  new String[0], HOST_ORG, 1L);
      ThreadContext.setContextPrincipal(principal);
      ThreadContext.setPrincipal(principal);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
      ThreadContext.setPrincipal(null);
      xSessionServiceStatic.close();
      sUtilStatic.close();
   }

   @Test
   void everyListedOrganizationKeyOpensInTheOrgPane() {
      List<IdentityID> listed = providerService.getFilteredOrganizations(PROVIDER, principal);
      assertEquals(3, listed.size(), listed.toString());

      for(IdentityID entry : listed) {
         IdentityID key = IdentityID.getIdentityIDFromKey(entry.convertToKey());
         EditOrganizationPaneModel model = assertDoesNotThrow(
            () -> service.getOrganizationModel(PROVIDER, key, principal, false, null),
            "org pane rejected listed key " + entry.convertToKey());

         assertEquals(entry.orgID, model.id());
         assertEquals(entry.name, model.name());
      }
   }

   private static FSOrganization org(String id, String name) {
      FSOrganization org = new FSOrganization(id);
      org.setName(name);
      return org;
   }

   private static final String PROVIDER = "Primary";
   private static final String HOST_ORG = "host-org";
   private static final String RENAMED_ORG = "orga";
   private static final String SAME_ORG = "same";

   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<XSessionService> xSessionServiceStatic;
   private AuthenticationProviderService providerService;
   private UserTreeService service;
   private SRPrincipal principal;
}
