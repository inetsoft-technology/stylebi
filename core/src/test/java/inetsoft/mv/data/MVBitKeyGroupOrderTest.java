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
package inetsoft.mv.data;

import inetsoft.mv.MVDef;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.util.Tool;
import inetsoft.util.swap.XSwappableObjectList;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Time;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #80458: the group key of a time/date/timestamp/double/float MV column is
 * {@code Double.doubleToLongBits(value)}. Compared as signed longs, the keys of negative values
 * (times before the UTC offset hour east of UTC, dates before 1970, negative doubles) sort
 * wrongly (descending by magnitude, null after the other negatives). The data node sort
 * ({@link MVRow.RowComparator}, {@link GroupedTableBlock#sortRows}) and the server merge
 * ({@link MVRow2.RowComparator}, {@link MVQuery#add}) must order them by numeric value, null
 * first, ascending or descending, and in the same way.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MVBitKeyGroupOrderTest {
   private static final double NULL = Tool.NULL_DOUBLE;

   @Test
   void signedLongOrderOfNegativeKeysIsWrong() {
      // documents the cause: the raw keys do not order like the values
      long a = Double.doubleToLongBits(-1.0);
      long b = Double.doubleToLongBits(-5.0);
      long n = Double.doubleToLongBits(NULL);
      assertNotEquals(-1.0 < -5.0, a < b);
      assertEquals(true, n > b, "null key sorts after other negatives as a signed long");
      assertEquals(true, MVRow.sortKey(n) < MVRow.sortKey(b));
      assertEquals(true, MVRow.sortKey(b) < MVRow.sortKey(a));
      assertEquals(true, MVRow.sortKey(a) < MVRow.sortKey(Double.doubleToLongBits(0.0)));
      assertEquals(true, MVRow.sortKey(Double.doubleToLongBits(2.0)) <
                         MVRow.sortKey(Double.doubleToLongBits(3.0)));
   }

   @Test
   void rowComparatorOrdersBitKeysNumerically() {
      List<MVRow> rows = rows(2.0, -1.0, NULL, -100.0, 0.0, 5.5, -5.5);
      rows.sort(new MVRow.RowComparator(new boolean[] { true }, new boolean[] { true }));
      assertEquals(Arrays.asList(NULL, -100.0, -5.5, -1.0, 0.0, 2.0, 5.5), values(rows));

      rows.sort(new MVRow.RowComparator(new boolean[] { false }, new boolean[] { true }));
      assertEquals(Arrays.asList(5.5, 2.0, 0.0, -1.0, -5.5, -100.0, NULL), values(rows));
   }

   @Test
   void rowComparatorWithoutFlagKeepsRawLongOrder() {
      // non-bit-key (dictionary / integer) groups are unchanged
      List<MVRow> rows = new ArrayList<>();

      for(long v : new long[] { 3, -2, 7, Long.MIN_VALUE, 0 }) {
         rows.add(new MVRow(new long[] { v }, new double[0]));
      }

      rows.sort(new MVRow.RowComparator(new boolean[] { true }));
      assertEquals(Long.MIN_VALUE, rows.get(0).getGroups()[0]);
      assertEquals(-2L, rows.get(1).getGroups()[0]);
      assertEquals(7L, rows.get(4).getGroups()[0]);
   }

   @Test
   void sortRowsPackedAndFallbackMatchComparator() {
      Random rnd = new Random(80458);

      for(int c = 0; c < 500; c++) {
         int d = 1 + rnd.nextInt(3);
         // at most 8^d distinct tuples exist
         int n = 1 + rnd.nextInt(Math.min(60, (int) Math.pow(8, d)));
         boolean[] orders = new boolean[d];
         boolean[] bits = new boolean[d];

         for(int i = 0; i < d; i++) {
            orders[i] = rnd.nextBoolean();
            bits[i] = rnd.nextBoolean();
         }

         Set<List<Long>> seen = new HashSet<>();
         List<MVRow> list = new ArrayList<>();

         while(list.size() < n) {
            long[] g = new long[d];

            for(int i = 0; i < d; i++) {
               int k = rnd.nextInt(8);
               // bit key columns: small doubles, negative and null included
               g[i] = bits[i] ? Double.doubleToLongBits(k == 0 ? NULL : (k - 4) * 3600000.0) :
                  k - 4;
            }

            if(seen.add(Arrays.asList(Arrays.stream(g).boxed().toArray(Long[]::new)))) {
               list.add(new MVRow(g, new double[0]));
            }
         }

         // some cases use a wide range so that the comparator fallback is used
         if(c % 5 == 0) {
            list.add(new MVRow(new long[d], new double[0]));
            list.get(list.size() - 1).getGroups()[0] = Long.MAX_VALUE;
         }

         List<MVRow> expected = new ArrayList<>(list);
         expected.sort(new MVRow.RowComparator(orders, bits));
         Collections.shuffle(list, rnd);
         GroupedTableBlock.sortRows(list, orders, bits);
         assertEquals(expected.size(), list.size());

         for(int i = 0; i < expected.size(); i++) {
            assertEquals(Arrays.toString(expected.get(i).getGroups()),
                         Arrays.toString(list.get(i).getGroups()), "case " + c + " row " + i);
         }
      }
   }

   @Test
   void mergedBlocksAreNumericallyOrderedAscending() throws Exception {
      // blocks overlap on -3600000 (01:00 in UTC+8 terms) and NULL; negative keys are the
      // times before the offset hour, a block sent in the old signed-long order
      List<Double> merged = run(true,
         new double[][] { { 28800000.0, -3600000.0, NULL, -25000000.0, 0.0 },
                          { -3600000.0, NULL, 3600000.0, -28000000.0 } });
      assertEquals(Arrays.asList(NULL, -28000000.0, -25000000.0, -3600000.0, 0.0, 3600000.0,
                                 28800000.0), merged);
   }

   @Test
   void mergedBlocksAreNumericallyOrderedDescending() throws Exception {
      List<Double> merged = run(false,
         new double[][] { { 28800000.0, -3600000.0, NULL, -25000000.0, 0.0 },
                          { -3600000.0, NULL, 3600000.0, -28000000.0 } });
      assertEquals(Arrays.asList(28800000.0, 3600000.0, 0.0, -3600000.0, -25000000.0, -28000000.0,
                                 NULL), merged);
   }

   @Test
   void timeKeysUnderEastOfUtcZoneAreChronological() throws Exception {
      TimeZone old = TimeZone.getDefault();
      TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));

      try {
         double t0730 = Time.valueOf("07:30:00").getTime();
         double t0101 = Time.valueOf("01:01:01").getTime();
         double t0800 = Time.valueOf("08:00:00").getTime();
         double t1259 = Time.valueOf("12:59:01").getTime();
         // the times before 08:00 are negative: the reported bug
         assertEquals(true, t0730 < 0 && t0101 < 0 && t0800 >= 0);

         List<Double> merged = run(true,
            new double[][] { { t1259, t0730, NULL }, { t0101, t0800, NULL, t0730 } });
         assertEquals(Arrays.asList(NULL, t0101, t0730, t0800, t1259), merged);
      }
      finally {
         TimeZone.setDefault(old);
      }
   }

   @Test
   void positiveKeysKeepOrder() throws Exception {
      List<Double> merged = run(true,
         new double[][] { { 5.0, 1.0, NULL }, { 3.0, 1.0, 9.0 } });
      assertEquals(Arrays.asList(NULL, 1.0, 3.0, 5.0, 9.0), merged);
   }

   private static List<MVRow> rows(double... vals) {
      List<MVRow> rows = new ArrayList<>();

      for(double v : vals) {
         rows.add(new MVRow(new long[] { Double.doubleToLongBits(v) }, new double[0]));
      }

      return rows;
   }

   private static List<Double> values(List<MVRow> rows) {
      List<Double> result = new ArrayList<>();

      for(MVRow row : rows) {
         result.add(Double.longBitsToDouble(row.getGroups()[0]));
      }

      return result;
   }

   /**
    * Run the blocks through a group only MVQuery on a non-dictionary, non-integer (double based)
    * group. The sub block rows arrive sorted as signed longs, like from an unfixed data node.
    */
   private static List<Double> run(boolean asc, double[][] blocks) throws Exception {
      GroupRef group = new GroupRef(new ColumnRef(new AttributeRef(null, "T")));
      MV mv = mock(MV.class);
      mv.types = new Class[] { Time.class };
      when(mv.getDef()).thenReturn(mock(MVDef.class));
      when(mv.indexOfHeader(anyString(), anyInt())).thenAnswer(
         inv -> MVDef.getMVHeader(group.getDataRef()).equals(inv.getArgument(0)) ? 0 : -1);
      MVQuery query = new MVQuery(mv, new GroupRef[] { group }, new boolean[] { asc },
                                  new AggregateRef[0], new AggregateRef[0], null);

      for(int b = 0; b < blocks.length; b++) {
         long[] keys = new long[blocks[b].length];

         for(int i = 0; i < keys.length; i++) {
            keys[i] = Double.doubleToLongBits(blocks[b][i]);
         }

         Arrays.sort(keys);
         XSwappableObjectList<MVRow> rows = new XSwappableObjectList<>(MVRow.class);

         for(long key : keys) {
            rows.add(new MVRow(new long[] { key }, new double[0]));
         }

         rows.complete();
         SubTableBlock block = mock(SubTableBlock.class);
         XSwappableObjectList<MVRow>[] holder = new XSwappableObjectList[] { rows };
         when(block.getBlockIndex()).thenReturn(b);
         when(block.getRowCount()).thenAnswer(inv -> holder[0].size());
         when(block.getRow(anyInt())).thenAnswer(inv -> holder[0].get(inv.getArgument(0)));
         org.mockito.Mockito.doAnswer(inv -> {
            holder[0] = inv.getArgument(0);
            return null;
         }).when(block).setRows(org.mockito.ArgumentMatchers.any());
         query.add("block" + b, block);
      }

      java.lang.reflect.Field field = MVQuery.class.getDeclaredField("table");
      field.setAccessible(true);
      MergedTableBlock table = (MergedTableBlock) field.get(query);
      table.complete();
      List<Double> result = new ArrayList<>();

      for(int r = 0; r < table.getRowCount(); r++) {
         result.add(Double.longBitsToDouble((Long) ((MVRow2) table.getRow(r)).groups2[0]));
      }

      return result;
   }
}
