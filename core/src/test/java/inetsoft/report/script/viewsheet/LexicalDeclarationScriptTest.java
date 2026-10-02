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
package inetsoft.report.script.viewsheet;

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end check for bug #76980 through a real runtime viewsheet and its worksheet
 * sandbox: top-level {@code const}/{@code let} in user scripts must stay visible after
 * the #75688 statement split and across scripts, as they were on Rhino.
 *
 * <ul>
 *   <li>A worksheet expression column is evaluated by the real embedded-table query
 *       (FormulaTableLens with the sandbox's script env and scope).</li>
 *   <li>A viewsheet onInit script declares a constant that a later assembly script
 *       reads, through the real {@link ViewsheetSandbox#processOnInit()} and
 *       {@link ViewsheetScope#execute(String, String)}.</li>
 *   <li>A library script's top-level constant is read by an assembly script.</li>
 * </ul>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "ViewsheetScopeTest.vso")
@Tag("core")
@Tag("integration")
class LexicalDeclarationScriptTest {
   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent());

   private ViewsheetSandbox sandbox;

   @BeforeEach
   void setUp() {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      sandbox = rvs.getViewsheetSandbox().orElseThrow();
   }

   /**
    * A worksheet expression column whose script declares a constant and then uses it in
    * a following if statement (two pieces after the #75688 split).
    */
   @Test
   void worksheetExpressionColumnSeesTopLevelConst() throws Exception {
      TableLens lens = queryWithExpression("c76980",
         "const x = 2; if(true) { field['int'] * x }");
      int base = findColumn(lens, "int");
      int expr = findColumn(lens, "c76980");
      assertTrue(lens.moreRows(1), "embedded table has no data rows");

      for(int r = 1; lens.moreRows(r); r++) {
         Number value = (Number) lens.getObject(r, base);
         Object result = lens.getObject(r, expr);
         assertNotNull(result, "expression column is null at row " + r +
            " (the const was not visible to the if statement)");
         assertEquals(value.doubleValue() * 2, ((Number) result).doubleValue(), 0.0001);
      }
   }

   /**
    * The same with let, and with the constant used inside try/catch, which silently
    * returned the catch value before the fix.
    */
   @Test
   void worksheetExpressionColumnConstInTryCatch() throws Exception {
      TableLens lens = queryWithExpression("c76980b",
         "let m = 3; const k = {a: 1}; try { field['int'] * m + k.a } catch(e) { -1 }");
      int base = findColumn(lens, "int");
      int expr = findColumn(lens, "c76980b");
      assertTrue(lens.moreRows(1), "embedded table has no data rows");

      for(int r = 1; lens.moreRows(r); r++) {
         Number value = (Number) lens.getObject(r, base);
         assertEquals(value.doubleValue() * 3 + 1,
            ((Number) lens.getObject(r, expr)).doubleValue(), 0.0001);
      }
   }

   /**
    * A viewsheet onInit constant is read by a later assembly script, as it was on Rhino.
    */
   @Test
   void onInitConstVisibleToAssemblyScript() throws Exception {
      Viewsheet vs = sandbox.getViewsheet();
      String assembly = vs.getAssemblies()[0].getAbsoluteName();
      vs.getViewsheetInfo().setScriptEnabled(true);
      vs.getViewsheetInfo().setOnInit("const PC76980 = 5; let PL76980 = 7;");
      sandbox.processOnInit();

      ViewsheetScope scope = sandbox.getScope();
      assertEquals(6.0, toDouble(scope.execute("PC76980 + 1", assembly)));
      assertEquals(70.0, toDouble(scope.execute(
         "const k = 2; if(true) { PC76980 * k * PL76980 }", assembly)));
   }

   /**
    * A top-level const in a library script is visible to other scripts, as it was on
    * Rhino (library sources are installed when the viewsheet's script engine starts).
    */
   @Test
   void libraryConstVisibleToAssemblyScript() throws Exception {
      LibManager mgr = LibManagerProvider.getInstance().getManager();
      mgr.setScript("lib76980", "const LIB76980 = 9;\nfunction lib76980() { return LIB76980 * 2; }");

      try {
         String assembly = sandbox.getViewsheet().getAssemblies()[0].getAbsoluteName();
         ViewsheetScope scope = sandbox.getScope();
         // the engine installs library sources when it starts; restart it so it picks up
         // the library added above, as a new session would
         scope.getScriptEnv().reset();
         assertEquals(18.0, toDouble(scope.execute("lib76980()", assembly)));
         assertEquals(27.0, toDouble(scope.execute("LIB76980 + lib76980()", assembly)));
      }
      finally {
         mgr.removeScript("lib76980");
      }
   }

   private TableLens queryWithExpression(String name, String script) throws Exception {
      AssetQuerySandbox wbox = sandbox.getAssetQuerySandbox();
      TableAssembly table = (TableAssembly) wbox.getWorksheet().getAssembly("Query1");
      assertNotNull(table, "test worksheet has no Query1");

      ExpressionRef exp = new ExpressionRef(null, name);
      exp.setName(name);
      exp.setExpression(script);
      ColumnRef column = new ColumnRef(exp);
      column.setDataType(XSchema.DOUBLE);
      ColumnSelection columns = table.getColumnSelection();
      columns.addAttribute(column);
      table.setColumnSelection(columns);
      wbox.resetTableLens("Query1");

      TableLens lens = wbox.getTableLens("Query1", AssetQuerySandbox.RUNTIME_MODE);
      assertNotNull(lens, "no table lens for Query1");
      return lens;
   }

   private static int findColumn(TableLens lens, String header) {
      lens.moreRows(0);

      for(int c = 0; c < lens.getColCount(); c++) {
         if(header.equals(String.valueOf(lens.getObject(0, c)))) {
            return c;
         }
      }

      StringBuilder headers = new StringBuilder();

      for(int c = 0; c < lens.getColCount(); c++) {
         headers.append(lens.getObject(0, c)).append(',');
      }

      fail("column " + header + " not found in " + headers);
      return -1;
   }

   private static double toDouble(Object value) {
      assertNotNull(value, "script returned null");
      return ((Number) value).doubleValue();
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId(ViewsheetScopeTest.ASSET_ID);
      event.setViewer(true);
      return event;
   }
}
