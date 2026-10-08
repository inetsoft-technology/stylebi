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
 * Bug #78080: {@code ViewsheetSandbox.processAssociation} evaluated the related selection
 * assemblies in a single pass over a shared applied-selections map. A list evaluated later can
 * remove a selected-but-excluded value from that map, and the lists evaluated before it kept
 * association states computed from the larger map. The association states after a click must
 * match the converged states, and a reset (Refresh, Undo/Redo, open) of the same selections must
 * give the same states.
 *
 * <p>Uses the embedded Hurricane data of EmbeddedVS1, where each storm {@code name} belongs to
 * exactly one {@code year} and has one or more {@code wind} values.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
class SelectionAssociationConvergenceTest {
   @BeforeEach
   void setUp() {
      oldPrincipal = ThreadContext.getContextPrincipal();
      AssetDataCache.getCache().clear();
   }

   @AfterEach
   void restore() {
      ThreadContext.setContextPrincipal(oldPrincipal);
   }

   /**
    * The reported case: State = {NJ, PA, FL}, then click Northeast in Region. FL becomes
    * selected-but-excluded, so South must be EXCLUDED in Region right after the click, as it is
    * after a Refresh.
    */
   @Test
   void clickedListIsReevaluatedWhenAnotherListExcludesItsSelection() throws Exception {
      RuntimeViewsheet rvs = vsResource.getRuntimeViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      String table = getTable(vs);
      List<Map<String, String>> rows = getRows(vs);

      // two years, one with two storms (NJ, PA in "Northeast") and one other year ("South")
      Map<String, List<String>> namesByYear = new TreeMap<>();
      rows.forEach(r -> namesByYear.computeIfAbsent(r.get("year"), k -> new ArrayList<>()));
      rows.stream().map(r -> Arrays.asList(r.get("year"), r.get("name"))).distinct()
         .forEach(p -> namesByYear.get(p.get(0)).add(p.get(1)));
      String northeast = namesByYear.entrySet().stream()
         .filter(e -> e.getValue().size() >= 2).map(Map.Entry::getKey).findFirst().orElseThrow();
      String south = namesByYear.keySet().stream()
         .filter(y -> !y.equals(northeast)).findFirst().orElseThrow();
      String nj = namesByYear.get(northeast).get(0);
      String pa = namesByYear.get(northeast).get(1);
      String fl = namesByYear.get(south).get(0);

      SelectionListVSAssembly stateList = addList(vs, "NameList", table, "name");
      SelectionListVSAssembly regionList = addList(vs, "YearList", table, "year");
      resetAll(box, vs);

      click(box, stateList, nj);
      click(box, stateList, pa);
      click(box, stateList, fl);
      click(box, regionList, northeast);

      int flState = getState(stateList, fl);
      assertTrue(isSelected(flState) && isExcluded(flState),
                 "FL should be selected but excluded after the click: " + flState);
      assertTrue(isCompatible(getState(regionList, northeast)));
      assertTrue(isExcluded(getState(regionList, south)),
                 "South must be EXCLUDED right after the click, was " +
                 getState(regionList, south));

      Map<String, Map<String, Integer>> afterClick = getStates(stateList, regionList);
      resetAll(box, vs);
      assertEquals(afterClick, getStates(stateList, regionList),
                   "a reset of the same selections must give the same association states");
   }

