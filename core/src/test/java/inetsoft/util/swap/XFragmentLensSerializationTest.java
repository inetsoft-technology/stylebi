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
package inetsoft.util.swap;

import inetsoft.report.TableLens;
import inetsoft.report.filter.AbstractConditionFilter;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.MinusTableLens;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78042, a table lens flushed to DistributedTableCacheStore with a swapped row map (the
 * XIntFragment row map of a condition filter, the XObjectFragment rows of a set lens) must be
 * readable by a node whose cache directory doesn't have the writer's swap files.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome()
@Tag("core")
class XFragmentLensSerializationTest {
   @Test
   void conditionFilterWithSwappedRowMapIsReadWithoutWriterSwapFiles() throws Exception {
      EvenRowFilter filter = new EvenRowFilter(table(100000));
      filter.moreRows(XTable.EOT);
      List<XIntFragment> fragments = intFragments(getField(filter, "rowmap"));
      assertTrue(fragments.size() > 1);
      List<File> files = new ArrayList<>();

      for(XIntFragment fragment : fragments) {
         fragment.swap();
         assertFalse(fragment.isValid(), "fragment is still in memory");
         files.add(fragment.getFile(fragment.prefix + ".tdat"));
      }

      byte[] bytes = serialize(filter);
      TableLens copy = withFilesMoved(files, () -> {
         TableLens read = (TableLens) deserialize(bytes);
         assertRows(filter, read);
         return read;
      });

      // the swapper of the reader swaps the copy out to its own swap files and reads them back
      List<XIntFragment> copyFragments = intFragments(getField(copy, "rowmap"));

      for(XIntFragment fragment : copyFragments) {
         assertTrue(fragment.isCompleted());
         assertTrue(fragment.swap(), "the copy can't be swapped");
      }

      assertRows(filter, copy);

      for(XIntFragment fragment : copyFragments) {
         fragment.dispose();
      }

      for(File file : files) {
         assertTrue(file.exists(), "the copy deleted the original's swap file " + file);
      }

      assertRows(new EvenRowFilter(table(100000)), filter);
   }

   @Test
   void conditionFilterWithResidentRowMapRoundTrips() throws Exception {
      EvenRowFilter filter = new EvenRowFilter(table(100000));
      filter.moreRows(XTable.EOT);

      for(XIntFragment fragment : intFragments(getField(filter, "rowmap"))) {
         assertTrue(fragment.isValid());
      }

      assertRows(filter, (TableLens) deserialize(serialize(filter)));
   }

   @Test
   void setLensWithSwappedRowsIsReadWithoutWriterSwapFiles() throws Exception {
      MinusTableLens lens = new MinusTableLens(table(30000), table(10));
      lens.moreRows(XTable.EOT);
      List<XObjectFragment<?>> fragments = objectFragments(getField(lens, "rows"));
      assertTrue(fragments.size() > 1);
      List<File> files = new ArrayList<>();

      for(XObjectFragment<?> fragment : fragments) {
         fragment.swap();
         assertFalse(fragment.isValid(), "fragment is still in memory");
         files.addAll(Arrays.asList(fragment.getSwapFiles()));
      }

      assertFalse(files.isEmpty());
      byte[] bytes = serialize(lens);
      TableLens copy = withFilesMoved(files, () -> {
         TableLens read = (TableLens) deserialize(bytes);
         assertRows(lens, read);
         return read;
      });

      for(XObjectFragment<?> fragment : objectFragments(getField(copy, "rows"))) {
         assertTrue(fragment.swap(), "the copy can't be swapped");
      }

      assertRows(lens, copy);
   }

   private static DefaultTableLens table(int rows) {
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "id", "name" };

      for(int r = 1; r <= rows; r++) {
         data[r] = new Object[] { r, "n" + r };
      }

      return new DefaultTableLens(data);
   }

   /**
    * Compare the rows of a copy with the original, which is the expected result.
    */
   private static void assertRows(TableLens expected, TableLens actual) {
      expected.moreRows(XTable.EOT);
      actual.moreRows(XTable.EOT);
      assertEquals(expected.getRowCount(), actual.getRowCount());

      for(int r = 0; r < expected.getRowCount(); r += 97) {
         assertEquals(expected.getObject(r, 0), actual.getObject(r, 0), "row " + r);
         assertEquals(expected.getObject(r, 1), actual.getObject(r, 1), "row " + r);
      }

      int last = expected.getRowCount() - 1;
      assertEquals(expected.getObject(last, 1), actual.getObject(last, 1), "row " + last);
   }

   private static List<XIntFragment> intFragments(Object list) throws Exception {
      List<XIntFragment> fragments = new ArrayList<>();

      for(XIntFragment fragment : (XIntFragment[]) getField(list, "fragments")) {
         if(fragment != null) {
            fragments.add(fragment);
         }
      }

      return fragments;
   }

   private static List<XObjectFragment<?>> objectFragments(Object list) throws Exception {
      List<XObjectFragment<?>> fragments = new ArrayList<>();

      for(XObjectFragment<?> fragment : (XObjectFragment<?>[]) getField(list, "fragments")) {
         if(fragment != null) {
            fragments.add(fragment);
         }
      }

      return fragments;
   }

   private static Object getField(Object obj, String name) throws Exception {
      for(Class<?> cls = obj.getClass(); cls != null; cls = cls.getSuperclass()) {
         try {
            Field field = cls.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(obj);
         }
         catch(NoSuchFieldException ignore) {
            // look in the super class
         }
      }

      throw new NoSuchFieldException(name);
   }

   /**
    * Serialize as DistributedTableCacheStore.put() does.
    */
   private static byte[] serialize(Object obj) throws IOException {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      try(ObjectOutputStream out = new ObjectOutputStream(new GZIPOutputStream(bytes))) {
         out.writeObject(obj);
      }

      return bytes.toByteArray();
   }

   private static Object deserialize(byte[] bytes) throws Exception {
      try(ObjectInputStream in =
             new ObjectInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes))))
      {
         return in.readObject();
      }
   }

   /**
    * Run an action while the swap files are moved out of the cache directory, as on another node.
    */
   private static <T> T withFilesMoved(List<File> files, Callable<T> action) throws Exception {
      Path dir = Files.createTempDirectory("swap78042");
      Map<Path, Path> moved = new LinkedHashMap<>();

      try {
         for(File file : files) {
            Path target = dir.resolve(file.getName());
            Files.move(file.toPath(), target);
            moved.put(file.toPath(), target);
         }

         return action.call();
      }
      finally {
         for(Map.Entry<Path, Path> e : moved.entrySet()) {
            Files.move(e.getValue(), e.getKey(), StandardCopyOption.REPLACE_EXISTING);
         }

         Files.deleteIfExists(dir);
      }
   }

   /**
    * A condition filter that keeps the rows with an even id.
    */
   private static final class EvenRowFilter extends AbstractConditionFilter {
      EvenRowFilter(TableLens table) {
         setTable(table);
      }

      @Override
      protected boolean checkCondition(int r) {
         return ((Integer) getTable().getObject(r, 0)) % 2 == 0;
      }
   }
}
