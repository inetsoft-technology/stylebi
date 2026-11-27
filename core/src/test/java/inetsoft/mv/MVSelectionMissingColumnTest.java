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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionListVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.SelectionVSUtil;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import inetsoft.web.admin.content.repository.MVSupportService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82492: a selection list bound to a column that its table doesn't have (e.g. removed from
 * the worksheet) was registered as a selection column of the table in the MV transformation
 * descriptor. With an MV, the selections on the table could then not be moved up to the MV, so
 * every other selection list on the same table lost its association (no COMPATIBLE values on
 * open, every value EXCLUDED after a click), and no association MV was created for the table.
 *
 * <p>Runs the real MV analysis and creation ({@link MVSupportService}) and the real viewsheet
 * runtime on the embedded Hurricane data of EmbeddedVS1, where each storm {@code name} (e.g.
 * ALBERTO-1988) belongs to exactly one {@code year}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  RankingConditionTest.MVConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
// about 14 s with the context, MV analysis and creation
@Tag("slow")
class MVSelectionMissingColumnTest {
   @BeforeEach
   void setUp() {
      oldPrincipal = ThreadContext.getContextPrincipal();
      user = SUtil.getPrincipal(
         new IdentityID(XPrincipal.SYSTEM, OrganizationManager.getInstance().getCurrentOrgID()),
         null, false);
      ThreadContext.setContextPrincipal(user);
      AssetDataCache.getCache().clear();
   }

   @AfterEach
   void tearDown() throws Exception {
      try {
         if(runtimeId != null) {
            viewsheetService.closeViewsheet(runtimeId, user);
         }
      }
      finally {
         runtimeId = null;

         try {
            if(!mvNames.isEmpty()) {
               mvSupport.dispose(new ArrayList<>(mvNames));
               mvNames.clear();
            }

            if(entry != null) {
               AssetUtil.getAssetRepository(false).removeSheet(entry, user, true);
               entry = null;
            }
         }
         finally {
            ThreadContext.setContextPrincipal(oldPrincipal);
         }
      }
   }

   /**
    * The reported case: lists on name and year keep their association with an MV when another
    * list on the same table is bound to a missing column, and the table gets an association MV.
    */
   @Test
   void otherListsKeepAssociationWithMissingColumnList() throws Exception {
      List<MVDef> defs = materialize("82492_missing", vs -> {
         addList(vs, "L_name", "name");
         addList(vs, "L_year", "year");
         addList(vs, "L_miss", "CUSTOMER_ID");
      });

      assertTrue(defs.stream().anyMatch(MVDef::isAssociationMV),
                 "an association MV must be created: " + defs);

      ViewsheetSandbox box = open();
      Viewsheet vs = box.getViewsheet();
      SelectionListVSAssembly names = (SelectionListVSAssembly) vs.getAssembly("L_name");
      SelectionListVSAssembly years = (SelectionListVSAssembly) vs.getAssembly("L_year");

      assertAllCompatible(names);
      assertAllCompatible(years);

      click(box, names, "ALBERTO-1988");
      assertOnlyCompatible(years, "1988");
   }

   /**
    * A list on a calculated field of the table is not a missing column: it keeps working with an
    * MV, together with a list on a missing column.
    */
   @Test
   void calcFieldListKeepsAssociationWithMissingColumnList() throws Exception {
      materialize("82492_calc", vs -> {
         ExpressionRef exp = new ExpressionRef(null, "yearCalc");
         exp.setExpression("field['year']");
         CalculateRef calc = new CalculateRef(true);
         calc.setDataRef(exp);
         calc.setDataType("integer");
         vs.addCalcField(getTable(vs), calc);

         addList(vs, "L_name", "name");
         addList(vs, "L_calc", "yearCalc");
         addList(vs, "L_miss", "CUSTOMER_ID");
      });

      ViewsheetSandbox box = open();
      Viewsheet vs = box.getViewsheet();
      SelectionListVSAssembly names = (SelectionListVSAssembly) vs.getAssembly("L_name");
      SelectionListVSAssembly calcs = (SelectionListVSAssembly) vs.getAssembly("L_calc");

      assertAllCompatible(names);
      assertAllCompatible(calcs);

      click(box, names, "ALBERTO-1988");
      assertOnlyCompatible(calcs, "1988");
   }

