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

import inetsoft.report.TableDataPath;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.MessageFormat;

import java.text.Format;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #80208: shared fixture for the export tests of text-formatted and unformatted numeric
 * cells (core and xml-formats).
 */
public final class ExportCellFormatFixture {
   private ExportCellFormatFixture() {
   }

   public static final int MSG0 = 0;
   public static final int MSG1 = 1;
   public static final int MSG_STR = 2;
   public static final int D1225 = 3;
   public static final int D_1234 = 4;
   public static final int D_SMALL = 5;
   public static final int D_12DIG = 6;
   public static final int D_12DIG_DEC = 7;
   public static final int D_NEG = 8;
   public static final int D_TINY = 9;
   public static final int D_INT = 10;
   public static final int COLS = 11;

   /** Two columns of text-formatted numbers, a text-formatted string and plain numbers. */
   public static class Base extends DefaultTableLens {
      public Base() {
         super(2, COLS);
         setHeaderRowCount(1);

         for(int c = 0; c < COLS; c++) {
            setObject(0, c, "h" + c);
         }

         setObject(1, MSG0, 12.0);
         setObject(1, MSG1, 12.0);
         setObject(1, MSG_STR, "A1");
         setObject(1, D1225, 12.25);
         setObject(1, D_1234, 1234.5678);
         setObject(1, D_SMALL, 1.2345E-5);
         setObject(1, D_12DIG, 123456789012.0);
         setObject(1, D_12DIG_DEC, 123456789012.5);
         setObject(1, D_NEG, -1234567.125);
         setObject(1, D_TINY, 1.0E-35);
         setObject(1, D_INT, 12.0);
      }

      @Override
      public Class<?> getColType(int c) {
         return c == MSG_STR ? String.class : Double.class;
      }

      @Override
      public Format getDefaultFormat(TableDataPath path) {
         if(path == null || path.getType() != TableDataPath.DETAIL || path.getPath().length == 0) {
            return null;
         }

         int col = Integer.parseInt(path.getPath()[path.getPath().length - 1].substring(1));

         switch(col) {
         case MSG0:
         case MSG1:
            return new MessageFormat("{0} pcs");
         case MSG_STR:
            return new MessageFormat("ID-{0}");
         default:
            return null;
         }
      }
   }

   public static ViewsheetSandbox box(String name) {
      Viewsheet vs = mock(Viewsheet.class);
      TableVSAssembly assembly = mock(TableVSAssembly.class);
      when(assembly.getName()).thenReturn(name);
      when(assembly.getFormatInfo()).thenReturn(new FormatInfo());
      when(assembly.getColumnSelection()).thenReturn(new ColumnSelection());
      when(vs.getAssembly(name)).thenReturn(assembly);
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getViewsheet()).thenReturn(vs);
      when(box.getScope()).thenReturn(mock(ViewsheetScope.class));
      return box;
   }
}
