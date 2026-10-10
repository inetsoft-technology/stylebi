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

import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.test.*;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.LockRestoreException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77227: AbstractVSExporter gets each chart's graph from a fresh export pair
 * (getVGraphPair(name, true, null, true, 1)) and skips a chart without a graph. A chart whose
 * graph fails to initialize must fail the export instead of being skipped silently; a chart
 * without data keeps being skipped without an error.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ExportGraphPairInitFailureTest {
   ViewsheetSandbox box;

   @BeforeEach
   void setUp() {
      box = spy(new ViewsheetSandbox(new Viewsheet(), AbstractSheet.SHEET_RUNTIME_MODE, null,
                                     false, null));
   }

   @Test
   void exportPairInitFailurePropagates() throws Exception {
      // Bug #78033: VGraphPair.initGraph0() now fetches data through the 4-arg
      // getData(name, initial, type, cutFlag) overload (added to report a possibly-cut
      // fetch back to the caller) instead of calling the 1-arg getData(name) overload
      // directly, so the stub must target the overload actually invoked -- a spy only
      // intercepts the exact overload it is told to stub, and falls through to the real
      // (4-arg) implementation for anything else. any() is used for the cutFlag array
      // since initGraph0 passes a fresh boolean[1] each call.
      doThrow(new IllegalStateException("query failed"))
         .when(box).getData(eq("Chart1"), eq(true), eq(DataMap.NORMAL), any());
      IllegalStateException ex = assertThrows(IllegalStateException.class,
                                              () -> box.getVGraphPair("Chart1", true, null, true, 1));
      assertEquals("query failed", ex.getMessage());

      doThrow(new LockRestoreException("lost"))
         .when(box).getData(eq("Chart1"), eq(true), eq(DataMap.NORMAL), any());
      assertThrows(LockRestoreException.class,
                   () -> box.getVGraphPair("Chart1", true, null, true, 1));
   }

   @Test
   void exportPairWithoutDataHasNoGraphAndNoError() throws Exception {
      doReturn(null).when(box).getData(eq("Chart1"), eq(true), eq(DataMap.NORMAL), any());
      VGraphPair pair = assertDoesNotThrow(() -> box.getVGraphPair("Chart1", true, null, true, 1));

      assertNotNull(pair);
      assertTrue(pair.isCompleted());
      assertNull(pair.getData());
      assertFalse(pair.isPlotted());
   }
}
