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

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.GaugeVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.script.ScriptException;
import inetsoft.util.stall.LockStallException;
import inetsoft.util.swap.LostSwapFile;
import inetsoft.util.swap.SwapFileReadException;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * #77123: the value of an output assembly (gauge, text, ...) read by a script is the
 * output query's row read. A lock stall in FAIL stall mode under that read must reach the
 * script, not read as a null value that script arithmetic turns into a wrong number
 * ({@code Gauge1.value * 2} gave 0).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class OutputVSAScriptableStallTest {
   private ViewsheetSandbox box;
   private OutputVSAScriptable gauge;
   private Viewsheet vs;

   @BeforeEach
   void setUp() {
      vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("vs1");
      GaugeVSAssembly assembly = new GaugeVSAssembly();
      assembly.getVSAssemblyInfo().setName("Gauge1");
      vs.addAssembly(assembly);

      box = mock(ViewsheetSandbox.class);
      when(box.getID()).thenReturn("vs1");
      when(box.getViewsheet()).thenReturn(vs);

      gauge = new GaugeVSAScriptable(box);
      gauge.setAssembly("Gauge1");
   }

   @Test
   void stalledValueReadThrowsTheStall() throws Exception {
      LockStallException stall = new LockStallException("test.site", "worker", 1234, null);
      when(box.getData("Gauge1")).thenThrow(stall);

      assertSame(stall, assertThrows(LockStallException.class, () -> gauge.getMember("value")));
   }

   @Test
   void stalledValueReadWrappedInScriptExceptionThrowsTheStall() throws Exception {
      LockStallException stall = new LockStallException("test.site", "worker", 1234, null);
      when(box.getData("Gauge1")).thenThrow(new ScriptException("output failed", stall));

      assertSame(stall, assertThrows(LockStallException.class, () -> gauge.getMember("value")));
   }

   @Test
   void stalledExecuteViewThrowsTheStall() throws Exception {
      LockStallException stall = new LockStallException("test.site", "worker", 1234, null);
      when(box.getData("Gauge1")).thenReturn(null);
      doThrow(new RuntimeException("view failed", stall)).when(box).executeView("Gauge1", false);

      assertSame(stall, assertThrows(LockStallException.class, () -> gauge.getMember("value")));
   }

   /**
    * #78000: the gauge used as a scalar reads its value the same way as .value, so a stall
    * under that read reaches the script too.
    */
   @Test
   void stalledScalarCoercionThrowsTheStall() throws Exception {
      LockStallException stall = new LockStallException("test.site", "worker", 1234, null);
      when(box.getData("Gauge1")).thenThrow(stall);

      for(String member : new String[] { "valueOf", "toString" }) {
         ProxyExecutable coerce = (ProxyExecutable) gauge.getMember(member);
         assertSame(stall, assertThrows(LockStallException.class, coerce::execute));
      }
   }

   /**
    * A failure that is not a stall still reads as a null value, as before.
    */
   @Test
   void failedValueReadStillReturnsNull() throws Exception {
      when(box.getData("Gauge1"))
         .thenThrow(new RuntimeException("boom"))
         .thenThrow(new ScriptException("script failed"));
      assertNull(gauge.getMember("value"));
      assertNull(gauge.getMember("value"));
   }

   @Test
   void valueReadReturnsTheData() throws Exception {
      when(box.getData("Gauge1")).thenReturn(5);
      assertEquals(5, gauge.getMember("value"));
   }

   /**
    * #77910: a lost swap file under the output query, as such or wrapped in the script
    * error of the query, must reach the script instead of reading as a null value.
    */
   @Test
   void lostSwapFileValueReadThrowsTheSwapFailure() throws Exception {
      try(LostSwapFile lost = new LostSwapFile()) {
         when(box.getData("Gauge1"))
            .thenAnswer(inv -> {
               lost.read();
               return null;
            })
            .thenAnswer(inv -> {
               try {
                  lost.read();
               }
               catch(SwapFileReadException ex) {
                  throw new ScriptException("output failed", ex);
               }

               return null;
            });

         assertEquals(lost.getFile(), assertThrows(SwapFileReadException.class,
            () -> gauge.getMember("value")).getFile());
         assertEquals(lost.getFile(), assertThrows(SwapFileReadException.class,
            () -> gauge.getMember("value")).getFile());
      }
   }
}
