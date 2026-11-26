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
package inetsoft.mv;

import inetsoft.mv.MVColumn;
import inetsoft.mv.MVCreatorUtil;
import inetsoft.mv.MVDef;
import inetsoft.mv.MVJob;
import inetsoft.mv.data.*;
import inetsoft.mv.data.BitSet;
import inetsoft.mv.data.ChannelProvider;
import inetsoft.mv.data.MVColumnInfo;
import inetsoft.mv.data.XDimDictionary;
import inetsoft.mv.formula.*;
import inetsoft.mv.fs.*;
import inetsoft.mv.fs.BlockFile;
import inetsoft.mv.fs.internal.*;
import inetsoft.mv.fs.internal.CacheBlockFile;
import inetsoft.mv.util.FileSeekableInputStream;
import inetsoft.mv.util.SeekableInputStream;
import inetsoft.mv.util.TransactionChannel;
import inetsoft.sree.SreeEnv;
import inetsoft.report.filter.Formula;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XConstants;
import inetsoft.uql.XNode;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.XBinaryCondition;
import inetsoft.uql.jdbc.XExpression;
import inetsoft.uql.jdbc.XSet;
import inetsoft.storage.BlobStorageManager;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The MV storage engine: dimension indexes and dictionaries, block building, measure columns,
 * mergeable formulas, table blocks, sub MV conditions, MV queries, MV storage and block files.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  MvStorageEngineTest.StorageConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MvStorageEngineTest {
   /** The test context has no MVStorage bean; build one on the test blob storage. */
   @Configuration
   static class StorageConfiguration {
      @Bean
      MVStorage mvStorage(BlobStorageManager blobStorageManager) {
         return new MVStorage(blobStorageManager);
      }
   }

   @Test
   void bitAndDictIndex() {
      // key 0 is the null slot, keys 1..29 are dictionary values
      int[] keys = new int[300];
      Random random = new Random(42);

      for(int i = 0; i < keys.length; i++) {
         keys[i] = i % 11 == 0 ? 0 : 1 + random.nextInt(29);
      }

      BitDimIndex bit = new BitDimIndex();

      for(int row = 0; row < keys.length; row++) {
         bit.addKey(keys[row], row);
      }

      bit.complete();
      checkIndex(bit, keys);

      // each DictDimIndex width picks a different packed buffer layout
      for(int bits : new int[] { 5, 8, 14, 24 }) {
         DictDimIndex dict = new DictDimIndex(bits);

         for(int row = 0; row < keys.length; row++) {
            dict.addKey(keys[row], row);
         }

         dict.complete();
         assertTrue(dict.getBits() >= bits);
         checkIndex(dict, keys);
      }
   }

   @Test
   void builderBlocks() throws Exception {
      int rows = 2500;
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "region", "grp", "amount" };

      for(int i = 0; i < rows; i++) {
         // region changes with each 1000 rows, grp with each 600 rows
         data[i + 1] = new Object[] { "R" + i / 1000, "G" + i / 600, i % 7 };
      }

      String oldPreferred = SreeEnv.getProperty("mv.preferred.block");
      SreeEnv.setProperty("mv.preferred.block", "1000");

      try {
         // without a break column the blocks follow the preferred size
         MVBuilder builder = new MVBuilder(new DefaultTableLens(data), definition(null),
                                           false);
         List<Integer> counts = new ArrayList<>();
         List<Integer> regions = new ArrayList<>();
         Iterator<SubMV> iter = builder.getSubMVs();

         while(iter.hasNext()) {
            SubMV sub = iter.next();
            counts.add(sub.getData().getRowCount());
            regions.add(builder.getMV().getDictionary(0, counts.size() - 1).size());
         }

         assertEquals(List.of(1000, 1000, 500), counts);
         assertEquals(3, builder.getMV().getBlockSize());
         assertEquals(List.of(1, 1, 1), regions);

         // a break column keeps equal values in one block
         builder = new MVBuilder(new DefaultTableLens(data), definition("grp"),
                                 false);
         counts.clear();
         iter = builder.getSubMVs();

         while(iter.hasNext()) {
            counts.add(iter.next().getData().getRowCount());
         }

         assertEquals(List.of(1200, 1200, 100), counts);
         assertEquals(3, builder.getMV().getBlockSize());
      }
      finally {
         SreeEnv.setProperty("mv.preferred.block", oldPreferred);
      }
   }

   private MVDef definition(String breakColumn) {
      MVDef def = mock(MVDef.class);
      List<MVColumn> columns = new ArrayList<>();
      columns.add(new MVColumn(column("region", "string"), true));
      columns.add(new MVColumn(column("grp", "string"), true));
      columns.add(new MVColumn(column("amount", "integer"), false));
      when(def.getColumns()).thenReturn(columns);
      when(def.getBreakColumn()).thenReturn(breakColumn);
      return def;
   }

   private static ColumnRef column(String name, String type) {
      ColumnRef ref = new ColumnRef(new AttributeRef(null, name));
      ref.setDataType(type);
      return ref;
   }

   @Test
   void dateTimeColumns() {
      long half = 30 * 60 * 1000L;
      long sec = 1000L;
      long base = 1_700_000_000_000L;
      long[] dates = { base - base % half, base - base % half + 47 * half,
                       base - base % half - 100 * half };
      long[] times = { 0L, 13 * 3600 * sec + 25 * 60 * sec + 7 * sec, 86_399L * sec };
      long[] stamps = { base - base % sec, base - base % sec + 86_400_000L, 1_000L };

      MVDateIntColumn date = new MVDateIntColumn(null, 0, null, dates.length, true);
      MVTimeIntColumn time = new MVTimeIntColumn(null, 0, null, times.length, true);
      MVTimestampIntColumn stamp = new MVTimestampIntColumn(null, 0, null, stamps.length, true);

      for(int i = 0; i < dates.length; i++) {
         date.setValue(i, dates[i]);
         time.setValue(i, times[i]);
         stamp.setValue(i, stamps[i]);
      }

      for(int i = 0; i < dates.length; i++) {
         assertEquals(new java.sql.Date(dates[i]), new java.sql.Date((long) date.getValue(i)),
                      "date " + i);
         assertEquals(new Time(times[i]), new Time((long) time.getValue(i)), "time " + i);
         assertEquals(new Timestamp(stamps[i]), new Timestamp((long) stamp.getValue(i)),
                      "timestamp " + i);
      }

      // a timestamp is kept to the second and a null stays null
      stamp.setValue(0, stamps[0] + 789);
      assertEquals((double) stamps[0], stamp.getValue(0));
      date.setValue(1, Tool.NULL_DOUBLE);
      assertEquals(Tool.NULL_DOUBLE, date.getValue(1));
      assertEquals((double) dates[2], date.getValue(2));
   }

   private void checkIndex(XDimIndex index, int[] keys) {
      for(int key : new int[] { 1, 7, 15, 29, 30 }) {
         assertEquals(scan(keys, k -> k == key), rows(index.getRows("=", key, true)),
                      index.getClass() + " = " + key);
         assertEquals(scan(keys, k -> k != 0 && k != key),
                      rows(index.getRows("=", key, true, true)),
                      index.getClass() + " != " + key);
         assertEquals(scan(keys, k -> k > key), rows(index.getRows(">", key, true)),
                      index.getClass() + " > " + key);
         assertEquals(scan(keys, k -> k >= key), rows(index.getRows(">=", key, true)),
                      index.getClass() + " >= " + key);
         assertEquals(scan(keys, k -> k != 0 && k < key), rows(index.getRows("<", key, true)),
                      index.getClass() + " < " + key);
         assertEquals(scan(keys, k -> k != 0 && k <= key), rows(index.getRows("<=", key, true)),
                      index.getClass() + " <= " + key);
      }

      long[] in = { 3, 12, 20 };
      assertEquals(scan(keys, k -> k == 3 || k == 12 || k == 20),
                   rows(index.getRows("IN", in, true)), index.getClass() + " IN");
      assertEquals(scan(keys, k -> k >= 5 && k <= 9),
                   rows(index.getRows("BETWEEN", new long[] { 5, 9 }, true)),
                   index.getClass() + " BETWEEN");
      assertEquals(scan(keys, k -> k == 0), rows(index.getRows(0, false)),
                   index.getClass() + " null slot");
   }

   private static List<Integer> scan(int[] keys, java.util.function.IntPredicate test) {
      List<Integer> result = new ArrayList<>();

      for(int row = 0; row < keys.length; row++) {
         if(test.test(keys[row])) {
            result.add(row);
         }
      }

      return result;
   }

   private static List<Integer> rows(BitSet set) {
      List<Integer> result = new ArrayList<>();
      org.roaringbitmap.IntIterator iter = set.intIterator();

      while(iter.hasNext()) {
         result.add(iter.next());
      }

      return result;
   }

   @Nested
   class Dictionary {
      private static XDimDictionary strings(Object... values) {
         XDimDictionary dict = new XDimDictionary();
         dict.setDataType(String.class);

         for(Object value : values) {
            dict.addValue(value);
         }

         return dict;
      }

      private static XDimDictionary completed(Object... values) {
         XDimDictionary dict = strings(values);
         dict.complete();
         return dict;
      }

      private static XDimDictionary ints(Object... values) {
         XDimDictionary dict = new XDimDictionary();
         dict.setDataType(Integer.class);

         for(Object value : values) {
            dict.addValue(value);
         }

         return dict;
      }

      /**
       * Write the dictionary to a file and read it back, the way a stored MV block does. The
       * raw values of an overflowed dictionary can only be read after this.
       */
      private static XDimDictionary roundTrip(XDimDictionary dict, Path dir) throws Exception {
         ChannelProvider provider = ChannelProvider.file(new File(dir.toFile(), "one.bin"));

         try(SeekableByteChannel out = provider.newWriteChannel()) {
            dict.write(out);
         }

         XDimDictionary result = new XDimDictionary();

         try(SeekableByteChannel in = provider.newReadChannel()) {
            result.read(provider, in);
         }

         return result;
      }

      @Test
      void nullSlotSortedValuesAndBlank() {
         XDimDictionary dict = completed("b", "a", null, "", "b");

         assertTrue(dict.containsNull());
         assertEquals(3, dict.size());
         assertNull(dict.getValue(0));
         assertEquals("a", dict.getValue(1));
         assertEquals("b", dict.getValue(2));
         assertEquals(0, dict.indexOf(null, 0));
         assertEquals(0, dict.indexOf("", 0));
         assertEquals(1, dict.indexOf("a", 0));
         assertEquals(2, dict.indexOf("b", 0));
         // a missing value is a negative insertion point
         assertEquals(-4, dict.indexOf("zzz", 0));
         assertEquals("b", dict.max());
      }

      @Test
      void noNullMeansNoReservedSlot() {
         XDimDictionary dict = completed("y", "x");

         assertFalse(dict.containsNull());
         assertEquals(2, dict.size());
         assertEquals("x", dict.getValue(0));
         assertEquals("y", dict.getValue(1));
         assertEquals(0, dict.indexOf("x", 0));
         // blank and null are not in the dictionary when no null was added
         assertEquals(-1, dict.indexOf("", 0));
         assertEquals(-1, dict.indexOf(null, 0));
         assertEquals("x", dict.min());
         assertEquals("y", dict.max());
      }

      @Test
      void emptyDictionaryReadsNull() {
         XDimDictionary dict = completed();

         assertEquals(0, dict.size());
         assertNull(dict.getValue(0));
         assertNull(dict.getValue(5));
      }

      @Test
      void nullOnlyDictionaryReadsNullAtSlotZero() {
         XDimDictionary dict = completed((Object) null, "");

         assertEquals(1, dict.size());
         assertNull(dict.getValue(0));
         assertEquals(0, dict.indexOf(null, 0));
      }

      @Test
      void overflowLastIndexReadsRawValue(@TempDir Path dir) throws Exception {
         XDimDictionary dict = strings("p", "q", "r");
         dict.setOverflow(true);
         dict.complete();

         XDimDictionary read = roundTrip(dict, dir);
         assertFalse(read.isValid());
         assertEquals(3, read.size());
         assertTrue(read.isOverflow());
         assertEquals("p", read.getValue(0));
         assertEquals("r", read.getValue(2));
      }

      @Test
      void integerDimensionIsStoredAsRange() {
         XDimDictionary dict = ints(5, 9, 7);
         dict.complete();

         assertFalse(dict.containsNull());
         assertEquals(5, dict.getRangeMin());
         assertEquals(5, dict.size());
         assertEquals(5, dict.min());
         assertEquals(9, dict.max());
         assertEquals(0, dict.indexOf(5, 0));
         assertEquals(4, dict.indexOf(9, 0));
         // every value of the range is derivable without a value list, 6 was never added
         assertEquals(6, dict.getValue(1));
         assertEquals(9, dict.getValue(4));
      }

      @Test
      void integerRangeWithNullKeepsIndexValueInverse() {
         XDimDictionary dict = ints(5, null, 9);
         dict.complete();

         assertTrue(dict.containsNull());
         assertNull(dict.getValue(0));
         assertEquals(0, dict.indexOf(null, 0));

         for(int value = 5; value <= 9; value++) {
            int index = dict.indexOf(value, 0);
            assertTrue(index >= 1, "index of " + value);
            assertEquals(value, dict.getValue(index));
         }

         assertEquals(6, dict.size());
      }

      @Test
      void integerRangeWiderThanIntFails() {
         XDimDictionary ok = ints(0, Integer.MAX_VALUE - 1);
         assertEquals(0, ok.getRangeMin());

         XDimDictionary dict = ints(-1);
         RuntimeException ex = assertThrows(RuntimeException.class,
                                            () -> dict.addValue(Integer.MAX_VALUE - 1));
         assertTrue(ex.getMessage().startsWith("Dictionary value overflow"), ex.getMessage());

         XDimDictionary wide = ints(Integer.MIN_VALUE);
         assertThrows(RuntimeException.class, () -> wide.addValue(Integer.MAX_VALUE));
      }

      @Test
      void overflowKeepsRawValuesAndIndexIsRow(@TempDir Path dir) throws Exception {
         XDimDictionary dict = strings("m", "n");
         dict.setOverflow(true);
         dict.addValue("n");
         dict.addValue("m");
         dict.addValue(null);
         dict.complete();

         assertTrue(dict.isOverflow());
         assertEquals(5, dict.size());

         dict.setBaseRow(10);
         assertEquals(7, dict.indexOf("anything", 17));
         assertEquals(0, dict.indexOf(null, 10));

         // every raw value is kept, in row order and with duplicates
         XDimDictionary read = roundTrip(dict, dir);
         assertEquals(5, read.size());
         assertTrue(read.isOverflow());
         assertEquals("m", read.getValue(0));
         assertEquals("n", read.getValue(1));
         assertEquals("n", read.getValue(2));
         assertEquals("m", read.getValue(3));
         assertNull(read.getValue(4));
      }

      @Test
      void distinctValuesAtMaxSizeTurnOnOverflow(@TempDir Path dir) throws Exception {
         int max = XDimDictionary.getMaxSize();
         XDimDictionary dict = strings();

         for(int i = 0; i < max - 1; i++) {
            dict.addValue("v" + i);
         }

         assertFalse(dict.resetOverflow());
         assertFalse(dict.isOverflow());

         dict.addValue("last");
         // true only the first time the overflow is detected
         assertTrue(dict.resetOverflow());
         assertTrue(dict.isOverflow());
         assertFalse(dict.resetOverflow());

         dict.complete();
         assertEquals(max, dict.size());

         XDimDictionary read = roundTrip(dict, dir);
         // size() loads the lazily read dictionary
         assertEquals(max, read.size());
         assertTrue(read.isOverflow());
         assertEquals("v0", read.getValue(0));
         assertEquals("last", read.getValue(max - 1));
      }

      @Test
      void integerRangeNeverOverflows() {
         XDimDictionary dict = ints(1, 2, 3);
         assertFalse(dict.resetOverflow());
         assertFalse(dict.isOverflow());
      }

      @Test
      void rangesResetAndExpandedByObject() {
         XDimDictionary dict = completed("a", "b", "c", "d");

         dict.resetRanges();
         // no object yet: the reset range is empty
         assertNull(dict.min());
         assertNull(dict.max());

         dict.expandRangeByObject("b");
         assertEquals("b", dict.min());
         assertEquals("b", dict.max());

         dict.expandRangeByObject("d");
         // null and unknown objects do not move the range
         dict.expandRangeByObject(null);
         dict.expandRangeByObject("zz");
         assertEquals("b", dict.min());
         assertEquals("d", dict.max());

         dict.resetRanges();
         dict.expandRangeByObject("c");
         assertEquals("c", dict.min());
         assertEquals("c", dict.max());

         dict.nullRanges();
         dict.expandRangeByObject("d");
         assertEquals("a", dict.min());
         assertEquals("d", dict.max());
      }

      @Test
      void rangeExpansionSkipsTheNullSlot() {
         // enough values that a search starting at the null slot would reach it
         XDimDictionary dict = completed("a", null, "b", "c", "d");

         dict.resetRanges();
         dict.expandRangeByObject("a");
         dict.expandRangeByObject("b");

         assertEquals("a", dict.min());
         assertEquals("b", dict.max());
      }

      @Test
      void integerRangeIsNotReset() {
         XDimDictionary dict = ints(3, 8);
         dict.complete();

         dict.resetRanges();
         dict.expandRangeByObject(5);

         assertEquals(3, dict.getRangeMin());
         assertEquals(3, dict.min());
         assertEquals(8, dict.max());
      }

      @Test
      void mergeFromCombinesValues() {
         XDimDictionary first = strings("a", "c");
         XDimDictionary second = completed("b", "c", null);

         first.mergeFrom(second);
         first.complete();

         assertTrue(first.containsNull());
         assertEquals(4, first.size());
         assertNull(first.getValue(0));
         assertEquals("a", first.getValue(1));
         assertEquals("b", first.getValue(2));
         assertEquals("c", first.getValue(3));
         assertEquals(completed("a", "b", "c", null), first);
      }

      @Test
      void mergeFromOverflowedDictionaryKeepsRawValues(@TempDir Path dir) throws Exception {
         XDimDictionary target = strings("a");
         XDimDictionary other = strings("x", "y");
         other.setOverflow(true);
         other.complete();
         XDimDictionary source = roundTrip(other, dir);
         // load the lazily read dictionary
         assertEquals(2, source.size());

         target.mergeFrom(source);
         target.complete();

         assertTrue(target.isOverflow());
         assertEquals(3, target.size());
      }

      @Test
      void cloneIsEmptyAndRefillsToEqualDictionary() {
         XDimDictionary original = completed("a", "b", null);
         XDimDictionary copy = (XDimDictionary) original.clone();

         assertNotSame(original, copy);
         assertFalse(copy.isOverflow());
         assertEquals(0, copy.hashCode());
         assertNotEquals(original, copy);

         copy.addValue("b");
         copy.addValue(null);
         copy.addValue("a");
         copy.complete();

         assertEquals(original, copy);
         assertEquals(original.hashCode(), copy.hashCode());
         assertEquals(original.size(), copy.size());

         XDimDictionary other = completed("a", "b", "z", null);
         assertNotEquals(original, other);
         assertNotEquals(original, "a");
      }

      @Test
      void cloneOfOverflowedAndIntegerDictionary() {
         XDimDictionary over = strings("a", "b");
         over.setOverflow(true);
         over.complete();
         assertFalse(((XDimDictionary) over.clone()).isOverflow());

         XDimDictionary numbers = ints(4, 8);
         numbers.complete();
         XDimDictionary copy = (XDimDictionary) numbers.clone();
         copy.addValue(4);
         copy.addValue(8);
         copy.complete();
         assertEquals(numbers, copy);
         assertEquals(4, copy.getRangeMin());
      }

      @Test
      void swapOutAndReloadFromCache() {
         XDimDictionary dict = completed("c", "a", null, "b");
         assertTrue(dict.isValid());

         assertTrue(dict.swap());
         assertFalse(dict.isValid());

         // reading reloads the values from the swap file
         assertEquals(4, dict.size());
         assertNull(dict.getValue(0));
         assertEquals("a", dict.getValue(1));
         assertEquals("c", dict.getValue(3));
         assertEquals(2, dict.indexOf("b", 0));
         // the null slot survives the reload
         assertTrue(dict.containsNull());
         assertEquals(0, dict.indexOf(null, 0));
         assertTrue(dict.isValid());

         dict.dispose();
         assertFalse(dict.isSwappable());
      }

      @Test
      void incompleteDictionaryIsNotSwapped() {
         XDimDictionary dict = strings("a");

         assertFalse(dict.isCompleted());
         assertFalse(dict.swap());
         assertEquals(0.0, dict.getSwapPriority());
      }

      @Test
      void writeAndReadRoundTrip(@TempDir Path dir) throws Exception {
         XDimDictionary dict = completed("c", "a", null, "b");
         XDimDictionary numbers = ints(10, null, 14);
         numbers.complete();
         XDimDictionary over = strings("m", "n", "m");
         over.setOverflow(true);
         over.complete();

         ChannelProvider provider = ChannelProvider.file(new File(dir.toFile(), "dicts.bin"));

         try(SeekableByteChannel out = provider.newWriteChannel()) {
            dict.write(out);
            numbers.write(out);
            over.write(out);
         }

         XDimDictionary rdict = new XDimDictionary();
         XDimDictionary rnumbers = new XDimDictionary();
         XDimDictionary rover = new XDimDictionary();

         try(SeekableByteChannel in = provider.newReadChannel()) {
            rdict.read(provider, in);
            rnumbers.read(provider, in);
            rover.read(provider, in);
         }

         assertFalse(rdict.isValid());
         assertEquals(dict.hashCode(), rdict.hashCode());
         assertEquals(4, rdict.size());
         assertTrue(rdict.containsNull());
         assertNull(rdict.getValue(0));
         assertEquals("a", rdict.getValue(1));
         assertEquals("c", rdict.getValue(3));
         // a string dictionary is written with the "no range" marker
         assertEquals("c", rdict.max());
         assertEquals(dict, rdict);

         assertEquals(numbers.hashCode(), rnumbers.hashCode());
         assertEquals(numbers.size(), rnumbers.size());
         assertEquals(numbers.getRangeMin(), rnumbers.getRangeMin());
         assertNull(rnumbers.getValue(0));
         assertEquals(numbers.getValue(2), rnumbers.getValue(2));

         assertEquals(3, rover.size());
         assertTrue(rover.isOverflow());
         assertEquals("m", rover.getValue(0));
         assertEquals("n", rover.getValue(1));
         assertEquals("m", rover.getValue(2));
      }

      private static MVColumn measure(String name) {
         ColumnRef ref = new ColumnRef(new AttributeRef(null, name));
         ref.setDataType("integer");
         return new MVColumn(ref, false);
      }

      @Test
      void nonNumericRangeIsIgnored() {
         MVColumn dim = new MVColumn(new ColumnRef(new AttributeRef(null, "d")), true);
         MVColumn bad = measure("bad");
         MVColumn good = measure("good");
         MVColumn skipped = measure("skipped");
         bad.setRange(5, 6);
         good.setRange(3, 20);
         skipped.setRange(7, 8);

         MVColumnInfo[] infos = {
            new MVColumnInfo(null, null, null),
            new MVColumnInfo(null, "abc", "xyz"),
            new MVColumnInfo(null, 1, 9),
            new MVColumnInfo(null, 0, 100)
         };

         MVCreatorUtil.resetNumRange(new MVColumn[] { dim, bad, good, skipped }, infos,
                                     new boolean[] { true, true, false });

         // text where a number was expected: the old range stays
         assertEquals(5, bad.getOriginalMin());
         assertEquals(6, bad.getOriginalMax());
         // the range widens to the smaller min and larger max
         assertEquals(1, good.getOriginalMin());
         assertEquals(20, good.getOriginalMax());
         // not a numeric column of the block: untouched
         assertEquals(7, skipped.getOriginalMin());
         assertEquals(8, skipped.getOriginalMax());
      }

      @Test
      void numericRangeTakenFromColumnInfoWhenUnset() {
         MVColumn dim = new MVColumn(new ColumnRef(new AttributeRef(null, "d")), true);
         MVColumn col = measure("amount");
         MVColumnInfo[] infos = {
            new MVColumnInfo(null, null, null), new MVColumnInfo(null, 2.5, 7.5)
         };

         MVCreatorUtil.resetNumRange(new MVColumn[] { dim, col }, infos, new boolean[] { true });

         assertEquals(2.5, col.getOriginalMin());
         assertEquals(7.5, col.getOriginalMax());
      }
   }

   @Nested
   class BlockData {
      private static final double EPS = 1e-9;

      // ---------------------------------------------------------------- formulas

      private static final double[] XS = { 2, 4, 4, 4, 5, 5, 7, 9, 1, 8 };
      private static final double[] YS = { 1, 3, 2, 6, 4, 7, 5, 9, 2, 8 };
      private static final int SPLIT = 4;

      private static double[] slice(double[] arr, int from, int to) {
         return Arrays.copyOfRange(arr, from, to);
      }

      private static double sum(double[] a) {
         double s = 0;

         for(double v : a) {
            s += v;
         }

         return s;
      }

      private static double sumsq(double[] a) {
         double s = 0;

         for(double v : a) {
            s += v * v;
         }

         return s;
      }

      private static double sumxy(double[] a, double[] b) {
         double s = 0;

         for(int i = 0; i < a.length; i++) {
            s += a[i] * b[i];
         }

         return s;
      }

      private static double[] varianceTuple(double[] a) {
         return new double[] { a.length, sumsq(a), sum(a) };
      }

      private static double[] covTuple(double[] a, double[] b) {
         return new double[] { sumxy(a, b), sum(a), sum(b), a.length };
      }

      private static double[] corrTuple(double[] a, double[] b) {
         // SUMWT, SUMx, SUMy, N, SUMSQx, SUMSQy over the pairs (Bug #82375)
         return new double[] { sumxy(a, b), sum(a), sum(b), a.length, sumsq(a), sumsq(b) };
      }

      private static double mean(double[] a) {
         return sum(a) / a.length;
      }

      private static double sampleVariance(double[] a) {
         double m = mean(a);
         double s = 0;

         for(double v : a) {
            s += (v - m) * (v - m);
         }

         return s / (a.length - 1);
      }

      private static double popVariance(double[] a) {
         return sampleVariance(a) * (a.length - 1) / a.length;
      }

      private static double popCov(double[] a, double[] b) {
         double ma = mean(a);
         double mb = mean(b);
         double s = 0;

         for(int i = 0; i < a.length; i++) {
            s += (a[i] - ma) * (b[i] - mb);
         }

         return s / a.length;
      }

      private static double[] feed(Formula formula, double[]... tuples) {
         for(double[] tuple : tuples) {
            formula.addValue(tuple);
         }

         return tuples.length == 0 ? null : tuples[0];
      }

      @Test
      void compositeFormulasMergePartialResults() {
         double[] x1 = slice(XS, 0, SPLIT);
         double[] x2 = slice(XS, SPLIT, XS.length);
         double[] y1 = slice(YS, 0, SPLIT);
         double[] y2 = slice(YS, SPLIT, YS.length);

         // average: partial (sum, count) pairs add up to the overall mean
         CompositeAverageFormula avg = new CompositeAverageFormula(1);
         feed(avg, new double[] { sum(x1), x1.length }, new double[] { sum(x2), x2.length });
         assertEquals(4.9, (Double) avg.getResult(), EPS);
         assertEquals(mean(XS), avg.getDoubleResult(), EPS);
         assertFalse(avg.isNull());

         // sample and population variance and standard deviation
         CompositeVarianceFormula var = new CompositeVarianceFormula(new int[] { 1, 2 });
         CompositePopulationVarianceFormula pvar =
            new CompositePopulationVarianceFormula(new int[] { 1, 2 });
         CompositeStandardDeviationFormula sd =
            new CompositeStandardDeviationFormula(new int[] { 1, 2 });
         CompositePopulationStandardDeviationFormula psd =
            new CompositePopulationStandardDeviationFormula(new int[] { 1, 2 });

         for(Formula f : new Formula[] { var, pvar, sd, psd }) {
            feed(f, varianceTuple(x1), varianceTuple(x2));
         }

         assertEquals(sampleVariance(XS), (Double) var.getResult(), EPS);
         assertEquals(popVariance(XS), (Double) pvar.getResult(), EPS);
         assertEquals(Math.sqrt(sampleVariance(XS)), (Double) sd.getResult(), EPS);
         assertEquals(Math.sqrt(popVariance(XS)), (Double) psd.getResult(), EPS);
         assertEquals(Math.sqrt(popVariance(XS)), psd.getDoubleResult(), EPS);

         // covariance (population)
         CompositeCovarianceFormula cov = new CompositeCovarianceFormula(new int[] { 1, 2 });
         feed(cov, covTuple(x1, y1), covTuple(x2, y2));
         assertEquals(popCov(XS, YS), (Double) cov.getResult(), EPS);

         // correlation
         CompositeCorrelationFormula corr = new CompositeCorrelationFormula(new int[] { 1, 2 });
         feed(corr, corrTuple(x1, y1), corrTuple(x2, y2));
         double expected = popCov(XS, YS) / Math.sqrt(popVariance(XS) * popVariance(YS));
         assertEquals(expected, (Double) corr.getResult(), EPS);

         // merged halves give the same value as one pass over all the data
         CompositeCorrelationFormula one = new CompositeCorrelationFormula(new int[] { 1, 2 });
         feed(one, corrTuple(XS, YS));
         assertEquals((Double) one.getResult(), (Double) corr.getResult(), EPS);

         // reset starts over
         var.reset();
         assertTrue(var.isNull());
      }

      @Test
      void sumFormulasAndEmptyResults() {
         double[] x1 = slice(XS, 0, SPLIT);
         double[] x2 = slice(XS, SPLIT, XS.length);
         double[] y1 = slice(YS, 0, SPLIT);
         double[] y2 = slice(YS, SPLIT, YS.length);

         // weighted sum and sum of squares are additive across the partial results
         SumWTFormula wt1 = new SumWTFormula(1);
         SumWTFormula wt2 = new SumWTFormula(1);
         SumWTFormula wt = new SumWTFormula(1);

         for(int i = 0; i < XS.length; i++) {
            (i < SPLIT ? wt1 : wt2).addValue(new double[] { XS[i], YS[i] });
            wt.addValue(new double[] { XS[i], YS[i] });
         }

         assertEquals(sumxy(XS, YS), wt.getDoubleResult(), EPS);
         assertEquals(sumxy(x1, y1) + sumxy(x2, y2), wt1.getDoubleResult() + wt2.getDoubleResult(),
                      EPS);

         SumSQFormula sq1 = new SumSQFormula();
         SumSQFormula sq2 = new SumSQFormula();

         for(double v : x1) {
            sq1.addValue(v);
         }

         for(double v : x2) {
            sq2.addValue(v);
         }

         sq1.addValue(Tool.NULL_DOUBLE);
         assertEquals(sumsq(XS), (Double) sq1.getResult() + (Double) sq2.getResult(), EPS);

         // a formula that saw nothing is null, or zero when default results are on
         CompositeAverageFormula avg = new CompositeAverageFormula(1);
         assertNull(avg.getResult());
         assertTrue(avg.isNull());
         avg.setDefaultResult(true);
         assertEquals(0.0, (Double) avg.getResult(), 0);

         CompositeVarianceFormula var = new CompositeVarianceFormula(new int[] { 1, 2 });
         var.addValue(new double[] { 1, 9, 3 });
         // one row has no sample variance, the default (true) gives 0
         assertEquals(0.0, (Double) var.getResult(), 0);
         var.setDefaultResult(false);
         assertNull(var.getResult());

         // composite formulas reject a plain value, they need the partial tuple
         assertThrows(RuntimeException.class, () -> avg.addValue(1.0));
      }

      @Test
      void mergeableCollectionFormulas() {
         double[] data = { 5, 3, 3, 9, 1, 3, 5, 7, 7, 7, 7, 2 };
         double[] a = slice(data, 0, 5);
         double[] b = slice(data, 5, data.length);

         DistinctCountFormula d1 = new DistinctCountFormula();
         DistinctCountFormula d2 = new DistinctCountFormula();
         DistinctCountFormula dall = new DistinctCountFormula();
         MedianFormula m1 = new MedianFormula();
         MedianFormula m2 = new MedianFormula();
         MedianFormula mall = new MedianFormula();
         ModeFormula o1 = new ModeFormula();
         ModeFormula o2 = new ModeFormula();
         ModeFormula oall = new ModeFormula();
         NthMostFrequentFormula n1 = new NthMostFrequentFormula(2);
         NthMostFrequentFormula n2 = new NthMostFrequentFormula(2);
         NthMostFrequentFormula nall = new NthMostFrequentFormula(2);

         for(double v : a) {
            d1.addValue(v);
            m1.addValue(v);
            o1.addValue(v);
            n1.addValue(v);
         }

         for(double v : b) {
            d2.addValue(v);
            m2.addValue(v);
            o2.addValue(v);
            n2.addValue(v);
         }

         for(double v : data) {
            dall.addValue(v);
            mall.addValue(v);
            oall.addValue(v);
            nall.addValue(v);
         }

         // null markers are ignored
         d1.addValue(Tool.NULL_DOUBLE);
         m1.addValue(Tool.NULL_DOUBLE);

         d1.merge(d2);
         m1.merge(m2);
         o1.merge(o2);
         n1.merge(n2);

         // distinct values: 1 2 3 5 7 9
         assertEquals(6, d1.getResult());
         assertEquals(dall.getResult(), d1.getResult());
         // sorted: 1 2 3 3 3 5 5 7 7 7 7 9 -> (5 + 5) / 2 ... index 5 and 6
         assertEquals(5.0, (Double) m1.getResult(), EPS);
         assertEquals(mall.getResult(), m1.getResult());
         // 7 appears 4 times, 3 appears 3 times
         assertEquals(7.0, (Double) o1.getResult(), EPS);
         assertEquals(oall.getResult(), o1.getResult());
         assertEquals(3.0, (Double) n1.getResult(), EPS);
         assertEquals(nall.getResult(), n1.getResult());
         assertEquals("Mode", new ModeFormula().getDisplayName());

         // merging into a copy does not change the source
         DistinctCountFormula copy = (DistinctCountFormula) d2.clone();
         copy.merge(d1);
         assertEquals(6, copy.getResult());
         assertEquals(4, d2.getResult());

         // an even count of values averages the two middle ones
         MedianFormula even = new MedianFormula();
         even.addValue(1.0);
         even.addValue(4.0);
         assertEquals(2.5, (Double) even.getResult(), EPS);
         assertTrue(new MedianFormula().isNull());
      }

      @Test
      void rowFoldMergesFormulaInfos() {
         double[] data = { 4, 4, 8, 1, 4, 8 };

         MVRow left = foldRow(1, data, 0, 3);
         MVRow right = foldRow(1, data, 3, 6);
         left.fold(right);

         assertEquals(3, left.getObject(0));
         assertEquals(4.0, (Double) left.getObject(1), EPS);
         assertEquals(4.0, (Double) left.getObject(2), EPS);

         // a row that carries no formulas folds its raw aggregates in
         MVRow plain = new MVRow(new long[] { 1 }, new double[] { 9, 9, 9 });
         left.fold(plain);
         assertEquals(4, left.getObject(0));
      }

      private static MVRow foldRow(long group, double[] data, int from, int to) {
         FormulaInfo[] infos = {
            FormulaInfo.create(new DistinctCountFormula(), new int[] { 0 }),
            FormulaInfo.create(new MedianFormula(), new int[] { 0 }),
            FormulaInfo.create(new ModeFormula(), new int[] { 0 })
         };
         MVRow row = new MVRow(new long[] { group }, new double[3]);
         row.setFormulas(infos);

         for(int i = from; i < to; i++) {
            row.add(new double[] { data[i] });
         }

         return row;
      }

      // ----------------------------------------------------------- fixture helpers

      private static final String[] HEADERS = { "region", "qty", "price", "ts" };
      private static final long T0 = 1_700_000_000_000L;
      private static int fileSeq = 0;

      private static Timestamp ts(int sec) {
         return new Timestamp(T0 + sec * 1000L);
      }

      /** Ten rows: region, qty (int), price (float-exact), ts (whole seconds). */
      private static Object[][] data() {
         return data(false);
      }

      private static Object[][] data(boolean nullStamp) {
         Object[][] rows = {
            HEADERS,
            { "A", 10, 1.5, ts(0) },
            { "B", 20, 2.25, ts(1) },
            { "A", null, null, nullStamp ? null : ts(2) },
            { "C", 40, 0.1, ts(3) },
            { "B", 50, -3.75, ts(4) },
            { "A", 10, 1.5, ts(5) },
            { null, 30, 1000000.0, ts(6) },
            { "C", 60, 0.0, ts(7) },
            { "B", 20, 2.25, ts(8) },
            { "A", 5, 7.0, ts(9) }
         };
         return rows;
      }

      private static ColumnRef column(String name, String type) {
         ColumnRef ref = new ColumnRef(new AttributeRef(null, name));
         ref.setDataType(type);
         return ref;
      }

      private static MVDef definition(boolean ranged) {
         MVDef def = mock(MVDef.class);
         List<MVColumn> columns = new ArrayList<>();
         columns.add(new MVColumn(column("region", "string"), true));
         MVColumn qty = new MVColumn(column("qty", "integer"), false);

         if(ranged) {
            qty.setRange(5, 60);
         }

         columns.add(qty);
         columns.add(new MVColumn(column("price", "double"), false));
         columns.add(new MVColumn(column("ts", "timeInstant"), false));
         when(def.getColumns()).thenReturn(columns);
         when(def.getBreakColumn()).thenReturn(null);
         when(def.getColumn(anyString(), anyBoolean())).thenAnswer(inv -> {
            for(MVColumn col : columns) {
               if(col.getName().equals(inv.getArgument(0)) &&
                  col.isDimension() == (boolean) inv.getArgument(1))
               {
                  return col;
               }
            }

            return null;
         });
         return def;
      }

      /** A built MV and its sub MVs, each written to and read back from a block file. */
      private static final class Built {
         MV mv;
         final List<SubMV> subs = new ArrayList<>();
         final List<BlockFile> files = new ArrayList<>();

         void dispose() {
            for(BlockFile file : files) {
               SubMV.removeMap(file);
               file.delete();
            }
         }
      }

      private static Built build(String name, boolean ranged, String blockSize,
                                 String doublePrecision) throws Exception
      {
         return build(name, data(false), ranged, blockSize, doublePrecision);
      }

      private static Built build(String name, Object[][] data, boolean ranged, String blockSize,
                                 String doublePrecision) throws Exception
      {
         String oldBlock = SreeEnv.getProperty("mv.preferred.block");
         String oldPrecision = SreeEnv.getProperty("mv.double.precision");

         if(blockSize != null) {
            SreeEnv.setProperty("mv.preferred.block", blockSize);
         }

         SreeEnv.setProperty("mv.double.precision", doublePrecision);

         try {
            MVBuilder builder = new MVBuilder(new DefaultTableLens(data), definition(ranged),
                                              false);
            Built built = new Built();
            Iterator<SubMV> iter = builder.getSubMVs();

            while(iter.hasNext()) {
               SubMV sub = iter.next();
               BlockFile file = new CacheBlockFile("mvdp-" + name + "-" + (fileSeq++) + ".dat");
               sub.write(file);
               built.files.add(file);
               built.subs.add(SubMV.get(file));
            }

            built.mv = builder.getMV();
            return built;
         }
         finally {
            SreeEnv.setProperty("mv.preferred.block", oldBlock);
            SreeEnv.setProperty("mv.double.precision", oldPrecision);
         }
      }

      private static List<Integer> toList(BitSet set) {
         List<Integer> result = new ArrayList<>();
         org.roaringbitmap.IntIterator iter = set.intIterator();

         while(iter.hasNext()) {
            result.add(iter.next());
         }

         return result;
      }

      // --------------------------------------------------------- measure columns

      @Test
      void timestampBeyondSignedIntSeconds() {
         // the column is unsigned, so seconds up to 2^32 - 1 (year 2106) should be kept
         MVTimestampIntColumn stamp = new MVTimestampIntColumn(null, 0, null, 2, true);
         double year2096 = 4_000_000_000_000.0;
         stamp.setValue(0, year2096);
         assertEquals(year2096, stamp.getValue(0), 0);
      }

      @Test
      void measureColumnTypeSelection() throws Exception {
         Built floats = build("types-float", true, null, "false");
         Built doubles = build("types-double", false, null, "true");

         try {
            SubMV sub = floats.subs.get(0);
            assertEquals(1, sub.getDimCount());
            assertEquals(3, sub.getMeasureCount());

            // integer measure with a known range is a ranged int column
            XDimIndex qty = sub.getIndex(sub.indexOfHeader("qty"));
            assertInstanceOf(MVIntColumn.class, qty);
            double[] expectQty = { 10, 20, Tool.NULL_DOUBLE, 40, 50, 10, 30, 60, 20, 5 };

            for(int i = 0; i < expectQty.length; i++) {
               assertEquals(expectQty[i], ((MVMeasureColumn) qty).getValue(i), 0, "qty " + i);
            }

            // the header (range) is loaded with the first access
            assertEquals(5, ((MVIntColumn) qty).min());
            assertEquals(60, ((MVIntColumn) qty).max());

            // a number measure is a float column unless double precision is on
            XDimIndex price = sub.getIndex(sub.indexOfHeader("price"));
            assertInstanceOf(MVFloatColumn.class, price);
            assertFalse(price instanceof MVDoubleColumn);
            assertEquals((double) 0.1f, ((MVMeasureColumn) price).getValue(3), 0);
            assertEquals(Tool.NULL_DOUBLE, ((MVMeasureColumn) price).getValue(2), 0);
            assertEquals(-3.75, ((MVMeasureColumn) price).getValue(4), 0);
            assertEquals(1000000.0, ((MVMeasureColumn) price).getValue(6), 0);

            // a timestamp measure is an unsigned int column holding whole seconds
            XDimIndex stamp = sub.getIndex(sub.indexOfHeader("ts"));
            assertInstanceOf(MVTimestampIntColumn.class, stamp);
            assertInstanceOf(MVUIntColumn.class, stamp);
            assertEquals((double) ts(4).getTime(), ((MVMeasureColumn) stamp).getValue(4), 0);
            assertEquals((double) ts(2).getTime(), ((MVMeasureColumn) stamp).getValue(2), 0);

            // double precision keeps the value a float would round
            SubMV dsub = doubles.subs.get(0);
            XDimIndex dprice = dsub.getIndex(dsub.indexOfHeader("price"));
            assertInstanceOf(MVDoubleColumn.class, dprice);
            assertEquals(0.1, ((MVMeasureColumn) dprice).getValue(3), 0);
            assertEquals(Tool.NULL_DOUBLE, ((MVMeasureColumn) dprice).getValue(2), 0);

            // the builder learns the range from the data when none is given
            XDimIndex wide = dsub.getIndex(dsub.indexOfHeader("qty"));
            assertInstanceOf(MVIntColumn.class, wide);
            assertEquals(Tool.NULL_DOUBLE, ((MVMeasureColumn) wide).getValue(2), 0);
            assertEquals(60.0, ((MVMeasureColumn) wide).getValue(7), 0);
            assertEquals(5, ((MVIntColumn) wide).min());
            assertEquals(60, ((MVIntColumn) wide).max());
            assertTrue(((MVIntColumn) wide).getBits() < 32);
         }
         finally {
            floats.dispose();
            doubles.dispose();
         }
      }

      @Test
      void nullTimestampFallsBackToDouble() throws Exception {
         // the unsigned int column cannot hold the null marker (a negative double), so a
         // timestamp column that has a null is stored as a double column
         Built built = build("types-nullts", data(true), true, null, "false");

         try {
            SubMV sub = built.subs.get(0);
            XDimIndex stamp = sub.getIndex(sub.indexOfHeader("ts"));
            assertInstanceOf(MVDoubleColumn.class, stamp);
            assertEquals((double) ts(4).getTime(), ((MVMeasureColumn) stamp).getValue(4), 0);
            assertEquals(Tool.NULL_DOUBLE, ((MVMeasureColumn) stamp).getValue(2), 0);
            assertEquals(List.of(2), toList(sub.getRows(cond("ts", "null", null))));
         }
         finally {
            built.dispose();
         }
      }

      private static BlockFile writeColumn(String name, XDimIndex column) throws Exception {
         BlockFile file = new CacheBlockFile("mvdp-" + name + "-" + (fileSeq++) + ".dat");

         try(inetsoft.mv.util.TransactionChannel channel = file.openWriteChannel()) {
            column.complete();
            column.write(channel, null);
            channel.commit();
         }

         return file;
      }

      @Test
      void measureColumnsRoundTrip() throws Exception {
         int size = 2500;
         List<BlockFile> files = new ArrayList<>();

         try {
            // int column with a range: negative, zero, the ends and null
            MVIntColumn ranged = new MVIntColumn(size, -1000, 1000);
            // int column without a range: full int span
            MVIntColumn wide = new MVIntColumn(size, null, null);
            MVFloatColumn flt = new MVFloatColumn(null, 0, null, size, true);
            MVDoubleColumn dbl = new MVDoubleColumn(null, 0, null, size, true);
            MVTimestampIntColumn stamp = new MVTimestampIntColumn(null, 0, null, size, true);
            double[] rangedVals = new double[size];
            double[] wideVals = new double[size];
            double[] fltVals = new double[size];
            double[] dblVals = new double[size];
            double[] stampVals = new double[size];
            double[] rangedSpecial = { -1000, 0, 1000, Tool.NULL_DOUBLE, -1, 1 };
            double[] wideSpecial =
               { Integer.MAX_VALUE, Integer.MIN_VALUE + 1, 0, Tool.NULL_DOUBLE };
            double[] fltSpecial = { Float.MAX_VALUE, Float.MIN_VALUE, -1.5, 0, Tool.NULL_DOUBLE };
            double[] dblSpecial = { Double.MAX_VALUE, Double.MIN_VALUE, -1e300, 0.1,
                                    Tool.NULL_DOUBLE };
            // epoch 0 is the stored null, so the smallest timestamp is one second
            double[] stampSpecial = { 1000, 86_400_000L, 2_000_000_000_000L, Tool.NULL_DOUBLE };

            for(int i = 0; i < size; i++) {
               rangedVals[i] = rangedSpecial[i % rangedSpecial.length];
               wideVals[i] = wideSpecial[i % wideSpecial.length];
               fltVals[i] = fltSpecial[i % fltSpecial.length];
               dblVals[i] = dblSpecial[i % dblSpecial.length];
               stampVals[i] = stampSpecial[i % stampSpecial.length];
               ranged.setValue(i, rangedVals[i]);
               wide.setValue(i, wideVals[i]);
               flt.setValue(i, fltVals[i]);
               dbl.setValue(i, dblVals[i]);
               stamp.setValue(i, stampVals[i]);
            }

            // what is read back from the written file equals what was stored
            BlockFile rangedFile = writeColumn("ranged", ranged);
            BlockFile wideFile = writeColumn("wide", wide);
            BlockFile fltFile = writeColumn("float", flt);
            BlockFile dblFile = writeColumn("double", dbl);
            BlockFile stampFile = writeColumn("stamp", stamp);
            files.addAll(List.of(rangedFile, wideFile, fltFile, dblFile, stampFile));

            MVIntColumn rangedIn = new MVIntColumn(rangedFile.openInputStream(), 0, rangedFile,
                                                   size, ranged.getBits());
            MVIntColumn wideIn = new MVIntColumn(wideFile.openInputStream(), 0, wideFile, size,
                                                 wide.getBits());
            MVFloatColumn fltIn = new MVFloatColumn(fltFile.openInputStream(), 0, fltFile, size,
                                                    false);
            MVDoubleColumn dblIn = new MVDoubleColumn(dblFile.openInputStream(), 0, dblFile, size,
                                                      false);
            MVTimestampIntColumn stampIn = new MVTimestampIntColumn(
               stampFile.openInputStream(), 0, stampFile, size, false);

            // a range gives a narrow packed column, none gives a full 32 bit int column
            assertTrue(ranged.getBits() < 32);
            assertEquals(32, wide.getBits());
            assertNull(wide.min());
            assertEquals(-1000, ranged.min());
            assertEquals(1000, ranged.max());

            for(int i = 0; i < size; i++) {
               assertEquals(rangedVals[i], ranged.getValue(i), 0, "ranged " + i);
               assertEquals(rangedVals[i], rangedIn.getValue(i), 0, "ranged in " + i);
               assertEquals(wideVals[i], wide.getValue(i), 0, "wide " + i);
               assertEquals(wideVals[i], wideIn.getValue(i), 0, "wide in " + i);
               assertEquals(fltVals[i], flt.getValue(i), 0, "float " + i);
               assertEquals(fltVals[i], fltIn.getValue(i), 0, "float in " + i);
               assertEquals(dblVals[i], dbl.getValue(i), 0, "double " + i);
               assertEquals(dblVals[i], dblIn.getValue(i), 0, "double in " + i);
               assertEquals(stampVals[i], stamp.getValue(i), 0, "stamp " + i);
               assertEquals(stampVals[i], stampIn.getValue(i), 0, "stamp in " + i);
            }
         }
         finally {
            files.forEach(BlockFile::delete);
         }
      }

      // ------------------------------------------------------------ sub MV rows

      private static XBinaryCondition cond(String col, String op, Object val) {
         return new XBinaryCondition(new XExpression(col, XExpression.FIELD),
                                     new XExpression(val, XExpression.VALUE), op);
      }

      private static XSet set(String relation, XNode... children) {
         XSet set = new XSet(relation);

         for(XNode child : children) {
            set.addChild(child);
         }

         return set;
      }

      /** Rows of block 0 matching a condition, dimension values mapped like the server does. */
      private static List<Integer> rows(Built built, XNode node) {
         XNode fixed = built.mv.fixFilter(node).get(0);
         return toList(built.subs.get(0).getRows(fixed));
      }

      @Test
      void subMvConditionsOnDictionaryAndMeasures() throws Exception {
         Built built = build("conds", true, null, "false");

         try {
            // dictionary column, region: A B A C B A null C B A
            assertEquals(List.of(1, 4, 8), rows(built, cond("region", "=", "B")));
            assertEquals(List.of(), rows(built, cond("region", "=", "Z")));
            XBinaryCondition notB = cond("region", "=", "B");
            notB.setIsNot(true);
            assertEquals(List.of(0, 2, 3, 5, 7, 9), rows(built, notB));
            assertEquals(List.of(0, 2, 3, 5, 7, 9),
                         rows(built, cond("region", "IN", new Object[] { "A", "C" })));
            assertEquals(List.of(6), rows(built, cond("region", "null", null)));
            assertEquals(List.of(1, 3, 4, 7, 8), rows(built, cond("region", ">", "A")));
            assertEquals(List.of(0, 1, 2, 4, 5, 8, 9), rows(built, cond("region", "<=", "B")));
            assertEquals(List.of(1, 4, 8), rows(built, cond("region", "STARTSWITH", "B")));
            assertEquals(List.of(3, 7), rows(built, cond("region", "CONTAINS", "C")));

            // int measure, qty: 10 20 null 40 50 10 30 60 20 5
            assertEquals(List.of(0, 5), rows(built, cond("qty", "=", 10)));
            XBinaryCondition notTen = cond("qty", "=", 10);
            notTen.setIsNot(true);
            assertEquals(List.of(1, 3, 4, 6, 7, 8, 9), rows(built, notTen));
            assertEquals(List.of(3, 4, 7), rows(built, cond("qty", ">", 30)));
            assertEquals(List.of(3, 4, 6, 7), rows(built, cond("qty", ">=", 30)));
            assertEquals(List.of(0, 5, 9), rows(built, cond("qty", "<", 20)));
            assertEquals(List.of(0, 5, 9), rows(built, cond("qty", "<=", 10)));
            assertEquals(List.of(0, 5, 7, 9),
                         rows(built, cond("qty", "IN", new Object[] { 10, 60, 5 })));
            assertEquals(List.of(2), rows(built, cond("qty", "null", null)));
            assertEquals(List.of(0, 5), rows(built, cond("qty", "STARTSWITH", 1)));
            assertEquals(List.of(0, 1, 3, 4, 5, 6, 7, 8),
                         rows(built, cond("qty", "CONTAINS", 0)));

            // float measure, price: 1.5 2.25 null 0.1 -3.75 1.5 1e6 0.0 2.25 7.0
            assertEquals(List.of(0, 5), rows(built, cond("price", "=", 1.5)));
            assertEquals(List.of(1, 6, 8, 9), rows(built, cond("price", ">", 2)));
            assertEquals(List.of(4), rows(built, cond("price", "<", 0)));
            assertEquals(List.of(2), rows(built, cond("price", "null", null)));

            // timestamp measure, seconds 0 1 2 3 4 5 6 7 8 9
            assertEquals(List.of(3), rows(built, cond("ts", "=", ts(3).getTime())));
            assertEquals(List.of(6, 7, 8, 9), rows(built, cond("ts", ">=", ts(6).getTime())));
            assertEquals(List.of(), rows(built, cond("ts", "null", null)));
         }
         finally {
            built.dispose();
         }
      }

      @Test
      void subMvConditionSets() throws Exception {
         Built built = build("condsets", true, null, "false");

         try {
            // and / or of conditions on different columns
            assertEquals(List.of(0, 5),
                         rows(built,
                              set(XSet.AND, cond("region", "=", "A"), cond("qty", "=", 10))));
            assertEquals(List.of(3, 7),
                         rows(built, set(XSet.OR, cond("region", "=", "C"), cond("qty", "=", 60))));

            // a negated and removes the second group from the first
            XSet notAnd = set(XSet.AND, cond("region", "=", "A"), cond("qty", "=", 10));
            notAnd.setIsNot(true);
            assertEquals(List.of(2, 9), rows(built, notAnd));

            // two bounds on one column are evaluated as one range
            assertEquals(List.of(1, 6, 8),
                         rows(built, set(XSet.AND, cond("qty", ">=", 20), cond("qty", "<", 40))));
            assertEquals(List.of(0, 1, 2, 4, 5, 8, 9),
                         rows(built, set(XSet.AND, cond("region", ">=", "A"),
                                         cond("region", "<=", "B"))));
            assertEquals(List.of(7), rows(built, set(XSet.AND, cond("qty", ">", 50),
                                                     cond("qty", "<=", 60))));
         }
         finally {
            built.dispose();
         }
      }

      @Test
      void subMvConditionErrors() throws Exception {
         Built built = build("condbad", true, null, "false");

         try {
            SubMV sub = built.subs.get(0);

            // non-numeric text against a numeric column is reported, with the text
            NumberFormatException nfe = assertThrows(NumberFormatException.class,
               () -> sub.getRows(cond("qty", "=", "abc")));
            assertTrue(nfe.getMessage().contains("abc"), nfe.getMessage());
            assertThrows(NumberFormatException.class,
                         () -> sub.getRows(cond("price", ">", "12x")));
            assertThrows(NumberFormatException.class,
                         () -> sub.getRows(cond("region", "=", "B")));

            // a sub query value is rejected
            inetsoft.util.MessageException subq = assertThrows(inetsoft.util.MessageException.class,
               () -> sub.getRows(cond("qty", "=", new SubQueryValue())));
            assertEquals("Subquery condition not supported.", subq.getMessage());

            // an unknown operation is reported with the operation
            RuntimeException op = assertThrows(RuntimeException.class,
                                               () -> sub.getRows(cond("qty", "~~", 1)));
            assertTrue(op.getMessage().contains("Unsupported operation: ~~"), op.getMessage());

            // like on a dictionary index is unsupported
            RuntimeException like = assertThrows(RuntimeException.class,
                                                 () -> sub.getRows(cond("region", "LIKE", "1")));
            assertTrue(like.getMessage().contains("Unsupported operation"), like.getMessage());
         }
         finally {
            built.dispose();
         }
      }

      // ------------------------------------------------------------ table blocks

      private static final AggregateRef SUM_QTY =
         new AggregateRef(column("qty", "integer"), AggregateFormula.SUM);
      private static final AggregateRef MAX_PRICE =
         new AggregateRef(column("price", "double"), AggregateFormula.MAX);
      private static final AggregateRef AVG_QTY =
         new AggregateRef(column("qty", "integer"), AggregateFormula.AVG);
      private static final AggregateRef COUNT_QTY =
         new AggregateRef(column("qty", "integer"), AggregateFormula.COUNT_ALL);

      private static AggregateRef[] subAggregates(AggregateRef... aggregates) {
         Set<AggregateRef> subs = new LinkedHashSet<>();

         for(AggregateRef aggregate : aggregates) {
            subs.addAll(aggregate.getSubAggregates());
         }

         return subs.toArray(new AggregateRef[0]);
      }

      private static SubTableBlock execute(Built built, int block, GroupRef[] groups,
                                           boolean[] order, AggregateRef[] subs, boolean detail)
         throws Exception
      {
         SubMVQuery query = new SubMVQuery(groups, order, subs, null);
         query.setBlockIndex(block);
         query.setDetail(detail);
         return query.execute(built.subs.get(block));
      }

      private static MVJob job() {
         MVJob job = mock(MVJob.class);
         TableAssembly table = mock(TableAssembly.class);
         when(job.getTable()).thenReturn(table);
         when(table.getMaxRows()).thenReturn(0);
         return job;
      }

      private static Map<String, List<Object>> tableRows(XTable table) {
         Map<String, List<Object>> result = new LinkedHashMap<>();

         for(int r = 1; r < table.getRowCount(); r++) {
            List<Object> row = new ArrayList<>();

            for(int c = 1; c < table.getColCount(); c++) {
               row.add(table.getObject(r, c));
            }

            result.put(String.valueOf(table.getObject(r, 0)), row);
         }

         return result;
      }

      private static void assertRow(Map<String, List<Object>> rows, String key,
                                    double... expected)
      {
         List<Object> row = rows.get(key);
         assertNotNull(row, "row " + key + " in " + rows.keySet());
         assertEquals(expected.length, row.size());

         for(int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], ((Number) row.get(i)).doubleValue(), EPS,
                         "row " + key + " col " + i);
         }
      }

      @Test
      void groupedBlocksMergeIntoQueryResult() throws Exception {
         // preferred block size 5: rows 0-4 in block 0, rows 5-9 in block 1
         Built built = build("blocks", true, "5", "false");

         try {
            assertEquals(2, built.subs.size());
            GroupRef[] groups = { new GroupRef(column("region", "string")) };
            boolean[] order = { true };
            AggregateRef[] aggregates = { SUM_QTY, MAX_PRICE, AVG_QTY, COUNT_QTY };
            AggregateRef[] subs = subAggregates(aggregates);
            assertEquals(3, subs.length);

            // block 0 holds region A B A C B: qty 10 20 null 40 50
            SubTableBlock first = execute(built, 0, groups, order, subs, false);
            assertInstanceOf(GroupedTableBlock.class, first);
            assertEquals(3, first.getRowCount());
            assertEquals(1, first.getDimCount());
            assertEquals(3, first.getMeasureCount());
            int sum = first.indexOfHeader(AggregateFormula.SUM.getUID("qty", null)) - 1;
            int count = first.indexOfHeader(AggregateFormula.COUNT_ALL.getUID("qty", null)) - 1;
            XDimDictionary dict = built.mv.getDictionary(0, 0);
            Map<String, Double> sums = new TreeMap<>();
            Map<String, Double> counts = new TreeMap<>();

            for(int r = 0; r < first.getRowCount(); r++) {
               MVRow row = first.getRow(r);
               String key = String.valueOf(dict.getValue((int) row.getGroups()[0]));
               sums.put(key, ((Number) row.getObject(sum)).doubleValue());
               counts.put(key, ((Number) row.getObject(count)).doubleValue());
            }

            assertEquals(Map.of("A", 10.0, "B", 70.0, "C", 40.0), sums);
            assertEquals(Map.of("A", 1.0, "B", 2.0, "C", 1.0), counts);

            // a part of a block is a window onto its rows
            PartTableBlock part = new PartTableBlock(first, 1, 3);
            assertEquals(2, part.getRowCount());
            assertSame(first.getRow(1), part.getRow(0));
            assertSame(first.getRow(2), part.getRow(1));
            assertEquals(first.getHeader(0), part.getHeader(0));
            assertEquals(first.getColCount(), part.getColCount());
            assertEquals(first.indexOfHeader("region"), part.indexOfHeader("region"));
            assertEquals(1, new PartTableBlock(first, 2, 100).getRowCount());

            // the merged result combines both blocks: group count, sum, max, avg, count
            SubTableBlock second = execute(built, 1, groups, order, subs, false);
            MVQuery query = new MVQuery(built.mv, groups, order, aggregates, subs, null);
            query.add("b0", first);
            query.add("b1", second);
            query.add("b1", second);
            query.complete(true);
            Map<String, List<Object>> merged = tableRows(query.getData());
            assertEquals(4, merged.size());
            assertRow(merged, "A", 25, 7.0, 25.0 / 3, 3);
            assertRow(merged, "B", 90, 2.25, 30, 3);
            assertRow(merged, "C", 100, 0.1f, 50, 2);
            assertRow(merged, "null", 30, 1000000.0, 30, 1);
         }
         finally {
            built.dispose();
         }
      }

      @Test
      void covarianceAndCorrelationLeaveOutPairsWithOneNull() throws Exception {
         // Bug #82375: the sub-aggregates of covariance and correlation must all range over the
         // rows where both columns are not null, as without MV (and as the database computes
         // them). Rows 0-4 are block 0, rows 5-9 block 1. In block 0, B's products sum to 0.
         Object[][] data = {
            HEADERS,
            { "A", 10, 1.5, ts(0) },
            { "A", 20, null, ts(1) },
            { "A", null, 4.0, ts(2) },
            { "B", 40, 0.0, ts(3) },
            { "B", 50, 0.0, ts(4) },
            { "A", 30, 2.5, ts(5) },
            { "A", 15, 7.0, ts(6) },
            { "B", 60, 1.25, ts(7) },
            { "B", 25, 3.0, ts(8) },
            { "B", 35, null, ts(9) }
         };
         Built built = build("covariance", data, true, "5", "false");

         try {
            assertEquals(2, built.subs.size());
            GroupRef[] groups = { new GroupRef(column("region", "string")) };
            boolean[] order = { true };
            AggregateRef cov = new AggregateRef(column("qty", "integer"),
                                                column("price", "double"),
                                                AggregateFormula.COVARIANCE);
            AggregateRef corr = new AggregateRef(column("qty", "integer"),
                                                 column("price", "double"),
                                                 AggregateFormula.CORRELATION);
            AggregateRef[] aggregates = { cov, corr };
            AggregateRef[] subs = subAggregates(aggregates);
            MVQuery query = new MVQuery(built.mv, groups, order, aggregates, subs, null);
            query.add("b0", execute(built, 0, groups, order, subs, false));
            query.add("b1", execute(built, 1, groups, order, subs, false));
            query.complete(true);
            Map<String, List<Object>> merged = tableRows(query.getData());

            double[] ax = { 10, 30, 15 };
            double[] ay = { 1.5, 2.5, 7.0 };
            double[] bx = { 40, 50, 60, 25 };
            double[] by = { 0.0, 0.0, 1.25, 3.0 };
            assertRow(merged, "A", popCov(ax, ay),
                      popCov(ax, ay) / Math.sqrt(popVariance(ax) * popVariance(ay)));
            assertRow(merged, "B", popCov(bx, by),
                      popCov(bx, by) / Math.sqrt(popVariance(bx) * popVariance(by)));
         }
         finally {
            built.dispose();
         }
      }

      @Test
      void detailAndGroupOnlyBlocks() throws Exception {
         Built built = build("detail", true, "5", "false");

         try {
            GroupRef[] groups = { new GroupRef(column("region", "string")),
                                  new GroupRef(column("qty", "integer")),
                                  new GroupRef(column("price", "double")) };
            boolean[] order = { true, true, true };
            AggregateRef[] none = new AggregateRef[0];

            // a detail query returns every row of the block in storage order
            SubTableBlock d0 = execute(built, 0, groups, order, none, true);
            SubTableBlock d1 = execute(built, 1, groups, order, none, true);
            assertInstanceOf(DetailTableBlock.class, d0);
            assertEquals(5, d0.getRowCount());
            assertEquals(0, d0.getMeasureCount());
            assertEquals(3, d0.getDimCount());
            assertEquals(10L, d0.getRow(0).getGroups()[1]);
            assertEquals(Double.doubleToLongBits(2.25), d0.getRow(1).getGroups()[2]);

            MVQuery detail = new MVQuery(built.mv, groups, order, none, none, null);
            detail.setDetail(true);
            detail.setJob(job());
            detail.add("d0", d0);
            detail.add("d1", d1);
            detail.complete(true);
            XTable table = detail.getData();
            assertEquals(11, table.getRowCount());
            assertEquals("region", table.getObject(0, 0));
            assertEquals("A", table.getObject(1, 0));
            assertEquals(10.0, ((Number) table.getObject(1, 1)).doubleValue(), 0);
            assertEquals(1.5, ((Number) table.getObject(1, 2)).doubleValue(), 0);
            assertNull(table.getObject(3, 1));
            assertNull(table.getObject(3, 2));
            assertNull(table.getObject(7, 0));
            assertEquals(1000000.0, ((Number) table.getObject(7, 2)).doubleValue(), 0);
            assertEquals("A", table.getObject(10, 0));
            assertEquals(7.0, ((Number) table.getObject(10, 2)).doubleValue(), 0);

            // a group only query returns the distinct values of both blocks
            GroupRef[] one = { groups[0] };
            SubTableBlock g0 = execute(built, 0, one, new boolean[] { true }, none, false);
            SubTableBlock g1 = execute(built, 1, one, new boolean[] { true }, none, false);
            assertEquals(3, g0.getRowCount());
            assertEquals(0, g0.getMeasureCount());
            MVQuery distinct = new MVQuery(built.mv, one, new boolean[] { true }, none, none, null);
            distinct.add("g0", g0);
            distinct.add("g1", g1);
            distinct.complete(true);
            Map<String, List<Object>> values = tableRows(distinct.getData());
            assertEquals(List.of("A", "B", "C", "null"), new ArrayList<>(new TreeSet<>(
               values.keySet())));
            assertEquals(4, values.size());

            // a streamed block reads rows of the sub MV consecutively
            DefaultTableBlock data = built.subs.get(0).getData();
            SubMVQuery query = new SubMVQuery(groups, order, none, null);
            data.init(query);
            StreamedDetailTableBlock streamed = new StreamedDetailTableBlock(data, true);
            streamed.init(query);
            BitSet pick = new BitSet();
            pick.add(1);
            pick.add(3);
            pick.complete();
            streamed.setRange(pick, Integer.MAX_VALUE);
            assertEquals(2, streamed.getRowCount());
            assertEquals(20L, streamed.getRow(0).getGroups()[1]);
            assertEquals(40L, streamed.getRow(1).getGroups()[1]);
            assertTrue(streamed.isCompleted());
            RuntimeException skip = assertThrows(RuntimeException.class, () -> streamed.getRow(5));
            assertTrue(skip.getMessage().contains("consecutively"), skip.getMessage());
            assertThrows(RuntimeException.class, () -> streamed.addRow(new MVRow()));
         }
         finally {
            built.dispose();
         }
      }
   }

   @Nested
   class QueryAndStorage {
      // ---------------------------------------------------------------- query builder

      @Test
      void aggregateTableToQuery() throws Exception {
         MV mv = buildMV();
         BoundTableAssembly table = table("t1", "region", "grp", "amount");
         AggregateInfo ainfo = new AggregateInfo();
         GroupRef region = new GroupRef(table.getColumnSelection(true).getAttribute("region"));
         ainfo.addGroup(region);
         AggregateRef sum = new AggregateRef(
            (ColumnRef) table.getColumnSelection(true).getAttribute("amount"),
            AggregateFormula.SUM);
         ainfo.addAggregate(sum);
         table.setAggregateInfo(ainfo);
         table.setMaxRows(25);
         SortInfo sinfo = new SortInfo();
         SortRef sort = new SortRef(table.getColumnSelection(true).getAttribute("region"));
         sort.setOrder(XConstants.SORT_DESC);
         sinfo.addSort(sort);
         table.setSortInfo(sinfo);

         MVQueryBuilder builder = new MVQueryBuilder(table, mv, new VariableTable(), null, null);
         MVQuery query = builder.getQuery();
         SubMVQuery sub = builder.getSubQuery();

         assertEquals(1, query.getGroups().length);
         assertEquals("region", query.getGroups()[0].getName());
         assertEquals(1, query.getAggregates().length);
         assertEquals(AggregateFormula.SUM, query.getAggregates()[0].getFormula());
         assertArrayEquals(new boolean[] { false }, query.getOrder());
         assertFalse(query.isDetail());
         assertNull(query.getCondition());
         assertFalse(sub.isDetail());
         assertEquals(25, sub.getMaxRows());
         assertArrayEquals(new String[] { "amount" }, sub.getAggregateColumns());

         // the static column listing gives the group and aggregate columns of the same table
         String[][] cols = MVQueryBuilder.getQueryColumns(table, mv.getDef());
         assertArrayEquals(new String[] { "region" }, cols[0]);
         assertArrayEquals(new String[] { "amount" }, cols[1]);
      }

      @Test
      void detailTableToQuery() throws Exception {
         MV mv = buildMV();
         BoundTableAssembly table = table("t2", "region", "amount");
         MVQueryBuilder builder = new MVQueryBuilder(table, mv, new VariableTable(), null, null);

         // no aggregate info: every column becomes a group of a detail query
         assertTrue(builder.getQuery().isDetail());
         assertTrue(builder.getSubQuery().isDetail());
         assertEquals(2, builder.getQuery().getGroups().length);
         assertEquals("region", builder.getQuery().getGroups()[0].getName());
         assertEquals("amount", builder.getQuery().getGroups()[1].getName());
         assertEquals(0, builder.getQuery().getAggregates().length);
         assertArrayEquals(new boolean[] { true, true }, builder.getQuery().getOrder());
         assertEquals(-1, builder.getSubQuery().getMaxRows());

         String[][] cols = MVQueryBuilder.getQueryColumns(table, mv.getDef());
         assertArrayEquals(new String[] { "region", "amount" }, cols[0]);
         assertEquals(0, cols[1].length);
      }

      @Test
      void missingColumnRejected() throws Exception {
         MV mv = buildMV();
         BoundTableAssembly table = table("t3", "region", "nosuch");
         RuntimeException ex = assertThrows(
            RuntimeException.class,
            () -> new MVQueryBuilder(table, mv, new VariableTable(), null, null));
         assertTrue(ex.getMessage().startsWith("Column not found in mv: "), ex.getMessage());
         assertTrue(ex.getMessage().contains("nosuch"), ex.getMessage());
      }

      private static BoundTableAssembly table(String name, String... cols) {
         BoundTableAssembly table = new BoundTableAssembly(new Worksheet(), name);
         ColumnSelection selection = new ColumnSelection();

         for(String col : cols) {
            ColumnRef ref = new ColumnRef(new AttributeRef(null, col));
            ref.setDataType("amount".equals(col) ? "integer" : "string");
            selection.addAttribute(ref);
         }

         table.setColumnSelection(selection);
         return table;
      }

      private MV buildMV() throws Exception {
         Object[][] data = new Object[][] {
            { "region", "grp", "amount" },
            { "east", "a", 1 }, { "east", "b", 2 }, { "west", "a", 3 }
         };
         MVDef def = mock(MVDef.class);
         List<MVColumn> columns = new ArrayList<>();
         columns.add(new MVColumn(column("region", "string"), true));
         columns.add(new MVColumn(column("grp", "string"), true));
         columns.add(new MVColumn(column("amount", "integer"), false));
         when(def.getColumns()).thenReturn(columns);
         when(def.getColumn(anyString(), anyBoolean())).thenAnswer(a -> {
            for(MVColumn col : columns) {
               if(col.matches(a.getArgument(0), a.getArgument(1))) {
                  return col;
               }
            }

            return null;
         });

         MVBuilder builder = new MVBuilder(new DefaultTableLens(data), def, false);
         Iterator<SubMV> iter = builder.getSubMVs();

         while(iter.hasNext()) {
            iter.next();
         }

         return builder.getMV();
      }

      private static ColumnRef column(String name, String type) {
         ColumnRef ref = new ColumnRef(new AttributeRef(null, name));
         ref.setDataType(type);
         return ref;
      }

      // ---------------------------------------------------------------- MVStorage

      @Test
      void storageSaveRenameRemove() throws Exception {
         MVStorage storage = MVStorage.getInstance();
         String a = MVStorage.getFile("fspart-a");
         String b = MVStorage.getFile("fspart-b");
         String c = MVStorage.getFile("fspart-c");
         assertTrue(a.endsWith(".mv"));
         assertEquals("fspart-a", storage.getNameForDef("fspart-a"));

         try {
            assertFalse(storage.exists(a));
            assertEquals(0L, storage.getLength(a));
            assertEquals(0L, storage.getLastModified(a));

            storage.put(a, buildMV());
            assertTrue(storage.exists(a));
            long length = storage.getLength(a);
            assertTrue(length > 0);
            assertTrue(storage.getLastModified(a) > 0);
            assertTrue(storage.listFiles().contains(a));
            assertTrue(storage.list().contains("fspart-a"));

            byte[] bytes;

            try(InputStream in = storage.getStorage(null).getInputStream(a)) {
               bytes = in.readAllBytes();
            }

            assertEquals(length, bytes.length);

            // rename moves the content and drops the old name
            storage.rename(a, b);
            assertFalse(storage.exists(a));
            assertTrue(storage.exists(b));
            assertEquals(length, storage.getLength(b));
            assertEquals(0L, storage.getLength(a));

            // copy keeps the source and writes the same bytes
            storage.copy(b, c);
            assertTrue(storage.exists(b));
            assertTrue(storage.exists(c));

            try(InputStream in = storage.getStorage(null).getInputStream(c)) {
               assertArrayEquals(bytes, in.readAllBytes());
            }

            storage.remove(b);
            assertFalse(storage.exists(b));
            assertTrue(storage.exists(c));
            assertFalse(storage.list().contains("fspart-b"));
            assertThrows(IOException.class, () -> storage.getStorage(null).getInputStream(b));
         }
         finally {
            for(String name : new String[] { a, b, c }) {
               if(storage.exists(name)) {
                  storage.remove(name);
               }
            }
         }
      }

      // ---------------------------------------------------------------- seekable streams

      @Test
      void fileSeekableStreamReadSeekMap(@TempDir File dir) throws Exception {
         File file = new File(dir, "seek.bin");
         byte[] data = new byte[256];

         for(int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
         }

         Files.write(file.toPath(), data);

         try(RandomAccessFile raf = new RandomAccessFile(file, "r");
             FileSeekableInputStream in = new FileSeekableInputStream(raf.getChannel(), file))
         {
            assertTrue(in.isOpen());
            assertEquals(256L, in.size());
            assertEquals(0L, in.position());
            assertSame(file, in.getFile());
            assertSame(file, in.getFilePath());
            assertEquals(file.lastModified(), in.getModificationTime());

            // read advances the position
            ByteBuffer buf = ByteBuffer.allocate(4);
            assertEquals(4, in.read(buf));
            assertArrayEquals(new byte[] { 0, 1, 2, 3 }, buf.array());
            assertEquals(4L, in.position());

            // seek back to an arbitrary offset
            assertSame(in, in.position(100));
            buf.clear();
            assertEquals(4, in.read(buf));
            assertArrayEquals(new byte[] { 100, 101, 102, 103 }, buf.array());

            // a read at the end reports EOF
            in.position(256);
            buf.clear();
            assertEquals(-1, in.read(buf));

            // map covers the requested window and moves the position past it
            ByteBuffer mapped = in.map(10, 6);
            assertEquals(6, mapped.remaining());
            byte[] got = new byte[6];
            mapped.get(got);
            assertArrayEquals(new byte[] { 10, 11, 12, 13, 14, 15 }, got);
            assertEquals(16L, in.position());
            in.unmap(mapped);

            // reopen yields an independent stream on the same file
            SeekableInputStream copy = in.reopen();
            assertNotSame(in, copy);
            assertEquals(0L, copy.position());
            assertEquals(256L, copy.size());
            assertEquals(file, copy.getFilePath());
            copy.position(255);
            buf.clear();
            buf.limit(1);
            assertEquals(1, copy.read(buf));
            assertEquals((byte) 255, buf.array()[0]);
            assertEquals(16L, in.position());
            copy.close();
            assertFalse(copy.isOpen());
            assertTrue(in.isOpen());
         }
      }

      @Test
      void reopenMissingFileReturnsNull(@TempDir File dir) throws Exception {
         File file = new File(dir, "gone.bin");
         Files.write(file.toPath(), new byte[] { 1 });
         FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ);
         FileSeekableInputStream in = new FileSeekableInputStream(channel, file);
         assertTrue(file.delete());
         assertNull(in.reopen());
         in.close();
         assertFalse(in.isOpen());
      }

      @Test
      void cacheBlockFileWriteAppendRead() throws Exception {
         CacheBlockFile file = new CacheBlockFile("fspart_cache", ".blk");
         String name = file.getName();

         try {
            assertTrue(name.startsWith("fspart_cache"), name);
            assertTrue(name.endsWith(".blk"), name);
            assertEquals(name, file.toString());

            // the temp-file constructor creates the empty file up front
            assertTrue(file.exists());
            assertEquals(0L, file.length());

            try(TransactionChannel out = file.openWriteChannel()) {
               assertEquals(
                  5, out.write(ByteBuffer.wrap("hello".getBytes(StandardCharsets.UTF_8))));
               assertEquals(5L, out.position());
               out.commit();
            }

            assertTrue(file.exists());
            assertEquals(5L, file.length());
            assertTrue(file.lastModified() > 0);

            // a second channel appends after seeking to the end
            try(TransactionChannel out = file.openWriteChannel()) {
               out.position(out.size());
               out.write(ByteBuffer.wrap(" world".getBytes(StandardCharsets.UTF_8)));
            }

            assertEquals(11L, file.length());

            try(SeekableInputStream in = file.openInputStream()) {
               assertEquals(11L, in.size());
               in.position(6);
               ByteBuffer buf = ByteBuffer.allocate(5);
               assertEquals(5, in.read(buf));
               assertEquals("world", new String(buf.array(), StandardCharsets.UTF_8));
               assertEquals(11L, in.position());
               assertEquals(file.lastModified(), in.getModificationTime());

               ByteBuffer mapped = in.map(0, 5);
               byte[] got = new byte[5];
               mapped.get(got);
               assertEquals("hello", new String(got, StandardCharsets.UTF_8));
               in.unmap(mapped);

               SeekableInputStream again = in.reopen();
               assertEquals(0L, again.position());
               assertEquals(11L, again.size());
               again.close();
               assertTrue(in.isOpen());
            }

            assertTrue(file.delete());
            assertFalse(file.exists());
            assertEquals(0L, file.length());
         }
         finally {
            file.delete();
         }

         // the named constructor resolves to a cache file of that name
         CacheBlockFile named = new CacheBlockFile("fspart-named.blk");
         assertEquals("fspart-named.blk", named.getName());
         assertFalse(named.exists());
      }
   }
}
