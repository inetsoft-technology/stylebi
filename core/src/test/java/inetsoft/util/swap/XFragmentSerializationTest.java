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

import inetsoft.test.*;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78042, a swapped fragment was serialized (e.g. by DistributedTableCacheStore) with no data
 * and the name of its swap file, so a copy read by another node, whose cache directory doesn't
 * have that file, failed with SwapFileReadException. The copy also kept the writer's prefix, so
 * its dispose() deleted the writer's swap file when both shared a cache directory.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome()
@Tag("core")
class XFragmentSerializationTest {
   @Test
   void swappedIntListIsReadWithoutWriterSwapFiles() throws Exception {
      XSwappableIntList list = new XSwappableIntList();

      for(int i = 0; i < 40000; i++) {
         list.add(i + 1000);
      }

      list.complete();
      XIntFragment[] fragments = fragments(list, 2);
      List<File> files = new ArrayList<>();

      for(XIntFragment fragment : fragments) {
         swap(fragment);
         files.add(intSwapFile(fragment));
      }

      byte[] bytes = serialize(list);
      assertTrue(bytes.length > 10000, "the values were not written: " + bytes.length + " bytes");

      // another node: the reader's cache directory doesn't have the writer's swap files
      XSwappableIntList copy = withFilesMoved(files, () -> {
         XSwappableIntList read = (XSwappableIntList) deserialize(bytes);
         assertEquals(40000, read.size());
         assertEquals(1005, read.get(5));
         assertEquals(40999, read.get(39999));
         return read;
      });

      XIntFragment[] copyFragments = fragments(copy, 2);

      for(int i = 0; i < copyFragments.length; i++) {
         assertNotEquals(fragments[i].prefix, copyFragments[i].prefix, "copy shares the prefix");
         assertTrue(copyFragments[i].isCompleted());
         // the copy is swapped by this JVM to its own swap file
         swap(copyFragments[i]);
      }

      assertEquals(1005, copy.get(5));
      assertEquals(40999, copy.get(39999));
      assertEquals(1005, list.get(5));

      copy.dispose();
      list.dispose();
   }

   @Test
   void swappedObjectListIsReadWithoutWriterSwapFiles() throws Exception {
      XSwappableObjectList<String> list = new XSwappableObjectList<>(null);

      for(int i = 0; i < 10000; i++) {
         list.add("s" + i);
      }

      list.complete();
      XObjectFragment<String>[] fragments = fragments(list, 2);
      List<File> files = new ArrayList<>();

      for(XObjectFragment<String> fragment : fragments) {
         swap(fragment);
         files.addAll(Arrays.asList(fragment.getSwapFiles()));
      }

      assertFalse(files.isEmpty());
      byte[] bytes = serialize(list);

      XSwappableObjectList<String> copy = withFilesMoved(files, () -> {
         @SuppressWarnings("unchecked")
         XSwappableObjectList<String> read = (XSwappableObjectList<String>) deserialize(bytes);
         assertEquals(10000, read.size());
         assertEquals("s5", read.get(5));
         assertEquals("s9999", read.get(9999));
         return read;
      });

      XObjectFragment<String>[] copyFragments = fragments(copy, 2);

      for(int i = 0; i < copyFragments.length; i++) {
         assertNotEquals(fragments[i].prefix, copyFragments[i].prefix, "copy shares the prefix");
         swap(copyFragments[i]);
      }

      assertEquals("s5", copy.get(5));
      assertEquals("s9999", copy.get(9999));
      assertEquals("s5", list.get(5));

      copy.dispose();
      list.dispose();
   }

   @Test
   void disposedIntCopyDoesNotDeleteOriginalSwapFile() throws Exception {
      XIntFragment original = new XIntFragment(values());
      swap(original);
      File file = intSwapFile(original);

      XIntFragment copy = (XIntFragment) deserialize(serialize(original));
      assertNotEquals(original.prefix, copy.prefix);
      copy.dispose();

      assertTrue(file.exists(), "the copy deleted the original's swap file");
      assertEquals(1005, original.getSafely(5));
      original.dispose();
   }

