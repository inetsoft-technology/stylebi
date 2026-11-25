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
package inetsoft.mv;

import inetsoft.mv.fs.internal.ClusterUtil;
import inetsoft.mv.trans.UserInfo;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link SharedMVUtil} decides which materialized view definitions share one MV: a definition
 * that can be shared by another ({@link MVDef#isSharedBy}) takes over its sheets and the other
 * one is dropped, sheets are moved off an MV they can no longer share, and an MV a re-analyzed
 * sheet no longer shares is marked invalid.
 * <p>
 * {@code isSharedBy} has its own rules (columns, data, scope, parent viewsheets) and is given
 * here as a table of which definition can be shared by which, so the tests cover the decisions
 * of SharedMVUtil only. The MV metadata (registered and invalid sheets) is real; the manager
 * is a mock that holds a list of stored definitions.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SharedMVUtilTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SharedMVUtilTest {
   @Configuration
   static class TestConfig {
      // MVManager.getManager() returns this bean
      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }
   }

   @Autowired
   MVManager manager;

   @BeforeEach
   void setUp() {
      reset(manager);
      stored.clear();
      sharedBy.clear();
      hints.clear();
      MVMetaData.SHARE_HINT.remove();

      when(manager.list(anyBoolean(), any())).thenAnswer(inv -> {
         MVManager.MVFilter filter = inv.getArgument(1);
         return stored.stream().filter(filter::accept).toArray(MVDef[]::new);
      });
      when(manager.list(anyBoolean())).thenAnswer(inv -> stored.toArray(new MVDef[0]));
      // as the manager does: the sheet is unregistered from the definition
      when(manager.remove(any(MVDef.class), anyString()))
         .thenAnswer(inv -> ((MVDef) inv.getArgument(0)).getMetaData()
            .unregister(inv.getArgument(1)));
   }

   @AfterEach
   void tearDown() {
      MVMetaData.SHARE_HINT.remove();
   }

   // ---------------------------------------------------------------- shareCreatedMV

   // the new MV can be shared by a stored one: the stored definition is dropped and its sheets
   // move to the new one
   @Test
   void createdMVTakesOverAStoredMVItCanBeSharedBy() {
      MVDef created = def("created", false, vs("f/a"));
      MVDef old = stored("old", false, vs("f/b"));
      canBeSharedBy(created, old);

      try(MockedStatic<ClusterUtil> ignored = mockStatic(ClusterUtil.class)) {
         assertSame(created, SharedMVUtil.shareCreatedMV(created));
      }

      verify(manager).remove(old);
      verify(manager).add(created);
      assertEquals(Set.of(vs("f/a"), vs("f/b")), sheets(created));
   }

   // a stored MV can be shared by the new one: the new definition is dropped, the stored one
   // takes its sheets and is returned
   @Test
   void storedMVTakesOverTheCreatedMV() {
      MVDef created = def("created", false, vs("f/a"));
      MVDef old = stored("old", false, vs("f/b"));
      canBeSharedBy(old, created);

      try(MockedStatic<ClusterUtil> ignored = mockStatic(ClusterUtil.class)) {
         assertSame(old, SharedMVUtil.shareCreatedMV(created));
      }

      verify(manager).remove(created);
      verify(manager).add(old);
      assertEquals(Set.of(vs("f/a"), vs("f/b")), sheets(old));
   }

   // worksheet MVs are only compared with worksheet MVs, viewsheet MVs with viewsheet MVs
   @Test
   void createdMVIsOnlyComparedWithMVsOfTheSameKind() {
      MVDef created = def("created", true, ws("f/w"));
      MVDef old = stored("old", false, vs("f/b"));
      canBeSharedBy(created, old);
      canBeSharedBy(old, created);

      try(MockedStatic<ClusterUtil> ignored = mockStatic(ClusterUtil.class)) {
         assertSame(created, SharedMVUtil.shareCreatedMV(created));
      }

      verify(manager, never()).remove(any(MVDef.class));
      verify(manager, never()).add(any(MVDef.class));
      assertEquals(Set.of(ws("f/w")), sheets(created));
   }

   // the stored entry of the new MV itself (same name) is not compared with it
   @Test
   void storedEntryOfTheSameNameIsSkipped() {
      MVDef created = def("mv1", false, vs("f/a"));
      MVDef sameName = stored("mv1", false, vs("f/a"));
      canBeSharedBy(created, sameName);

      try(MockedStatic<ClusterUtil> ignored = mockStatic(ClusterUtil.class)) {
         assertSame(created, SharedMVUtil.shareCreatedMV(created));
      }

      verify(manager, never()).remove(any(MVDef.class));
   }

   // Bug #82223: the cluster file deleted after a merge is the file of the MV that is removed,
   // not of the one that is kept, in both directions (this test and the next). It only deletes on
   // a cluster (server.type server_cluster).
   @Test
   void clusterFileOfTheRemovedStoredMVIsDeleted() {
      MVDef created = def("created", false, vs("f/a"));
      MVDef old = stored("old", false, vs("f/b"));
      canBeSharedBy(created, old);

      try(MockedStatic<ClusterUtil> cluster = mockStatic(ClusterUtil.class)) {
         SharedMVUtil.shareCreatedMV(created);
         // created is kept, old is dropped
         cluster.verify(() -> ClusterUtil.deleteClusterMV("old"));
         cluster.verify(() -> ClusterUtil.deleteClusterMV("created"), never());
      }
   }

   // the other direction: see the test above
   @Test
   void clusterFileOfTheRemovedCreatedMVIsDeleted() {
      MVDef created = def("created", false, vs("f/a"));
      MVDef old = stored("old", false, vs("f/b"));
      canBeSharedBy(old, created);

      try(MockedStatic<ClusterUtil> cluster = mockStatic(ClusterUtil.class)) {
         SharedMVUtil.shareCreatedMV(created);
         // old is kept, created is dropped
         cluster.verify(() -> ClusterUtil.deleteClusterMV("created"));
         cluster.verify(() -> ClusterUtil.deleteClusterMV("old"), never());
      }
   }

   // ---------------------------------------------------------------- shareAnalyzedMV

   // an analyzed definition that a stored MV can share is replaced by the stored MV, which takes
   // its sheets and is marked as existing
   @Test
   void analyzedMVIsReplacedByAStoredMVThatCanShareIt() {
      MVDef analyzed = def("analyzed", false, vs("f/a"));
      MVDef old = stored("old", false, vs("f/b"));
      canBeSharedBy(old, analyzed);

      List<MVDef> result = SharedMVUtil.shareAnalyzedMV(new ArrayList<>(List.of(analyzed)), hints);

      assertEquals(List.of(old), result);
      assertTrue(old.exist);
      assertEquals(Set.of(vs("f/a"), vs("f/b")), sheets(old));
      verify(manager, never()).remove(any(MVDef.class), anyString());
      assertTrue(hints.isEmpty());
   }

   // a re-analyzed sheet whose new definition the stored MV can no longer share is taken off that
   // MV; the new definition is returned to be created
   @Test
   void sheetIsTakenOffAStoredMVItCanNoLongerShare() {
      MVDef analyzed = def("analyzed", false, vs("f/a"));
      MVDef old = stored("old", false, vs("f/a"), vs("f/b"));

      List<MVDef> result = SharedMVUtil.shareAnalyzedMV(new ArrayList<>(List.of(analyzed)), hints);

      assertEquals(List.of(analyzed), result);
      verify(manager).remove(old, vs("f/a"));
      assertEquals(Set.of(vs("f/b")), sheets(old));
   }

   // analyzed definitions that can share each other become one definition with both sheets
   @Test
   void analyzedMVsThatCanShareEachOtherAreMerged() {
      MVDef a1 = def("a1", false, vs("f/a"));
      MVDef a2 = def("a2", false, vs("f/b"));
      canBeSharedBy(a2, a1);

      List<MVDef> result = SharedMVUtil.shareAnalyzedMV(new ArrayList<>(List.of(a1, a2)), hints);

      assertEquals(List.of(a2), result);
      assertEquals(Set.of(vs("f/a"), vs("f/b")), sheets(a2));
   }

   // the reason a stored MV can't share the analyzed one is reported for the analyzed sheet;
   // a stored MV in the recycle bin is not reported
   @Test
   void reasonAStoredMVIsNotSharedIsReported() {
      MVDef analyzed = def("analyzed", false, vs("f/a"));
      stored("old", false, vs("f/b"));
      stored("deleted", false, vs("Recycle Bin/c"));
      MVMetaData.SHARE_HINT.set("columns differ");

      SharedMVUtil.shareAnalyzedMV(new ArrayList<>(List.of(analyzed)), hints);

      assertEquals(1, hints.size(), hints.toString());
      UserInfo hint = hints.get(0);
      assertEquals("f/a", hint.getSheetName());
      assertEquals("T", hint.getBoundTable());
      assertTrue(hint.getMessage().startsWith("columns differ, "), hint.getMessage());
      assertTrue(hint.getMessage().contains("f/b"), hint.getMessage());
   }

   // two analyzed association MVs that can't share each other don't give a warning, other
   // definitions do
   @Test
   void analyzedAssociationMVsThatAreNotSharedGiveNoWarning() {
      MVDef a1 = def("a1", false, vs("f/a"));
      MVDef a2 = def("a2", false, vs("f/b"));
      MVMetaData.SHARE_HINT.set("columns differ");

      SharedMVUtil.shareAnalyzedMV(new ArrayList<>(List.of(a1, a2)), hints);
      assertEquals(1, hints.size(), hints.toString());

      hints.clear();
      when(a1.isAssociationMV()).thenReturn(true);
      when(a2.isAssociationMV()).thenReturn(true);
      MVMetaData.SHARE_HINT.set("columns differ");

      SharedMVUtil.shareAnalyzedMV(new ArrayList<>(List.of(a1, a2)), hints);
      assertTrue(hints.isEmpty(), hints.toString());
   }

   // a sheet is kept apart per chain of parent viewsheets: re-analyzing it as a top viewsheet
   // doesn't take it off the MV it uses inside a parent viewsheet
   @Test
   void sheetUnderAParentViewsheetIsNotTakenOffByATopLevelAnalysis() {
      MVDef analyzed = def("analyzed", false, vs("f/a"));
      MVDef embedded = stored("embedded", false, vs("f/a"));
      when(embedded.getParentVsIds()).thenReturn(List.of(vs("f/parent")));

      SharedMVUtil.shareAnalyzedMV(new ArrayList<>(List.of(analyzed)), hints);

      verify(manager, never()).remove(any(MVDef.class), anyString());
      assertEquals(Set.of(vs("f/a")), sheets(embedded));
   }

   // ---------------------------------------------------------------- checkMVValid

   // a stored MV that the re-analyzed sheet can no longer share is marked invalid for that sheet
   // and changed; the analyzed definition is returned
   @Test
   void storedMVTheSheetNoLongerSharesIsMarkedInvalid() {
      MVDef analyzed = def("analyzed", false, vs("f/a"));
      MVDef old = stored("old", false, vs("f/a"));

      List<MVDef> result = SharedMVUtil.checkMVValid(new ArrayList<>(List.of(analyzed)));

      assertEquals(List.of(analyzed), result);
      assertFalse(old.getMetaData().isValid());
      verify(old).setChanged(true);
   }

   // a stored MV that can share the re-analyzed definition is valid again, and the analyzed
   // definition is not returned
   @Test
   void storedMVThatSharesTheSheetIsValidAgain() {
      MVDef analyzed = def("analyzed", false, vs("f/a"));
      MVDef old = stored("old", false, vs("f/a"));
      old.getMetaData().invalidRegister(vs("f/a"));
      canBeSharedBy(old, analyzed);

      List<MVDef> result = SharedMVUtil.checkMVValid(new ArrayList<>(List.of(analyzed)));

      assertTrue(result.isEmpty(), result.toString());
      assertTrue(old.getMetaData().isValid());
      verify(old, never()).setChanged(anyBoolean());
   }

   // a stored MV that is not shareable is not marked invalid
   @Test
   void storedMVThatIsNotShareableIsNotMarkedInvalid() {
      MVDef analyzed = def("analyzed", false, vs("f/a"));
      MVDef old = stored("old", false, vs("f/a"));
      when(old.isShareable()).thenReturn(false);

      SharedMVUtil.checkMVValid(new ArrayList<>(List.of(analyzed)));

      assertTrue(old.getMetaData().isValid());
      verify(old, never()).setChanged(anyBoolean());
   }

   // ---------------------------------------------------------------- removeMV

   // removing the MVs of a folder takes only the sheets of that folder off the MVs; once an MV
   // has no sheet left its dependencies are removed
   @Test
   void removeMVOfAFolderTakesOffOnlyTheSheetsOfThatFolder() {
      MVDef shared = stored("shared", false, vs("f1/a"), vs("f2/b"));
      MVDef single = stored("single", false, vs("f1/c"));

      SharedMVUtil.removeMV("f1");

      verify(manager).remove(shared, vs("f1/a"));
      verify(manager).remove(single, vs("f1/c"));
      verify(manager, never()).remove(shared, vs("f2/b"));
      assertEquals(Set.of(vs("f2/b")), sheets(shared));
      verify(manager).removeDependencies(AssetEntry.createAssetEntry(vs("f1/c")));
      verify(manager, never()).removeDependencies(AssetEntry.createAssetEntry(vs("f1/a")));
   }

   // removing the MV of a worksheet only looks at worksheet MVs that the worksheet is registered to
   @Test
   void removeMVOfAWorksheetOnlyTouchesItsWorksheetMVs() {
      AssetEntry entry = AssetEntry.createAssetEntry(ws("f/w"));
      MVDef wsMV = stored("wsMV", true, ws("f/w"));
      MVDef otherWsMV = stored("otherWsMV", true, ws("f/other"));
      MVDef vsMV = stored("vsMV", false, ws("f/w"));

      SharedMVUtil.removeMV(entry);

      verify(manager).remove(wsMV, ws("f/w"));
      verify(manager).removeDependencies(entry);
      verify(manager, never()).remove(eq(otherWsMV), anyString());
      verify(manager, never()).remove(eq(vsMV), anyString());
   }

   // ---------------------------------------------------------------- helpers

   // a definition registered to the sheets; isSharedBy follows the sharedBy table, shareMV
   // registers the sheets of the other definition as MVDef.shareMV does
   private MVDef def(String name, boolean wsMV, String... sheets) {
      MVDef def = mock(MVDef.class);
      MVMetaData data = new MVMetaData();

      for(String sheet : sheets) {
         data.register(sheet);
      }

      when(def.getName()).thenReturn(name);
      when(def.isWSMV()).thenReturn(wsMV);
      when(def.getMetaData()).thenReturn(data);
      when(def.getBoundTable()).thenReturn("T");
      when(def.isShareable()).thenReturn(true);
      when(def.isSharedBy(any())).thenAnswer(
         inv -> sharedBy.getOrDefault(def, Set.of()).contains(inv.getArgument(0)));
      doAnswer(inv -> {
         data.register(((MVDef) inv.getArgument(0)).getMetaData());
         return null;
      }).when(def).shareMV(any());
      when(def.toString()).thenReturn(name);
      return def;
   }

   private MVDef stored(String name, boolean wsMV, String... sheets) {
      MVDef def = def(name, wsMV, sheets);
      stored.add(def);
      return def;
   }

   // def.isSharedBy(other) is true
   private void canBeSharedBy(MVDef def, MVDef other) {
      sharedBy.computeIfAbsent(def, k -> new HashSet<>()).add(other);
   }

   private static Set<String> sheets(MVDef def) {
      return Set.of(def.getMetaData().getRegisteredSheets());
   }

   private static String vs(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, path, null)
         .toIdentifier();
   }

   private static String ws(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, path, null)
         .toIdentifier();
   }

   private final List<MVDef> stored = new ArrayList<>();
   private final Map<MVDef, Set<MVDef>> sharedBy = new HashMap<>();
   private final List<UserInfo> hints = new ArrayList<>();
}
