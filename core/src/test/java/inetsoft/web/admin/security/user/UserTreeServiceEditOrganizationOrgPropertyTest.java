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
 * Bug #77269 -- the destructive half ("P-save"): a site admin's UNCHANGED save of another
 * organization was clearing max.row.count/max.col.count/max.cell.size/max.user.count for that
 * org, because getOrganizationModel's property read (fixed by UserTreeServiceOrgPropertyReadTest)
 * silently returned an empty properties list, and the EM client echoes that list back verbatim on
 * Save. editOrganization's own property-removal block (already correctly runInOrgScope-wrapped)
 * then treats every property missing from the posted list as user-removed and nulls it.
 *
 * UserTreeServiceOrgPropertyReadTest exercises getOrganizationModel only; it does not prove that
 * feeding its result back into editOrganization -- the charter's own repro shape -- actually
 * round-trips. This test closes that gap: it calls the real getOrganizationModel to obtain the
 * exact model a site admin's GET would return, mimics the client's own save preparation
 * (edit-identity-view.component.ts:528, model.oldName = originalModel.name) and feeds that model
 * into the real, unmodified editOrganization, then asserts the properties survive.
 *
 * Drives the real SreeEnv/PropertiesEngine/OrganizationManager/ThreadContext chain, the same as
 * UserTreeServiceOrgPropertyReadTest. The one addition is a static mock of
 * SecurityEngine.getSecurity() (editOrganization's own siteAdmin gate,
 * OrganizationManager.isSiteAdmin(principal), reads the security provider through that static
 * entry point, not through UserTreeService's own injected SecurityEngine field) -- this is an
 * unrelated collaborator (authorization, not org/property scoping) and does not touch anything
 * the diagnosed bug depends on.
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UserTreeServiceEditOrganizationOrgPropertyTest {
   @BeforeEach
   void setUp() {
      sUtilStatic = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(true);

      XSessionService mockSessionService = mock(XSessionService.class);
      lenient().when(mockSessionService.createSessionID(anyString(), any()))
         .thenAnswer(inv -> inv.getArgument(0, String.class) + "-session");
      xSessionServiceStatic = mockStatic(XSessionService.class);
      xSessionServiceStatic.when(XSessionService::getService).thenReturn(mockSessionService);

      // editOrganization's own siteAdmin gate (OrganizationManager.isSiteAdmin(principal)) reads
      // the security provider through the SecurityEngine.getSecurity() static entry point, not
      // through UserTreeService's own injected securityEngine field -- mock only that entry
      // point, so the constructed principal's ADMIN_ROLE resolves as a system administrator.
      // This is authorization, an unrelated collaborator to the diagnosed org/property bug.
      SecurityProvider staticSecurityProvider = mock(SecurityProvider.class, withSettings().lenient());
      when(staticSecurityProvider.isSystemAdministratorRole(ADMIN_ROLE)).thenReturn(true);
      SecurityEngine staticSecurityEngine = mock(SecurityEngine.class, withSettings().lenient());
      when(staticSecurityEngine.getSecurityProvider()).thenReturn(staticSecurityProvider);
      securityEngineStatic = mockStatic(SecurityEngine.class, withSettings().lenient());
      securityEngineStatic.when(SecurityEngine::getSecurity).thenReturn(staticSecurityEngine);

      targetOrg = org(TARGET_ORG, TARGET_NAME);
      provider = mock(AuthenticationProvider.class, withSettings().lenient());
      when(provider.getGroups()).thenReturn(new IdentityID[0]);
      when(provider.getOrganization(CALLER_ORG)).thenReturn(org(CALLER_ORG, "Host Organization"));
      when(provider.getOrganization(TARGET_ORG)).thenReturn(targetOrg);
      when(provider.getOrganizationId(TARGET_NAME)).thenReturn(TARGET_ORG);

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

      SystemAdminService systemAdminService =
         mock(SystemAdminService.class, withSettings().lenient());
      when(systemAdminService.getOrganizationModification(any(), any(), any()))
         .thenReturn(new IdentityModification(targetOrg));
      when(systemAdminService.hasSysAdmin(anySet())).thenReturn(true);
      when(systemAdminService.hasOrgAdmin(anySet())).thenReturn(true);

      LocalizationSettingsService localizationSettingsService =
         mock(LocalizationSettingsService.class, withSettings().lenient());
      when(localizationSettingsService.getModel())
         .thenReturn(LocalizationSettingsModel.builder().build());

      CustomThemesManager customThemesManager =
         mock(CustomThemesManager.class, withSettings().lenient());
      when(customThemesManager.getCustomThemes()).thenReturn(new HashSet<>());

      service = new UserTreeService(
         providerService, systemAdminService, identityService, localizationSettingsService,
         securityEngine, null, null, null, null, null, null, null, customThemesManager, null,
         null, null, null);

      currentPrincipal = new SRPrincipal(new IdentityID("boss", CALLER_ORG),
                                          new IdentityID[] { ADMIN_ROLE }, new String[0],
                                          CALLER_ORG, 1L);
      ThreadContext.setContextPrincipal(currentPrincipal);
      ThreadContext.setPrincipal(currentPrincipal);
   }

   @AfterEach
   void tearDown() {
      for(String key : ALL_MAX_KEYS) {
         SreeEnv.remove(QUALIFIED.apply(key));
      }
      ThreadContext.setContextPrincipal(null);
      ThreadContext.setPrincipal(null);
      securityEngineStatic.close();
      xSessionServiceStatic.close();
      sUtilStatic.close();
   }

   @Test
   void unchangedSiteAdminSaveKeepsAllOrgProperties() throws Exception {
      for(String key : ALL_MAX_KEYS) {
         SreeEnv.setProperty(QUALIFIED.apply(key), "222", false);
      }

      EditOrganizationPaneModel readBack = readAsSiteAdmin();
      assertEquals(ALL_MAX_KEYS.length, readBack.properties().size(),
                   "the GET must return every max.* property before the save round trip is even "
                      + "meaningful");

      // the client's own save preparation: model.oldName = originalModel.name
      // (edit-identity-view.component.ts:528), everything else posted back verbatim
      EditOrganizationPaneModel toSave =
         EditOrganizationPaneModel.builder().from(readBack).oldName(TARGET_NAME).build();

      service.editOrganization(toSave, "Primary", currentPrincipal);

      for(String key : ALL_MAX_KEYS) {
         assertEquals("222", SreeEnv.getProperty(QUALIFIED.apply(key), false, false),
                      key + " must survive an unchanged site-admin save");
      }
   }

   @Test
   void droppingOnePropertyBeforeSaveRemovesOnlyThatOne() throws Exception {
      for(String key : ALL_MAX_KEYS) {
         SreeEnv.setProperty(QUALIFIED.apply(key), "222", false);
      }

      EditOrganizationPaneModel readBack = readAsSiteAdmin();
      List<PropertyModel> withoutColCount = readBack.properties().stream()
         .filter(p -> !"max.col.count".equals(p.name()))
         .collect(Collectors.toList());
      assertEquals(ALL_MAX_KEYS.length - 1, withoutColCount.size());

      EditOrganizationPaneModel toSave = EditOrganizationPaneModel.builder().from(readBack)
         .oldName(TARGET_NAME).properties(withoutColCount).build();

      service.editOrganization(toSave, "Primary", currentPrincipal);

      assertNull(SreeEnv.getProperty(QUALIFIED.apply("max.col.count"), false, false),
                 "the property dropped from the posted list must be removed");
      assertEquals("222", SreeEnv.getProperty(QUALIFIED.apply("max.row.count"), false, false),
                   "a property still in the posted list must survive");
      assertEquals("222", SreeEnv.getProperty(QUALIFIED.apply("max.cell.size"), false, false),
                   "a property still in the posted list must survive");
      assertEquals("222", SreeEnv.getProperty(QUALIFIED.apply("max.user.count"), false, false),
                   "a property still in the posted list must survive");
   }

   private EditOrganizationPaneModel readAsSiteAdmin() {
      return service.getOrganizationModel(
         "Primary", new IdentityID(TARGET_NAME, TARGET_ORG), currentPrincipal, false, null);
   }

   private static FSOrganization org(String id, String name) {
      FSOrganization org = new FSOrganization(id);
      org.setName(name);
      return org;
   }

   private static final String CALLER_ORG = "host-org";
   private static final String TARGET_ORG = "s30b";
   private static final String TARGET_NAME = "S30B Org";
   private static final String[] ALL_MAX_KEYS =
      { "max.row.count", "max.col.count", "max.cell.size", "max.user.count" };
   private static final java.util.function.Function<String, String> QUALIFIED =
      key -> "inetsoft.org." + TARGET_ORG + "." + key;
   private static final IdentityID ADMIN_ROLE = new IdentityID("Administrator", null);

   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<XSessionService> xSessionServiceStatic;
   private MockedStatic<SecurityEngine> securityEngineStatic;
   private AuthenticationProvider provider;
   private Organization targetOrg;
   private UserTreeService service;
   private SRPrincipal currentPrincipal;
}
