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
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78579: the caller-binding refusals of {@code writeBackFormDataDirectly} (row out of range,
 * column not in the table, table not an embedded worksheet table) were thrown as plain
 * {@code RuntimeException}s, which {@code CapturingCommandDispatcher.toCommandError} does not
 * recognise, so the wiz agent got the opaque "StyleBI-side bug" 500. They must be thrown as
 * exactly {@code MessageException} (not a subclass -- {@code toCommandError} matches the exact
 * class) carrying the existing catalog text. The null table/column no-op must stay silent.
 */
@WizAgentTestSupport
class ViewsheetSandboxWriteBackRefusalTest {
   private static final String TABLE = "T78579";

   private Viewsheet vs;
   private ViewsheetSandbox sandbox;

   @BeforeEach
   void setUp() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly embedded = new EmbeddedTableAssembly(ws, TABLE);
      embedded.setEmbeddedData(new XEmbeddedTable(
         new String[]{ XSchema.STRING, XSchema.INTEGER },
         new Object[][] {
            { "state", "qty" },
            { "CA", 10 },
            { "NY", 20 }
         }));
      ws.addAssembly(embedded);

      vs = new Viewsheet();
      Method setBaseWorksheet = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      setBaseWorksheet.setAccessible(true);
      setBaseWorksheet.invoke(vs, ws);

      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
         "test/ViewsheetSandboxWriteBackRefusalTest", null,
         OrganizationManager.getInstance().getCurrentOrgID());
      sandbox = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);
   }

   private XEmbeddedTable data() {
      return ((EmbeddedTableAssembly) vs.getOriginalWorksheet().getAssembly(TABLE)).getEmbeddedData();
   }

   private static void assertExactMessageException(Exception e, String expectedText) {
      assertSame(MessageException.class, e.getClass(),
                 "expected exactly MessageException but was " + e.getClass().getName() +
                 ": " + e.getMessage());
      assertEquals(expectedText, e.getMessage());
   }

   @Test
   void rowInvalidIsAnExactMessageExceptionWithTheCatalogText() {
      Exception e = assertThrows(Exception.class,
         () -> sandbox.writeBackFormDataDirectly(null, TABLE, "qty", 5, 90));
      assertExactMessageException(
         e, Catalog.getCatalog().getString("write.back.failed.rowInvalid", 5, TABLE));
      assertEquals(3, data().getRowCount());
   }

   @Test
   void notFindColumnIsAnExactMessageExceptionWithTheCatalogText() {
      Exception e = assertThrows(Exception.class,
         () -> sandbox.writeBackFormDataDirectly(null, TABLE, "nosuch", 1, 90));
      assertExactMessageException(
         e, Catalog.getCatalog().getString("write.back.failed.notFindColumn", "nosuch", TABLE));
      assertEquals(10, data().getObject(1, 1));
   }

   @Test
   void wrongSourceIsAnExactMessageExceptionWithTheCatalogText() {
      Exception e = assertThrows(Exception.class,
         () -> sandbox.writeBackFormDataDirectly(null, "NoSuchTable", "qty", 1, 90));
      assertExactMessageException(
         e, Catalog.getCatalog().getString("write.back.failed.wrongSource", "NoSuchTable"));
   }

   /** Control (row L8): a write-back input with no table or column stays a silent no-op. */
   @Test
   void nullTableOrColumnStaysASilentNoOp() {
      assertDoesNotThrow(() -> sandbox.writeBackFormDataDirectly(null, null, "qty", 5, 90));
      assertDoesNotThrow(() -> sandbox.writeBackFormDataDirectly(null, TABLE, null, 5, 90));
      assertEquals(3, data().getRowCount());
      assertEquals(10, data().getObject(1, 1));
   }
}
