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
package inetsoft.web.viewsheet.service;

import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionTreeVSAssemblyInfo;
import inetsoft.util.ThreadContext;
import inetsoft.web.viewsheet.event.ApplySelectionListEvent;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

/**
 * Bug #78821: {@code selectSubtree} on a path that ends at a leaf of a single-select level left the
 * parent selected with no child, and the refresh then filled in the parent's first child. The
 * single-level clean-up ran after the path was selected and unselected the leaf, which it keeps
 * only when it is a composite; it now runs first, as in {@code applySelection}.
 *
 * <p>Uses the embedded Hurricane data of EmbeddedVS1: a column tree on {@code year > name}, where
 * the first year has at least two names. The second name of that year is not first in sort order,
 * which is what the first-child fallback would pick instead.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
class VSSelectionServiceSelectSubtreeSingleLevelTest {
   @BeforeEach
   void setUp() throws Exception {
      oldPrincipal = ThreadContext.getContextPrincipal();
      AssetDataCache.getCache().clear();
      init();
   }

   @AfterEach
   void restore() {
      ThreadContext.setContextPrincipal(oldPrincipal);
   }

   // ── the bug: a leaf on a single-select level ────────────────────────────

   @Test
   void leafOnAnAllSingleTreeSelectsExactlyTheLeaf() throws Exception {
      allLevelsSingle();

      selectSubtree(true, year1, name1b);

      assertEquals(Set.of(year1, year1 + "/" + name1b), selected());
   }

   @Test
   void leafOnAnAllSingleTreeReplacesTheSelectionInAnotherParent() throws Exception {
      allLevelsSingle();
      apply(path(year2, name2a));

      selectSubtree(true, year1, name1b);

      assertEquals(Set.of(year1, year1 + "/" + name1b), selected());
   }

   @Test
   void leafReplacesTheSiblingUnderTheSameParent() throws Exception {
      allLevelsSingle();
      selectSubtree(true, year1, name1a);

      selectSubtree(true, year1, name1b);

      assertEquals(Set.of(year1, year1 + "/" + name1b), selected());
   }

   @Test
   void selectingTheSelectedLeafAgainKeepsIt() throws Exception {
      allLevelsSingle();
      selectSubtree(true, year1, name1b);

      selectSubtree(true, year1, name1b);

      assertEquals(Set.of(year1, year1 + "/" + name1b), selected());
   }

   /**
    * Only the leaf level is single: one name per year, so the other year's name stays selected.
    */
   @Test
   void leafLevelSingleKeepsTheOtherParentsSelection() throws Exception {
      singleLevels(1);
      apply(path(year2, name2a));

      selectSubtree(true, year1, name1b);

      assertEquals(Set.of(year2, year2 + "/" + name2a, year1, year1 + "/" + name1b), selected());
   }

   // ── what a native subtree select can do must not change ─────────────────

   /**
    * The native UI allows a subtree select at a parent whose deeper levels are all multi-select.
    */
   @Test
   void parentSubtreeWithOnlyTheParentLevelSingleSelectsAllChildren() throws Exception {
      singleLevels(0);
      apply(path(year2, name2a));

      selectSubtree(true, year1);

      assertEquals(subtreePaths(year1), selected());
   }

   @Test
   void leafWithOnlyTheParentLevelSingleAddsTheLeaf() throws Exception {
      singleLevels(0);
      selectSubtree(true, year1, name1a);

      selectSubtree(true, year1, name1b);

      assertEquals(Set.of(year1, year1 + "/" + name1a, year1 + "/" + name1b), selected());
   }

   @Test
   void multiSelectTreeSelectsTheWholeParentSubtree() throws Exception {
      selectSubtree(true, year1);

      assertEquals(subtreePaths(year1), selected());
   }

   @Test
   void multiSelectTreeAddsAcrossParents() throws Exception {
      selectSubtree(true, year1, name1b);
      selectSubtree(true, year2, name2a);

      assertEquals(Set.of(year1, year1 + "/" + name1b, year2, year2 + "/" + name2a), selected());
   }

   @Test
   void clearingAParentSubtreeOnAMultiSelectTreeStillClearsIt() throws Exception {
      selectSubtree(true, year1);
      selectSubtree(true, year2);

      selectSubtree(false, year1);

      assertEquals(subtreePaths(year2), selected());
   }

   // ── bug-78869: a clear must not touch what it was not asked to clear ─────

   @Test
   void clearOfALeafInAnotherParentKeepsTheSelectionOnAnAllSingleTree() throws Exception {
      allLevelsSingle();
      apply(path(year2, name2a));

      selectSubtree(false, year1, name1a);

      assertEquals(Set.of(year2, year2 + "/" + name2a), selected());
   }

   @Test
   void clearOfASiblingLeafKeepsTheSelectionOnAnAllSingleTree() throws Exception {
      allLevelsSingle();
      apply(path(year1, name1a));

      selectSubtree(false, year1, name1b);

      assertEquals(Set.of(year1, year1 + "/" + name1a), selected());
   }

   @Test
   void clearOfAnotherBranchKeepsTheSelectionWhenOnlyTheParentLevelIsSingle() throws Exception {
      singleLevels(0);
      apply(path(year1, name1a));

      selectSubtree(false, year2);

      assertEquals(Set.of(year1, year1 + "/" + name1a), selected());
   }

   @Test
   void clearOfALeafInAnotherParentKeepsTheSelectionWhenOnlyTheLeafLevelIsSingle()
      throws Exception
   {
      singleLevels(1);
      apply(path(year2, name2a));

      selectSubtree(false, year1, name1a);

      assertEquals(Set.of(year2, year2 + "/" + name2a), selected());
   }

   @Test
   void clearOfAnUnselectedLeafKeepsTheSelectedParentOnAnAllSingleTree() throws Exception {
      allLevelsSingle();
      apply(path(year1, name1a));

      selectSubtree(false, year1, name1b);

      assertEquals(Set.of(year1, year1 + "/" + name1a), selected());
   }

   @Test
   void clearOfTheOnlySelectedLeafUnselectsItsParent() throws Exception {
      allLevelsSingle();
      apply(path(year1, name1a));

      selectSubtree(false, year1, name1a);

      assertEquals(Set.of(), selected());
   }

   @Test
   void clearOfASiblingLeafKeepsTheParentAndTheOtherLeafOnAMultiSelectTree() throws Exception {
      selectSubtree(true, year1, name1a);
      selectSubtree(true, year1, name1b);

      selectSubtree(false, year1, name1b);

      assertEquals(Set.of(year1, year1 + "/" + name1a), selected());
   }

   @Test
   void clearOfAnUnselectedLeafKeepsTheSelectedParentOnAMultiSelectTree() throws Exception {
      selectSubtree(true, year1, name1a);

      selectSubtree(false, year1, name1b);

      assertEquals(Set.of(year1, year1 + "/" + name1a), selected());
   }

   @Test
   void clearOfTheOnlySelectedLeafUnselectsItsParentOnAMultiSelectTree() throws Exception {
      selectSubtree(true, year1, name1a);

      selectSubtree(false, year1, name1a);

      assertEquals(Set.of(), selected());
   }

   // ── setup ───────────────────────────────────────────────────────────────

   /**
    * {@code singleSelect:true} with no per-level edit: every level is single-select.
    */
   private void allLevelsSingle() {
      tree().getSelectionTreeInfo().setSingleSelection(true);
   }

   /**
    * A per-level configuration: only the given levels are single-select.
    */
   private void singleLevels(Integer... levels) {
      SelectionTreeVSAssemblyInfo info = tree().getSelectionTreeInfo();
      info.setSingleSelection(true);

      for(Integer level : levels) {
         info.addSingleSelectionLevel(level);
      }
   }

   private SelectionTreeVSAssembly tree() {
      return (SelectionTreeVSAssembly) vs.getAssembly("ST1");
   }

   private void init() throws Exception {
      RuntimeViewsheet rvs = vsResource.getRuntimeViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      vs = box.getViewsheet();
      String table = ((TableVSAssembly) vs.getAssembly("TableView1")).getSourceInfo().getSource();

      SelectionTreeVSAssembly tree = new SelectionTreeVSAssembly(vs, "ST1");
      tree.setTableName(table);
      tree.setDataRefs(new DataRef[] { column("year"), column("name") });
      vs.addAssembly(tree);

      box.lockWrite();

      try {
         box.reset(null, vs.getAssemblies(), new ChangedAssemblyList(), true, true, null);
      }
      finally {
         box.unlockWrite();
      }

      SelectionList years = tree().getSelectionList();
      CompositeSelectionValue first = (CompositeSelectionValue) years.getSelectionValue(0);
      CompositeSelectionValue second = (CompositeSelectionValue) years.getSelectionValue(1);
      year1 = first.getValue();
      year2 = second.getValue();
      name1a = first.getSelectionList().getSelectionValue(0).getValue();
      name1b = first.getSelectionList().getSelectionValue(1).getValue();
      name2a = second.getSelectionList().getSelectionValue(0).getValue();
      rid = vsResource.getRuntimeId();
      principal = ThreadContext.getPrincipal();
      dispatcher = mock(CommandDispatcher.class, RETURNS_DEEP_STUBS);
   }

   private void selectSubtree(boolean select, String... path) throws Exception {
      service.selectSubtree(rid, "ST1", event(select, path), principal, dispatcher, "");
   }

   private void apply(String[]... paths) throws Exception {
      service.applySelection(rid, "ST1", event(true, paths), principal, dispatcher, "");
   }

   private static ApplySelectionListEvent event(boolean select, String[]... paths) {
      ApplySelectionListEvent event = new ApplySelectionListEvent();
      event.setType(ApplySelectionListEvent.Type.APPLY);
      List<ApplySelectionListEvent.Value> values = new ArrayList<>();

      for(String[] path : paths) {
         ApplySelectionListEvent.Value value = new ApplySelectionListEvent.Value();
         value.setValue(path);
         value.setSelected(select);
         values.add(value);
      }

      event.setValues(values);
      return event;
   }

   /**
    * The year and every name under it, as paths.
    */
   private Set<String> subtreePaths(String year) {
      CompositeSelectionValue node =
         (CompositeSelectionValue) tree().getSelectionList().findValue(year, false);
      Set<String> paths = new TreeSet<>();
      paths.add(year);

      for(SelectionValue name : node.getSelectionList().getSelectionValues()) {
         paths.add(year + "/" + name.getValue());
      }

      return paths;
   }

   /**
    * The selected paths of the tree's state.
    */
   private Set<String> selected() {
      CompositeSelectionValue root = tree().getStateCompositeSelectionValue();
      return selectedPaths(root == null ? null : root.getSelectionList(), "");
   }

   private static Set<String> selectedPaths(SelectionList list, String prefix) {
      Set<String> paths = new TreeSet<>();

      for(int i = 0; list != null && i < list.getSelectionValueCount(); i++) {
         SelectionValue value = list.getSelectionValue(i);
         String path = prefix + value.getValue();

         if(value.isSelected()) {
            paths.add(path);
         }

         if(value instanceof CompositeSelectionValue) {
            paths.addAll(selectedPaths(
               ((CompositeSelectionValue) value).getSelectionList(), path + "/"));
         }
      }

      return paths;
   }

   private static String[] path(String... values) {
      return values;
   }

   private static ColumnRef column(String name) {
      return new ColumnRef(new AttributeRef(null, name));
   }

   private static OpenViewsheetEvent openViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }

   @Autowired
   private VSSelectionService service;
   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());
   private Principal oldPrincipal;
   private Viewsheet vs;
   private String rid;
   private Principal principal;
   private CommandDispatcher dispatcher;
   private String year1;
   private String year2;
   private String name1a;
   private String name1b;
   private String name2a;
}
