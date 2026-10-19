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
import inetsoft.uql.viewsheet.internal.SelectionListVSAssemblyInfo;
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
 * Bug #78584: a change started in a column-mode selection tree must reach the list and tree
 * neighbours on the same columns ({@code VSSelectionService.refreshNeighborSelections}). If it
 * doesn't, a cleared value stays in a neighbour's state and the next association selects the
 * tree's own node again: a list neighbour gives back the root, a tree neighbour every path.
 *
 * <p>Uses the embedded Hurricane data of EmbeddedVS1. The list SL1 is on {@code year}, the trees
 * ST1 and ST2 are column trees on {@code year > name}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("slow")
class VSSelectionServiceTreeClearTest {
   @BeforeEach
   void setUp() {
      oldPrincipal = ThreadContext.getContextPrincipal();
      AssetDataCache.getCache().clear();
   }

   @AfterEach
   void restore() {
      ThreadContext.setContextPrincipal(oldPrincipal);
   }

   @Test
   void subtreeClearWithListNeighbourClearsBoth() throws Exception {
      init("ST1");

      selectSubtree("ST1", true, year1);

      // the select side is unchanged: the list already received the year through association
      assertEquals(Set.of(year1), selected("SL1"));
      assertEquals(subtree(year1), selected("ST1"));

      selectSubtree("ST1", false, year1);

      assertEquals(Set.of(), selected("SL1"));
      assertEquals(Set.of(), selected("ST1"));
   }

   @Test
   void deselectRootWithListNeighbourClearsBoth() throws Exception {
      init("ST1");

      apply("ST1", true, path(year1, name1a));

      assertEquals(Set.of(year1), selected("SL1"));
      assertEquals(Set.of(year1, year1 + "/" + name1a), selected("ST1"));

      apply("ST1", false, path(year1));

      assertEquals(Set.of(), selected("SL1"));
      assertEquals(Set.of(), selected("ST1"));
   }

   @Test
   void subtreeClearWithListAndTreeNeighboursClearsAll() throws Exception {
      init("ST1", "ST2");

      selectSubtree("ST1", true, year1);

      assertEquals(Set.of(year1), selected("SL1"));
      assertEquals(subtree(year1), selected("ST1"));
      assertEquals(subtree(year1), selected("ST2"));

      selectSubtree("ST1", false, year1);

      assertEquals(Set.of(), selected("SL1"));
      assertEquals(Set.of(), selected("ST1"));
      assertEquals(Set.of(), selected("ST2"));
   }

   @Test
   void deselectRootWithListAndTreeNeighboursClearsAll() throws Exception {
      init("ST1", "ST2");

      apply("ST1", true, path(year1, name1a));

      assertEquals(Set.of(year1), selected("SL1"));
      assertEquals(Set.of(year1, year1 + "/" + name1a), selected("ST2"));

      apply("ST1", false, path(year1));

      assertEquals(Set.of(), selected("SL1"));
      assertEquals(Set.of(), selected("ST1"));
      assertEquals(Set.of(), selected("ST2"));
   }

   /**
    * Deselecting one of two leaves keeps the other leaf, and its year, in every neighbour.
    */
   @Test
   void partialDeselectKeepsTheOtherLeaf() throws Exception {
      init("ST1", "ST2");

      apply("ST1", true, path(year1, name1a), path(year1, name1b));
      apply("ST1", false, path(year1, name1a));

      Set<String> kept = Set.of(year1, year1 + "/" + name1b);
      assertEquals(Set.of(year1), selected("SL1"));
      assertEquals(kept, selected("ST1"));
      assertEquals(kept, selected("ST2"));
   }

   @Test
   void clearingOneOfTwoSubtreesKeepsTheOther() throws Exception {
      init("ST1", "ST2");

      selectSubtree("ST1", true, year1);
      selectSubtree("ST1", true, year2);
      selectSubtree("ST1", false, year1);

      assertEquals(Set.of(year2), selected("SL1"));
      assertEquals(subtree(year2), selected("ST1"));
      assertEquals(subtree(year2), selected("ST2"));
   }

