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
package inetsoft.sree.security;

/*
 * Ad-hoc empirical check, spun out of a user report: "after renaming an organization, every
 * Presentation setting (date/time format, dashboard, PDF generation, export menu, toolbar
 * options, share settings, composer message, time settings, login banner) was lost."
 *
 * Login banner/logo/favicon/welcome page are already explained by the confirmed
 * PortalThemesManager defect (org-lifecycle-resource-matrix.md, section 3.5 附录二). But the
 * other settings listed (date/time format, dashboard, PDF, export menu, toolbar, share, composer
 * message, time) are NOT PortalThemesManager-backed -- they go through
 * SreeEnv.setProperty(name, val, true)/getProperty(name, false, true), which
 * PropertiesEngine.setProperty(..., orgScope=true)/useAvailableOrgProperty() resolve into
 * "inetsoft.org.{orgId}.{name}" keys, and AbstractEditableAuthenticationProvider
 * .copyScopedProperties()/clearScopedProperties() are the migration methods invoked
 * unconditionally inside copyOrganizationInternal() for exactly this key family.
 *
 * A read/write case-sensitivity mismatch was suspected and ruled out by direct source reading:
 * OrganizationManager.getCurrentOrgID() (no-arg, OrganizationManager.java:64-68) already lowercases
 * before returning, so both the write path (PropertiesEngine.setProperty, line ~301) and the read
 * path (useAvailableOrgProperty, line ~387, whose own .toLowerCase() call is redundant) end up
 * building the same lowercase-orgId key regardless of the actual organization id's casing.
 *
 * No other bug was found by static reading of copyScopedProperties()/clearScopedProperties()
 * themselves. The existing coverage for these two methods
 * (AbstractEditableAuthenticationProviderStaticDepTest) mocks SreeEnv entirely via mockStatic, so
 * it only ever verified "was SreeEnv.setProperty/remove called with the right literal argument
 * strings" -- it never exercised a real end-to-end round trip through the actual
 * OrganizationManager/PropertiesEngine/ThreadContext machinery a live EM session uses. This test
 * fills that gap: real SreeEnv, real org-context switching (ThreadContext.setContextPrincipal() +
 * OrganizationContextHolder.setCurrentOrgId(), mirroring the precedent in
 * DashboardRegistryOrgLifecycleTest/PermissionMatrixResourcesS*Test), driving the actual
 * copyScopedProperties() method via reflection (StubProvider, reused from
 * AbstractEditableAuthenticationProviderStaticDepTest, same package).
 */

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.RepletRegistry;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.web.admin.favorites.FavoritesService;
import inetsoft.uql.util.Identity;
import inetsoft.util.DataSpace;
import inetsoft.util.ThreadContext;
import inetsoft.util.log.LogManager;
import inetsoft.web.admin.security.IdentityModel;
import inetsoft.web.admin.security.IdentityService;
import inetsoft.web.admin.security.user.EditOrganizationPaneModel;
import inetsoft.web.admin.security.user.IdentityThemeService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class OrgLifecycleScopedPropertiesIntegrationTest {

   // No-op mock: these tests assert on scoped properties, not EM favorites; the org-delete
   // path only hands favorites cleanup off to this collaborator.
   private final FavoritesService favoritesService = mock(FavoritesService.class);

   @Autowired
   private DataSpace dataSpace;

   private SecurityTestDataBuilder builder;

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
      OrganizationContextHolder.setCurrentOrgId(null);

      if(builder != null) {
         builder.teardown();
         builder = null;
      }
   }

   @Test
   void rename_copyScopedProperties_realRoundTrip_presentationStylePropertySurvives()
      throws Exception
   {
      String fromOrgId = "scopedprops_from";
      String toOrgId = "scopedprops_to";

      // Simulate a real EM session acting as fromOrgId, setting a Presentation-style org-scoped
      // property exactly the way PresentationFormatsSettingsService.setModel() does.
      actAs(fromOrgId);
      SreeEnv.setProperty("format.date", "MM/dd/yyyy", true);
      assertEquals("MM/dd/yyyy", SreeEnv.getProperty("format.date", false, true),
                  "precondition: property must be readable back under its own org context");

      AbstractEditableAuthenticationProviderStaticDepTest.StubProvider provider =
         new AbstractEditableAuthenticationProviderStaticDepTest.StubProvider();
      Method m = AbstractEditableAuthenticationProvider.class.getDeclaredMethod(
         "copyScopedProperties", String.class, String.class, boolean.class);
      m.setAccessible(true);
      m.invoke(provider, fromOrgId, toOrgId, true);

      // Switch the EM session to the renamed org and read back the same logical setting.
      actAs(toOrgId);
      assertEquals("MM/dd/yyyy", SreeEnv.getProperty("format.date", false, true),
                  "after rename, the same setting must be readable under the new org id");

      // Old org id must no longer see the (now-migrated-away) org-specific value -- it falls
      // back to the shared global default ("yyyy-MM-dd", defaults.properties:69), not null.
      actAs(fromOrgId);
      assertEquals("yyyy-MM-dd", SreeEnv.getProperty("format.date", false, true),
                  "old org id must fall back to the global default, not keep the migrated-away value");
   }

   private static void actAs(String orgId) {
      ThreadContext.setContextPrincipal(new SRPrincipal(new IdentityID("tester", orgId),
         new IdentityID[0], new String[0], orgId, 1L));
      OrganizationContextHolder.setCurrentOrgId(orgId);
   }

   // ── real end-to-end entry point: does the user-reported "everything in Presentation reset"
   //    survive when driven through the actual IdentityService.setOrganizationInfo() path (not
   //    just the isolated copyScopedProperties() method above)? Mirrors
   //    DashboardRegistryOrgLifecycleTest#rename_setOrganizationInfo_... 's harness exactly
   //    (real FileAuthenticationProvider via SecurityTestDataBuilder, spy IdentityService with
   //    only the storage helpers UNRELATED to this scenario stubbed out) -- DashboardRegistryManager
   //    and PortalThemesManager are mocked here (not real beans) since this scenario doesn't care
   //    about their own correctness, only about whether SreeEnv-backed properties survive the full
   //    orchestration around them. ──

   @Test
   void rename_realSetOrganizationInfoEntryPoint_presentationStylePropertySurvives() throws Exception {
      String fromOrgId = "scopedprops_full_from";
      String fromOrgName = "ScopedPropsFullFrom";
      String toOrgId = "scopedprops_full_to";
      String toOrgName = "ScopedPropsFullTo";
      IdentityID user = new IdentityID("carol", fromOrgId);

      builder = SecurityTestDataBuilder.create()
         .addOrg(fromOrgName, fromOrgId)
         .addUser("carol", fromOrgId, "password")
         .setup();

      // Seed the property exactly the way a real EM admin session would, acting as fromOrgId,
      // through the real orgScope=true write path -- not a raw qualified-key set.
      actAs(fromOrgId);
      SreeEnv.setProperty("format.date", "MM/dd/yyyy", true);
      assertEquals("MM/dd/yyyy", SreeEnv.getProperty("format.date", false, true),
                  "precondition: property must be readable back under its own org context");

      AuthenticationProvider authc = SecurityEngine.getSecurity().getSecurityProvider()
         .getAuthenticationProvider();
      FileAuthenticationProvider fileProvider =
         (FileAuthenticationProvider) ((AuthenticationChain) authc).getProviders().get(0);
      FSOrganization oldOrg = (FSOrganization) fileProvider.getOrganization(fromOrgId);

      RepletRegistryManager repletRegistryManager = mock(RepletRegistryManager.class);
      when(repletRegistryManager.getRegistry(anyString())).thenReturn(mock(RepletRegistry.class));

      // syncIdentity() (IdentityService.java:553) unconditionally calls
      // libManagerProvider.getManager(identityId.orgID) near the top, before it ever reaches the
      // ORGANIZATION-type rename branch -- a null libManagerProvider throws an NPE right there and
      // aborts the whole call before copyOrganization()/copyScopedProperties() ever run. Must be a
      // working mock, not null (DashboardRegistryOrgLifecycleTest's 4e "succeeds" with a null one
      // only because its own assertions are about state set up-stream of this NPE, not because the
      // NPE doesn't happen).
      LibManagerProvider libManagerProvider = mock(LibManagerProvider.class);
      when(libManagerProvider.getManager(anyString())).thenReturn(mock(LibManager.class));

      IdentityService realService = new IdentityService(
         SecurityEngine.getSecurity(), SecurityEngine.getSecurity().getSecurityProvider(),
         mock(IdentityThemeService.class), null, null, favoritesService, null, null,
         mock(DataCycleManager.class), null, mock(LogManager.class), null, null, null,
         Optional.empty(),
         null, mock(CustomThemesManager.class), null,
         mock(DashboardRegistryManager.class),
         libManagerProvider, null, mock(PortalThemesManager.class), null, dataSpace,
         null, null, null,
         repletRegistryManager,
         Optional.empty());

      IdentityService spyService = spy(realService);
      // Stub storage helpers unrelated to this scenario (same list as
      // DashboardRegistryOrgLifecycleTest's 4e) so the rest of setOrganizationInfo()/syncIdentity()
      // can run without Blob/MV/schedule infrastructure. copyScopedProperties()/
      // clearScopedProperties() are private methods on AbstractEditableAuthenticationProvider, not
      // IdentityService -- they are NOT stubbed and run for real, same as in 4e.
      doNothing().when(spyService).updateOrgProperties(any(), any());
      doNothing().when(spyService).updateAutoSaveFiles(any(), any(), any());
      doNothing().when(spyService).updateTaskSaveFiles(any(), any());
      doNothing().when(spyService).updateIdentityPermissions(
         anyInt(), any(), any(), any(), any(), anyBoolean());
      doNothing().when(spyService).clearDataSourceMetadata();
      doNothing().when(spyService).copyStorages(any(), any(), anyBoolean());
      doNothing().when(spyService).copyRepletRegistry(any(), any());
      doNothing().when(spyService).removeOrgProperties(any());
      doNothing().when(spyService).updateRepletRegistry(any(), any());
      doNothing().when(spyService).removeStorages(any());
      doNothing().when(spyService).addCopiedIdentityPermission(any(), any(), any(), anyInt(), anyBoolean());

      IdentityModel carolMember = IdentityModel.builder()
         .identityID(user)
         .type(Identity.USER)
         .build();

      EditOrganizationPaneModel model = EditOrganizationPaneModel.builder()
         .id(toOrgId)
         .name(toOrgName)
         .oldName(fromOrgName)
         .members(List.of(carolMember))
         .status(true)
         .build();

      Method setOrganizationInfo = IdentityService.class.getDeclaredMethod(
         "setOrganizationInfo", FSOrganization.class, EditOrganizationPaneModel.class,
         EditableAuthenticationProvider.class, Principal.class);
      setOrganizationInfo.setAccessible(true);

      CustomThemesManager themesManager = mock(CustomThemesManager.class);
      when(themesManager.getCustomThemes()).thenReturn(new HashSet<>());

      // Mirror EM UI: operator switches to the org being renamed before editing its id.
      OrganizationContextHolder.setCurrentOrgId(fromOrgId);

      try(MockedStatic<CustomThemesManager> ctm = mockStatic(CustomThemesManager.class)) {
         ctm.when(CustomThemesManager::getManager).thenReturn(themesManager);

         try {
            setOrganizationInfo.invoke(spyService, oldOrg, model, fileProvider, mock(Principal.class));
         }
         catch(Exception e) {
            // Tolerated: the replace=true tail hits the static RepletRegistryManager.getInstance(),
            // which has no bean registration in this minimal context (same rationale as
            // DashboardRegistryOrgLifecycleTest's 4e). copyScopedProperties() -- the step this
            // scenario cares about -- runs strictly earlier in the same call and has already taken
            // effect (verified directly below via the raw qualified keys) by the time this later,
            // unrelated step throws.
         }
      }

      // Assert directly on the raw qualified keys (bypassing org-scope resolution) so the
      // assertion is unambiguous about what actually changed in storage.
      assertNull(SreeEnv.getProperty("inetsoft.org." + fromOrgId + ".format.date"),
                "old org's qualified property key must be gone after a real rename");
      assertEquals("MM/dd/yyyy", SreeEnv.getProperty("inetsoft.org." + toOrgId + ".format.date"),
                  "new org's qualified property key must hold the migrated value");

      // Switch to the new org id, exactly as the EM UI does after a successful rename, and check
      // whether the property survived the REAL end-to-end orchestration (not just the isolated
      // copyScopedProperties() call already verified above).
      actAs(toOrgId);
      assertEquals("MM/dd/yyyy", SreeEnv.getProperty("format.date", false, true),
                  "after the real setOrganizationInfo() rename entry point, the property must "
                  + "still be readable under the new org id");
   }
}
