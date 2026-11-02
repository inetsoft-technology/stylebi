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
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.lang.reflect.Field;
import java.security.Principal;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78494: export and alert sandboxes of a scheduled run carry {@code __is_scheduler__} as
 * {@code Boolean.TRUE}, the open as the String {@code "true"}. A selection processing failure
 * in {@link ViewsheetSandbox#reset} must fail the scheduled run in both forms, and is still only
 * logged in an interactive run.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class ViewsheetSandboxSchedulerFlagTest {
   @BeforeEach
   void setUp() {
      savedPrincipal = ThreadContext.getContextPrincipal();
      selectionUpdates.set(0);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(savedPrincipal);
   }

   @Test
   void exportTimeBooleanFlagFailsOnSelectionFailure() throws Exception {
      assertResetFails(Boolean.TRUE);
   }

   @Test
   void openTimeStringFlagFailsOnSelectionFailure() throws Exception {
      assertResetFails("true");
   }

   @Test
   void interactiveRunOnlyLogsSelectionFailure() throws Exception {
      ViewsheetSandbox box = newSandbox(null);

      assertDoesNotThrow(() -> resetAll(box));
      assertTrue(selectionUpdates.get() > 0, "the selection failure was reached");
   }

   private void assertResetFails(Object flag) throws Exception {
      ViewsheetSandbox box = newSandbox(flag);

      RuntimeException ex = assertThrows(RuntimeException.class, () -> resetAll(box),
         "flag " + flag + " (" + flag.getClass().getSimpleName() + ")");
      assertEquals("Failure while processing viewsheet", ex.getMessage());
      assertInstanceOf(IOException.class, ex.getCause());
      assertTrue(selectionUpdates.get() > 0);
   }

   private static void resetAll(ViewsheetSandbox box) throws Exception {
      Viewsheet vs = box.getViewsheet();
      box.reset(null, vs.getAssemblies(), new ChangedAssemblyList(), true, false, null);
   }

   // a selection list bound to an embedded table, whose update fails in processSelections
   private static ViewsheetSandbox newSandbox(Object flag) throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new EmbeddedTableAssembly(ws, "E1"));
      Viewsheet vs = new Viewsheet();
      Field wsField = Viewsheet.class.getDeclaredField("ws");
      wsField.setAccessible(true);
      wsField.set(vs, ws);
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        "test/vs78494", null);
      vs.setEntry(entry);
      SelectionListVSAssembly selection = new SelectionListVSAssembly(vs, "SL1");
      selection.setTableName("E1");
      vs.addAssembly(selection);

      ViewsheetSandbox box =
         new ViewsheetSandbox(vs, Viewsheet.SHEET_RUNTIME_MODE, null, false, entry)
      {
         @Override
         void updateAssembly(VSAssembly assembly, boolean out) throws Exception {
            if(assembly instanceof SelectionListVSAssembly) {
               selectionUpdates.incrementAndGet();
               throw new IOException("selection failure");
            }

            super.updateAssembly(assembly, out);
         }
      };

      if(flag != null) {
         box.getVariableTable().put("__is_scheduler__", flag);
      }

      return box;
   }

   private static final AtomicInteger selectionUpdates = new AtomicInteger();
   private Principal savedPrincipal;
}
