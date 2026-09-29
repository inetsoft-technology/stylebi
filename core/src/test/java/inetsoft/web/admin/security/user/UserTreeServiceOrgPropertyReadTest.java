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
 * Bug #77269: a site admin's GET of another organization's model returned properties: []
 * even though the org has org-scoped properties, and the same site admin saving that model
 * unchanged then cleared max.row.count/max.col.count/max.cell.size/max.user.count for that org.
 *
 * getOrganizationModel's property-scan loop already matches the persisted, fully-qualified key
 * (e.g. "inetsoft.org.s30b.max.row.count") by prefix against the TARGET org, but then re-reads it
 * through SreeEnv.getProperty(bareName, false, true) -- orgScope=true -- which re-resolves the
 * org from the CALLER's own current org (via PropertiesEngine.useAvailableOrgProperty ->
 * OrganizationManager.getCurrentOrgID() -> ThreadContext -> XPrincipal.getCurrentOrgId()), not
 * from the org actually being viewed. A site admin whose own current org differs from the org
 * they are viewing gets every property silently dropped; an org admin viewing their own org
 * happened to work by coincidence (caller org == target org). Because the EM client
 * (edit-identity-view.component.ts) echoes the GET's properties verbatim on Save,
 * editOrganization's already-correctly-scoped "absent from the posted list -> remove" logic then
 * nulls out every max.* property that was silently dropped from the read.
 *
 * This test drives the real SreeEnv/PropertiesEngine chain (not a static-mocked
 * OrganizationManager, which would only prove "the code calls runInOrgScope" without proving the
 * real org-resolution precedence still lands on the right key) through the actual, unmodified
 * getOrganizationModel method. Only XSessionService.getService() (needed to construct a real
 * XPrincipal) and SUtil.isMultiTenant() (an unrelated, orthogonal switch used by getOrgGroups) are
 * mocked; SreeEnv/PropertiesEngine/OrganizationManager/ThreadContext are all real.
 */

import inetsoft.sree.SreeEnv;
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
import inetsoft.web.admin.security.PropertyModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UserTreeServiceOrgPropertyReadTest {
   @BeforeEach
   void setUp() {
      sUtilStatic = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(true);

      XSessionService mockSessionService = mock(XSessionService.class);
      lenient().when(mockSessionService.createSessionID(anyString(), any()))
         .thenAnswer(inv -> inv.getArgument(0, String.class) + "-session");
      xSessionServiceStatic = mockStatic(XSessionService.class);
      xSessionServiceStatic.when(XSessionService::getService).thenReturn(mockSessionService);

      provider = mock(AuthenticationProvider.class, withSettings().lenient());
      when(provider.getGroups()).thenReturn(new IdentityID[0]);
      when(provider.getOrganization(CALLER_ORG)).thenReturn(org(CALLER_ORG, "Host Organization"));
      when(provider.getOrganization(TARGET_ORG)).thenReturn(org(TARGET_ORG, TARGET_NAME));

      AuthenticationProviderService providerService =
         mock(AuthenticationProviderService.class, withSettings().lenient());
      when(providerService.getProviderByName("Primary")).thenReturn(provider);

      SecurityProvider securityProvider = mock(SecurityProvider.class, withSettings().lenient());
      SecurityEngine securityEngine = mock(SecurityEngine.class, withSettings().lenient());
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);

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
   }

   @AfterEach
   void tearDown() {
      SreeEnv.remove(QUALIFIED_KEY);
      SreeEnv.remove(OTHER_ORG_KEY);
      ThreadContext.setContextPrincipal(null);
      ThreadContext.setPrincipal(null);
      xSessionServiceStatic.close();
      sUtilStatic.close();
   }

   @Test
   void siteAdminSeesAnotherOrgsProperty() {
      SreeEnv.setProperty(QUALIFIED_KEY, "222", false);
      login(new IdentityID("boss", CALLER_ORG), CALLER_ORG);

      EditOrganizationPaneModel model = service.getOrganizationModel(
         "Primary", new IdentityID(TARGET_NAME, TARGET_ORG), currentPrincipal, false, null);

      assertEquals(List.of(PropertyModel.builder().name("max.row.count").value("222").build()),
                   model.properties(),
                   "site admin must see the same property the org's own admin sees");
   }

   @Test
   void orgAdminOfTargetOrgSeesItsOwnProperty() {
      SreeEnv.setProperty(QUALIFIED_KEY, "222", false);
      login(new IdentityID("alice", TARGET_ORG), TARGET_ORG);

      EditOrganizationPaneModel model = service.getOrganizationModel(
         "Primary", new IdentityID(TARGET_NAME, TARGET_ORG), currentPrincipal, false, null);

      assertEquals(List.of(PropertyModel.builder().name("max.row.count").value("222").build()),
                   model.properties(),
                   "the previously-working case (caller org == target org) must keep working");
   }

   @Test
   void mixedCaseOrgIdStillResolvesThePersistedLowercaseKey() {
      SreeEnv.setProperty(QUALIFIED_KEY, "222", false);
      when(provider.getOrganization("S30b")).thenReturn(org("S30b", TARGET_NAME));
      login(new IdentityID("boss", CALLER_ORG), CALLER_ORG);

      EditOrganizationPaneModel model = service.getOrganizationModel(
         "Primary", new IdentityID(TARGET_NAME, "S30b"), currentPrincipal, false, null);

      assertEquals(List.of(PropertyModel.builder().name("max.row.count").value("222").build()),
                   model.properties());
   }

   @Test
   void propertyOfADifferentOrgIsNotLeaked() {
      SreeEnv.setProperty(OTHER_ORG_KEY, "999", false);
      login(new IdentityID("boss", CALLER_ORG), CALLER_ORG);

      EditOrganizationPaneModel model = service.getOrganizationModel(
         "Primary", new IdentityID(TARGET_NAME, TARGET_ORG), currentPrincipal, false, null);

      assertTrue(model.properties().isEmpty(),
                 "a property belonging to a different org must not show up when viewing s30b");
   }

   private void login(IdentityID user, String orgId) {
      currentPrincipal = new SRPrincipal(user, new IdentityID[0], new String[0], orgId, 1L);
      ThreadContext.setContextPrincipal(currentPrincipal);
      ThreadContext.setPrincipal(currentPrincipal);
   }

   private static FSOrganization org(String id, String name) {
      FSOrganization org = new FSOrganization(id);
      org.setName(name);
      return org;
   }

   private static final String CALLER_ORG = "host-org";
   private static final String TARGET_ORG = "s30b";
   private static final String TARGET_NAME = "S30B Org";
   private static final String QUALIFIED_KEY = "inetsoft.org." + TARGET_ORG + ".max.row.count";
   private static final String OTHER_ORG_KEY = "inetsoft.org.otherorg.max.row.count";

   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<XSessionService> xSessionServiceStatic;
   private AuthenticationProvider provider;
   private UserTreeService service;
   private SRPrincipal currentPrincipal;
}
