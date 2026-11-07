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
package inetsoft.web.admin.ai.recyclebin;

import inetsoft.sree.RepletRegistry;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.Tool;
import inetsoft.web.RecycleBin;
import inetsoft.web.RecycleUtils;
import inetsoft.web.security.auth.MissingResourceException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.Arrays;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #76672 follow-up: {@code requireRestorableSource} is the source-side twin of {@code
 * wouldCollide}, mirroring the lookups {@code RecycleUtils.restoreSheet}/{@code restoreWSFolder}/
 * {@code restoreRepositoryFolder} run internally BEFORE their own first mutating statement, so
 * {@code RecycleBinChangesetApplyService.applyRestore} can gate {@code mutationEntered} on them.
 * These tests pin the mirror to what {@code RecycleUtils} actually does -- if the two ever drift,
 * the gate silently becomes either too wide or too narrow.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class RecycleBinServiceTest {
   @Mock private RecycleBin recycleBin;
   @Mock private AssetRepository assetRepository;
   @Mock private SecurityProvider securityProvider;
   @Mock private Principal user;

   private RecycleBinService service;

   @BeforeEach
   void setUp() {
      service = new RecycleBinService(recycleBin, assetRepository, securityProvider);
   }

   private static RecycleBin.Entry entry(int type, int scope, String path, String originalPath,
                                         IdentityID owner)
   {
      RecycleBin.Entry entry = new RecycleBin.Entry();
      entry.setPath(path);
      entry.setOriginalPath(originalPath);
      entry.setName(path);
      entry.setType(type);
      entry.setOriginalScope(scope);
      entry.setOriginalUser(owner);
      entry.setTimestamp(new Date());
      return entry;
   }

   private static AssetEntry resolved(AssetEntry.Type type, String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, type, path, null);
   }

   private static ArgumentMatcher<AssetEntry> is(int scope, AssetEntry.Type type, String path) {
      return e -> e != null && e.getScope() == scope && e.getType() == type &&
         path.equals(e.getPath());
   }

   // ---------------------------------------------------------------- sheets

   @Test void passesWhenTheRecycledWorksheetIsStillResolvable() throws Exception {
      RecycleBin.Entry e = entry(RepositoryEntry.WORKSHEET, AssetRepository.GLOBAL_SCOPE,
         "Recycle Bin/uuid1", "folder1/ws1", new IdentityID("admin", "host-org"));
      when(assetRepository.getAssetEntry(argThat(
         is(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "Recycle Bin/uuid1"))))
         .thenReturn(resolved(AssetEntry.Type.WORKSHEET, "Recycle Bin/uuid1"));

      assertDoesNotThrow(() -> service.requireRestorableSource(e, user));
   }

   @Test void throwsWhenTheRecycledWorksheetWasPurgedConcurrently() throws Exception {
      RecycleBin.Entry e = entry(RepositoryEntry.WORKSHEET, AssetRepository.GLOBAL_SCOPE,
         "Recycle Bin/uuid1", "folder1/ws1", new IdentityID("admin", "host-org"));
      when(assetRepository.getAssetEntry(any())).thenReturn(null);

      assertThrows(MissingResourceException.class,
                   () -> service.requireRestorableSource(e, user));
   }

   /** RecycleUtils.getSheetEntry falls back to VIEWSHEET_SNAPSHOT when the VIEWSHEET lookup misses;
    * a snapshot-only entry must NOT be reported as vanished. */
   @Test void passesWhenTheDashboardResolvesOnlyAsAViewsheetSnapshot() throws Exception {
      RecycleBin.Entry e = entry(RepositoryEntry.VIEWSHEET, AssetRepository.GLOBAL_SCOPE,
         "Recycle Bin/uuid2", "dashboards/vs1", new IdentityID("admin", "host-org"));
      when(assetRepository.getAssetEntry(argThat(
         is(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "Recycle Bin/uuid2"))))
         .thenReturn(null);
      when(assetRepository.getAssetEntry(argThat(
         is(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET_SNAPSHOT, "Recycle Bin/uuid2"))))
         .thenReturn(resolved(AssetEntry.Type.VIEWSHEET_SNAPSHOT, "Recycle Bin/uuid2"));

      assertDoesNotThrow(() -> service.requireRestorableSource(e, user));
   }

   /** Regression: restoreSheet re-reads the entry a second time under the resolved type. For a My
    * Dashboards dashboard that only resolved through the GLOBAL snapshot fallback, that re-read is
    * user-scoped and comes back null, so restoreSheet throws -- still strictly before validatePath.
    * The gate has to mirror that second lookup, or the entry passes here and is then misreported as
    * rollback-failed, which is exactly what this whole change exists to prevent. */
   @Test void throwsWhenTheSecondTypeQualifiedLookupMissesForAMyDashboardsSnapshot()
      throws Exception
   {
      IdentityID owner = new IdentityID("jdoe", "host-org");
      RecycleBin.Entry e = entry(RepositoryEntry.VIEWSHEET, AssetRepository.USER_SCOPE,
         "uuid8", "vs2", owner);
      // first lookup: user-scoped VIEWSHEET -> miss; fallback: global snapshot -> hit;
      // second lookup: user-scoped VIEWSHEET_SNAPSHOT -> miss.
      when(assetRepository.getAssetEntry(argThat(
         is(AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET, "uuid8")))).thenReturn(null);
      when(assetRepository.getAssetEntry(argThat(
         is(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET_SNAPSHOT,
            Tool.MY_DASHBOARD + "/uuid8"))))
         .thenReturn(resolved(AssetEntry.Type.VIEWSHEET_SNAPSHOT, "uuid8"));
      when(assetRepository.getAssetEntry(argThat(
         is(AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET_SNAPSHOT, "uuid8"))))
         .thenReturn(null);

      assertThrows(MissingResourceException.class,
                   () -> service.requireRestorableSource(e, user));
   }

   /** A user-scoped entry is looked up under USER_SCOPE with the MY_DASHBOARD prefix stripped --
    * RecycleUtils.restoreSheet prepends it to build the trash path, getAssetEntry strips it back. */
   @Test void looksUpAUserScopedSheetInUserScopeWithTheOwner() throws Exception {
      IdentityID owner = new IdentityID("jdoe", "host-org");
      RecycleBin.Entry e = entry(RepositoryEntry.WORKSHEET, AssetRepository.USER_SCOPE,
         "uuid3", "ws2", owner);
      when(assetRepository.getAssetEntry(argThat(
         is(AssetRepository.USER_SCOPE, AssetEntry.Type.WORKSHEET, "uuid3"))))
         .thenReturn(resolved(AssetEntry.Type.WORKSHEET, "uuid3"));

      assertDoesNotThrow(() -> service.requireRestorableSource(e, user));

      // Two lookups, mirroring restoreSheet's getSheetEntry followed by its type-qualified re-read;
      // both must be user-scoped, owner-carrying and prefix-stripped.
      ArgumentCaptor<AssetEntry> captor = ArgumentCaptor.forClass(AssetEntry.class);
      verify(assetRepository, times(2)).getAssetEntry(captor.capture());

      for(AssetEntry candidate : captor.getAllValues()) {
         assertEquals(AssetRepository.USER_SCOPE, candidate.getScope());
         assertEquals(owner, candidate.getUser());
         assertFalse(candidate.getPath().startsWith(Tool.MY_DASHBOARD));
      }
   }

   // ---------------------------------------------------------------- ws folders

   @Test void throwsWhenTheRecycledWorksheetFolderIsGone() throws Exception {
      RecycleBin.Entry e = entry(RepositoryEntry.WORKSHEET_FOLDER, AssetRepository.GLOBAL_SCOPE,
         "Recycle Bin/uuid4", "folder1/sub", null);
      when(assetRepository.getAssetEntry(any())).thenReturn(null);

      assertThrows(MissingResourceException.class,
                   () -> service.requireRestorableSource(e, user));
   }

   /** RecycleUtils.restoreWSFolder resolves the folder a second time through its own parent
    * listing (getFolderAssetEntry) and dereferences the result unchecked -- a miss there is still
    * strictly pre-mutation, so it must be gated too. */
   @Test void throwsWhenTheWorksheetFolderIsMissingFromItsParentListing() throws Exception {
      RecycleBin.Entry e = entry(RepositoryEntry.WORKSHEET_FOLDER, AssetRepository.GLOBAL_SCOPE,
         "Recycle Bin/uuid4", "folder1/sub", null);
      when(assetRepository.getAssetEntry(any()))
         .thenReturn(resolved(AssetEntry.Type.FOLDER, "Recycle Bin/uuid4"));
      when(assetRepository.getEntries(any(), eq(user), eq(ResourceAction.READ), any()))
         .thenReturn(new AssetEntry[0]);

      assertThrows(MissingResourceException.class,
                   () -> service.requireRestorableSource(e, user));
   }

   /** Regression: a user-scoped worksheet folder's trash path is a bare "Recycle Bin/&lt;uuid&gt;" with
    * no My Dashboards prefix, so the scope must be picked off the OWNER (what restoreWSFolder does),
    * not off the path. Keying off the path refused every private-folder restore outright. */
   @Test void looksUpAUserOwnedWorksheetFolderInUserScopeDespiteThePrefixlessTrashPath()
      throws Exception
   {
      IdentityID owner = new IdentityID("jdoe", "host-org");
      RecycleBin.Entry e = entry(RepositoryEntry.WORKSHEET_FOLDER, AssetRepository.USER_SCOPE,
         "Recycle Bin/uuid7", "folder1/sub", owner);
      AssetEntry folder = new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.FOLDER,
                                         "Recycle Bin/uuid7", owner);
      when(assetRepository.getAssetEntry(argThat(
         is(AssetRepository.USER_SCOPE, AssetEntry.Type.FOLDER, "Recycle Bin/uuid7"))))
         .thenReturn(folder);
      when(assetRepository.getEntries(any(), eq(user), eq(ResourceAction.READ), any()))
         .thenReturn(new AssetEntry[]{ folder });

      assertDoesNotThrow(() -> service.requireRestorableSource(e, user));

      ArgumentCaptor<AssetEntry> captor = ArgumentCaptor.forClass(AssetEntry.class);
      verify(assetRepository).getAssetEntry(captor.capture());
      assertEquals(AssetRepository.USER_SCOPE, captor.getValue().getScope());
      assertEquals(owner, captor.getValue().getUser());
   }

   @Test void passesWhenTheWorksheetFolderIsListedByItsParent() throws Exception {
      RecycleBin.Entry e = entry(RepositoryEntry.WORKSHEET_FOLDER, AssetRepository.GLOBAL_SCOPE,
         "Recycle Bin/uuid4", "folder1/sub", null);
      AssetEntry folder = resolved(AssetEntry.Type.FOLDER, "Recycle Bin/uuid4");
      when(assetRepository.getAssetEntry(any())).thenReturn(folder);
      when(assetRepository.getEntries(any(), eq(user), eq(ResourceAction.READ), any()))
         .thenReturn(new AssetEntry[]{ folder });

      assertDoesNotThrow(() -> service.requireRestorableSource(e, user));
   }

   // ---------------------------------------------------------------- repository folders

   /** restoreRepositoryFolder opens with getRegistry(originalPath, owner) and only then calls
    * checkParentFolderExist, which already mutates -- so getRegistry is the whole gate here, the
    * same one applyPurge's repository-folder branch uses. */
   @Test void repositoryFolderGateIsTheRegistryLoad() throws Exception {
      RecycleBin.Entry e = entry(RepositoryEntry.FOLDER, AssetRepository.GLOBAL_SCOPE,
         "Recycle Bin/uuid5", "dashboards/folder1", new IdentityID("admin", "host-org"));

      try(MockedStatic<RecycleUtils> recycleUtils = mockStatic(RecycleUtils.class,
         Answers.CALLS_REAL_METHODS))
      {
         recycleUtils.when(() -> RecycleUtils.getRegistry("dashboards/folder1", e.getOriginalUser()))
            .thenThrow(new Exception("simulated registry load failure"));

         Exception thrown = assertThrows(Exception.class,
                                         () -> service.requireRestorableSource(e, user));
         assertEquals("simulated registry load failure", thrown.getMessage());
      }

      verifyNoInteractions(assetRepository);
   }

   // ---------------------------------------------------------------- occupantOf (bug #80307)

   /** Mirrors AutoSaveRecycleBinServiceTest's own occupantWith helper: stubs assetRepository
    * .containsEntry to answer true only for the types listed as "present" at the destination. */
   private RecycleBinService.Occupant occupantWith(int entryType, String originalPath,
                                                    AssetEntry.Type... present)
      throws Exception
   {
      RecycleBin.Entry e = entry(entryType, AssetRepository.GLOBAL_SCOPE, "Recycle Bin/uuid9",
         originalPath, null);
      java.util.Set<AssetEntry.Type> types = new java.util.HashSet<>(Arrays.asList(present));
      lenient().when(assetRepository.containsEntry(any(AssetEntry.class)))
         .thenAnswer(inv -> types.contains(((AssetEntry) inv.getArgument(0)).getType()));
      return service.occupantOf(e);
   }

   @Test void occupantOfReportsFolderForAWorksheetEntry() throws Exception {
      assertEquals(RecycleBinService.Occupant.FOLDER, occupantWith(
         RepositoryEntry.WORKSHEET, "ws1", AssetEntry.Type.FOLDER));
   }

   @Test void occupantOfReportsFolderAndSnapshotForAViewsheetEntry() throws Exception {
      assertEquals(RecycleBinService.Occupant.FOLDER, occupantWith(
         RepositoryEntry.VIEWSHEET, "vs1", AssetEntry.Type.REPOSITORY_FOLDER));
      assertEquals(RecycleBinService.Occupant.SNAPSHOT, occupantWith(
         RepositoryEntry.VIEWSHEET, "vs2", AssetEntry.Type.VIEWSHEET_SNAPSHOT));
   }

   @Test void occupantOfReportsSameTypeSheetAsOverwritable() throws Exception {
      RecycleBinService.Occupant ws = occupantWith(
         RepositoryEntry.WORKSHEET, "ws1", AssetEntry.Type.WORKSHEET);
      RecycleBinService.Occupant vs = occupantWith(
         RepositoryEntry.VIEWSHEET, "vs1", AssetEntry.Type.VIEWSHEET);

      assertEquals(RecycleBinService.Occupant.SAME_TYPE_SHEET, ws);
      assertEquals(RecycleBinService.Occupant.SAME_TYPE_SHEET, vs);
      assertTrue(ws.overwritable());
   }

   @Test void occupantOfReportsNoneWhenNothingOccupiesTheDestination() throws Exception {
      assertEquals(RecycleBinService.Occupant.NONE,
                   occupantWith(RepositoryEntry.WORKSHEET, "ws1"));
   }

   /** The mirror case bug #80307 also reports: restoring a recycled worksheet FOLDER onto a
    * same-named WORKSHEET must report the real occupant (a worksheet, never overwritable here),
    * not the entry's own "worksheetFolder" type. */
   @Test void occupantOfReportsWorksheetForAWorksheetFolderEntry() throws Exception {
      RecycleBinService.Occupant occupant = occupantWith(
         RepositoryEntry.WORKSHEET_FOLDER, "folder1", AssetEntry.Type.WORKSHEET);

      assertEquals(RecycleBinService.Occupant.WORKSHEET, occupant);
      assertFalse(occupant.overwritable());
      assertEquals("worksheet", occupant.describe("worksheetFolder"));
   }

   /** A genuine folder-on-folder match for a worksheet-folder entry is left classified as
    * overwritable, unchanged from the pre-existing behavior -- bug #80307 is about a type
    * MISMATCH being misdescribed, not about this already-same-typed case. */
   @Test void occupantOfReportsSameTypeSheetForAFolderOnFolderMatch() throws Exception {
      RecycleBinService.Occupant occupant = occupantWith(
         RepositoryEntry.WORKSHEET_FOLDER, "folder1", AssetEntry.Type.FOLDER);

      assertEquals(RecycleBinService.Occupant.SAME_TYPE_SHEET, occupant);
      assertTrue(occupant.overwritable());
   }

   /** The repository (dashboard) folder branch keeps wouldCollide's old registry.isFolder check
    * unchanged (out of scope for bug #80307, see 03-fix.md) -- its only positive signal already
    * names the same type ("folder") the recycled entry itself declares. */
   @Test void occupantOfRepositoryFolderBranchIsUnchanged() throws Exception {
      RecycleBin.Entry e = entry(RepositoryEntry.FOLDER, AssetRepository.GLOBAL_SCOPE,
         "Recycle Bin/uuid10", "dashboards/folder2", new IdentityID("admin", "host-org"));
      RepletRegistry registry = mock(RepletRegistry.class);

      try(MockedStatic<RecycleUtils> recycleUtils = mockStatic(RecycleUtils.class,
         Answers.CALLS_REAL_METHODS))
      {
         recycleUtils.when(() -> RecycleUtils.getRegistry("dashboards/folder2", e.getOriginalUser()))
            .thenReturn(registry);

         when(registry.isFolder("dashboards/folder2")).thenReturn(true);
         assertEquals(RecycleBinService.Occupant.SAME_TYPE_SHEET, service.occupantOf(e));

         when(registry.isFolder("dashboards/folder2")).thenReturn(false);
         assertEquals(RecycleBinService.Occupant.NONE, service.occupantOf(e));
      }

      verifyNoInteractions(assetRepository);
   }
}