   /**
    * A year picked in the list itself survives the tree's clear of another year.
    */
   @Test
   void listPickSurvivesTreeClearOfAnotherYear() throws Exception {
      init("ST1");

      apply("SL1", true, path(year2));
      selectSubtree("ST1", true, year1);
      selectSubtree("ST1", false, year1);

      assertEquals(Set.of(year2), selected("SL1"));
      assertEquals(Set.of(year2), selected("ST1"));
   }

   /**
    * Bug #78787: with a search showing only name1a, deselecting just that visible leaf (what the
    * wiz clear now sends) keeps the search-hidden name1b selected.
    */
   @Test
   void deselectingTheVisibleLeafUnderASearchKeepsTheHiddenSibling() throws Exception {
      init("ST1");
      Assumptions.assumeFalse(name1b.toLowerCase().contains(name1a.toLowerCase()));

      apply("ST1", true, path(year1, name1a), path(year1, name1b));
      ((SelectionTreeVSAssembly) vs.getAssembly("ST1")).getSelectionTreeInfo()
         .setSearchString(name1a);

      apply("ST1", false, path(year1, name1a));

      assertEquals(Set.of(year1, year1 + "/" + name1b), selected("ST1"));
   }

   private void init(String... trees) throws Exception {
      RuntimeViewsheet rvs = vsResource.getRuntimeViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      vs = box.getViewsheet();
      String table = ((TableVSAssembly) vs.getAssembly("TableView1")).getSourceInfo().getSource();

      SelectionListVSAssembly yearList = new SelectionListVSAssembly(vs, "SL1");
      SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) yearList.getInfo();
      info.setTableName(table);
      info.setDataRef(column("year"));
      vs.addAssembly(yearList);

      for(String name : trees) {
         SelectionTreeVSAssembly tree = new SelectionTreeVSAssembly(vs, name);
         tree.setTableName(table);
         tree.setDataRefs(new DataRef[] { column("year"), column("name") });
         vs.addAssembly(tree);
      }

      box.lockWrite();

      try {
         box.reset(null, vs.getAssemblies(), new ChangedAssemblyList(), true, true, null);
      }
      finally {
         box.unlockWrite();
      }

      SelectionList years = ((SelectionTreeVSAssembly) vs.getAssembly("ST1")).getSelectionList();
      CompositeSelectionValue first = (CompositeSelectionValue) years.getSelectionValue(0);
      year1 = first.getValue();
      year2 = years.getSelectionValue(1).getValue();
      name1a = first.getSelectionList().getSelectionValue(0).getValue();
      name1b = first.getSelectionList().getSelectionValue(1).getValue();
      rid = vsResource.getRuntimeId();
      principal = ThreadContext.getPrincipal();
      dispatcher = mock(CommandDispatcher.class, RETURNS_DEEP_STUBS);
   }

   private void selectSubtree(String tree, boolean select, String year) throws Exception {
      service.selectSubtree(rid, tree, event(select, path(year)), principal, dispatcher, "");
   }

   private void apply(String assembly, boolean select, String[]... paths) throws Exception {
      service.applySelection(rid, assembly, event(select, paths), principal, dispatcher, "");
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
    * The year and every name under it, as paths of ST1's full selection list.
    */
   private Set<String> subtree(String year) {
      SelectionTreeVSAssembly tree = (SelectionTreeVSAssembly) vs.getAssembly("ST1");
      CompositeSelectionValue node =
         (CompositeSelectionValue) tree.getSelectionList().findValue(year, false);
      Set<String> paths = new TreeSet<>();
      paths.add(year);

      for(SelectionValue name : node.getSelectionList().getSelectionValues()) {
         paths.add(year + "/" + name.getValue());
      }

      return paths;
   }

   /**
    * The selected values (list) or paths (tree) of the assembly's state.
    */
   private Set<String> selected(String name) {
      Object assembly = vs.getAssembly(name);

      if(assembly instanceof SelectionListVSAssembly list) {
         return selectedPaths(list.getStateSelectionList(), "");
      }

      CompositeSelectionValue root =
         ((SelectionTreeVSAssembly) assembly).getStateCompositeSelectionValue();
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
}