   /**
    * A three-list chain (Name, Wind, Year). After clicking Year, Wind excludes one of its own
    * selections (tX) after Name and Year were already evaluated, and Name excludes FL after Year
    * was evaluated. Both earlier lists must be re-evaluated. On a reset, Name (which holds an
    * excluded selection) heads the pass and Wind's excluded selection is seeded back in, so the
    * reset path needs the re-evaluation as well.
    */
   @Test
   void threeListChainConvergesOnClickAndOnReset() throws Exception {
      RuntimeViewsheet rvs = vsResource.getRuntimeViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      String table = getTable(vs);
      List<Map<String, String>> rows = getRows(vs);

      Map<String, String> yearOf = new TreeMap<>();
      Map<String, Set<String>> windsOf = new TreeMap<>();
      rows.forEach(r -> {
         yearOf.put(r.get("name"), r.get("year"));
         windsOf.computeIfAbsent(r.get("name"), k -> new TreeSet<>()).add(r.get("wind"));
      });
      Set<String> winds = new TreeSet<>();
      windsOf.values().forEach(winds::addAll);
      String[] picked = pickChain(yearOf, windsOf, winds);
      assertNotNull(picked, "no suitable storms in the test data");
      String y1 = picked[0], nj = picked[1], pa = picked[2], fl = picked[3];
      String tN = picked[4], tX = picked[5];

      SelectionListVSAssembly nameList = addList(vs, "NameList", table, "name");
      SelectionListVSAssembly windList = addList(vs, "WindList", table, "wind");
      SelectionListVSAssembly yearList = addList(vs, "YearList", table, "year");
      resetAll(box, vs);

      click(box, nameList, nj);
      click(box, nameList, pa);
      click(box, nameList, fl);
      click(box, windList, tN);
      click(box, windList, tX);
      click(box, yearList, y1);

      // converged selections: FL and tX are selected but excluded
      Map<String, Set<String>> applied = new HashMap<>();
      applied.put("name", new HashSet<>(Arrays.asList(nj, pa)));
      applied.put("wind", new HashSet<>(Collections.singletonList(tN)));
      applied.put("year", new HashSet<>(Collections.singletonList(y1)));

      assertConverged(rows, applied, nameList, windList, yearList);
      assertTrue(isExcluded(getState(nameList, fl)));
      assertTrue(isExcluded(getState(windList, tX)));

      resetAll(box, vs);
      assertConverged(rows, applied, nameList, windList, yearList);
   }

   /**
    * Find a year y1 with two storms NJ, PA that both have wind tN, a wind tX that NJ and PA don't
    * have but another y1 storm without tN has, and a storm FL of another year that has both tN and
    * tX, so that every clicked value is compatible when it is clicked.
    */
   private static String[] pickChain(Map<String, String> yearOf, Map<String, Set<String>> windsOf,
                                     Set<String> winds)
   {
      for(String y1 : new TreeSet<>(yearOf.values())) {
         List<String> names = yearOf.keySet().stream()
            .filter(n -> y1.equals(yearOf.get(n))).toList();

         for(String tN : winds) {
            List<String> withTN = names.stream().filter(n -> windsOf.get(n).contains(tN)).toList();

            if(withTN.size() < 2) {
               continue;
            }

            String nj = withTN.get(0);
            String pa = withTN.get(1);

            for(String tX : winds) {
               if(tX.equals(tN) || windsOf.get(nj).contains(tX) || windsOf.get(pa).contains(tX)) {
                  continue;
               }

               boolean staleName = names.stream().anyMatch(
                  n -> windsOf.get(n).contains(tX) && !windsOf.get(n).contains(tN));
               Optional<String> fl = yearOf.keySet().stream()
                  .filter(n -> !y1.equals(yearOf.get(n)) && windsOf.get(n).contains(tX) &&
                     windsOf.get(n).contains(tN))
                  .findFirst();

               if(staleName && fl.isPresent()) {
                  return new String[] { y1, nj, pa, fl.get(), tN, tX };
               }
            }
         }
      }

      return null;
   }

   /**
    * Check each list's COMPATIBLE/EXCLUDED states against the values associated with the
    * converged selections of the other lists, computed from the raw rows.
    */
   private static void assertConverged(List<Map<String, String>> rows,
                                       Map<String, Set<String>> applied,
                                       SelectionListVSAssembly... lists)
   {
      for(SelectionListVSAssembly list : lists) {
         String col = list.getDataRef().getName();
         Set<String> expected = new HashSet<>();

         for(Map<String, String> row : rows) {
            boolean match = applied.entrySet().stream()
               .filter(e -> !e.getKey().equals(col))
               .allMatch(e -> e.getValue().contains(row.get(e.getKey())));

            if(match) {
               expected.add(row.get(col));
            }
         }

         SelectionList slist = list.getSelectionList();
         assertNotNull(slist, list.getName());
         List<String> wrong = new ArrayList<>();

         for(int i = 0; i < slist.getSelectionValueCount(); i++) {
            SelectionValue value = slist.getSelectionValue(i);
            int state = value.getState();
            boolean compatible = expected.contains(value.getValue());

            if(compatible != isCompatible(state) || compatible == isExcluded(state)) {
               wrong.add(value.getValue() + "=" + state);
            }
         }

         assertTrue(wrong.isEmpty(), list.getName() + " has stale association states " + wrong);
      }
   }

