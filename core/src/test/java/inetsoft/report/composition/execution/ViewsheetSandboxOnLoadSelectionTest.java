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
import inetsoft.test.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.*;
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
}
