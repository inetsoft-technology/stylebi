/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.report.style;

import inetsoft.report.TableLens;
import inetsoft.report.filter.GroupedTable;
import inetsoft.report.filter.style.*;
import inetsoft.report.internal.table.DefaultGroupedTable;
import inetsoft.test.TestSerializeUtils;
import inetsoft.test.XTableUtil;
import inetsoft.uql.XTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.params.provider.Arguments.arguments;

public class TableStyleTest {
   @ParameterizedTest(name = "testSerialize {0}")
   @MethodSource("tableStyleProvider")
   public void testSerialize(@SuppressWarnings("unused") String name, TableStyle originalTable, Class cls) throws Exception
   {
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(cls, deserializedTable.getClass());
   }

   static Stream<Arguments> tableStyleProvider() throws Exception {
      Class[] tableStyleClasses = new Class[]{
         TableStyle.class,
         Accounting1.class,
         Accounting2.class,
         Accounting3.class,
         Accounting4.class,
         ButtonHeaderGrid.class,
         Classic1.class,
         Classic2.class,
         Classic3.class,
         Classic4.class,
         Colorful1.class,
         Colorful2.class,
         Colorful3.class,
         Colorful4.class,
         ColumnFillColor.class,
         ColumnFillHeader.class,
         ColumnFillTotals.class,
         Columns1.class,
         Columns2.class,
         Columns3.class,
         Columns4.class,
         Columns5.class,
         Columns6.class,
         Contemporary1.class,
         Contemporary2.class,
         CrosstabStyle.class,
         DoubleBorderHeader.class,
         DoubleBorderMixed.class,
         DoubleLineGrid.class,
         DoubleLowered3DGrid.class,
         DoubleRaised3DGrid.class,
         DropTable.class,
         Effect3D1.class,
         Effect3D2.class,
         Effect3D3.class,
         Elegant.class,
         Executive.class,
         FancyFills.class,
         FancyHeader.class,
         FancyJustify.class,
         FancyLabels.class,
         FancyOpen.class,
         FancyShading.class,
         FancyTotals.class,
         GreenStrip.class,
         Grid1.class,
         Grid2.class,
         Grid3.class,
         Grid4.class,
         Grid5.class,
         Grid6.class,
         Grid7.class,
         Grid8.class,
         GroupStyle.class,
         HeaderFillColumn.class,
         HeaderFillSingle.class,
         LeadingBreak.class,
         LedgerHeader.class,
         LedgerOpen.class,
         List1.class,
         List2.class,
         List3.class,
         List4.class,
         List5.class,
         List6.class,
         List7.class,
         List8.class,
         Lowered3DGrid.class,
         NoLinesColumns.class,
         NoLinesHeader.class,
         NoLinesNoBorders.class,
         NoLinesSeparator.class,
         NoLinesSingle.class,
         NoLinesTotals.class,
         Professional.class,
         Raised3DGrid.class,
         RowFillColumns.class,
         RowFillHeader.class,
         RowFillSingle.class,
         Simple1.class,
         Simple2.class,
         Simple3.class,
         Simple4.class,
         SingleDoubleBorder.class,
         SingleLines.class,
         SingleNoBorder.class,
         SingleOpen.class,
         SingleUnderlined.class,
         Subtle1.class,
         Subtle2.class,
         TitleGrid.class,
         TrailingBreak.class
      };

      return Arrays.stream(tableStyleClasses).map((cls -> {
         TableStyle tableStyle = null;

         try {
            if(GroupStyle.class.isAssignableFrom(cls)) {
               Constructor<?> cstr = cls.getConstructor(GroupedTable.class);
               DefaultGroupedTable groupedTable = new DefaultGroupedTable();
               groupedTable.setData(XTableUtil.getDefaultData());
               groupedTable.setGroupColCount(1);
               tableStyle = (TableStyle) cstr.newInstance(groupedTable);
            }
            else {
               Constructor<?> cstr = cls.getConstructor(TableLens.class);
               tableStyle = (TableStyle) cstr.newInstance(XTableUtil.getDefaultTableLens());
            }

         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }

         return arguments(cls.getName(), tableStyle, cls);
      }));
   }
}
