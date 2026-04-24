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
 * Scope: concrete methods in AbstractEditableAuthenticationProvider that obtain
 * their dependencies through static factory methods (SreeEnv, DataSpace,
 * CustomThemesManager, AbstractFileSystem, DefaultBlockSystem).
 *
 * Tier: [mockStatic] — Mockito.mockStatic intercepts static factory calls;
 * no Spring application context required.
 *
 * Companion file to AbstractEditableAuthenticationProviderTest ([unit] tier).
 * Static-free methods (copyIdentityRoles, copyRole/User/GroupToOrganization,
 * copyRootPermittedIdentities, fireAuthenticationChanged, etc.) are tested there.
 *
 * Cases deferred — require integration context:
 *
 * [Integration] copyOrganizationInternal full flow
 *               → requires @SreeHome + ScheduleManager.getScheduleManager()
 *                  + PortalThemesManager.getManager() + OrganizationManager.runInOrgScope()
 *               → NOT yet covered
 *
 * [AbstractEditableAuthenticationProviderStaticDepTest — future]
 * copyUserToOrganization(defaultPassword != null)
 *               → Tool.hash() requires PasswordEncryption (Spring bean) even after
 *                  mockStatic(Tool.class); needs @SreeHome or full context
 */

/*
 * Intent vs implementation suspects (supplement to AbstractEditableAuthenticationProviderTest)
 *
 * Issue #74695
 * [Suspect 6] copyScopedProperties — SreeEnv.save() called inside the loop
 *             intent: persist each copied property atomically
 *             actual: save() called once per matching property PLUS once after the loop;
 *             N matching properties → N+1 disk writes; should be 1 after-loop save only
 *             — AbstractEditableAuthenticationProvider:484
 *
 * [Suspect 7] clearScopedProperties vs copyScopedProperties — case inconsistency
 *             clearScopedProperties: prefix = "inetsoft.org." + oldOrgId (no toLowerCase)
 *             copyScopedProperties: prefix = "inetsoft.org." + fromOrgId.toLowerCase()
 *             actual: org IDs with uppercase letters are never cleared by clearScopedProperties
 *             after they were copied by copyScopedProperties (key format mismatch)
 *             — AbstractEditableAuthenticationProvider:407 vs :468
 *
 * [Suspect 8] copyThemes(replace=true) — mutation of shared CustomTheme objects
 *             pre-processing loop calls t.setOrganizations(newOrgs) on the same objects
 *             referenced by sourceThemes; main loop then sees already-mutated organizations,
 *             so theme.getOrganizations().contains(fromOrgId) == false →
 *             manager.setOrgSelectedTheme(clone.getId(), toOrgId) is silently skipped
 *             — AbstractEditableAuthenticationProvider:319 (pre-loop) vs :355 (main loop)
 *             Fix: deepClone themes before the pre-processing step, or re-check orgID on the clone
 */

/*
 * clearScopedProperties decision tree
 *  ├─ [A] property starts with "inetsoft.org." + orgId → SreeEnv.remove called
 *  ├─ [B] property does not match prefix              → not removed
 *  └─ [C] empty Properties                            → no remove calls
 *
 * copyScopedProperties decision tree
 *  ├─ [A] matching property, replace=false → setProperty called, remove NOT called
 *  ├─ [B] matching property, replace=true  → setProperty AND remove called
 *  ├─ [C] no matching property             → only after-loop save() called
 *  └─ [D] SreeEnv.save() throws IOException → exception swallowed, method does not throw
 *
 * copyThemes decision tree
 *  ├─ [A] fromOrgId is empty string → early return; DataSpace/Manager NOT called
 *  ├─ [B] no theme matches fromOrgId → setCustomThemes called with unchanged set
 *  ├─ [C] theme with orgID==fromOrgId, replace=false → clone added, original kept
 *  ├─ [D] theme with orgID==fromOrgId, replace=true  → original removed, clone added,
 *  │       setOrgSelectedTheme(null, fromOrgId) called
 *  └─ [E] global theme + replace=true mutation bug   → setOrgSelectedTheme(clone.getId(), toOrgId)
 *          skipped because pre-loop already removed fromOrgId from organizations  [Suspect 8]
 *
 * copyDataSpace decision tree
 *  ├─ [A] replace=true                  → dataSpace.rename called for each scoped path
 *  ├─ [B] replace=false, not default org → dataSpace.copy called; copyFileSystem NOT triggered
 *  └─ [C] replace=false, fromOrg is default org → dataSpace.copy called AND
 *          copyFileSystemFileAndBlockSystemFile triggered (AbstractFileSystem.getOrgPaths called)
 *
 * copyFileSystemFileAndBlockSystemFile decision tree
 *  ├─ [A] path exists in dataSpace → dataSpace.copy called
 *  └─ [B] path does not exist      → dataSpace.copy NOT called
 */