   /**
    * The missing-column list still holds a value selected before its column was removed from the
    * table. The other lists keep their association.
    */
   @Test
   void staleSelectionOnMissingColumnListIsIgnored() throws Exception {
      materialize("82492_stale", vs -> {
         addList(vs, "L_name", "name");
         addList(vs, "L_year", "year");
         SelectionListVSAssembly miss = addList(vs, "L_miss", "CUSTOMER_ID");
         SelectionList slist = new SelectionList();
         SelectionValue value = new SelectionValue("42", "42");
         value.setSelected(true);
         slist.setSelectionValues(new SelectionValue[] { value });
         miss.setStateSelectionList(slist);
      });

      ViewsheetSandbox box = open();
      Viewsheet vs = box.getViewsheet();
      SelectionListVSAssembly names = (SelectionListVSAssembly) vs.getAssembly("L_name");
      SelectionListVSAssembly years = (SelectionListVSAssembly) vs.getAssembly("L_year");

      assertAllCompatible(names);
      assertAllCompatible(years);

      click(box, names, "ALBERTO-1988");
      assertOnlyCompatible(years, "1988");
   }

   /**
    * A list on a column of the table is added after the MVs were built, so the association MV
    * doesn't have its column, and the viewsheet is saved with a value of it selected. That
    * selection is not a missing column: it must still restrict the other lists (the full MV is
    * used for it), not be ignored.
    */
   @Test
   void selectionOnColumnMissingFromStaleAssociationMVIsApplied() throws Exception {
      List<MVDef> defs = materialize("82492_assoc", vs -> {
         addList(vs, "L_name", "name");
         addList(vs, "L_year", "year");
         addList(vs, "L_miss", "CUSTOMER_ID");
      });
      assertTrue(defs.stream().anyMatch(MVDef::isAssociationMV),
                 "an association MV must be created: " + defs);

      // a wind value that only some years have
      AssetRepository repository = AssetUtil.getAssetRepository(false);
      Viewsheet vs = (Viewsheet) repository.getSheet(entry, user, false, AssetContent.ALL);
      List<Map<String, String>> rows = getRows(vs);
      Set<String> allYears = new TreeSet<>();
      Map<String, Set<String>> yearsOfWind = new TreeMap<>();
      rows.forEach(r -> {
         allYears.add(r.get("year"));
         yearsOfWind.computeIfAbsent(r.get("wind"), k -> new TreeSet<>()).add(r.get("year"));
      });
      String wind = yearsOfWind.entrySet().stream()
         .filter(e -> e.getValue().size() > 1 && e.getValue().size() < allYears.size())
         .map(Map.Entry::getKey).findFirst().orElseThrow();
      Set<String> expected = yearsOfWind.get(wind);

      // add the list after the MVs were built (no new analysis) with the value selected
      vs = (Viewsheet) vs.clone();
      SelectionListVSAssembly winds = addList(vs, "L_wind", "wind");
      SelectionList slist = new SelectionList();
      SelectionValue value = new SelectionValue(wind, wind);
      value.setSelected(true);
      slist.setSelectionValues(new SelectionValue[] { value });
      winds.setStateSelectionList(slist);
      repository.setSheet(entry, vs, user, true);

      ViewsheetSandbox box = open();
      SelectionListVSAssembly years =
         (SelectionListVSAssembly) box.getViewsheet().getAssembly("L_year");
      Set<String> compatible = new TreeSet<>();
      getStates(years).forEach((v, state) -> {
         if((state & SelectionValue.STATE_COMPATIBLE) != 0) {
            compatible.add(v);
         }
      });

      assertEquals(expected, compatible, "L_year must be restricted by wind " + wind);
   }

   private static List<Map<String, String>> getRows(Viewsheet vs) {
      Object assembly = vs.getBaseWorksheet().getAssembly(getTable(vs));

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

         for(String col : new String[] { "wind", "year" }) {
            Object value = data.getObject(r, cols.get(col));
            row.put(col, value == null ? null : Tool.getDataString(value));
         }

         if(!row.containsValue(null)) {
            rows.add(row);
         }
      }

