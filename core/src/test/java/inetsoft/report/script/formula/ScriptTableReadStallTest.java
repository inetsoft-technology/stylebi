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
package inetsoft.report.script.formula;

import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.script.viewsheet.GaugeVSAScriptable;
import inetsoft.test.*;
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.viewsheet.GaugeVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import inetsoft.util.stall.LockStallException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static inetsoft.util.stall.StallTestSupport.assertStallOf;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * #77123, end to end through the real GraalJS engine: a script that reads a worksheet table
 * or an output value while the read hits a lock stall (FAIL stall mode) must fail with the
 * stall, not compute a wrong number from a table read as empty or a value read as null.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ScriptTableReadStallTest {
   private static final String SUM =
      "var t = 0; for(var i = 1; i < Query1.length; i++) { t += Query1[i][1]; } t";

   private GraalJavaScriptEnv senv;

   @BeforeEach
   void setUp() {
      senv = new GraalJavaScriptEnv();
      senv.init();
   }

   @Test
   void worksheetTableSumThrowsTheStall() throws Exception {
      LockStallException stall = new LockStallException("test.site", "worker", 1234, null);
      AssetQueryScope scope = worksheetScope(stall);

      Throwable thrown = assertThrows(Throwable.class,
         () -> senv.exec(senv.compile(SUM), scope, null, null));
      assertStallOf(stall, LockStallException.find(thrown));
   }

   @Test
   void worksheetTableSumReadsTheTable() throws Exception {
      AssetQueryScope scope = worksheetScope(null);
      assertEquals(6, ((Number) senv.exec(senv.compile(SUM), scope, null, null)).intValue());
   }

   @Test
   void outputValueArithmeticThrowsTheStall() throws Exception {
      LockStallException stall = new LockStallException("test.site", "worker", 1234, null);
      ViewsheetSandbox box = gaugeBox();
      when(box.getData("Gauge1")).thenThrow(stall);
      senv.put("Gauge1", gauge(box));

      Throwable thrown = assertThrows(Throwable.class,
         () -> senv.exec(senv.compile("Gauge1.value * 2"), null, null, null));
      assertStallOf(stall, LockStallException.find(thrown));
   }

   @Test
   void outputValueArithmeticReadsTheValue() throws Exception {
      ViewsheetSandbox box = gaugeBox();
      when(box.getData("Gauge1")).thenReturn(5);
      senv.put("Gauge1", gauge(box));

      Object result = senv.exec(senv.compile("Gauge1.value * 2"), null, null, null);
      assertEquals(10, ((Number) result).intValue());
   }

   private static AssetQueryScope worksheetScope(LockStallException stall) throws Exception {
      Worksheet ws = mock(Worksheet.class);
      when(ws.getAssembly("Query1")).thenReturn(mock(EmbeddedTableAssembly.class));
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getWorksheet()).thenReturn(ws);

      if(stall != null) {
         when(box.getTableLens(eq("Query1"), anyInt(), any())).thenThrow(stall);
      }
      else {
         when(box.getTableLens(eq("Query1"), anyInt(), any())).thenReturn(
            new DefaultTableLens(new Object[][] { { "a", "b" }, { "x", 2 }, { "y", 4 } }));
      }

      return new AssetQueryScope(box);
   }

   private static ViewsheetSandbox gaugeBox() {
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("vs1");
      GaugeVSAssembly assembly = new GaugeVSAssembly();
      assembly.getVSAssemblyInfo().setName("Gauge1");
      vs.addAssembly(assembly);

      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getID()).thenReturn("vs1");
      when(box.getViewsheet()).thenReturn(vs);
      return box;
   }

   private static GaugeVSAScriptable gauge(ViewsheetSandbox box) {
      GaugeVSAScriptable gauge = new GaugeVSAScriptable(box);
      gauge.setAssembly("Gauge1");
      return gauge;
   }
}
