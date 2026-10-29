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
package inetsoft.mv.local;

import inetsoft.mv.DateMVColumn;
import inetsoft.mv.MVColumn;
import inetsoft.mv.XDynamicMVColumn;
import inetsoft.mv.data.XDynamicTable;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.mv.data.MV;
import inetsoft.mv.MVCreatorUtil;
import inetsoft.mv.MVDef;
import inetsoft.mv.data.MVStorage;
import inetsoft.uql.XTable;
import org.junit.jupiter.api.Tag;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79747: the time slider and calendar read the range of a date MV from the
 * {@link DateMVColumn} min/max, which only grows while rows are converted. The re-scan of an
 * incremental update that deleted rows must clear it first, or the min of the deleted rows stays.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LocalMVIncrementalDateRangeTest {
   private static final long DAY = 86_400_000L;

   @Test
   void deletedMinIsNotKeptAfterTheRescan() {
      DateMVColumn col = column(DateRangeRef.DAY_INTERVAL);
      // the original build saw 2020 .. 2031 (day numbers stand in for the dates)
      scan(col, 10 * DAY, 400 * DAY, 4000 * DAY);
      assertEquals(new Date(10 * DAY), col.getMin());

      // the update deleted the rows before day 400, the rest is converted again
      LocalMVIncremental.resetDateRanges(List.of(col));
      scan(col, 400 * DAY, 4000 * DAY);

      assertEquals(new Date(400 * DAY), col.getMin());
      assertEquals(new Date(4000 * DAY), col.getMax());
   }

   @Test
   void deletedMaxIsNotKeptAfterTheRescan() {
      DateMVColumn col = column(DateRangeRef.DAY_INTERVAL);
      scan(col, 10 * DAY, 4000 * DAY);
      LocalMVIncremental.resetDateRanges(List.of(col));
      scan(col, 10 * DAY);

      assertEquals(new Date(10 * DAY), col.getMin());
      assertEquals(new Date(10 * DAY), col.getMax());
   }

   @Test
   void noRowsLeftClearsTheRange() {
      DateMVColumn col = column(DateRangeRef.YEAR_INTERVAL);
      scan(col, 10 * DAY, 400 * DAY);
      LocalMVIncremental.resetDateRanges(List.of(col));

      assertNull(col.getMin());
      assertNull(col.getMax());
   }

   @Test
   void everyDateLevelColumnIsReset() {
      DateMVColumn year = column(DateRangeRef.YEAR_INTERVAL);
      DateMVColumn month = column(DateRangeRef.MONTH_INTERVAL);
      DateMVColumn minute = column(DateRangeRef.MINUTE_INTERVAL);
      scan(year, 10 * DAY);
      scan(month, 10 * DAY);
      scan(minute, 10 * DAY);

      LocalMVIncremental.resetDateRanges(List.of(year, month, minute));

      for(DateMVColumn col : List.of(year, month, minute)) {
         assertNull(col.getMin());
         assertNull(col.getMax());
      }
   }

   @Test
   void otherColumnsAreLeftAlone() {
      ColumnRef ref = new ColumnRef(new AttributeRef("test", "amount"));
      ref.setDataType(XSchema.INTEGER);
      MVColumn num = new MVColumn(ref, false);
      num.setRange(1, 9);

      LocalMVIncremental.resetDateRanges(List.of(num));

      assertEquals(1, num.getOriginalMin().intValue());
      assertEquals(9, num.getOriginalMax().intValue());
   }

   /**
    * The incremental update path: updateDictionaryRanges is given the rows left after a delete
    * and must not keep the date range of the deleted rows.
    */
   @Test
   void updateDictionaryRangesDropsTheRangeOfDeletedRows() throws Exception {
      MVDef def = newDef();
      DateMVColumn day = (DateMVColumn) def.getColumns().get(1);
      // the range of the original build
      scan(day, 10 * DAY, 400 * DAY, 4000 * DAY);
      assertEquals(new Date(10 * DAY), day.getMin());

      // the delete removed the first row, two rows are left
      update(def, rows(400 * DAY, 4000 * DAY));

      assertEquals(new Date(400 * DAY), day.getMin());
      assertEquals(new Date(4000 * DAY), day.getMax());
   }

   @Test
   void updateDictionaryRangesClearsTheRangeWhenNoRowsAreLeft() throws Exception {
      MVDef def = newDef();
      DateMVColumn day = (DateMVColumn) def.getColumns().get(1);
      scan(day, 10 * DAY, 400 * DAY);

      update(def, rows());

      assertNull(day.getMin());
      assertNull(day.getMax());
   }

   private static MVDef newDef() {
      ColumnRef baseRef = new ColumnRef(new AttributeRef("test", "dateCol"));
      baseRef.setDataType(XSchema.TIME_INSTANT);
      MVColumn base = new MVColumn(baseRef, true);
      DateMVColumn day = column(DateRangeRef.DAY_INTERVAL);
      MVDef def = new MVDef();

      try {
         // the columns are otherwise loaded from the MV manager
         Class<?> type = Class.forName("inetsoft.mv.MVDef$MVContainer");
         java.lang.reflect.Constructor<?> ctor =
            type.getDeclaredConstructor(List.class, inetsoft.uql.asset.Worksheet.class);
         ctor.setAccessible(true);
         java.lang.reflect.Field field = MVDef.class.getDeclaredField("container");
         field.setAccessible(true);
         field.set(def, ctor.newInstance(new ArrayList<>(List.of(base, day)), null));
      }
      catch(ReflectiveOperationException e) {
         throw new IllegalStateException(e);
      }

      return def;
   }

   private static XTable rows(long... times) {
      Object[][] rows = new Object[times.length + 1][];
      rows[0] = new Object[] { "dateCol" };

      for(int i = 0; i < times.length; i++) {
         rows[i + 1] = new Object[] { new Date(times[i]) };
      }

      return new DefaultTableLens(rows);
   }

   private static void update(MVDef def, XTable data) throws Exception {
      MV mv = Mockito.mock(MV.class);
      Mockito.when(mv.getDef()).thenReturn(Mockito.mock(MVDef.class));
      Mockito.when(mv.getBlockSize()).thenReturn(1);
      Mockito.when(mv.getHeaders()).thenReturn(new String[] { "dateCol", "dateCol_day" });
      MVStorage storage = Mockito.mock(MVStorage.class);
      Mockito.when(storage.get(Mockito.any())).thenReturn(mv);

      try(MockedStatic<MVStorage> statics = Mockito.mockStatic(MVStorage.class)) {
         statics.when(() -> MVStorage.getFile(Mockito.any())).thenReturn("f");
         statics.when(MVStorage::getInstance).thenReturn(storage);
         LocalMVIncremental incremental = new LocalMVIncremental(def);
         incremental.updateDictionaryRanges(MVCreatorUtil.expand(def, data));
      }
   }

   private static void scan(DateMVColumn col, long... times) {
      Object[][] rows = new Object[times.length + 1][];
      rows[0] = new Object[] { "dateCol" };

      for(int i = 0; i < times.length; i++) {
         rows[i + 1] = new Object[] { new Date(times[i]) };
      }

      XDynamicTable table = new XDynamicTable(
         new DefaultTableLens(rows), new XDynamicMVColumn[] { col }, new int[] { 0 });

      for(int r = 1; table.moreRows(r); r++) {
         table.getObject(r, 1);
      }
   }

   private static DateMVColumn column(int level) {
      ColumnRef baseRef = new ColumnRef(new AttributeRef("test", "dateCol"));
      baseRef.setDataType(XSchema.TIME_INSTANT);
      ColumnRef rangeRef = new ColumnRef(
         new AttributeRef("test", DateRangeRef.getName("dateCol", level)));
      rangeRef.setDataType(XSchema.TIME_INSTANT);
      DateMVColumn col = new DateMVColumn(new MVColumn(baseRef, true), rangeRef, level);
      col.setDimension(true);
      return col;
   }
}