      return rows;
   }

   /**
    * Save a copy of EmbeddedVS1 changed by {@code change}, analyze it and create its MVs the way
    * the Enterprise Manager does.
    */
   private List<MVDef> materialize(String name, java.util.function.Consumer<Viewsheet> change)
      throws Exception
   {
      AssetRepository repository = AssetUtil.getAssetRepository(false);
      AssetEntry base = AssetEntry.createAssetEntry(BASE_ID);
      Viewsheet vs = (Viewsheet) repository.getSheet(base, user, false, AssetContent.ALL);
      vs = (Viewsheet) vs.clone();
      change.accept(vs);
      entry = new AssetEntry(base.getScope(), base.getType(), name, base.getUser(),
                             base.getOrgID());
      repository.setSheet(entry, vs, user, true);

      MVSupportService.AnalysisResult result = mvSupport.analyze(
         List.of(entry.toIdentifier()), false, true, true, user, false, false);
      // not result.waitFor(), see SreeHomeExtension.materialize
      Awaitility.await()
         .atMost(2, TimeUnit.MINUTES)
         .pollInterval(200, TimeUnit.MILLISECONDS)
         .until(result::isCompleted);
      assertTrue(result.getExceptions().isEmpty(), () -> result.getExceptions().stream()
         .map(e -> e.getMessage()).collect(Collectors.joining("\n")));

      List<MVSupportService.MVStatus> statuses = result.getStatus();
      List<MVDef> defs = statuses.stream().map(MVSupportService.MVStatus::getDefinition)
         .collect(Collectors.toList());
      List<String> names = defs.stream().map(MVDef::getName).collect(Collectors.toList());
      assertFalse(names.isEmpty(), "no MV was analyzed");
      mvNames.addAll(names);

      try {
         assertNull(mvSupport.createMV(names, statuses, false, false, user));
      }
      catch(Exception | Error e) {
         throw e;
      }
      catch(Throwable e) {
         throw new Exception("Failed to create MV", e);
      }

      return defs;
   }

   private ViewsheetSandbox open() throws Exception {
      runtimeId = viewsheetService.openViewsheet(entry, user, true);
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, user);
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      box.lockWrite();

      try {
         // CoreLifecycleService.refreshViewsheet(reset=true)
         box.reset(null, box.getViewsheet().getAssemblies(), new ChangedAssemblyList(), true,
                   true, null);
      }
      finally {
         box.unlockWrite();
      }

      return box;
   }

   private static String getTable(Viewsheet vs) {
      return ((TableVSAssembly) vs.getAssembly("TableView1")).getSourceInfo().getSource();
   }

   private static SelectionListVSAssembly addList(Viewsheet vs, String name, String col) {
      SelectionListVSAssembly selection = new SelectionListVSAssembly(vs, name);
      SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) selection.getInfo();
      info.setTableName(getTable(vs));
      info.setDataRef(new ColumnRef(new AttributeRef(null, col)));
      vs.addAssembly(selection);
      return selection;
   }

   /**
    * A plain click on a value of a multi-select list, applied the way
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
               svalue.setSelected(true);
               svalue.setExcluded(false);
               found = true;
            }
         }

         assertTrue(found, value + " not in " + list.getName());
         slist.setSelectionValues(
            SelectionVSUtil.shrinkSelectionValues(list, slist.getSelectionValues()));
         int hint = list.setStateSelectionList(slist);
         box.processChange(list.getAbsoluteName(), hint, new ChangedAssemblyList(true));
      }
      finally {
         box.unlockWrite();
      }
   }

   private static Map<String, Integer> getStates(SelectionListVSAssembly list) {
      SelectionList slist = list.getSelectionList();
      assertNotNull(slist, list.getName() + " has no values");
      Map<String, Integer> states = new TreeMap<>();

      for(int i = 0; i < slist.getSelectionValueCount(); i++) {
         states.put(slist.getSelectionValue(i).getValue(), slist.getSelectionValue(i).getState());
      }

      assertFalse(states.isEmpty(), list.getName() + " has no values");
      return states;
   }

   private static void assertAllCompatible(SelectionListVSAssembly list) {
      Map<String, Integer> states = getStates(list);
      List<String> wrong = states.entrySet().stream()
         .filter(e -> (e.getValue() & SelectionValue.STATE_COMPATIBLE) == 0)
         .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.toList());

      assertTrue(wrong.isEmpty(), list.getName() + " values not compatible on open: " + wrong);
   }

   private static void assertOnlyCompatible(SelectionListVSAssembly list, String expected) {
      Map<String, Integer> states = getStates(list);
      assertTrue(states.containsKey(expected), expected + " not in " + list.getName());
      List<String> wrong = new ArrayList<>();

      states.forEach((value, state) -> {
         boolean compatible = (state & SelectionValue.STATE_COMPATIBLE) != 0;
         boolean excluded = (state & SelectionValue.STATE_EXCLUDED) != 0;

         if(compatible != value.equals(expected) || excluded == value.equals(expected)) {
            wrong.add(value + "=" + state);
         }
      });

      assertTrue(wrong.isEmpty(), list.getName() + " has wrong association states " + wrong);
   }

   private static final String BASE_ID = "1^128^__NULL__^EmbeddedVS1^host-org";

   @Autowired
   private ViewsheetService viewsheetService;
   @Autowired
   private MVSupportService mvSupport;

   private final Set<String> mvNames = new LinkedHashSet<>();
   private Principal oldPrincipal;
   private Principal user;
   private AssetEntry entry;
   private String runtimeId;
}
