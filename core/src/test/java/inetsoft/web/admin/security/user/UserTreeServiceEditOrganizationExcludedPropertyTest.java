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
 * Bug #77885 -- EM Security > Organization > Properties stored an organization override of a key
 * in PropertiesEngine.EXCLUDED_ORG_PROPERTIES (e.g. security.enabled). Such a key is always read
 * globally, so the override was stored and shown as an org setting but never used. EM All
 * Properties and the config API already refuse it (Bug #77694).
 *
 * Drives the real getOrganizationModel/editOrganization over the real SreeEnv/PropertiesEngine/
 * OrganizationManager/ThreadContext chain, set up like
 * UserTreeServiceEditOrganizationOrgPropertyTest. Only authorization collaborators are mocked.
 */

import inetsoft.sree.PropertiesEngine;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.security.*;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.MessageException;
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
class UserTreeServiceEditOrganizationExcludedPropertyTest {
   @BeforeEach
   void setUp() {
      sUtilStatic = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(true);

      XSessionService mockSessionService = mock(XSessionService.class);
      lenient().when(mockSessionService.createSessionID(anyString(), any()))
         .thenAnswer(inv -> inv.getArgument(0, String.class) + "-session");
      xSessionServiceStatic = mockStatic(XSessionService.class);
      xSessionServiceStatic.when(XSessionService::getService).thenReturn(mockSessionService);

      // OrganizationManager.isSiteAdmin(principal) reads the provider through the static
      // SecurityEngine.getSecurity() entry point: ADMIN_ROLE is a system administrator role,
      // ORG_ADMIN_ROLE is not
      SecurityProvider staticSecurityProvider =
         mock(SecurityProvider.class, withSettings().lenient());
      when(staticSecurityProvider.isSystemAdministratorRole(ADMIN_ROLE)).thenReturn(true);
      SecurityEngine staticSecurityEngine = mock(SecurityEngine.class, withSettings().lenient());
      when(staticSecurityEngine.getSecurityProvider()).thenReturn(staticSecurityProvider);
      securityEngineStatic = mockStatic(SecurityEngine.class, withSettings().lenient());
      securityEngineStatic.when(SecurityEngine::getSecurity).thenReturn(staticSecurityEngine);

      Organization targetOrg = org(TARGET_ORG, TARGET_NAME);
      provider = mock(AuthenticationProvider.class, withSettings().lenient());
      when(provider.getGroups()).thenReturn(new IdentityID[0]);
      when(provider.getOrganization(CALLER_ORG)).thenReturn(org(CALLER_ORG, "Host Organization"));
      when(provider.getOrganization(TARGET_ORG)).thenReturn(targetOrg);
      when(provider.getOrganizationId(TARGET_NAME)).thenReturn(TARGET_ORG);

      AuthenticationProviderService providerService =
         mock(AuthenticationProviderService.class, withSettings().lenient());
      when(providerService.getProviderByName("Primary")).thenReturn(provider);

      SecurityProvider securityProvider = mock(SecurityProvider.class, withSettings().lenient());
      when(securityProvider.getOrganizationMembers(anyString())).thenReturn(new String[0]);
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

      siteAdmin = new SRPrincipal(new IdentityID("boss", CALLER_ORG),
                                  new IdentityID[] { ADMIN_ROLE }, new String[0],
                                  CALLER_ORG, 1L);
      orgAdmin = new SRPrincipal(new IdentityID("orgboss", TARGET_ORG),
                                 new IdentityID[] { ORG_ADMIN_ROLE }, new String[0],
                                 TARGET_ORG, 2L);
      setPrincipal(siteAdmin);
   }

   @AfterEach
   void tearDown() {
      for(String key : TOUCHED_KEYS) {
         SreeEnv.remove(QUALIFIED.apply(key));
         SreeEnv.remove(PropertiesEngine.getOrgPropertyPrefix(MIXED_ORG) + key);
      }

      setPrincipal(null);
      securityEngineStatic.close();
      xSessionServiceStatic.close();
      sUtilStatic.close();
   }

   @Test
   void excludedKeyIsRefusedAndNothingWritten() {
      EditOrganizationPaneModel toSave = saveModel(siteAdmin, List.of(
         prop("max.row.count", "5"), prop("olap.security.enabled", "true"),
         prop("security.enabled", "false")));

      MessageException ex = assertThrows(
         MessageException.class, () -> service.editOrganization(toSave, "Primary", siteAdmin));
      assertTrue(ex.getMessage().contains("security.enabled"), ex.getMessage());

      assertNull(stored("security.enabled"), "the excluded override must not be stored");
      assertNull(stored("max.row.count"), "a refused save must not write the other properties");
      assertNull(stored("olap.security.enabled"),
                 "a refused save must not write the other properties");
   }

   @Test
   void excludedKeyIsRefusedRegardlessOfCase() {
      EditOrganizationPaneModel toSave = saveModel(siteAdmin, List.of(
         prop("Replet.Cache.Directory", "/tmp/x")));

      assertThrows(MessageException.class,
                   () -> service.editOrganization(toSave, "Primary", siteAdmin));
      assertNull(stored("replet.cache.directory"));
   }

   @Test
   void allowedKeysStillSave() throws Exception {
      // olap.security.enabled only ends with an excluded name, and is a real per-org property
      EditOrganizationPaneModel toSave = saveModel(siteAdmin, List.of(
         prop("max.row.count", "5"), prop("olap.security.enabled", "true")));

      service.editOrganization(toSave, "Primary", siteAdmin);

      assertEquals("5", stored("max.row.count"));
      assertEquals("true", stored("olap.security.enabled"));
   }

   @Test
   void getHidesStoredExcludedKeyAndSaveKeepsIt() throws Exception {
      // an override stored before this fix
      SreeEnv.setProperty(QUALIFIED.apply("security.enabled"), "false", false);
      SreeEnv.setProperty(QUALIFIED.apply("max.row.count"), "222", false);

      EditOrganizationPaneModel readBack = service.getOrganizationModel(
         "Primary", new IdentityID(TARGET_NAME, TARGET_ORG), siteAdmin, false, null);
      List<String> names = readBack.properties().stream().map(PropertyModel::name).toList();

      assertFalse(names.contains("security.enabled"),
                  "the GET must not show an excluded override as an org setting: " + names);
      assertTrue(names.contains("max.row.count"), "an allowed property is still shown: " + names);

      // the client posts the list back verbatim (edit-identity-view.component.ts)
      EditOrganizationPaneModel toSave =
         EditOrganizationPaneModel.builder().from(readBack).oldName(TARGET_NAME).build();
      service.editOrganization(toSave, "Primary", siteAdmin);

      assertEquals("false", stored("security.enabled"),
                   "an org save must not delete the stored override; cleanup is in All Properties");
      assertEquals("222", stored("max.row.count"));
   }

   @Test
   void orgAdminSaveIsNotNewlyRefused() throws Exception {
      setPrincipal(orgAdmin);

      // a non-site admin may only write the max.* properties, so the excluded name is skipped
      // as before rather than refused
      EditOrganizationPaneModel toSave = saveModel(orgAdmin, List.of(
         prop("max.row.count", "7"), prop("security.enabled", "false")));

      service.editOrganization(toSave, "Primary", orgAdmin);

      assertEquals("7", stored("max.row.count"));
      assertNull(stored("security.enabled"));
   }

   @Test
   void mixedCaseOrgIdHidesAndRefusesExcludedKey() throws Exception {
      // an org ID with upper-case letters: the stored key's org segment is lower-cased, so the
      // GET filter and the save check must both match it regardless of the ID's case
      when(provider.getOrganization(MIXED_ORG)).thenReturn(org(MIXED_ORG, MIXED_NAME));
      when(provider.getOrganizationId(MIXED_NAME)).thenReturn(MIXED_ORG);

      SreeEnv.setProperty("inetsoft.org." + MIXED_ORG + ".Security.Enabled", "false", false);
      SreeEnv.setProperty("inetsoft.org." + MIXED_ORG + ".max.row.count", "333", false);
      assertEquals("false", mixedStored("security.enabled"),
                   "precondition: the override is stored under the lower-cased org prefix");

      EditOrganizationPaneModel readBack = service.getOrganizationModel(
         "Primary", new IdentityID(MIXED_NAME, MIXED_ORG), siteAdmin, false, null);
      List<String> names = readBack.properties().stream().map(PropertyModel::name).toList();
      assertFalse(names.contains("security.enabled"), names.toString());
      assertTrue(names.contains("max.row.count"), names.toString());

      // an unchanged save goes through and keeps the stored override
      service.editOrganization(
         EditOrganizationPaneModel.builder().from(readBack).oldName(MIXED_NAME).build(),
         "Primary", siteAdmin);
      assertEquals("false", mixedStored("security.enabled"));

      // adding a new excluded override is refused and writes nothing
      EditOrganizationPaneModel toSave = EditOrganizationPaneModel.builder().from(readBack)
         .oldName(MIXED_NAME)
         .properties(List.of(prop("max.row.count", "9"), prop("sree.home", "/x"))).build();
      assertThrows(MessageException.class,
                   () -> service.editOrganization(toSave, "Primary", siteAdmin));
      assertNull(mixedStored("sree.home"));
      assertEquals("333", mixedStored("max.row.count"));
   }

   private static String mixedStored(String key) {
      return SreeEnv.getProperty(PropertiesEngine.getOrgPropertyPrefix(MIXED_ORG) + key, false, false);
   }

   private EditOrganizationPaneModel saveModel(SRPrincipal principal, List<PropertyModel> props) {
      EditOrganizationPaneModel readBack = service.getOrganizationModel(
         "Primary", new IdentityID(TARGET_NAME, TARGET_ORG), principal, false, null);
      return EditOrganizationPaneModel.builder().from(readBack)
         .oldName(TARGET_NAME).properties(props).build();
   }

   private static String stored(String key) {
      return SreeEnv.getProperty(QUALIFIED.apply(key), false, false);
   }

   private static PropertyModel prop(String name, String value) {
      return PropertyModel.builder().name(name).value(value).build();
   }

   private static void setPrincipal(SRPrincipal principal) {
      ThreadContext.setContextPrincipal(principal);
      ThreadContext.setPrincipal(principal);
   }

   private static FSOrganization org(String id, String name) {
      FSOrganization org = new FSOrganization(id);
      org.setName(name);
      return org;
   }

   private static final String CALLER_ORG = "host-org";
   private static final String TARGET_ORG = "s30b";
   private static final String TARGET_NAME = "S30B Org";
   private static final String MIXED_ORG = "MixOrg";
   private static final String MIXED_NAME = "Mix Org";
   private static final String[] TOUCHED_KEYS = {
      "security.enabled", "sree.home", "replet.cache.directory", "olap.security.enabled",
      "max.row.count", "max.col.count", "max.cell.size", "max.user.count" };
   private static final java.util.function.Function<String, String> QUALIFIED =
      key -> "inetsoft.org." + TARGET_ORG + "." + key;
   private static final IdentityID ADMIN_ROLE = new IdentityID("Administrator", null);
   private static final IdentityID ORG_ADMIN_ROLE =
      new IdentityID("Organization Administrator", null);

   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<XSessionService> xSessionServiceStatic;
   private MockedStatic<SecurityEngine> securityEngineStatic;
   private AuthenticationProvider provider;
   private UserTreeService service;
   private SRPrincipal siteAdmin;
   private SRPrincipal orgAdmin;
}
