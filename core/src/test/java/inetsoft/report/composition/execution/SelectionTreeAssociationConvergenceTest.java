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
package inetsoft.report.composition.execution;

import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionListVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.SelectionVSUtil;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78080 with a Selection Tree as the clicked (head) assembly: State = {NJ, PA, FL} in a
 * Selection List, then click Northeast in a Selection Tree on the region column. The list,
 * evaluated after the tree, makes FL selected-but-excluded and removes it from the applied
 * selections, so the tree must be evaluated again and show South as EXCLUDED, as a reset does.
 *
 * <p>Uses the embedded Hurricane data of EmbeddedVS1, where each storm {@code name} belongs to
 * exactly one {@code year}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
class SelectionTreeAssociationConvergenceTest {
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
   void clickedTreeIsReevaluatedWhenAListExcludesItsSelection() throws Exception {
      RuntimeViewsheet rvs = vsResource.getRuntimeViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      String table = ((TableVSAssembly) vs.getAssembly("TableView1")).getSourceInfo().getSource();

      Map<String, List<String>> namesByYear = getNamesByYear(vs);
      String northeast = namesByYear.entrySet().stream()
         .filter(e -> e.getValue().size() >= 2).map(Map.Entry::getKey).findFirst().orElseThrow();
      String south = namesByYear.keySet().stream()
         .filter(y -> !y.equals(northeast)).findFirst().orElseThrow();
      String nj = namesByYear.get(northeast).get(0);
      String pa = namesByYear.get(northeast).get(1);
      String fl = namesByYear.get(south).get(0);

      SelectionListVSAssembly stateList = new SelectionListVSAssembly(vs, "NameList");
      SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) stateList.getInfo();
      info.setTableName(table);
      info.setDataRef(new ColumnRef(new AttributeRef(null, "name")));
      vs.addAssembly(stateList);

      SelectionTreeVSAssembly regionTree = new SelectionTreeVSAssembly(vs, "YearTree");
      regionTree.setTableName(table);
      regionTree.setDataRefs(new DataRef[] { new ColumnRef(new AttributeRef(null, "year")) });
      vs.addAssembly(regionTree);
      resetAll(box, vs);

      clickList(box, stateList, nj);
      clickList(box, stateList, pa);
      clickList(box, stateList, fl);
      clickTree(box, regionTree, northeast);

      int flState = getState(stateList.getSelectionList(), fl);
      assertTrue(isSelected(flState) && isExcluded(flState),
                 "FL should be selected but excluded after the click: " + flState);
      assertTrue(isCompatible(getState(regionTree.getSelectionList(), northeast)));
      assertTrue(isExcluded(getState(regionTree.getSelectionList(), south)),
                 "South must be EXCLUDED in the tree right after the click, was " +
                 getState(regionTree.getSelectionList(), south));

      Map<String, Integer> afterClick = getStates(regionTree.getSelectionList());
      resetAll(box, vs);
      assertEquals(afterClick, getStates(regionTree.getSelectionList()),
                   "a reset of the same selections must give the same tree states");
   }

   private static Map<String, List<String>> getNamesByYear(Viewsheet vs) {
      Object assembly = vs.getBaseWorksheet().getAssembly("Query1");

      while(assembly instanceof MirrorTableAssembly) {
         assembly = ((MirrorTableAssembly) assembly).getTableAssembly();
      }

      XTable data = ((EmbeddedTableAssembly) assembly).getEmbeddedData();
      Map<String, Integer> cols = new HashMap<>();

      for(int c = 0; c < data.getColCount(); c++) {
         cols.put(String.valueOf(data.getObject(0, c)), c);
      }

      Map<String, List<String>> namesByYear = new TreeMap<>();

      for(int r = 1; data.moreRows(r); r++) {
         Object year = data.getObject(r, cols.get("year"));
         Object name = data.getObject(r, cols.get("name"));

         if(year != null && name != null) {
            List<String> names = namesByYear.computeIfAbsent(
               Tool.getDataString(year), k -> new ArrayList<>());

            if(!names.contains(Tool.getDataString(name))) {
               names.add(Tool.getDataString(name));
            }
         }
      }

      return namesByYear;
   }

   private static void resetAll(ViewsheetSandbox box, Viewsheet vs) throws Exception {
      box.lockWrite();

      try {
         // CoreLifecycleService.refreshViewsheet(reset=true), used by Refresh and Undo/Redo
         box.reset(null, vs.getAssemblies(), new ChangedAssemblyList(), true, true, null);
      }
      finally {
         box.unlockWrite();
      }
   }

   /** Clone the current list and select a value the way VSSelectionService does a plain click. */
   private static SelectionList select(SelectionList current, String name, String value) {
      assertNotNull(current, name);
      SelectionList slist = (SelectionList) current.clone();
      SelectionValue svalue = slist.findValue(value, false);
      assertNotNull(svalue, value + " not in " + name);
      // a click on an excluded value resets the other selections (requiresReset)
      assertTrue(isCompatible(svalue.getState()), value + " is not compatible in " + name);
      svalue.setSelected(true);
      svalue.setExcluded(false);
      return slist;
   }

   private static void clickList(ViewsheetSandbox box, SelectionListVSAssembly list, String value)
      throws Exception
   {
      box.lockWrite();

      try {
         SelectionList slist = select(list.getSelectionList(), list.getName(), value);
         slist.setSelectionValues(
            SelectionVSUtil.shrinkSelectionValues(list, slist.getSelectionValues()));
         int hint = list.setStateSelectionList(slist);
         box.processChange(list.getAbsoluteName(), hint, new ChangedAssemblyList(true));
      }
      finally {
         box.unlockWrite();
      }
   }

   private static void clickTree(ViewsheetSandbox box, SelectionTreeVSAssembly tree, String value)
      throws Exception
   {
      box.lockWrite();

      try {
         SelectionList slist = select(tree.getSelectionList(), tree.getName(), value);
         slist.setSelectionValues(
            SelectionVSUtil.shrinkSelectionValues(tree, slist.getSelectionValues()));
         CompositeSelectionValue cvalue = new CompositeSelectionValue();
         cvalue.setLevel(-1);
         cvalue.setSelectionList(slist);
         int hint = tree.setStateCompositeSelectionValue(cvalue);
         box.processChange(tree.getAbsoluteName(), hint, new ChangedAssemblyList(true));
      }
      finally {
         box.unlockWrite();
      }
   }

   private static Map<String, Integer> getStates(SelectionList slist) {
      Map<String, Integer> map = new TreeMap<>();

      for(int i = 0; slist != null && i < slist.getSelectionValueCount(); i++) {
         map.put(slist.getSelectionValue(i).getValue(), slist.getSelectionValue(i).getState());
      }

      return map;
   }

   private static int getState(SelectionList slist, String value) {
      Integer state = getStates(slist).get(value);
      assertNotNull(state, value + " not found");
      return state;
   }

   private static boolean isSelected(int state) {
      return (state & SelectionValue.STATE_SELECTED) != 0;
   }

   private static boolean isCompatible(int state) {
      return (state & SelectionValue.STATE_COMPATIBLE) != 0;
   }

   private static boolean isExcluded(int state) {
      return (state & SelectionValue.STATE_EXCLUDED) != 0;
   }

   private static OpenViewsheetEvent openViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());
   private Principal oldPrincipal;
}
