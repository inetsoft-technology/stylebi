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
package inetsoft.report.io.viewsheet;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.TableDataVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #78260: a table whose data failed to load (a condition expression that throws leaves the
 * table without a lens) made every table-expanding export (CSV always, other formats with
 * "Expand Components") fail with a NullPointerException, surfaced as an opaque 500.
 */
@Tag("core")
class AbstractVSExporterNullLensTest {
   @Test
   void expandingATableWithNoLensLeavesItAloneInsteadOfThrowing() throws Exception {
      AbstractVSExporter exporter = mock(AbstractVSExporter.class, CALLS_REAL_METHODS);
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getVSTableLens(anyString(), anyBoolean(), anyFloat())).thenReturn(null);
      TableVSAssembly table = mock(TableVSAssembly.class);
      when(table.getAbsoluteName()).thenReturn("Table1");

      Method expand = AbstractVSExporter.class.getDeclaredMethod(
         "expandTable", TableDataVSAssembly.class,
         ViewsheetSandbox.class, boolean.class);
      expand.setAccessible(true);

      assertDoesNotThrow(() -> {
         try {
            expand.invoke(exporter, table, box, true);
         }
         catch(InvocationTargetException e) {
            throw e.getCause();
         }
      });
   }
}
