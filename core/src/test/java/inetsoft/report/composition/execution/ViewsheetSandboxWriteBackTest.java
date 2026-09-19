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

import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.asset.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression for Bug #76799: writeBackFormDataDirectly() performed zero bounds
 * checking on {@code row} before writing into the embedded table, so row 0 (the
 * header) silently renamed the column header instead of failing, and a row one past
 * the last data row silently appended a phantom row instead of failing.
 */
@WizAgentTestSupport
class ViewsheetSandboxWriteBackTest {
   private static final String TABLE = "Query1_O";
   private static final int CUSTOMER_ID_COL = 0;

   private Viewsheet vs;
   private ViewsheetSandbox sandbox;

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

      // Viewsheet.setBaseWorksheet() is private; it is what populates both the base
      // and original worksheet fields that writeBackFormDataDirectly reads.
      Method setBaseWorksheet = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      setBaseWorksheet.setAccessible(true);
      setBaseWorksheet.invoke(vs, ws);

      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "test/ViewsheetSandboxWriteBackTest",
         null, OrganizationManager.getInstance().getCurrentOrgID());
      sandbox = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);
   }

   /**
    * setBaseWorksheet() clones ws into vs's originalWs, which is the copy
    * writeBackFormDataDirectly actually mutates -- so assertions must read back through
    * the viewsheet's own worksheet, not the pre-clone EmbeddedTableAssembly built in setUp().
    */
   private EmbeddedTableAssembly writtenTable() {
      return (EmbeddedTableAssembly) vs.getOriginalWorksheet().getAssembly(TABLE);
   }

   private Object cell(int row, int col) {
      return writtenTable().getEmbeddedData().getObject(row, col);
   }

   @Test
   void rejectsRowZeroInsteadOfRenamingTheHeader() {
      assertThrows(RuntimeException.class,
         () -> sandbox.writeBackFormDataDirectly(null, TABLE, "customer_id", 0, "80"));
      assertEquals("customer_id", cell(0, CUSTOMER_ID_COL));
   }

   @Test
   void rejectsOnePastTheLastRowInsteadOfAppendingAPhantomRow() {
      int rowCount = writtenTable().getEmbeddedData().getRowCount();

      assertThrows(RuntimeException.class,
         () -> sandbox.writeBackFormDataDirectly(null, TABLE, "customer_id", rowCount, "x"));
      assertEquals(rowCount, writtenTable().getEmbeddedData().getRowCount());
   }

   @Test
   void stillWritesAValidRow() throws Exception {
      sandbox.writeBackFormDataDirectly(null, TABLE, "customer_id", 1, "80");

      assertEquals("80", cell(1, CUSTOMER_ID_COL));
   }
}
