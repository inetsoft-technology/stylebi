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
import inetsoft.sree.security.OrganizationManager;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.InputVSAssemblyInfo;
import inetsoft.util.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for ViewsheetSandbox.refreshVariable(), specifically the write-back of an
 * input assembly's selected value into the cell of its bound embedded table.
 *
 * The target cell is addressed by the assembly's columnValue/rowValue, both of which may
 * be bound to a variable or an expression. The method is private, so it is exercised via
 * reflection, and a minimal AssetQuerySandbox is injected into the sandbox to avoid the
 * full viewsheet init cycle -- the same approach as ApplyParameterToInputTest.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, RefreshVariableTest.TestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RefreshVariableTest {
   /**
    * refreshVariable() invalidates the cache for the table it just wrote to. Only the
    * write itself is under test here, so a stub is enough and it keeps the context from
    * pulling in the whole data source registry.
    */
   @Configuration
   static class TestConfig {
      @Bean
      public AssetDataCache assetDataCache() {
         return mock(AssetDataCache.class);
      }
   }

   /** Name of the bound embedded table in the base worksheet. */
   private static final String TABLE = "Query1_O";
   /** Column index of "state" / "product_name" in the embedded data. */
   private static final int STATE_COL = 0;
   private static final int PRODUCT_COL = 1;

   private Viewsheet vs;
   private EmbeddedTableAssembly embedded;
   private ViewsheetSandbox sandbox;
   private Method refreshVariable;

   @BeforeEach
   void setUp() throws Exception {
      Worksheet ws = new Worksheet();
      embedded = new EmbeddedTableAssembly(ws, TABLE);
      // row 0 is the header row, so the addressable data rows are 1 and 2
      embedded.setEmbeddedData(new XEmbeddedTable(
         new String[]{ XSchema.STRING, XSchema.STRING },
         new Object[][] {
            { "state", "product_name" },
            { "NY", "InsideView" },
            { "CA", "Fast Mail" }
         }));
      ws.addAssembly(embedded);

      vs = new Viewsheet();

      // Viewsheet.setBaseWorksheet() is private and only reachable through a full
      // update() against an asset repository, so wire the base worksheet directly.
      Field wsField = Viewsheet.class.getDeclaredField("ws");
      wsField.setAccessible(true);
      wsField.set(vs, ws);

      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "test/RefreshVariableTest",
         null, OrganizationManager.getInstance().getCurrentOrgID());
      sandbox = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);

      Field wboxField = ViewsheetSandbox.class.getDeclaredField("wbox");
      wboxField.setAccessible(true);
      wboxField.set(sandbox, new AssetQuerySandbox(ws, null, new VariableTable()));

      refreshVariable = ViewsheetSandbox.class.getDeclaredMethod(
         "refreshVariable", InputVSAssembly.class, boolean.class, ChangedAssemblyList.class);
      refreshVariable.setAccessible(true);
   }

   /**
    * Create an input assembly bound to a cell of the embedded table.
    *
    * @param columnValue the design time column binding, literal or dynamic.
    * @param rowValue    the design time row binding, literal or dynamic.
    */
   private TextInputVSAssembly createInput(String columnValue, String rowValue) {
      TextInputVSAssembly input = new TextInputVSAssembly(vs, "TextInput1");
      input.setTableName(TABLE);
      input.setColumnValue(columnValue);
      input.setRowValue(rowValue);
      input.setSelectedObject("written");
      vs.addAssembly(input);

      return input;
   }

   /**
    * Stand in for ViewsheetSandbox.executeDynamicValues(), which resolves a variable or
    * expression and stores the result on the DynamicValue. updateAssembly() always runs
    * that immediately before refreshVariable().
    */
   private void execute(InputVSAssembly input, String dvalue, Object rvalue) {
      ((InputVSAssemblyInfo) input.getVSAssemblyInfo()).getDynamicValues().stream()
         .filter(dval -> Tool.equals(dvalue, dval.getDValue()))
         .forEach(dval -> dval.setRValue(rvalue));
   }

   private Object cell(int row, int col) {
      return embedded.getEmbeddedData().getObject(row, col);
   }

   private void invoke(InputVSAssembly input) throws Exception {
      refreshVariable.invoke(sandbox, input, false, null);
   }

   @Test
   void literalColumnValueWritesCell() throws Exception {
      invoke(createInput("state", "1"));

      assertEquals("written", cell(1, STATE_COL));
   }

   @Test
   void expressionColumnValueWritesCell() throws Exception {
      TextInputVSAssembly input = createInput("='state'", "1");
      execute(input, "='state'", "state");

      invoke(input);

      assertEquals("written", cell(1, STATE_COL));
   }

   @Test
   void variableColumnValueWritesCell() throws Exception {
      TextInputVSAssembly input = createInput("$(PField)", "=2");
      execute(input, "$(PField)", "product_name");
      execute(input, "=2", 2);

      invoke(input);

      assertEquals("written", cell(2, PRODUCT_COL));
   }

   @Test
   void unresolvableColumnLeavesTableUnchanged() throws Exception {
      TextInputVSAssembly input = createInput("='nosuchcolumn'", "1");
      execute(input, "='nosuchcolumn'", "nosuchcolumn");

      assertDoesNotThrow(() -> invoke(input));
      assertEquals("NY", cell(1, STATE_COL));
   }

   @Test
   void literalColumnValueWinsOverStaleCachedColumn() throws Exception {
      // Bug #75929: the DataRef cached on the assembly info is only refreshed on a full
      // sandbox reset, so a binding changed since then must not be written to the old
      // column.
      TextInputVSAssembly input = createInput("state", "1");
      DataRef stale = embedded.getColumnSelection(false).getAttribute("product_name");
      input.setColumn(stale);

      invoke(input);

      assertEquals("written", cell(1, STATE_COL));
      assertEquals("InsideView", cell(1, PRODUCT_COL));
   }
}
