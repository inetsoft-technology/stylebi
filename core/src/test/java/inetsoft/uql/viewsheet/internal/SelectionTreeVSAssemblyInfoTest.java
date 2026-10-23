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
package inetsoft.uql.viewsheet.internal;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.SelectionTreeVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for Bug #79252's "one open design question" (flagged by the refuter's
 * recheck, not by the fix itself -- the existing mechanism already degrades safely): a dynamic
 * parentIdColumn/idColumn/labelColumn on an ID-hierarchy SelectionTree is resolved, on every
 * ordinary refresh, by {@link SelectionTreeVSAssemblyInfo#update(Viewsheet, ColumnSelection)} /
 * {@code restoreRefs}, which additively rebuilds {@code dataRefs} from the table's live column
 * selection keyed on the live-resolved {@code getParentID()}/{@code getID()}/{@code getLabel()}
 * values. These tests exercise that method directly (bypassing set_selection_source, whose own
 * dynamic-value resolution is covered by {@code SelectionBindingServiceTest}) for the specific
 * edge case the refuter flagged: a dynamic reference whose bound input has no value yet, so the
 * live-resolved name is null and cannot match any real column. The live-resolved name is
 * simulated directly via {@code setParentID}/{@code setID}/{@code setLabel} (the RUNTIME value
 * setters), the same shape {@code getParentID()}/{@code getID()}/{@code getLabel()} read back --
 * this needs no live DynamicValue/variable resolution machinery, since {@code update()}/
 * {@code restoreRefs} only ever consume the already-resolved String those getters return.
 */
@WizAgentTestSupport
class SelectionTreeVSAssemblyInfoTest {
   private static final String TABLE = "Query1_O";

   private Viewsheet vs;

   @BeforeEach
   void setUp() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly embedded = new EmbeddedTableAssembly(ws, TABLE);
      embedded.setEmbeddedData(new XEmbeddedTable(
         new String[]{ XSchema.STRING, XSchema.STRING, XSchema.STRING },
         new Object[][] {
            { "CITY", "STATE", "NAME" },
            { "1", "", "All Products" }
         }));
      ws.addAssembly(embedded);

      vs = new Viewsheet();

      Method setBaseWorksheet = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      setBaseWorksheet.setAccessible(true);
      setBaseWorksheet.invoke(vs, ws);
   }

   private SelectionTreeVSAssemblyInfo createIdModeTree() {
      SelectionTreeVSAssembly tree = new SelectionTreeVSAssembly(vs, "Tree1");
      tree.setTableName(TABLE);
      vs.addAssembly(tree);

      SelectionTreeVSAssemblyInfo info = (SelectionTreeVSAssemblyInfo) tree.getVSAssemblyInfo();
      info.setMode(SelectionTreeVSAssemblyInfo.ID);
      return info;
   }

   private static List<String> namesOf(DataRef[] refs) {
      return Arrays.stream(refs).map(DataRef::getName).toList();
   }

   @Test
   void restoresAllThreeColumnsWhenEveryoneResolves() throws Exception {
      SelectionTreeVSAssemblyInfo info = createIdModeTree();
      info.setParentID("CITY");
      info.setID("STATE");
      info.setLabel("NAME");

      info.update(vs, new ColumnSelection());

      List<String> names = namesOf(info.getDataRefs());
      assertTrue(names.contains("CITY"), names.toString());
      assertTrue(names.contains("STATE"), names.toString());
      assertTrue(names.contains("NAME"), names.toString());
   }

   /**
    * The refuter's narrower edge case: a dynamic labelColumn bound to an input with no value yet
    * (or any other case where the live-resolved name matches no real column) degrades
    * predictably -- no entry added for that one field -- rather than throwing, while
    * parentId/id, which do resolve, are added normally.
    */
   @Test
   void degradesPredictablyWhenTheLiveResolvedLabelMatchesNoColumn() throws Exception {
      SelectionTreeVSAssemblyInfo info = createIdModeTree();
      info.setParentID("CITY");
      info.setID("STATE");
      info.setLabel(null); // simulates a dynamic reference whose input has no value yet

      assertDoesNotThrow(() -> info.update(vs, new ColumnSelection()));

      List<String> names = namesOf(info.getDataRefs());
      assertTrue(names.contains("CITY"), names.toString());
      assertTrue(names.contains("STATE"), names.toString());
      assertFalse(names.contains("NAME"), names.toString());
      assertEquals(2, names.size());
   }

   @Test
   void recoversOnTheNextUpdateOnceTheLiveValueResolves() throws Exception {
      SelectionTreeVSAssemblyInfo info = createIdModeTree();
      info.setParentID("CITY");
      info.setID("STATE");
      info.setLabel(null);
      info.update(vs, new ColumnSelection());
      assertFalse(namesOf(info.getDataRefs()).contains("NAME"));

      // The input now has a value -- the same additive rebuild that ran (safely) on the first,
      // unresolved render runs again on the next refresh and picks it up; no special recovery
      // path is needed.
      info.setLabel("NAME");
      info.update(vs, new ColumnSelection());

      assertTrue(namesOf(info.getDataRefs()).contains("NAME"));
   }
}
