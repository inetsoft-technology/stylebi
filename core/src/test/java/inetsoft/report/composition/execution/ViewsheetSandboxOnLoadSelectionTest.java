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

import inetsoft.report.TableLens;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeSheet;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.AssemblyEntry;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.RadioButtonVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.SelectionListVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79367: the viewsheet onLoad script ran before the changed selection was applied to the
 * table, so a read of {@code Table.table} in onLoad returned the rows of the previous selection.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class ViewsheetSandboxOnLoadSelectionTest {
   /**
    * Select one value, add a second one, then drop the first one. After each change the row
    * count written by onLoad must be the count of the table under the current selection.
    */
   @Test
   void onLoadReadsTheTableFilteredByTheCurrentSelection() throws Exception {
      ViewsheetSandbox box = vsResource.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      SourceInfo source =
         (SourceInfo) ((TableVSAssembly) vs.getAssembly(TABLE)).getSourceInfo().clone();
      SelectionListVSAssembly selection = selection(vs, source.getSource());
      TextVSAssembly text = new TextVSAssembly(vs, TEXT);
      vs.addAssembly(text);
      vs.getViewsheetInfo().setScriptEnabled(true);
      vs.getViewsheetInfo().setOnLoad(
         TEXT + ".text = 'Rows:' + (" + TABLE + ".table.length - 1);");

      int all = rows(box.getData(TABLE));
      int[] counts = new int[3];
      String[][] steps = { { "NAMED" }, { "NAMED", "UNNAMED" }, { "UNNAMED" } };

      for(int i = 0; i < steps.length; i++) {
         select(box, selection, steps[i]);
         counts[i] = rows(box.getData(TABLE));
         assertEquals("Rows:" + counts[i], textOf(text), "onLoad after step " + (i + 1));
      }

      assertTrue(counts[0] < all && counts[2] < all && counts[0] != counts[2],
                 "fixture: the steps must filter differently");
   }

   /**
    * A viewsheet opened with a selection saved in it: the first onLoad run, in reset(), must
    * already read the table filtered by the saved selection.
    */
   @Test
   void onLoadReadsTheTableFilteredByTheSavedSelectionOnOpen() throws Exception {
      ViewsheetSandbox box = vsResource.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      SourceInfo source =
         (SourceInfo) ((TableVSAssembly) vs.getAssembly(TABLE)).getSourceInfo().clone();
      SelectionListVSAssembly selection = selection(vs, source.getSource());
      TextVSAssembly text = new TextVSAssembly(vs, TEXT);
      vs.addAssembly(text);
      vs.getViewsheetInfo().setScriptEnabled(true);
      vs.getViewsheetInfo().setOnLoad(
         TEXT + ".text = 'Rows:' + (" + TABLE + ".table.length - 1);");

      int all = rows(box.getData(TABLE));
      SelectionList list = new SelectionList();
      SelectionValue value = new SelectionValue("NAMED", "NAMED");
      value.setState(SelectionValue.STATE_SELECTED);
      list.setSelectionValues(new SelectionValue[] { value });
      selection.setStateSelectionList(list);

      box.lockWrite();

      try {
         box.reset(null, vs.getAssemblies(), new ChangedAssemblyList(), true, true, null);
      }
      finally {
         box.unlockWrite();
      }

      int filtered = rows(box.getData(TABLE));
      assertTrue(filtered < all, "fixture: the saved selection must filter the table");
      assertEquals("Rows:" + filtered, textOf(text), "onLoad when the viewsheet is opened");
   }

   /**
    * Bug #82168: a viewsheet without an onLoad script, opened with a selection saved in it, and a
    * crosstab with a condition on an input. A text expression run in reset() before the saved
    * selection is applied cached the crosstab's table and data arrays, and the later runs of
    * the expression read them, so the text showed the rows before the selection.
    */
   @Test
   void crosstabTableAndDataReadTheSavedSelectionOnOpenWithoutOnLoad() throws Exception {
      assertCrosstabScriptReadsTheSavedSelectionOnOpen(true, false);
   }

   /**
    * Bug #82168, opened with the breakable change list the viewer uses: the crosstab's data
    * array, which only clearDataArray() drops, must not keep the rows before the selection.
    */
   @Test
   void crosstabDataReadsTheSavedSelectionOnViewerOpenWithoutOnLoad() throws Exception {
      assertCrosstabScriptReadsTheSavedSelectionOnOpen(false, true);
   }

   private void assertCrosstabScriptReadsTheSavedSelectionOnOpen(boolean inputCondition,
                                                                 boolean viewer)
      throws Exception
   {
      ViewsheetSandbox box = vsResource.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      SourceInfo source =
         (SourceInfo) ((TableVSAssembly) vs.getAssembly(TABLE)).getSourceInfo().clone();
      SelectionListVSAssembly selection = selection(vs, source.getSource());
      CrosstabVSAssembly crosstab = crosstab(vs, source);

      if(inputCondition) {
         likeInputCondition(vs, crosstab);
      }

      vs.addAssembly(crosstab);
      TextVSAssembly text = new TextVSAssembly(vs, TEXT);
      ((TextVSAssemblyInfo) text.getVSAssemblyInfo()).setTextValue(
         "='Rows:' + " + CROSSTAB + ".table.length + ' Data:' + " + CROSSTAB + ".data.length");
      vs.addAssembly(text);
      vs.getViewsheetInfo().setScriptEnabled(true);
      assertNull(vs.getViewsheetInfo().getOnLoad(), "fixture: the viewsheet has no onLoad script");

      int all = rows(box.getData(TABLE));
      SelectionList list = new SelectionList();
      SelectionValue value = new SelectionValue("NAMED", "NAMED");
      value.setState(SelectionValue.STATE_SELECTED);
      list.setSelectionValues(new SelectionValue[] { value });
      selection.setStateSelectionList(list);
      ChangedAssemblyList clist = viewer ? viewerList(box) : new ChangedAssemblyList();
      box.lockWrite();

      try {
         box.reset(null, vs.getAssemblies(), clist, true, true, null);
      }
      finally {
         box.unlockWrite();
      }

      int filtered = rows(box.getData(TABLE));
      assertTrue(filtered < all, "fixture: the saved selection must filter the table");
      TableLens view = box.getVSTableLens(CROSSTAB, false);
      view.moreRows(Integer.MAX_VALUE);
      // the crosstab's data is the filtered base table with its header row
      assertEquals("Rows:" + view.getRowCount() + " Data:" + (filtered + 1), textOf(text),
                   "text expression when the viewsheet is opened");
   }

   /**
    * A crosstab of the count of names by type, on the source of the table.
    */
   private static CrosstabVSAssembly crosstab(Viewsheet vs, SourceInfo source) {
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, CROSSTAB);
      crosstab.setSourceInfo(source);
      VSDimensionRef dim = new VSDimensionRef();
      dim.setDataRef(new AttributeRef("type"));
      dim.setGroupColumnValue("type");
      dim.setDataType(XSchema.STRING);
      VSAggregateRef agg = new VSAggregateRef();
      agg.setDataRef(new AttributeRef("name"));
      agg.setColumnValue("name");
      agg.setFormulaValue(AggregateFormula.COUNT_ALL.getFormulaName());
      VSCrosstabInfo cinfo = new VSCrosstabInfo();
      cinfo.setDesignRowHeaders(new VSDimensionRef[] { dim });
      cinfo.setDesignColHeaders(new VSDimensionRef[0]);
      cinfo.setDesignAggregates(new VSAggregateRef[] { agg });
      crosstab.setVSCrosstabInfo(cinfo);
      return crosstab;
   }

   /**
    * Add a radio button and a crosstab condition {@code name like $(radio)} that matches all.
    */
   private static void likeInputCondition(Viewsheet vs, CrosstabVSAssembly crosstab) {
      RadioButtonVSAssembly radio = new RadioButtonVSAssembly(vs, RADIO);
      RadioButtonVSAssemblyInfo info = (RadioButtonVSAssemblyInfo) radio.getVSAssemblyInfo();
      ListData data = new ListData();
      data.setLabels(new String[] { "All", "A" });
      data.setValues(new Object[] { "%", "A%" });
      data.setDataType(XSchema.STRING);
      info.setListData(data);
      info.setDataType(XSchema.STRING);
      info.setSelectedObject("%");
      vs.addAssembly(radio);

      Condition condition = new Condition(XSchema.STRING);
      condition.setOperation(XCondition.LIKE);
      condition.addValue(new UserVariable(RADIO));
      ConditionList conditions = new ConditionList();
      conditions.append(new ConditionItem(new AttributeRef("name"), condition, 0));
      crosstab.setPreConditionList(conditions);
   }

   /**
    * A breakable list whose ready listener does what the viewer's does for a ready assembly:
    * execute its view and load its view table.
    */
   private static ChangedAssemblyList viewerList(ViewsheetSandbox box) {
      ChangedAssemblyList clist = new ChangedAssemblyList(true);
      clist.setReadyListener(new ChangedAssemblyList.ReadyListener() {
         @Override
         public void onReady() throws Exception {
            while(!clist.getReadyList().isEmpty()) {
               AssemblyEntry entry = clist.getReadyList().removeFirst();
               box.executeView(entry.getAbsoluteName(), false);
               box.getVSTableLens(entry.getAbsoluteName(), false);

               if(!clist.getProcessedList().contains(entry)) {
                  clist.getProcessedList().add(entry);
               }
            }
         }

         @Override
         public void setRuntimeSheet(RuntimeSheet rs) {
         }

         @Override
         public RuntimeSheet getRuntimeSheet() {
            return null;
         }

         @Override
         public void setInitingGrid(boolean grid) {
         }

         @Override
         public boolean isInitingGrid() {
            return false;
         }

         @Override
         public String getID() {
            return null;
         }

         @Override
         public void setID(String id) {
         }
      });

      return clist;
   }

   /**
    * Without an onLoad script the change is applied by processSelections only, as before.
    */
   @Test
   void selectionChangeWithoutOnLoadStillFiltersTheTable() throws Exception {
      ViewsheetSandbox box = vsResource.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      SourceInfo source =
         (SourceInfo) ((TableVSAssembly) vs.getAssembly(TABLE)).getSourceInfo().clone();
      SelectionListVSAssembly selection = selection(vs, source.getSource());
      int all = rows(box.getData(TABLE));

      select(box, selection, "NAMED");
      assertTrue(rows(box.getData(TABLE)) < all);
   }

   private static SelectionListVSAssembly selection(Viewsheet vs, String table) {
      SelectionListVSAssembly selection = new SelectionListVSAssembly(vs, "Type79367");
      SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) selection.getInfo();
      info.setTableName(table);
      info.setDataRef(new ColumnRef(new AttributeRef(null, "type")));
      vs.addAssembly(selection);
      return selection;
   }

   /**
    * Select the values in the selection list, as VSSelectionService.applySelection does.
    */
   private static void select(ViewsheetSandbox box, SelectionListVSAssembly selection,
                              String... selected) throws Exception
   {
      box.lockWrite();

      try {
         SelectionList list = new SelectionList();
         SelectionValue[] values = new SelectionValue[selected.length];

         for(int i = 0; i < selected.length; i++) {
            values[i] = new SelectionValue(selected[i], selected[i]);
            values[i].setState(SelectionValue.STATE_SELECTED);
         }

         list.setSelectionValues(values);
         int hint = selection.setStateSelectionList(list);
         box.processChange(selection.getAbsoluteName(), hint, new ChangedAssemblyList());
      }
      finally {
         box.unlockWrite();
      }
   }

   private static String textOf(TextVSAssembly text) {
      return ((TextVSAssemblyInfo) text.getVSAssemblyInfo()).getText();
   }

   private static int rows(Object data) {
      TableLens lens = (TableLens) data;
      lens.moreRows(Integer.MAX_VALUE);
      return lens.getRowCount() - lens.getHeaderRowCount();
   }

   private static OpenViewsheetEvent openViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());

   private static final String TABLE = "TableView1";
   private static final String TEXT = "Text79367";
   private static final String CROSSTAB = "Crosstab82168";
   private static final String RADIO = "Radio82168";
}
