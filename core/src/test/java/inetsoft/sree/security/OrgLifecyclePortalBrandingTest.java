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
 * Scenario 12a (org-lifecycle-resource-matrix.md, 三、3.8.1) -- control group proving cssEntries IS
 * kept in sync on copy/rename, unlike logoEntries/faviconEntries (PR #4469, still open) and separately
 * from welcomePageEntries (fixed by PR #4454). Only asserts the addCSSEntry/removeCSSEntry map calls;
 * physical file relocation is copyDataSpace()'s job, already covered by
 * OrgLifecycleDataSpaceIntegrationTest. 12d (delete) is covered by IdentityServiceAutoSaveOrgLifecycle
 * Test, not duplicated here. Harness mirrors OrgLifecycleThemeOrchestrationTest, since the cssEntries
 * block is inlined in copyOrganizationInternal() rather than factored into its own testable method.
 */

import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.portal.PortalWelcomePage;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.DataSpace;
import inetsoft.web.admin.security.IdentityService;
import inetsoft.web.admin.security.user.IdentityThemeService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  OrgLifecyclePortalBrandingTest.PortalThemesManagerConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class OrgLifecyclePortalBrandingTest {

   @Autowired
   private PortalThemesManager portalThemesManager;

   // copy branch (replace=false)
   @Test
   void copy_cssEntrySynced_newOrgGetsMapEntry_sourceEntryNotRemoved() {
      String fromOrgId = "css_orch_copy_from";
      String toOrgId = "css_orch_copy_to";
      String cssName = "brand.css";

      when(portalThemesManager.getCssEntries())
         .thenReturn(Map.of(fromOrgId, fromOrgId + "/" + cssName));

      StubProvider provider = new StubProvider();

      CustomThemesManager noopThemesManager = noopThemesManager();

      try(MockedStatic<CustomThemesManager> ctm = mockStatic(CustomThemesManager.class)) {
         ctm.when(CustomThemesManager::getManager).thenReturn(noopThemesManager);

         provider.copyOrganization(fromOrganization(fromOrgId), toOrgId,
            mock(IdentityService.class), mock(IdentityThemeService.class),
            mock(DashboardRegistryManager.class), mock(DataCycleManager.class),
            mock(Principal.class), false, null);
      }

      verify(portalThemesManager).addCSSEntry(toOrgId, toOrgId + "/" + cssName);
      verify(portalThemesManager, never()).removeCSSEntry(fromOrgId);
   }

   // rename branch (replace=true)
   @Test
   void rename_cssEntrySynced_newOrgGetsMapEntry_sourceEntryRemoved() {
      String fromOrgId = "css_orch_rename_from";
      String toOrgId = "css_orch_rename_to";
      String cssName = "brand.css";

      when(portalThemesManager.getCssEntries())
         .thenReturn(Map.of(fromOrgId, fromOrgId + "/" + cssName));

      StubProvider provider = new StubProvider();

      CustomThemesManager noopThemesManager = noopThemesManager();

      try(MockedStatic<CustomThemesManager> ctm = mockStatic(CustomThemesManager.class)) {
         ctm.when(CustomThemesManager::getManager).thenReturn(noopThemesManager);

         provider.copyOrganization(fromOrganization(fromOrgId), toOrgId,
            mock(IdentityService.class), mock(IdentityThemeService.class),
            mock(DashboardRegistryManager.class), mock(DataCycleManager.class),
            mock(Principal.class), true, null);
      }

      verify(portalThemesManager).addCSSEntry(toOrgId, toOrgId + "/" + cssName);
      verify(portalThemesManager).removeCSSEntry(fromOrgId);
   }

   // ── TC-01/TC-02: logoEntries copy/rename (PR #4469's actual new code path) ──

   @Test
   void copy_logoEntrySynced_newOrgGetsMapEntry_sourceEntryNotRemoved() {
      String fromOrgId = "logo_orch_copy_from";
      String toOrgId = "logo_orch_copy_to";
      String logoName = "logo.png";

      when(portalThemesManager.getLogoEntries())
         .thenReturn(Map.of(fromOrgId, "portal/" + fromOrgId + "/" + logoName));

      invokeCopyOrganization(fromOrgId, toOrgId, false);

      verify(portalThemesManager).addLogoEntry(toOrgId, "portal/" + toOrgId + "/" + logoName);
      verify(portalThemesManager, never()).removeLogoEntry(fromOrgId);
   }

   @Test
   void rename_logoEntrySynced_newOrgGetsMapEntry_sourceEntryRemoved() {
      String fromOrgId = "logo_orch_rename_from";
      String toOrgId = "logo_orch_rename_to";
      String logoName = "logo.png";

      when(portalThemesManager.getLogoEntries())
         .thenReturn(Map.of(fromOrgId, "portal/" + fromOrgId + "/" + logoName));

      invokeCopyOrganization(fromOrgId, toOrgId, true);

      verify(portalThemesManager).addLogoEntry(toOrgId, "portal/" + toOrgId + "/" + logoName);
      verify(portalThemesManager).removeLogoEntry(fromOrgId);
   }

   // ── TC-03/TC-04: faviconEntries copy/rename (mirrors logo) ──

   @Test
   void copy_faviconEntrySynced_newOrgGetsMapEntry_sourceEntryNotRemoved() {
      String fromOrgId = "favicon_orch_copy_from";
      String toOrgId = "favicon_orch_copy_to";
      String faviconName = "favicon.ico";

      when(portalThemesManager.getFaviconEntries())
         .thenReturn(Map.of(fromOrgId, "portal/" + fromOrgId + "/" + faviconName));

      invokeCopyOrganization(fromOrgId, toOrgId, false);

      verify(portalThemesManager).addFaviconEntry(toOrgId, "portal/" + toOrgId + "/" + faviconName);
      verify(portalThemesManager, never()).removeFaviconEntry(fromOrgId);
   }

   @Test
   void rename_faviconEntrySynced_newOrgGetsMapEntry_sourceEntryRemoved() {
      String fromOrgId = "favicon_orch_rename_from";
      String toOrgId = "favicon_orch_rename_to";
      String faviconName = "favicon.ico";

      when(portalThemesManager.getFaviconEntries())
         .thenReturn(Map.of(fromOrgId, "portal/" + fromOrgId + "/" + faviconName));

      invokeCopyOrganization(fromOrgId, toOrgId, true);

      verify(portalThemesManager).addFaviconEntry(toOrgId, "portal/" + toOrgId + "/" + faviconName);
      verify(portalThemesManager).removeFaviconEntry(fromOrgId);
   }

   // ── TC-08/TC-09: welcomePageEntries regression spot-check (already fixed by PR #4454,
   // zero coverage of its own until now) ──

   @Test
   void copy_welcomePageEntrySynced_newOrgGetsClonedEntry() {
      String fromOrgId = "welcome_orch_copy_from";
      String toOrgId = "welcome_orch_copy_to";

      when(portalThemesManager.getWelcomePage(fromOrgId))
         .thenReturn(new PortalWelcomePage(1, "welcome.html"));

      invokeCopyOrganization(fromOrgId, toOrgId, false);

      verify(portalThemesManager).setWelcomePage(eq(toOrgId), any(PortalWelcomePage.class));
   }

   @Test
   void rename_welcomePageEntrySynced_sourceEntryRemoved() {
      String fromOrgId = "welcome_orch_rename_from";
      String toOrgId = "welcome_orch_rename_to";

      when(portalThemesManager.getWelcomePage(fromOrgId))
         .thenReturn(new PortalWelcomePage(1, "welcome.html"));

      invokeCopyOrganization(fromOrgId, toOrgId, true);

      verify(portalThemesManager).setWelcomePage(eq(toOrgId), any(PortalWelcomePage.class));
      verify(portalThemesManager).removeWelcomePage(fromOrgId);
   }

   // ── TC-10: empty-org baseline (null-guard path smoke check) ──

   @Test
   void copyAndRename_emptyOrg_noBrandingEntriesCreated() {
      // getCssEntries()/getLogoEntries()/getFaviconEntries() stay at the config's Map.of()
      // defaults, and getWelcomePage(fromOrgId) is left unstubbed (returns null) -- exercising
      // every one of the four `if (x != null)` guards' "nothing to do" branch on the add/copy side.
      //
      // A blanket never().addLogoEntry(any(), any()) etc. would be wrong here: @DirtiesContext is
      // not used on this class, so Spring/JUnit5 caches and reuses the same ApplicationContext (and
      // therefore the same singleton mock PortalThemesManager bean) across every test method in this
      // class. An unscoped any()/any() "never called" assertion would check the mock's cumulative
      // invocation history across the whole class run, not just this test, and would pass or fail
      // depending on method execution order relative to the other tests above that DO call
      // addLogoEntry/addFaviconEntry/etc. with different org ids. Scoping every assertion to this
      // test's own from/to org ids (as the pre-existing CSS tests already do for their never()
      // checks) avoids that order-dependent flakiness.
      //
      // The rename side asserts the remove* calls WERE made, not never() -- confirmed by actually
      // running this test: removeCSSEntry/removeLogoEntry/removeFaviconEntry/removeWelcomePage
      // (AbstractEditableAuthenticationProvider.java:333-336) run unconditionally inside
      // `if(replace)`, regardless of whether that org ever had a corresponding entry. This is
      // correct, intentional cleanup behavior (ConcurrentHashMap.remove() on an absent key is a
      // harmless no-op), not a product bug -- so only the copy side (replace=false, where the whole
      // `if(replace)` block is skipped entirely) asserts the remove* calls were never made.
      String copyFrom = "empty_orch_copy_from";
      String copyTo = "empty_orch_copy_to";
      String renameFrom = "empty_orch_rename_from";
      String renameTo = "empty_orch_rename_to";

      assertDoesNotThrow(() -> invokeCopyOrganization(copyFrom, copyTo, false));
      assertDoesNotThrow(() -> invokeCopyOrganization(renameFrom, renameTo, true));

      for(String toOrgId : new String[] { copyTo, renameTo }) {
         verify(portalThemesManager, never()).addCSSEntry(eq(toOrgId), any());
         verify(portalThemesManager, never()).addLogoEntry(eq(toOrgId), any());
         verify(portalThemesManager, never()).addFaviconEntry(eq(toOrgId), any());
         verify(portalThemesManager, never()).setWelcomePage(eq(toOrgId), any());
      }

      // Copy branch: replace=false skips the whole `if(replace)` cleanup block, so none of the
      // remove* calls should fire for copyFrom.
      verify(portalThemesManager, never()).removeCSSEntry(copyFrom);
      verify(portalThemesManager, never()).removeLogoEntry(copyFrom);
      verify(portalThemesManager, never()).removeFaviconEntry(copyFrom);
      verify(portalThemesManager, never()).removeWelcomePage(copyFrom);

      // Rename branch: replace=true's cleanup unconditionally calls all four remove* methods for
      // fromOrgId regardless of whether it had any branding entries -- see Deviation #2 above.
      verify(portalThemesManager).removeCSSEntry(renameFrom);
      verify(portalThemesManager).removeLogoEntry(renameFrom);
      verify(portalThemesManager).removeFaviconEntry(renameFrom);
      verify(portalThemesManager).removeWelcomePage(renameFrom);
   }

   // ── TC-11: map entry present but no backing file on disk (intentional design -- map
   // bookkeeping and file copy are decoupled, per commit c01225758's revert of 269bcf3bd) ──

   @Test
   void copy_logoEntry_missingBackingFile_mapEntryStillAdded() {
      String fromOrgId = "logo_missingfile_copy_from";
      String toOrgId = "logo_missingfile_copy_to";

      // Deliberately no dataSpace.withOutputStream(...) seed for "portal/" + fromOrgId + "/logo.png"
      // -- the real DataSpace bean behind PortalThemesManagerConfig has nothing written to it, so
      // dataSpace.getInputStream(odir, "logo.png") returns null and the file copy is skipped.
      when(portalThemesManager.getLogoEntries())
         .thenReturn(Map.of(fromOrgId, "portal/" + fromOrgId + "/logo.png"));

      assertDoesNotThrow(() -> invokeCopyOrganization(fromOrgId, toOrgId, false));

      verify(portalThemesManager).addLogoEntry(toOrgId, "portal/" + toOrgId + "/logo.png");
   }

   // ── TC-12: rename where fromOrgId/newOrgID differ only by case -- map keys are raw,
   // case-sensitive ConcurrentHashMap keys, so no collision expected (unlike the physical-storage
   // -bucket hazard the sibling `sameStorageBucket` guard exists for) ──

   @Test
   void rename_caseOnlyOrgIdChange_logoEntryMovesCorrectly_noKeyCollision() {
      String fromOrgId = "CaseOrg75800";
      String toOrgId = "caseorg75800";

      when(portalThemesManager.getLogoEntries())
         .thenReturn(Map.of(fromOrgId, "portal/" + fromOrgId + "/logo.png"));

      invokeCopyOrganization(fromOrgId, toOrgId, true);

      verify(portalThemesManager).addLogoEntry(toOrgId, "portal/" + toOrgId + "/logo.png");
      verify(portalThemesManager).removeLogoEntry(fromOrgId);
   }

   // ── TC-13: re-cloning the same source to the same target twice (org-ID reuse after a
   // presumed prior delete of toOrgId) -- second clone must cleanly overwrite, not throw or
   // silently no-op ──

   @Test
   void reClone_sameTargetOrgIdTwice_secondCloneOverwritesCleanly() {
      String fromOrgId = "reclone_from";
      String toOrgId = "reclone_to";

      when(portalThemesManager.getLogoEntries())
         .thenReturn(Map.of(fromOrgId, "portal/" + fromOrgId + "/logo.png"));

      invokeCopyOrganization(fromOrgId, toOrgId, false);
      invokeCopyOrganization(fromOrgId, toOrgId, false);

      verify(portalThemesManager, times(2))
         .addLogoEntry(toOrgId, "portal/" + toOrgId + "/logo.png");
   }

   // Shared invocation helper for the logo/favicon/welcome-page/edge-case tests above -- mirrors
   // the mockStatic(CustomThemesManager.class) + copyOrganization(...) boilerplate common to both
   // existing CSS tests, factored out to avoid repeating the same 10-argument call and
   // try-with-resources block across a dozen test methods. Package-private (not private) so
   // OrgLifecyclePortalBrandingDataSpaceIntegrationTest, in the same package, can reuse it too.
   static void invokeCopyOrganization(String fromOrgId, String toOrgId, boolean replace) {
      StubProvider provider = new StubProvider();
      CustomThemesManager noopThemesManager = noopThemesManager();

      try(MockedStatic<CustomThemesManager> ctm = mockStatic(CustomThemesManager.class)) {
         ctm.when(CustomThemesManager::getManager).thenReturn(noopThemesManager);

         provider.copyOrganization(fromOrganization(fromOrgId), toOrgId,
            mock(IdentityService.class), mock(IdentityThemeService.class),
            mock(DashboardRegistryManager.class), mock(DataCycleManager.class),
            mock(Principal.class), replace, null);
      }
   }

   private static Organization fromOrganization(String fromOrgId) {
      FSOrganization fromOrganization = new FSOrganization(fromOrgId);
      fromOrganization.setName(fromOrgId);
      return fromOrganization;
   }

   // copyThemes() runs first and needs a non-null empty theme set to no-op safely; unrelated to
   // this scenario, see OrgLifecycleThemeOrchestrationTest for why it's mocked rather than a real bean.
   private static CustomThemesManager noopThemesManager() {
      CustomThemesManager mockManager = mock(CustomThemesManager.class);
      when(mockManager.getCustomThemes()).thenReturn(Collections.emptySet());
      return mockManager;
   }

   // unstubbed PortalThemesManager NPEs on getCssEntries(); each test re-stubs it before use.
   // getLogoEntries()/getFaviconEntries() are given the same deliberate empty-map default here
   // (rather than relying on Mockito's implicit null return, which only "works" today because of
   // AbstractEditableAuthenticationProvider's defensive `!= null` guards) so every test that
   // doesn't care about logo/favicon still exercises a real empty-map lookup, not an unstubbed null.
   @Configuration
   public static class PortalThemesManagerConfig {
      @Bean
      public PortalThemesManager portalThemesManager() {
         PortalThemesManager mockPortalThemesManager = mock(PortalThemesManager.class);
         when(mockPortalThemesManager.getCssEntries()).thenReturn(Map.of());
         when(mockPortalThemesManager.getLogoEntries()).thenReturn(Map.of());
         when(mockPortalThemesManager.getFaviconEntries()).thenReturn(Map.of());
         return mockPortalThemesManager;
      }

      // rename's cleanup path calls RepletRegistryManager.getInstance().clearOrgCache(); not
      // provided by BaseTestConfiguration, so registered here as a real instance.
      @Bean
      public RepletRegistryManager repletRegistryManager(DataSpace dataSpace) {
         return new RepletRegistryManager(dataSpace);
      }
   }

   // mirrors OrgLifecycleThemeOrchestrationTest.StubProvider; unused abstract members are no-ops.
   static class StubProvider extends AbstractEditableAuthenticationProvider {
      @Override public User  getUser(IdentityID id)  { return null; }
      @Override public Group getGroup(IdentityID id) { return null; }
      @Override public Role  getRole(IdentityID id)  { return null; }

      @Override public boolean authenticate(IdentityID userIdentity, Object credential) { return false; }
      @Override public Organization getOrganization(String id)  { return null; }
      @Override public String getOrgIdFromName(String name)     { return null; }
      @Override public String getOrgNameFromID(String id)       { return null; }
      @Override public String[] getOrganizationIDs()            { return new String[0]; }
      @Override public String[] getOrganizationNames()          { return new String[0]; }
      @Override public void tearDown() {}
   }
}