   private static Map<String, Map<String, Integer>> getStates(SelectionListVSAssembly... lists) {
      Map<String, Map<String, Integer>> states = new TreeMap<>();

      for(SelectionListVSAssembly list : lists) {
         Map<String, Integer> map = new TreeMap<>();
         SelectionList slist = list.getSelectionList();

         for(int i = 0; slist != null && i < slist.getSelectionValueCount(); i++) {
            map.put(slist.getSelectionValue(i).getValue(), slist.getSelectionValue(i).getState());
         }

         states.put(list.getName(), map);
      }

      return states;
   }

   private static String getTable(Viewsheet vs) {
      return ((TableVSAssembly) vs.getAssembly("TableView1")).getSourceInfo().getSource();
   }

   private static List<Map<String, String>> getRows(Viewsheet vs) {
      Object assembly = vs.getBaseWorksheet().getAssembly("Query1");

      while(assembly instanceof MirrorTableAssembly) {
         assembly = ((MirrorTableAssembly) assembly).getTableAssembly();
      }

      XTable data = ((EmbeddedTableAssembly) assembly).getEmbeddedData();
      Map<String, Integer> cols = new HashMap<>();

      for(int c = 0; c < data.getColCount(); c++) {
         cols.put(String.valueOf(data.getObject(0, c)), c);
      }

      List<Map<String, String>> rows = new ArrayList<>();

      for(int r = 1; data.moreRows(r); r++) {
         Map<String, String> row = new HashMap<>();

         for(String col : new String[] { "name", "wind", "year" }) {
            Object value = data.getObject(r, cols.get(col));
            row.put(col, value == null ? null : Tool.getDataString(value));
         }

         if(!row.containsValue(null)) {
            rows.add(row);
         }
      }

      return rows;
   }

   private static SelectionListVSAssembly addList(Viewsheet vs, String name, String table,
                                                  String col)
   {
      SelectionListVSAssembly selection = new SelectionListVSAssembly(vs, name);
      SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) selection.getInfo();
      info.setTableName(table);
      info.setDataRef(new ColumnRef(new AttributeRef(null, col)));
      vs.addAssembly(selection);
      return selection;
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

   /**
    * A plain (non-toggle) click on a value of a multi-select list, applied the way
    * VSSelectionService.applySelection does it.
    */
   private static void click(ViewsheetSandbox box, SelectionListVSAssembly list, String value)
      throws Exception
   {
      box.lockWrite();

      try {
         SelectionList slist = list.getSelectionList();
         assertNotNull(slist, list.getName());
         slist = (SelectionList) slist.clone();
         boolean found = false;

         for(SelectionValue svalue : slist.getSelectionValues()) {
            if(value.equals(svalue.getValue())) {
               // a click on an excluded value resets the other lists (requiresReset)
               assertTrue(isCompatible(svalue.getState()),
                          value + " is not compatible in " + list.getName());
               svalue.setSelected(true);
               svalue.setExcluded(false);
               found = true;
            }
         }

         assertTrue(found, value + " not in " + list.getName());
         slist.setSelectionValues(SelectionVSUtil.shrinkSelectionValues(list, slist.getSelectionValues()));
         int hint = list.setStateSelectionList(slist);
         box.processChange(list.getAbsoluteName(), hint, new ChangedAssemblyList(true));
      }
      finally {
         box.unlockWrite();
      }
   }

   private static int getState(SelectionListVSAssembly list, String value) {
      SelectionList slist = list.getSelectionList();

      for(int i = 0; slist != null && i < slist.getSelectionValueCount(); i++) {
         if(value.equals(slist.getSelectionValue(i).getValue())) {
            return slist.getSelectionValue(i).getState();
         }
      }

      fail(value + " not in " + list.getName());
      return -1;
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