import inetsoft.mv.fs.internal.AbstractFileSystem;
import inetsoft.mv.fs.internal.DefaultBlockSystem;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.portal.CustomTheme;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.util.DataSpace;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
class AbstractEditableAuthenticationProviderStaticDepTest {

   private StubProvider provider;

   @BeforeEach
   void setUp() {
      provider = new StubProvider();
   }

   // -------------------------------------------------------------------------
   // clearScopedProperties — protected; directly accessible (same package)
   // -------------------------------------------------------------------------

   // [Path A] property matches prefix → SreeEnv.remove called for it
   @Test
   void clearScopedProperties_matchingProperty_removed() {
      Properties props = new Properties();
      props.setProperty("inetsoft.org.fromOrg.someKey", "value");
      props.setProperty("other.property", "other");

      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(SreeEnv::getProperties).thenReturn(props);

         provider.clearScopedProperties("fromOrg");

         sreeEnv.verify(() -> SreeEnv.remove("inetsoft.org.fromOrg.someKey"));
         sreeEnv.verify(() -> SreeEnv.remove(any(String.class)), times(1));
      }
   }

   // [Path B] non-matching property → SreeEnv.remove NOT called
   @Test
   void clearScopedProperties_nonMatchingProperty_notRemoved() {
      Properties props = new Properties();
      props.setProperty("inetsoft.org.otherOrg.key", "value");

      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(SreeEnv::getProperties).thenReturn(props);

         provider.clearScopedProperties("fromOrg");

         sreeEnv.verify(() -> SreeEnv.remove(any(String.class)), never());
      }
   }

   // [Path C] empty Properties → no remove calls, no exception
   @Test
   void clearScopedProperties_emptyProperties_noOp() {
      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(SreeEnv::getProperties).thenReturn(new Properties());

         assertDoesNotThrow(() -> provider.clearScopedProperties("fromOrg"));

         sreeEnv.verify(() -> SreeEnv.remove(any(String.class)), never());
      }
   }

   // Issue #74695
   // [Suspect 7] clearScopedProperties uses raw orgId; copyScopedProperties lowercases it — key mismatch for uppercase orgIds
   @Disabled("Suspect 7: clearScopedProperties prefix uses raw orgId; " +
             "copyScopedProperties lowercases it — keys written and read use different cases; " +
             "Fix: apply toLowerCase() in clearScopedProperties prefix to match the key format written by copyScopedProperties")
   @Test
   void clearScopedProperties_uppercaseOrgId_doesNotRemoveLowercasedCopiedKey() {
      // copyScopedProperties("FROMORG", ...) writes key "inetsoft.org.fromorg.theme" (lowercased)
      // clearScopedProperties("FROMORG") looks for prefix "inetsoft.org.FROMORG." (raw) → no match → key survives
      Properties props = new Properties();
      props.setProperty("inetsoft.org.fromorg.theme", "blue");

      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(SreeEnv::getProperties).thenReturn(props);

         provider.clearScopedProperties("FROMORG");

         sreeEnv.verify(() -> SreeEnv.remove("inetsoft.org.fromorg.theme"), times(1));
      }
   }

   // -------------------------------------------------------------------------
   // copyScopedProperties — private; tested via reflection
   // -------------------------------------------------------------------------

   // [Path A] matching property, replace=false → setProperty called, remove NOT called
   @Test
   void copyScopedProperties_matchingProperty_replaceFalse_copiedNotRemoved() {
      Properties props = new Properties();
      props.setProperty("inetsoft.org.fromorg.theme", "blue");

      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(SreeEnv::getProperties).thenReturn(props);

         provider.callCopyScopedProperties("fromOrg", "newOrg", false);

         sreeEnv.verify(() -> SreeEnv.setProperty("inetsoft.org.neworg.theme", "blue"));
         sreeEnv.verify(() -> SreeEnv.remove(any(String.class)), never());
      }
   }

   // [Path B] matching property, replace=true → setProperty AND remove called
   @Test
   void copyScopedProperties_matchingProperty_replaceTrue_copiedAndRemoved() {
      Properties props = new Properties();
      props.setProperty("inetsoft.org.fromorg.theme", "blue");

      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(SreeEnv::getProperties).thenReturn(props);

         provider.callCopyScopedProperties("fromOrg", "newOrg", true);

         sreeEnv.verify(() -> SreeEnv.setProperty("inetsoft.org.neworg.theme", "blue"));
         sreeEnv.verify(() -> SreeEnv.remove("inetsoft.org.fromorg.theme"));
      }
   }

   // [Path C] no matching property → setProperty NOT called, save() called once after loop
   @Test
   void copyScopedProperties_noMatchingProperty_onlyFinalSaveCalled() {
      Properties props = new Properties();
      props.setProperty("inetsoft.org.otherorg.key", "value");

      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(SreeEnv::getProperties).thenReturn(props);

         provider.callCopyScopedProperties("fromOrg", "newOrg", false);

         sreeEnv.verify(() -> SreeEnv.setProperty(any(), any()), never());
         sreeEnv.verify(SreeEnv::save, times(1));
      }
   }

   // [Path D] SreeEnv.save() throws IOException → swallowed, method does not propagate
   @Test
   void copyScopedProperties_saveThrows_exceptionSwallowed() {
      Properties props = new Properties();
      props.setProperty("inetsoft.org.fromorg.key", "v");

      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(SreeEnv::getProperties).thenReturn(props);
         sreeEnv.when(SreeEnv::save).thenThrow(new IOException("disk full"));

         assertDoesNotThrow(() -> provider.callCopyScopedProperties("fromOrg", "newOrg", false));
      }
   }

   // -------------------------------------------------------------------------
   // copyThemes — private; tested via reflection
   // -------------------------------------------------------------------------

   // [Path A] empty fromOrgId → early return; no DataSpace or Manager calls
   @Test
   void copyThemes_emptyFromOrgId_earlyReturn() {
      try(MockedStatic<DataSpace> ds = mockStatic(DataSpace.class);
          MockedStatic<CustomThemesManager> ctm = mockStatic(CustomThemesManager.class)) {

         provider.callCopyThemes("", "toOrg", false);

         ds.verify(DataSpace::getDataSpace, never());
         ctm.verify(CustomThemesManager::getManager, never());
      }
   }

   // [Path B] no theme matches fromOrgId → setCustomThemes called with original (unchanged) set
   @Test
   void copyThemes_noMatchingTheme_setCustomThemesWithOriginal() {
      CustomTheme unrelated = new CustomTheme();
      unrelated.setId("other-theme");
      unrelated.setOrgID("otherOrg");
      unrelated.setOrganizations(new ArrayList<>());

      Set<CustomTheme> original = new HashSet<>(Set.of(unrelated));

      try(MockedStatic<DataSpace> ds = mockStatic(DataSpace.class);
          MockedStatic<CustomThemesManager> ctm = mockStatic(CustomThemesManager.class)) {

         DataSpace mockDs = mock(DataSpace.class);
         ds.when(DataSpace::getDataSpace).thenReturn(mockDs);

         CustomThemesManager mockManager = mock(CustomThemesManager.class);
         ctm.when(CustomThemesManager::getManager).thenReturn(mockManager);
         when(mockManager.getCustomThemes()).thenReturn(original);

         provider.callCopyThemes("fromOrg", "toOrg", false);

         @SuppressWarnings("unchecked")
         ArgumentCaptor<Set<CustomTheme>> captor = ArgumentCaptor.forClass(Set.class);
         verify(mockManager).setCustomThemes(captor.capture());

         assertEquals(1, captor.getValue().size());
         assertTrue(captor.getValue().stream().anyMatch(t -> "otherOrg".equals(t.getOrgID())));
      }
   }

   // [Path C] theme with orgID==fromOrgId, replace=false → clone added, original kept
   @Test
   void copyThemes_matchingTheme_replaceFalse_cloneAddedOriginalKept() {
      CustomTheme theme = new CustomTheme();
      theme.setId("theme-1");
      theme.setOrgID("fromOrg");
      theme.setOrganizations(new ArrayList<>());

      try(MockedStatic<DataSpace> ds = mockStatic(DataSpace.class);
          MockedStatic<CustomThemesManager> ctm = mockStatic(CustomThemesManager.class)) {

         DataSpace mockDs = mock(DataSpace.class);
         ds.when(DataSpace::getDataSpace).thenReturn(mockDs);

         CustomThemesManager mockManager = mock(CustomThemesManager.class);
         ctm.when(CustomThemesManager::getManager).thenReturn(mockManager);
         when(mockManager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(theme)));

         provider.callCopyThemes("fromOrg", "toOrg", false);

         @SuppressWarnings("unchecked")
         ArgumentCaptor<Set<CustomTheme>> captor = ArgumentCaptor.forClass(Set.class);
         verify(mockManager).setCustomThemes(captor.capture());

         Set<CustomTheme> result = captor.getValue();
         assertEquals(2, result.size(), "original + clone expected");
         assertTrue(result.stream().anyMatch(t -> "toOrg".equals(t.getOrgID())), "clone must have toOrgId");
         assertTrue(result.stream().anyMatch(t -> "fromOrg".equals(t.getOrgID())), "original must remain");
      }
   }

   // [Path D] theme with orgID==fromOrgId, replace=true → original removed, clone added,
   //           setOrgSelectedTheme(null, fromOrgId) called to clear old selection
   @Test
   void copyThemes_matchingTheme_replaceTrue_originalRemovedCloneAdded() {
      CustomTheme theme = new CustomTheme();
      theme.setId("theme-1");
      theme.setOrgID("fromOrg");
      theme.setOrganizations(new ArrayList<>());

      try(MockedStatic<DataSpace> ds = mockStatic(DataSpace.class);
          MockedStatic<CustomThemesManager> ctm = mockStatic(CustomThemesManager.class)) {

         DataSpace mockDs = mock(DataSpace.class);
         ds.when(DataSpace::getDataSpace).thenReturn(mockDs);

         CustomThemesManager mockManager = mock(CustomThemesManager.class);
         ctm.when(CustomThemesManager::getManager).thenReturn(mockManager);
         when(mockManager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(theme)));

         provider.callCopyThemes("fromOrg", "toOrg", true);

         @SuppressWarnings("unchecked")
         ArgumentCaptor<Set<CustomTheme>> captor = ArgumentCaptor.forClass(Set.class);
         verify(mockManager).setCustomThemes(captor.capture());

         Set<CustomTheme> result = captor.getValue();
         assertEquals(1, result.size(), "only clone expected; original must be removed");
         assertTrue(result.stream().anyMatch(t -> "toOrg".equals(t.getOrgID())));

         verify(mockManager).setOrgSelectedTheme(null, "fromOrg");
      }
   }

   // Issue #74695
   // [Path E] replace=true mutation bug — pre-loop mutates shared CustomTheme.organizations;
   //           main loop finds organizations no longer contains fromOrgId → setOrgSelectedTheme skipped  [Suspect 8]
   @Disabled("Suspect 8: replace=true pre-processing mutates shared theme.organizations — AbstractEditableAuthenticationProvider:319; " +
             "Fix: deepClone source themes before the pre-processing step so main loop sees unmutated state")
   @Test
   void copyThemes_globalThemeWithOrg_replaceTrue_setOrgSelectedThemeCalledForClone() {
      // Global theme (orgID=null) with fromOrgId in organizations — used to pick a custom theme per org
      CustomTheme globalTheme = new CustomTheme();
      globalTheme.setId("global-1");
      globalTheme.setOrgID(null);
      globalTheme.setOrganizations(new ArrayList<>(List.of("fromOrg")));

      try(MockedStatic<DataSpace> ds = mockStatic(DataSpace.class);
          MockedStatic<CustomThemesManager> ctm = mockStatic(CustomThemesManager.class);
          MockedStatic<Organization> org = mockStatic(Organization.class)) {

         DataSpace mockDs = mock(DataSpace.class);
         ds.when(DataSpace::getDataSpace).thenReturn(mockDs);

         CustomThemesManager mockManager = mock(CustomThemesManager.class);
         ctm.when(CustomThemesManager::getManager).thenReturn(mockManager);
         when(mockManager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(globalTheme)));

         // fromOrg is the default org so global theme enters the main loop
         org.when(Organization::getDefaultOrganizationID).thenReturn("fromOrg");

         provider.callCopyThemes("fromOrg", "toOrg", true);

         // Expected correct behavior: a clone is created and registered as selected for toOrg
         verify(mockManager).setOrgSelectedTheme(notNull(), eq("toOrg"));
      }
   }

   // -------------------------------------------------------------------------
   // copyDataSpace — private; tested via reflection
   // -------------------------------------------------------------------------

   // [Path A] replace=true → rename called for each scoped path
   @Test
   void copyDataSpace_replaceTrue_renamesAllScopedPaths() {
      FSOrganization fromOrg = new FSOrganization("fromOrg");
      FSOrganization toOrg   = new FSOrganization("toOrg");

      try(MockedStatic<DataSpace> ds = mockStatic(DataSpace.class)) {
         DataSpace mockDs = mock(DataSpace.class);
         ds.when(DataSpace::getDataSpace).thenReturn(mockDs);
         when(mockDs.getOrgScopedPaths(fromOrg)).thenReturn(new String[]{"portal/fromOrg/file.css"});

         provider.callCopyDataSpace(fromOrg, toOrg, true);

         verify(mockDs).rename("portal/fromOrg/file.css", "portal/toOrg/file.css");
         verify(mockDs, never()).copy(any(), any());
      }
   }

   // [Path B] replace=false, fromOrg is NOT the default org → copy called, filesystem helper NOT triggered
   @Test
   void copyDataSpace_replaceFalse_notDefaultOrg_copiesPathsNoFilesystemHelper() {
      FSOrganization fromOrg = new FSOrganization("fromOrg");
      FSOrganization toOrg   = new FSOrganization("toOrg");

      try(MockedStatic<DataSpace> ds = mockStatic(DataSpace.class);
          MockedStatic<Organization> org = mockStatic(Organization.class);
          MockedStatic<AbstractFileSystem> afs = mockStatic(AbstractFileSystem.class)) {

         DataSpace mockDs = mock(DataSpace.class);
         ds.when(DataSpace::getDataSpace).thenReturn(mockDs);
         when(mockDs.getOrgScopedPaths(fromOrg)).thenReturn(new String[]{"portal/fromOrg/file.css"});

         org.when(Organization::getDefaultOrganizationID).thenReturn("defaultOrg"); // not fromOrg

         provider.callCopyDataSpace(fromOrg, toOrg, false);

         verify(mockDs).copy("portal/fromOrg/file.css", "portal/toOrg/file.css");
         verify(mockDs, never()).rename(any(), any());
         afs.verify(() -> AbstractFileSystem.getOrgPaths(null), never());
      }
   }

   // [Path C] replace=false, fromOrg IS the default org → copy called AND filesystem helper triggered
   @Test
   void copyDataSpace_replaceFalse_isDefaultOrg_triggersFilesystemHelper() {
      FSOrganization fromOrg = new FSOrganization("defaultOrg");
      FSOrganization toOrg   = new FSOrganization("toOrg");

      try(MockedStatic<DataSpace> ds = mockStatic(DataSpace.class);
          MockedStatic<Organization> org = mockStatic(Organization.class);
          MockedStatic<AbstractFileSystem> afs = mockStatic(AbstractFileSystem.class);
          MockedStatic<DefaultBlockSystem> dbs = mockStatic(DefaultBlockSystem.class)) {

         DataSpace mockDs = mock(DataSpace.class);
         ds.when(DataSpace::getDataSpace).thenReturn(mockDs);
         when(mockDs.getOrgScopedPaths(fromOrg)).thenReturn(new String[]{});

         org.when(Organization::getDefaultOrganizationID).thenReturn("defaultOrg");

         // Return empty arrays so copyFileSystemFileAndBlockSystemFile inner loops are no-ops
         afs.when(() -> AbstractFileSystem.getOrgPaths(null)).thenReturn(new String[0]);
         dbs.when(() -> DefaultBlockSystem.getOrgPaths(null)).thenReturn(new String[0]);

         provider.callCopyDataSpace(fromOrg, toOrg, false);

         // Presence of this call proves copyFileSystemFileAndBlockSystemFile was reached
         afs.verify(() -> AbstractFileSystem.getOrgPaths(null));
      }
   }

   // -------------------------------------------------------------------------
   // copyFileSystemFileAndBlockSystemFile — private; tested via reflection
   // -------------------------------------------------------------------------

   // [Path A] path exists in dataSpace → copy called
   @Test
   void copyFileSystemFileAndBlockSystemFile_existingPath_copied() {
      try(MockedStatic<DataSpace> ds = mockStatic(DataSpace.class);
          MockedStatic<AbstractFileSystem> afs = mockStatic(AbstractFileSystem.class);
          MockedStatic<DefaultBlockSystem> dbs = mockStatic(DefaultBlockSystem.class)) {

         DataSpace mockDs = mock(DataSpace.class);
         ds.when(DataSpace::getDataSpace).thenReturn(mockDs);

         afs.when(() -> AbstractFileSystem.getOrgPaths(null)).thenReturn(new String[]{"mv/data"});
         afs.when(() -> AbstractFileSystem.getOrgFileName("mv/data", "fromOrg")).thenReturn("mv/data/fromOrg");
         afs.when(() -> AbstractFileSystem.getOrgFileName("mv/data", "toOrg")).thenReturn("mv/data/toOrg");

         dbs.when(() -> DefaultBlockSystem.getOrgPaths(null)).thenReturn(new String[0]);

         when(mockDs.exists(null, "mv/data/fromOrg")).thenReturn(true);

         provider.callCopyFileSystemFileAndBlockSystemFile("fromOrg", "toOrg");

         verify(mockDs).copy("mv/data/fromOrg", "mv/data/toOrg");
      }
   }

   // [Path B] path does not exist → copy NOT called (early continue)
   @Test
   void copyFileSystemFileAndBlockSystemFile_nonExistingPath_notCopied() {
      try(MockedStatic<DataSpace> ds = mockStatic(DataSpace.class);
          MockedStatic<AbstractFileSystem> afs = mockStatic(AbstractFileSystem.class);
          MockedStatic<DefaultBlockSystem> dbs = mockStatic(DefaultBlockSystem.class)) {

         DataSpace mockDs = mock(DataSpace.class);
         ds.when(DataSpace::getDataSpace).thenReturn(mockDs);

         afs.when(() -> AbstractFileSystem.getOrgPaths(null)).thenReturn(new String[]{"mv/data"});
         afs.when(() -> AbstractFileSystem.getOrgFileName("mv/data", "fromOrg")).thenReturn("mv/data/fromOrg");

         dbs.when(() -> DefaultBlockSystem.getOrgPaths(null)).thenReturn(new String[0]);

         when(mockDs.exists(null, "mv/data/fromOrg")).thenReturn(false);

         provider.callCopyFileSystemFileAndBlockSystemFile("fromOrg", "toOrg");

         verify(mockDs, never()).copy(any(), any());
      }
   }

   // -------------------------------------------------------------------------
   // StubProvider — mirrors AbstractEditableAuthenticationProviderTest.StubProvider
   // but adds only the reflection helpers needed for [mockStatic] methods.
   // Does NOT duplicate the [unit] helpers (copyIdentityRoles, etc.) — those are
   // in the other test class.
   // -------------------------------------------------------------------------

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

      // clearScopedProperties is protected — directly callable from same package; no helper needed.

      void callCopyScopedProperties(String fromOrgId, String newOrgId, boolean replace) {
         try {
            var m = AbstractEditableAuthenticationProvider.class.getDeclaredMethod(
               "copyScopedProperties", String.class, String.class, boolean.class);
            m.setAccessible(true);
            m.invoke(this, fromOrgId, newOrgId, replace);
         }
         catch(ReflectiveOperationException e) {
            throw new AssertionError("reflection failed: copyScopedProperties", e);
         }
      }

      void callCopyThemes(String fromOrgId, String toOrgId, boolean replace) {
         try {
            var m = AbstractEditableAuthenticationProvider.class.getDeclaredMethod(
               "copyThemes", String.class, String.class, boolean.class);
            m.setAccessible(true);
            m.invoke(this, fromOrgId, toOrgId, replace);
         }
         catch(ReflectiveOperationException e) {
            throw new AssertionError("reflection failed: copyThemes", e);
         }
      }

      void callCopyDataSpace(Organization fromOrg, Organization toOrg, boolean replace) {
         try {
            var m = AbstractEditableAuthenticationProvider.class.getDeclaredMethod(
               "copyDataSpace", Organization.class, Organization.class, boolean.class);
            m.setAccessible(true);
            m.invoke(this, fromOrg, toOrg, replace);
         }
         catch(ReflectiveOperationException e) {
            throw new AssertionError("reflection failed: copyDataSpace", e);
         }
      }

      void callCopyFileSystemFileAndBlockSystemFile(String fromOrgId, String toOrgId) {
         try {
            var m = AbstractEditableAuthenticationProvider.class.getDeclaredMethod(
               "copyFileSystemFileAndBlockSystemFile", String.class, String.class);
            m.setAccessible(true);
            m.invoke(this, fromOrgId, toOrgId);
         }
         catch(ReflectiveOperationException e) {
            throw new AssertionError("reflection failed: copyFileSystemFileAndBlockSystemFile", e);
         }
      }
   }
}
