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

import inetsoft.report.*;
import inetsoft.report.composition.WorksheetService;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.script.ScriptException;
import inetsoft.util.script.graal.ScriptStopTestSupport;
import inetsoft.util.script.graal.ScriptTimeoutGuard;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80328: the sandbox evaluated the cell format expressions of a table, crosstab or
 * freehand table with no cell context and no fallback, so a valid {@code $name} reference
 * or a bad expression raised an error that failed any format edit (and aborted the other
 * values of the assembly). Only the cell formats are tolerant; everything else must still
 * surface.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PluginsTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetSandboxCellFormatExprTest {
   private static final TableDataPath DETAIL = new TableDataPath(-1, TableDataPath.DETAIL);
   private static final TableDataPath HEADER = new TableDataPath(-1, TableDataPath.HEADER);

   @AfterEach
   void tearDown() {
      WorksheetService.ASSET_EXCEPTIONS.remove();
      if(box != null) {
         box.dispose();
      }
   }

   @Test
   void freehandNamedCellReferenceRaisesNoError() throws Exception {
      CalcTableVSAssembly calc = new CalcTableVSAssembly(vs, "Calc1");
      TableLayout layout = calc.getTableLayout();
      layout.setColCount(1);
      layout.setCellBinding(0, 0, new TableCellBinding(CellBinding.BIND_FORMULA, "1"));
      VSCompositeFormat fmt = new VSCompositeFormat();
      fmt.getUserDefinedFormat().setForegroundValue("=$total > 5 ? 0xFF0000 : 0x00FF00");
      TableDataPath path = ((CalcTableVSAssemblyInfo) calc.getVSAssemblyInfo()).getCellDataPath(0, 0);
      calc.getFormatInfo().setFormat(path, fmt);
      vs.addAssembly(calc);
      box = newBox();
      WorksheetService.ASSET_EXCEPTIONS.set(new ArrayList<>());

      box.executeView("Calc1", true);

      assertTrue(errors().isEmpty(), "a cell format failure must not be an assembly error: " + errors());
   }

   @Test
   void failingCellFormatRaisesNoError() throws Exception {
      table("=if(");

      box.executeView("Tbl", true);

      assertTrue(errors().isEmpty(), "errors: " + errors());
   }

   @Test
   void failingCellFormatDoesNotSkipTheOtherValues() throws Exception {
      TableVSAssembly table = table("=if(");
      VSCompositeFormat header = new VSCompositeFormat();
      header.getUserDefinedFormat().setBackgroundValue("=0xFF0000");
      table.getFormatInfo().setFormat(HEADER, header);
      table.getVSAssemblyInfo().setVisibleValue("=true");

      box.executeView("Tbl", true);

      for(String dvalue : new String[] { "=0xFF0000", "=true" }) {
         DynamicValue value = table.getViewDynamicValues(true).stream()
            .filter(v -> dvalue.equals(v.getDValue())).findFirst().orElseThrow();
         assertNotNull(value.getRValue(), dvalue + " must still be evaluated");
      }
   }

   @Test
   void failingEnabledStillRaises() throws Exception {
      TableVSAssembly table = table("=0xFF0000");
      table.getVSAssemblyInfo().setEnabledValue("=if(");

      box.executeView("Tbl", true);

      assertEquals(1, errors().size());
      assertInstanceOf(ScriptException.class, errors().get(0));
   }

   @Test
   void failingObjectFormatStillRaises() throws Exception {
      TextVSAssembly text = new TextVSAssembly(vs, "Text1");
      vs.addAssembly(text);
      text.getVSAssemblyInfo().getFormat().getUserDefinedFormat().setForegroundValue("=if(");
      box = newBox();
      WorksheetService.ASSET_EXCEPTIONS.set(new ArrayList<>());

      box.executeView("Text1", true);

      assertEquals(1, errors().size());
      assertInstanceOf(ScriptException.class, errors().get(0));
   }

   @Test
   void failingTableObjectFormatStillRaises() throws Exception {
      TableVSAssembly table = table("=0xFF0000");
      table.getVSAssemblyInfo().getFormat().getUserDefinedFormat().setForegroundValue("=if(");

      box.executeView("Tbl", true);

      assertEquals(1, errors().size());
      assertInstanceOf(ScriptException.class, errors().get(0));
   }

   @Test
   void stoppedCellFormatScriptStillPropagates() throws Exception {
      String previous = ScriptStopTestSupport.setTimeout("1");

      try {
         TableVSAssembly table = table("=while(true) {}; 0xFF0000");

         Throwable ex = assertThrows(Throwable.class,
                                     () -> box.executeViewDynamicValues("Tbl", table));
         assertTrue(ScriptTimeoutGuard.isStop(ex), ex.toString());
      }
      finally {
         Thread.interrupted();
         ScriptStopTestSupport.setTimeout(previous);
      }
   }

   private List<Exception> errors() {
      List<Exception> list = WorksheetService.ASSET_EXCEPTIONS.get();
      return list == null ? new ArrayList<>() : list;
   }

   private TableVSAssembly table(String detailForeground) {
      TableVSAssembly table = new TableVSAssembly(vs, "Tbl");
      vs.addAssembly(table);
      VSCompositeFormat detail = new VSCompositeFormat();
      detail.getUserDefinedFormat().setForegroundValue(detailForeground);
      table.getFormatInfo().setFormat(DETAIL, detail);
      box = newBox();
      WorksheetService.ASSET_EXCEPTIONS.set(new ArrayList<>());
      return table;
   }

   private ViewsheetSandbox newBox() {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        "Fmt80328", null);
      return new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);
   }

   private final Viewsheet vs = new Viewsheet();
   private ViewsheetSandbox box;
}
