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

import inetsoft.report.filter.Formula;
import inetsoft.report.TableLens;
import inetsoft.report.filter.CalcFieldFormula;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.script.formula.AssetQueryScope;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77123 (H3a D-1): the VS crosstab aggregate calc field
 * ({@code AbstractCrosstabVSAQuery.getFormula}) ran on the sandbox's shared scope, whose mode
 * is never written in pool mode. Its script must read worksheet tables in the same mode with
 * the pool on as with it off, and {@code worksheet} must resolve to the same state.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CrosstabCalcFieldQueryViewTest {
   @Test
   void poolModeViewerCrosstabCalcFieldReadsTablesInTheQueryMode() throws Exception {
      assertEquals(List.of(AssetQuerySandbox.RUNTIME_MODE),
                   calcField(true, AbstractSheet.SHEET_RUNTIME_MODE, "worksheet['T1'].length"));
   }

   @Test
   void poolOffViewerCrosstabCalcFieldIsUnchanged() throws Exception {
      assertEquals(List.of(AssetQuerySandbox.RUNTIME_MODE),
                   calcField(false, AbstractSheet.SHEET_RUNTIME_MODE, "worksheet['T1'].length"));
   }

   @Test
   void poolModeComposerCrosstabCalcFieldReadsTablesInTheQueryMode() throws Exception {
      assertEquals(List.of(AssetQuerySandbox.LIVE_MODE),
                   calcField(true, AbstractSheet.SHEET_DESIGN_MODE, "T1.length"));
   }

   /**
    * The calc-field view's mode is the one VSAQuery.getTableLens gives the crosstab's
    * worksheet query, in every branch.
    */
   @Test
   void queryModeMatchesTheWorksheetQueryMode() throws Exception {
      assertEquals(AssetQuerySandbox.RUNTIME_MODE,
                   queryMode(false, false, null, AbstractSheet.SHEET_RUNTIME_MODE));
      assertEquals(AssetQuerySandbox.LIVE_MODE,
                   queryMode(false, false, null, AbstractSheet.SHEET_DESIGN_MODE));
      assertEquals(AssetQuerySandbox.DESIGN_MODE,
                   queryMode(false, true, null, AbstractSheet.SHEET_DESIGN_MODE));
      assertEquals(AssetQuerySandbox.DESIGN_MODE,
                   queryMode(true, false, null, AbstractSheet.SHEET_RUNTIME_MODE));
      assertEquals(AssetQuerySandbox.DESIGN_MODE, queryMode(
         true, false, new SourceInfo(SourceInfo.ASSET, null, "T1"),
         AbstractSheet.SHEET_DESIGN_MODE));
      // bound to a vs assembly, the meta uses live data
      assertEquals(AssetQuerySandbox.LIVE_MODE, queryMode(
         true, false, new SourceInfo(SourceInfo.VS_ASSEMBLY, null, "Crosstab1"),
         AbstractSheet.SHEET_DESIGN_MODE));
   }

   private static int queryMode(boolean metadata, boolean vsMetadata, SourceInfo source,
                                int vsMode) throws Exception
   {
      ViewsheetSandbox vbox = mock(ViewsheetSandbox.class);
      doReturn(vsMode).when(vbox).getMode();
      Viewsheet vs = mock(Viewsheet.class);
      ViewsheetInfo info = new ViewsheetInfo();
      info.setMetadata(vsMetadata);
      doReturn(info).when(vs).getViewsheetInfo();
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      doReturn(source).when(assembly).getSourceInfo();

      CrosstabVSAQuery query = mock(CrosstabVSAQuery.class, Mockito.CALLS_REAL_METHODS);
      Field boxField = VSAQuery.class.getDeclaredField("box");
      boxField.setAccessible(true);
      boxField.set(query, vbox);
      doReturn(vs).when(query).getViewsheet();
      doReturn(assembly).when(query).getAssembly();
      doReturn(metadata).when(query).isMetadata();

      Method getQueryMode = AbstractCrosstabVSAQuery.class.getDeclaredMethod("getQueryMode");
      getQueryMode.setAccessible(true);
      return (int) getQueryMode.invoke(query);
   }

   /**
    * Run a formula step of the crosstab's worksheet query in the mode VSAQuery gives it for
    * the viewsheet mode, then evaluate a crosstab calc field built by getFormula, and return
    * the modes T1 was read in.
    */
   private static List<Integer> calcField(boolean pool, int vsMode, String expression)
      throws Exception
   {
      // the worksheet query mode VSAQuery.getTableLens picks for this viewsheet mode
      int queryMode = vsMode == AbstractSheet.SHEET_RUNTIME_MODE ?
         AssetQuerySandbox.RUNTIME_MODE : AssetQuerySandbox.LIVE_MODE;
      List<Integer> modes = new CopyOnWriteArrayList<>();
      AssetQuerySandbox wbox = PoolTestSupport.poolBox(pool);
      Worksheet ws = new Worksheet();
      ws.addAssembly(new EmbeddedTableAssembly(ws, "T1"));
      doReturn(ws).when(wbox).getWorksheet();
      VariableTable boxVars = new VariableTable();
      doReturn(boxVars).when(wbox).getVariableTable();
      DefaultTableLens t1 = new DefaultTableLens(new Object[][] {{"a"}, {1}, {2}, {3}});
      doAnswer(inv -> {
         modes.add(inv.getArgument(1));
         return t1;
      }).when(wbox).getTableLens(eq("T1"), anyInt(), any());

      // AssetQuery's expression-column step (pool off: writes the mode on the shared scope)
      if(!wbox.isScriptPoolMode()) {
         AssetQueryScope scope = wbox.getScope();
         scope.setVariableTable(boxVars);
         scope.setMode(queryMode);
      }

      ViewsheetSandbox vbox = mock(ViewsheetSandbox.class);
      doReturn(wbox).when(vbox).getAssetQuerySandbox();
      doReturn(vsMode).when(vbox).getMode();
      Viewsheet vs = mock(Viewsheet.class);
      doReturn(new ViewsheetInfo()).when(vs).getViewsheetInfo();
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      doReturn(new AssemblyRef[0]).when(assembly).getDependedWSAssemblies();

      CrosstabVSAQuery query = mock(CrosstabVSAQuery.class, Mockito.CALLS_REAL_METHODS);
      Field boxField = VSAQuery.class.getDeclaredField("box");
      boxField.setAccessible(true);
      boxField.set(query, vbox);
      doReturn(vs).when(query).getViewsheet();
      doReturn(assembly).when(query).getAssembly();
      doReturn(false).when(query).isMetadata();
      doReturn("T1").when(query).getSourceTable();

      CalculateRef cref = new CalculateRef(false);
      ExpressionRef eref = new ExpressionRef(null, "calc");
      eref.setExpression(expression);
      cref.setDataRef(eref);
      IAggregateRef aggregate = mock(IAggregateRef.class);
      doReturn(AggregateFormula.SUM).when(aggregate).getFormula();
      doReturn(cref).when(aggregate).getDataRef();

      Method getFormula = AbstractCrosstabVSAQuery.class.getDeclaredMethod(
         "getFormula", IAggregateRef.class, TableLens.class);
      getFormula.setAccessible(true);
      Formula form;

      // the expression names no aggregate
      try(MockedStatic<VSUtil> util = mockStatic(VSUtil.class, Mockito.CALLS_REAL_METHODS)) {
         util.when(() -> VSUtil.findAggregate(any(Viewsheet.class), any(), anyList(), any()))
            .thenReturn(new java.util.ArrayList<>());
         form = (Formula) getFormula.invoke(query, aggregate, t1);
      }

      assertInstanceOf(CalcFieldFormula.class, form);
      form.addValue(new Object[0]);
      assertEquals(3.0 + 1, ((Number) form.getResult()).doubleValue());
      assertFalse(modes.isEmpty(), "the calc field did not read T1");
      return modes;
   }
}
