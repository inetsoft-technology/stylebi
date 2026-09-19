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
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.TextInputVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression for Bug #76799: InputVSAssemblyInfo.update()'s own row-bounds check
 * (row < 0 || row > table.getRowCount()) was off-by-one at both ends -- it wrongly
 * allowed row 0 (the embedded table's header row) and row == getRowCount() (one past
 * the last data row) when validating an Input assembly's (Slider/Spinner/TextInput/etc.)
 * own bound row/column read.
 */
@WizAgentTestSupport
class InputVSAssemblyInfoTest {
   private static final String TABLE = "Query1_O";

   private Viewsheet vs;

   @BeforeEach
   void setUp() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly embedded = new EmbeddedTableAssembly(ws, TABLE);
      embedded.setEmbeddedData(new XEmbeddedTable(
         new String[]{ XSchema.STRING },
         new Object[][] {
            { "customer_id" },
            { "1001" }
         }));
      ws.addAssembly(embedded);

      vs = new Viewsheet();

      Method setBaseWorksheet = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      setBaseWorksheet.setAccessible(true);
      setBaseWorksheet.invoke(vs, ws);
   }

   private TextInputVSAssembly createInput(String rowValue) {
      TextInputVSAssembly input = new TextInputVSAssembly(vs, "TextInput1");
      input.setTableName(TABLE);
      input.setColumnValue("customer_id");
      input.setRowValue(rowValue);
      vs.addAssembly(input);

      return input;
   }

   @Test
   void rejectsRowZeroAsTheHeaderRow() {
      TextInputVSAssemblyInfo info = (TextInputVSAssemblyInfo) createInput("0").getVSAssemblyInfo();

      assertThrows(RuntimeException.class, () -> info.update(vs, new ColumnSelection()));
   }

   @Test
   void rejectsOnePastTheLastDataRow() {
      TextInputVSAssemblyInfo info = (TextInputVSAssemblyInfo) createInput("2").getVSAssemblyInfo();

      assertThrows(RuntimeException.class, () -> info.update(vs, new ColumnSelection()));
   }

   @Test
   void acceptsAValidDataRow() {
      TextInputVSAssemblyInfo info = (TextInputVSAssemblyInfo) createInput("1").getVSAssemblyInfo();

      assertDoesNotThrow(() -> info.update(vs, new ColumnSelection()));
   }
}
