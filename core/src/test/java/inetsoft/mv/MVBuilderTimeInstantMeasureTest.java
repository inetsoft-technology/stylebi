/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.mv;

import inetsoft.mv.MVDef.MVContainer;
import inetsoft.mv.data.*;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79858: a TIME_INSTANT column is always an MV measure (MVDef.createMVColumns clears the
 * dimension flag of every date/time column), so java.time/driver values such as the
 * OffsetDateTime of a TIMESTAMP WITH TIME ZONE column must be stored by the measure writer, the
 * date range and the date-part companion columns the same as a Timestamp is.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MVBuilderTimeInstantMeasureTest {
   private static final OffsetDateTime[] ODTS = {
      OffsetDateTime.parse("2020-01-01T00:00:00Z"),
      OffsetDateTime.parse("2020-01-02T10:30:00+08:00"),
      OffsetDateTime.parse("2021-03-04T23:45:00-05:00")
   };

   @Test
   void offsetDateTimeMeasureEqualsTimestampMeasure() throws Exception {
      Build control = build(timestamps(), XSchema.TIME_INSTANT);
      Build actual = build(ODTS, XSchema.TIME_INSTANT);

      // base measure plus the Year and Month companion columns
      assertEquals(3, actual.columns.size());
      assertEquals(control.columns, actual.columns);
      assertEquals(control.min, actual.min);
      assertEquals(control.max, actual.max);
      assertNotNull(actual.min);
      assertNotNull(actual.max);
      assertEquals(ODTS[0].toInstant().toEpochMilli(), ((Date) actual.min).getTime());
      assertEquals(ODTS[2].toInstant().toEpochMilli(), ((Date) actual.max).getTime());

      // the stored measure is the epoch of the instant, not the -1 of an unconverted value
      List<Double> base = actual.columns.get(0);

      for(int i = 0; i < ODTS.length; i++) {
         assertEquals(ODTS[i].toInstant().toEpochMilli(), base.get(i), 0);
      }

      // the date-part companions are not null/-1 either
      for(int c = 1; c < actual.columns.size(); c++) {
         for(Double d : actual.columns.get(c)) {
            assertNotEquals(Tool.NULL_DOUBLE, d, "date part " + c + " lost");
            assertNotEquals(-1.0, d, "date part " + c + " lost");
         }
      }
   }

   @Test
   void dateTimeOffsetDriverObjectMeasureEqualsTimestampMeasure() throws Exception {
      Object[] vals = new Object[ODTS.length];

      for(int i = 0; i < vals.length; i++) {
         vals[i] = new microsoft.sql.DateTimeOffset(ODTS[i]);
      }

      Build control = build(timestamps(), XSchema.TIME_INSTANT);
      Build actual = build(vals, XSchema.TIME_INSTANT);
      assertEquals(control.columns, actual.columns);
      assertEquals(control.min, actual.min);
      assertEquals(control.max, actual.max);
   }

   @Test
   void otherJavaTimeValuesAreConverted() throws Exception {
      Object[] zoned = new Object[ODTS.length];
      Object[] instants = new Object[ODTS.length];

      for(int i = 0; i < zoned.length; i++) {
         zoned[i] = ODTS[i].atZoneSameInstant(ZoneId.of("Asia/Tokyo"));
         instants[i] = ODTS[i].toInstant();
      }

      List<List<Double>> expected = build(timestamps(), XSchema.TIME_INSTANT).columns;
      assertEquals(expected, build(zoned, XSchema.TIME_INSTANT).columns);
      assertEquals(expected, build(instants, XSchema.TIME_INSTANT).columns);

      Object[] local = new Object[ODTS.length];
      Object[] localTs = new Object[ODTS.length];

      for(int i = 0; i < local.length; i++) {
         LocalDateTime ldt = LocalDateTime.of(2020, 1, 1 + i, 10, 30);
         local[i] = ldt;
         localTs[i] = Timestamp.valueOf(ldt);
      }

      assertEquals(build(localTs, XSchema.TIME_INSTANT).columns,
                   build(local, XSchema.TIME_INSTANT).columns);
   }

   @Test
   void dateColumnStoresTheSameDayAsSqlDate() throws Exception {
      Object[] sqlDates = new Object[ODTS.length];

      for(int i = 0; i < sqlDates.length; i++) {
         sqlDates[i] = new java.sql.Date(ODTS[i].toInstant().toEpochMilli());
      }

      // a DATE column holding offset values stores what java.sql.Date cells of the same
      // instants store (time of day cleared)
      assertEquals(build(sqlDates, XSchema.DATE).columns.get(0),
                   build(ODTS, XSchema.DATE).columns.get(0));
   }

   @Test
   void timeColumnStoresTheSameTimeAsSqlTime() throws Exception {
      Object[] sqlTimes = new Object[ODTS.length];

      for(int i = 0; i < sqlTimes.length; i++) {
         sqlTimes[i] = new java.sql.Time(ODTS[i].toInstant().toEpochMilli());
      }

      // a TIME column holding offset date-time values stores what java.sql.Time cells of the
      // same instants store
      List<Double> expected = build(sqlTimes, XSchema.TIME).columns.get(0);
      assertEquals(expected, build(ODTS, XSchema.TIME).columns.get(0));
      assertNotEquals(-1.0, expected.get(0), 0);
   }

   @Test
   void nullAndTimestampBehaviourUnchanged() throws Exception {
      Object[] vals = { new Timestamp(1577836800000L), null, new Timestamp(1577961000000L) };
      List<Double> col = build(vals, XSchema.TIME_INSTANT).columns.get(0);
      assertEquals(1577836800000.0, col.get(0), 0);
      assertEquals(Tool.NULL_DOUBLE, col.get(1), 0);
      assertEquals(1577961000000.0, col.get(2), 0);
   }

   private static Object[] timestamps() {
      Object[] result = new Object[ODTS.length];

      for(int i = 0; i < result.length; i++) {
         result[i] = new Timestamp(ODTS[i].toInstant().toEpochMilli());
      }

      return result;
   }

   private static final class Build {
      final List<List<Double>> columns = new ArrayList<>();
      Object min;
      Object max;
   }

   /**
    * Builds an MV as MVDef.createMVColumns shapes it for a date/time column: the base column is
    * a measure, followed by real Year and Month companion columns (also measures). Returns every
    * measure column's stored values plus the base column's date range.
    */
   private Build build(Object[] vals, String type) throws Exception {
      Object[][] data = new Object[vals.length + 1][];
      data[0] = new Object[] { "dt" };

      for(int i = 0; i < vals.length; i++) {
         data[i + 1] = new Object[] { vals[i] };
      }

      ColumnRef ref = new ColumnRef(new AttributeRef(null, "dt"));
      ref.setDataType(type);
      MVColumn base = new MVColumn(ref, false);
      List<MVColumn> columns = new ArrayList<>();
      columns.add(base);

      for(int level : new int[] { DateRangeRef.YEAR_INTERVAL, DateRangeRef.MONTH_INTERVAL }) {
         ColumnRef part = DateMVColumn.createDateColumn(level, ref);
         DateMVColumn dcol = new DateMVColumn(base, part, level);
         dcol.setReal(true);
         dcol.setDimension(false);
         columns.add(dcol);
      }

      MVDef def = new MVDef();
      def.container = new MVContainer(columns, null);

      MVBuilder builder = new MVBuilder(new DefaultTableLens(data), def, false);
      Build result = new Build();
      Iterator<SubMV> it = builder.getSubMVs();
      Field fm = DefaultTableBlock.class.getDeclaredField("mcols");
      fm.setAccessible(true);

      while(it.hasNext()) {
         DefaultTableBlock blk = it.next().getData();
         Object[] mc = (Object[]) fm.get(blk);
         assertNotNull(mc);

         for(Object m : mc) {
            List<Double> col = new ArrayList<>();

            for(int r = 0; r < blk.getRowCount(); r++) {
               col.add(((MVMeasureColumn) m).getValue(r));
            }

            result.columns.add(col);
         }
      }

      MVColumnInfo info = builder.getMV().getBlockInfo(0).getColumnInfo(0);
      result.min = info.getMin();
      result.max = info.getMax();
      return result;
   }
}