   @Test
   void changedObjectCopyDoesNotDeleteOriginalSwapFiles() throws Exception {
      XObjectFragment<String> original = objectFragment();
      swap(original);
      File[] files = original.getSwapFiles();
      assertTrue(files.length > 0);

      @SuppressWarnings("unchecked")
      XObjectFragment<String> copy = (XObjectFragment<String>) deserialize(serialize(original));
      assertNotEquals(original.prefix, copy.prefix);

      // swap the copy to its own file, then change it, which deletes the copy's swap files
      swap(copy);
      assertEquals("v5", copy.getSafely(5));
      copy.set(5, "changed");
      copy.change();
      copy.dispose();

      for(File file : files) {
         assertTrue(file.exists(), "the copy deleted the original's swap file " + file);
      }

      assertEquals("v5", original.getSafely(5));
      original.dispose();
   }

   @Test
   void fragmentWrittenWithoutValuesFailsToRead() throws Exception {
      // the state an older version wrote for a fragment swapped out while it was serialized
      XIntFragment fragment = new XIntFragment(values());
      swap(fragment);
      setField(fragment, "valid", true);
      byte[] bytes = serialize(fragment);
      setField(fragment, "valid", false);

      assertThrows(InvalidObjectException.class, () -> deserialize(bytes));
      assertEquals(1005, fragment.getSafely(5));
      fragment.dispose();
   }

   @Test
   void fragmentSwappedWhileSerializedIsWrittenWithValues() throws Exception {
      XIntFragment fragment = new XIntFragment(values());
      AtomicBoolean stop = new AtomicBoolean();
      List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
      Thread swapper = new Thread(() -> {
         try {
            while(!stop.get()) {
               fragment.swap();
               fragment.getSafely(0);
            }
         }
         catch(Throwable ex) {
            errors.add(ex);
         }
      }, "XFragmentSerializationTest-swap");
      swapper.start();

      try {
         long end = System.currentTimeMillis() + 2000;

         while(System.currentTimeMillis() < end) {
            XIntFragment copy = (XIntFragment) deserialize(serialize(fragment));
            assertEquals(1005, copy.getSafely(5));
            assertEquals(1999, copy.getSafely(999));
            copy.dispose();
         }
      }
      finally {
         stop.set(true);
         swapper.join(10000);
      }

      assertFalse(swapper.isAlive());
      assertTrue(errors.isEmpty(), () -> "swap thread failed: " + errors);
      fragment.dispose();
   }

   private static int[] values() {
      int[] values = new int[1000];

      for(int i = 0; i < values.length; i++) {
         values[i] = i + 1000;
      }

      return values;
   }

   private static XObjectFragment<String> objectFragment() {
      XObjectFragment<String> fragment = new XObjectFragment<>((char) 100, (char) 100, String.class);

      for(int i = 0; i < 100; i++) {
         fragment.add("v" + i);
      }

      fragment.complete();
      return fragment;
   }

   /**
    * Swap a fragment out. The swapper of the test context may have swapped it already.
    */
   private static void swap(XSwappable fragment) {
      fragment.swap();
      assertFalse(fragment.isValid(), "fragment is still in memory");
   }

   private static File intSwapFile(XIntFragment fragment) {
      File file = fragment.getFile(fragment.prefix + ".tdat");
      assertTrue(file.exists(), "swap file was not written");
      return file;
   }

   private static XIntFragment[] fragments(XSwappableIntList list, int count) throws Exception {
      XIntFragment[] fragments = (XIntFragment[]) getField(list, "fragments");
      return Arrays.copyOf(fragments, count);
   }

   @SuppressWarnings("unchecked")
   private static XObjectFragment<String>[] fragments(XSwappableObjectList<String> list, int count)
      throws Exception
   {
      XObjectFragment<String>[] fragments = (XObjectFragment<String>[]) getField(list, "fragments");
      return Arrays.copyOf(fragments, count);
   }

   private static Object getField(Object obj, String name) throws Exception {
      Field field = obj.getClass().getDeclaredField(name);
      field.setAccessible(true);
      return field.get(obj);
   }

   private static void setField(Object obj, String name, Object value) throws Exception {
      Field field = obj.getClass().getDeclaredField(name);
      field.setAccessible(true);
      field.set(obj, value);
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
    * Run an action while the swap files are moved out of the cache directory.
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
}
