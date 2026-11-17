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
package inetsoft.uql.asset;

/*
 * General-purpose test file for LocalDependencyHandler. Add further scenarios for this class here
 * rather than creating new per-scenario test classes -- keep each scenario's own rationale in a
 * comment block right above its test method(s), the way the logical model scenario below does, so
 * the file-level comment doesn't have to be rewritten every time a new scenario is added.
 */

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.sync.DependenciesInfo;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.RenameTransformObject;
import inetsoft.uql.erm.XEntity;
import inetsoft.uql.erm.XLogicalModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  LocalDependencyHandlerTest.TestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LocalDependencyHandlerTest {
   /*
    * DependencyStorageService is a @Service picked up by component scan in production, so it is not
    * in the test context. The handler reaches it through the static
    * DependencyStorageService.getInstance() -> ConfigurationContext.getSpringBean(), which throws
    * when the bean is missing -- and the handler swallows that, silently writing nothing.
    */
   @Configuration
   static class TestConfiguration {
      @Bean
      public DependencyStorageService dependencyStorageService() {
         return mock(DependencyStorageService.class);
      }
   }

   @BeforeEach
   void setUp() {
      // the context is cached across test methods, so drop the previous method's stubbing
      reset(dependencyStorageService);
      handler = new LocalDependencyHandler(mock(XRepository.class));
   }

   /*
    * Bug #75816: an extended (child) logical model was registered under the flat
    * "datasource/extended name" path, the same form a base model uses. Extended names come from
    * additional connections and can collide with a base model's name, so the edge could be
    * attributed to -- and later deleted from -- the wrong model. Extended models are identified by
    * the three-segment "datasource/base name/extended name" path instead, matching what the delete
    * paths clean up (RepositoryObjectService.deleteNodes(), LogicalModelService.removeModel()).
    *
    * The key side is unchanged: XLogicalModel.getPartition() returns the base model's partition for
    * an extended model, so both register under the base physical view.
    */
   @Test
   void baseLogicalModelIsRegisteredUnderItsOwnName() throws Exception {
      handler.updateModelDependencies(logicalModel(null), true);

      assertEquals(List.of(identifier(AssetEntry.Type.LOGIC_MODEL, LOGICAL_MODEL_NAME)),
                   capturedDependencies(identifier(AssetEntry.Type.PARTITION,
                                                   PHYSICAL_VIEW_NAME)));
   }

   @Test
   void extendedLogicalModelIsQualifiedByItsBaseModel() throws Exception {
      handler.updateModelDependencies(logicalModel(LOGICAL_MODEL_NAME), true);

      assertEquals(List.of(identifier(AssetEntry.Type.LOGIC_MODEL,
                                      LOGICAL_MODEL_NAME + "/" + EXTENDED_NAME)),
                   capturedDependencies(identifier(AssetEntry.Type.PARTITION,
                                                   PHYSICAL_VIEW_NAME)));
   }

   /*
    * Removal has to name the extended model exactly the way registration did, otherwise the rename
    * path leaves a stale edge behind.
    */
   @Test
   void removingAnExtendedLogicalModelUsesTheSameQualifiedName() throws Exception {
      DependenciesInfo stored = new DependenciesInfo();
      AssetEntry extended = new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.LOGIC_MODEL,
         DATA_SOURCE + "/" + LOGICAL_MODEL_NAME + "/" + EXTENDED_NAME, null);
      stored.setDependencies(new ArrayList<>(List.of(extended)));
      when(dependencyStorageService.get(identifier(AssetEntry.Type.PARTITION, PHYSICAL_VIEW_NAME)))
         .thenReturn(stored);

      handler.updateModelDependencies(logicalModel(LOGICAL_MODEL_NAME), false);

      assertTrue(capturedDependencies(identifier(AssetEntry.Type.PARTITION, PHYSICAL_VIEW_NAME))
                    .isEmpty(),
                 "the extended model should no longer depend on the physical view");
   }

   /*
    * Bug #78034: updateWorksheetDependencies() returned from the whole method when it met a bound
    * or attached assembly with no source, such as a variable assembly without a table/query choice
    * source. Every assembly after it was never indexed, so a worksheet holding a variable before a
    * mirror of another worksheet was missing from that worksheet's reverse dependencies. Saving
    * runs the same loop with add=false for the old sheet, so removal stopped there as well.
    */
   @Test
   void assemblyAfterASourcelessVariableIsStillIndexed() throws Exception {
      handler.updateSheetDependencies(worksheetWithVariableBeforeMirror(), worksheet("W2"), true);

      assertEquals(List.of(worksheet("W2").toIdentifier()),
                   capturedDependencies(worksheet("W1").toIdentifier()));
   }

   @Test
   void assemblyAfterASourcelessVariableIsStillRemoved() throws Exception {
      DependenciesInfo stored = new DependenciesInfo();
      stored.setDependencies(new ArrayList<>(List.of(worksheet("W2"))));
      when(dependencyStorageService.get(worksheet("W1").toIdentifier())).thenReturn(stored);

      handler.updateSheetDependencies(worksheetWithVariableBeforeMirror(), worksheet("W2"), false);

      assertTrue(capturedDependencies(worksheet("W1").toIdentifier()).isEmpty(),
                 "W2 should no longer depend on W1");
   }

   /**
    * Creates a worksheet holding a variable assembly with no source followed by an outer mirror of
    * worksheet "W1".
    */
   private Worksheet worksheetWithVariableBeforeMirror() {
      VariableAssembly variable = mock(VariableAssembly.class);
      MirrorTableAssembly mirror = mock(MirrorTableAssembly.class);
      when(mirror.isOuterMirror()).thenReturn(true);
      when(mirror.getEntry()).thenReturn(worksheet("W1"));
      when(mirror.getColumnSelection(false)).thenReturn(new ColumnSelection());

      Worksheet ws = mock(Worksheet.class);
      when(ws.getAssemblies()).thenReturn(new Assembly[] { variable, mirror });
      return ws;
   }

   /*
    * Bug #79060: updateWorksheetDependencies() recorded the worksheet -> worksheet edge only for an
    * outer MirrorTableAssembly. The variable, named group, condition and date range mirrors also
    * implement MirrorAssembly and are created with the mirrored worksheet's entry, and
    * Worksheet.getOuterDependents() already walks all of them, but the reverse index never got
    * their edge. A rename or move of the source worksheet then never reached the mirroring
    * worksheet. The cause varies with the mirror type (every MirrorAssembly implementor); only the
    * index writer was narrower than the worksheet model.
    */
   @Test
   void outerVariableMirrorIsIndexed() throws Exception {
      assertMirrorIndexed(MirrorKind.VARIABLE);
   }

   @Test
   void outerNamedGroupMirrorIsIndexed() throws Exception {
      assertMirrorIndexed(MirrorKind.NAMED_GROUP);
   }

   @Test
   void outerConditionMirrorIsIndexed() throws Exception {
      assertMirrorIndexed(MirrorKind.CONDITION);
   }

   @Test
   void outerDateRangeMirrorIsIndexed() throws Exception {
      assertMirrorIndexed(MirrorKind.DATE_RANGE);
   }

   @Test
   void outerTableMirrorIsStillIndexed() throws Exception {
      assertMirrorIndexed(MirrorKind.TABLE);
   }

   @Test
   void outerMirrorsAreRemovedForEveryType() throws Exception {
      for(MirrorKind kind : MirrorKind.values()) {
         reset(dependencyStorageService);
         DependenciesInfo stored = new DependenciesInfo();
         stored.setDependencies(new ArrayList<>(List.of(worksheet("W2"))));
         when(dependencyStorageService.get(worksheet("W1").toIdentifier())).thenReturn(stored);

         handler.updateSheetDependencies(worksheetWithMirror(kind, true), worksheet("W2"), false);

         assertTrue(capturedDependencies(worksheet("W1").toIdentifier()).isEmpty(),
                    kind + ": W2 should no longer depend on W1");
      }
   }

   @Test
   void innerMirrorIsNotIndexed() throws Exception {
      handler.updateSheetDependencies(worksheetWithMirror(MirrorKind.VARIABLE, false),
                                      worksheet("W2"), true);

      verify(dependencyStorageService, never()).put(eq(worksheet("W1").toIdentifier()), any());
   }

   @Test
   void outerMirrorWithoutAnEntryIsSkipped() throws Exception {
      MirrorVariableAssembly mirror = mock(MirrorVariableAssembly.class);
      when(mirror.isOuterMirror()).thenReturn(true);
      when(mirror.getEntry()).thenReturn(null);
      Worksheet ws = mock(Worksheet.class);
      when(ws.getAssemblies()).thenReturn(new Assembly[] { mirror });

      handler.updateSheetDependencies(ws, worksheet("W2"), true);

      verify(dependencyStorageService, never()).put(any(), any());
   }

   private void assertMirrorIndexed(MirrorKind kind) throws Exception {
      Worksheet ws = worksheetWithMirror(kind, true);
      // the writer must agree with the model walk it was narrower than
      assertEquals(List.of(worksheet("W1")), List.of(ws.getOuterDependents()));

      handler.updateSheetDependencies(ws, worksheet("W2"), true);

      assertEquals(List.of(worksheet("W2").toIdentifier()),
                   capturedDependencies(worksheet("W1").toIdentifier()));
   }

   private enum MirrorKind { VARIABLE, NAMED_GROUP, CONDITION, DATE_RANGE, TABLE }

   /**
    * Creates a worksheet holding one mirror of worksheet "W1" of the given kind, built from the
    * real mirror class over a real base assembly (the table mirror is a mock, as above).
    */
   private Worksheet worksheetWithMirror(MirrorKind kind, boolean outer) {
      Worksheet src = new Worksheet();
      Worksheet ws = new Worksheet();
      AssetEntry entry = worksheet("W1");
      WSAssembly mirror;

      switch(kind) {
      case VARIABLE:
         DefaultVariableAssembly variable = new DefaultVariableAssembly(src, "var");
         variable.setVariable(new AssetVariable());
         mirror = new MirrorVariableAssembly(ws, "mirrorVar", entry, outer, variable);
         break;
      case NAMED_GROUP:
         mirror = new MirrorNamedGroupAssembly(
            ws, "mirrorGroup", entry, outer, new DefaultNamedGroupAssembly(src, "group"));
         break;
      case CONDITION:
         mirror = new MirrorConditionAssembly(
            ws, "mirrorCondition", entry, outer, new DefaultConditionAssembly(src, "cond"));
         break;
      case DATE_RANGE:
         mirror = new MirrorDateRangeAssembly(
            ws, "mirrorRange", entry, outer, new DefaultDateRangeAssembly(src, "range"));
         break;
      default:
         MirrorTableAssembly table = mock(MirrorTableAssembly.class);
         when(table.isOuterMirror()).thenReturn(outer);
         when(table.getEntry()).thenReturn(entry);
         when(table.getColumnSelection(false)).thenReturn(new ColumnSelection());
         Worksheet mocked = mock(Worksheet.class);
         when(mocked.getAssemblies()).thenReturn(new Assembly[] { table });
         when(mocked.getOuterDependents()).thenReturn(new AssetEntry[] { entry });
         return mocked;
      }

      ws.addAssembly(mirror);
      return ws;
   }

   private AssetEntry worksheet(String name) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, name, null);
   }

   /**
    * Creates a logical model bound to {@link #PHYSICAL_VIEW_NAME}.
    *
    * @param baseName the name of the base model, or {@code null} for a base model.
    */
   private XLogicalModel logicalModel(String baseName) {
      XLogicalModel model = mock(XLogicalModel.class);
      when(model.getDataSource()).thenReturn(DATA_SOURCE);
      when(model.getPartition()).thenReturn(PHYSICAL_VIEW_NAME);
      when(model.getEntities()).thenReturn(Collections.enumeration(List.<XEntity>of()));

      if(baseName == null) {
         when(model.getName()).thenReturn(LOGICAL_MODEL_NAME);
      }
      else {
         XLogicalModel base = mock(XLogicalModel.class);
         when(base.getName()).thenReturn(baseName);
         when(model.getName()).thenReturn(EXTENDED_NAME);
         when(model.getBaseModel()).thenReturn(base);
      }

      return model;
   }

   /*
    * Bug #80472: deleteDependencies() visits every key of the dependency store and used to rewrite
    * each one that has dependents, even when the deleted asset is not among them. Only keys that
    * actually list the deleted asset should be written.
    */
   @Test
   void deleteDependenciesOnlyWritesKeysThatListTheEntry() throws Exception {
      AssetEntry deleted = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "deleted", null);
      AssetEntry other = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "other", null);
      DependenciesInfo listing = new DependenciesInfo();
      listing.setDependencies(new ArrayList<>(List.of(deleted, other)));
      DependenciesInfo unrelated = new DependenciesInfo();
      unrelated.setDependencies(new ArrayList<>(List.of(other)));
      when(dependencyStorageService.getKeys(any())).thenReturn(java.util.Set.of("A", "B"));
      when(dependencyStorageService.get("A")).thenReturn(listing);
      when(dependencyStorageService.get("B")).thenReturn(unrelated);

      handler.deleteDependencies(deleted);

      verify(dependencyStorageService).put(eq("A"), any());
      verify(dependencyStorageService, never()).put(eq("B"), any());
      assertEquals(List.of(other), listing.getDependencies());
      assertEquals(List.of(other), unrelated.getDependencies());
   }

   /*
    * Bug #81546: renaming a folder updated the dependency store once per renamed sheet, each time
    * scanning every key (N sheets x K keys reads). The batch overload rewrites the store in a
    * single pass: one key listing, one read per key, one write per changed key.
    */
   @Test
   void batchRenameScansTheDependencyStoreOnce() throws Exception {
      int sheets = 20;
      int keys = 200;
      Map<AssetObject, AssetObject> renames = new LinkedHashMap<>();

      for(int i = 0; i < sheets; i++) {
         renames.put(sheet("old" + i), sheet("new" + i));
      }

      Set<String> keyNames = new LinkedHashSet<>();
      DependenciesInfo[] infos = new DependenciesInfo[keys];

      for(int k = 0; k < keys; k++) {
         String name = "K" + k;
         keyNames.add(name);
         infos[k] = new DependenciesInfo();
         // even keys list one renamed sheet, odd keys list none; key 0 lists several
         List<AssetObject> list = new ArrayList<>(List.of(sheet("untouched")));

         if(k == 0) {
            list.add(sheet("old1"));
            list.add(sheet("old2"));
            list.add(sheet("old3"));
         }
         else if(k % 2 == 0) {
            list.add(sheet("old" + (k % sheets)));
         }

         infos[k].setDependencies(list);
         when(dependencyStorageService.get(name)).thenReturn(infos[k]);
      }

      when(dependencyStorageService.getKeys(any())).thenReturn(keyNames);

      handler.updateDependencies(renames);

      verify(dependencyStorageService, times(1)).getKeys(any());
      verify(dependencyStorageService, times(keys)).get(anyString());
      verify(dependencyStorageService, times(keys / 2)).put(anyString(), any());
      verify(dependencyStorageService, never()).put(eq("K1"), any());
      assertEquals(List.of(sheet("untouched"), sheet("new1"), sheet("new2"), sheet("new3")),
                   infos[0].getDependencies());
   }

   // a key that lists several renamed sheets is written once with all of them replaced
   @Test
   void batchRenameWritesAKeyOnceForSeveralSheets() throws Exception {
      DependenciesInfo info = new DependenciesInfo();
      info.setDependencies(new ArrayList<>(List.of(sheet("a"), sheet("b"))));
      info.setEmbedDependencies(new ArrayList<>(List.of(sheet("a"))));
      when(dependencyStorageService.getKeys(any())).thenReturn(Set.of("K"));
      when(dependencyStorageService.get("K")).thenReturn(info);

      handler.updateDependencies(Map.of(sheet("a"), sheet("a2"), sheet("b"), sheet("b2")));

      verify(dependencyStorageService, times(1)).put(eq("K"), any());
      assertEquals(List.of(sheet("a2"), sheet("b2")), info.getDependencies());
      assertEquals(List.of(sheet("a2")), info.getEmbedDependencies());
   }

   // an overlapping map (a -> b, b -> c) replaces each element once, by its original identifier
   @Test
   void batchRenameDoesNotChainOverlappingRenames() throws Exception {
      DependenciesInfo info = new DependenciesInfo();
      info.setDependencies(new ArrayList<>(List.of(sheet("a"), sheet("b"))));
      when(dependencyStorageService.getKeys(any())).thenReturn(Set.of("K"));
      when(dependencyStorageService.get("K")).thenReturn(info);

      handler.updateDependencies(Map.of(sheet("a"), sheet("b"), sheet("b"), sheet("c")));

      assertEquals(List.of(sheet("b"), sheet("c")), info.getDependencies());
   }

   // the single pair method behaves as before: it replaces the entry and writes only that key
   @Test
   void singleRenameStillReplacesTheEntry() throws Exception {
      DependenciesInfo listing = new DependenciesInfo();
      listing.setDependencies(new ArrayList<>(List.of(sheet("a"))));
      DependenciesInfo unrelated = new DependenciesInfo();
      unrelated.setDependencies(new ArrayList<>(List.of(sheet("z"))));
      when(dependencyStorageService.getKeys(any())).thenReturn(Set.of("A", "B"));
      when(dependencyStorageService.get("A")).thenReturn(listing);
      when(dependencyStorageService.get("B")).thenReturn(unrelated);

      handler.updateDependencies(sheet("a"), sheet("a2"));

      verify(dependencyStorageService).put(eq("A"), any());
      verify(dependencyStorageService, never()).put(eq("B"), any());
      assertEquals(List.of(sheet("a2")), listing.getDependencies());
   }

   private AssetEntry sheet(String name) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, name, null);
   }

   private String identifier(AssetEntry.Type type, String name) {
      return new AssetEntry(AssetRepository.QUERY_SCOPE, type, DATA_SOURCE + "/" + name, null)
         .toIdentifier();
   }

   /**
    * @return the identifiers of the assets stored as dependents of the given key.
    */
   private List<String> capturedDependencies(String key) throws Exception {
      ArgumentCaptor<RenameTransformObject> captor =
         ArgumentCaptor.forClass(RenameTransformObject.class);
      verify(dependencyStorageService).put(eq(key), captor.capture());

      return ((DependenciesInfo) captor.getValue()).getDependencies().stream()
         .map(asset -> ((AssetEntry) asset).toIdentifier())
         .toList();
   }

   private LocalDependencyHandler handler;
   @Autowired private DependencyStorageService dependencyStorageService;

   private static final String DATA_SOURCE = "Derby Embedded";
   private static final String PHYSICAL_VIEW_NAME = "physicalView";
   private static final String LOGICAL_MODEL_NAME = "logicalModel";
   private static final String EXTENDED_NAME = "additionalConnection";
}
