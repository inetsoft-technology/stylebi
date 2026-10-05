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
import inetsoft.util.FileSystemService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.invocation.Invocation;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77676, XObjectFragment.change() looked for swap files on every
 * XSwappableObjectList.set(), even for a fragment that was never swapped. Building the file
 * path and the stat made each set() ~70x slower, which made grouping in SummaryFilter ~10x
 * slower. The swap files must still be removed when the fragment may have them (#77636).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class XObjectFragmentChangeTest {
   @Test
   void setOnNeverSwappedListDoesNotAccessFiles() throws Exception {
      XSwappableObjectList<String> list = createObjectList();
      clearInvocations(fileSystemService);

      for(int i = 0; i < OBJ_COUNT; i++) {
         list.set(i, "NEW" + i);
      }

      for(int i = 0; i < 3; i++) {
         assertEquals(0, swapFileLookups(fragment(list, i)),
                      "set() looked for the swap files of a fragment that was never swapped");
      }

      assertEquals("NEW8200", list.get(8200));
      list.dispose();
   }

   @Test
   void setAfterSwapInIsWrittenOnNextSwap() throws Exception {
      XSwappableObjectList<String> list = createObjectList();
      XObjectFragment<?> fragment = fragment(list, 1);
      assertTrue(fragment.swap(), "fragment was not swapped");
      assertEquals("v8200", list.get(8200));

      list.set(8200, "NEW");
      assertFalse(swapFile(fragment).exists(), "swap file was kept after set()");

      // the files are gone, so the following set() calls have nothing to look for
      clearInvocations(fileSystemService);
      list.set(8201, "NEW1");
      assertEquals(0, swapFileLookups(fragment), "set() looked for deleted swap files");

      assertTrue(fragment.swap(), "fragment was not swapped again");
      assertEquals("NEW", list.get(8200));
      assertEquals("NEW1", list.get(8201));

      // and again, after the changed values were written to new swap files
      list.set(8202, "NEW2");
      assertTrue(fragment.swap(), "fragment was not swapped a third time");
      assertEquals("NEW", list.get(8200));
      assertEquals("NEW1", list.get(8201));
      assertEquals("NEW2", list.get(8202));
      assertEquals("v8203", list.get(8203));
      list.dispose();
   }

   @Test
   void setAfterSwapReusingFileIsWrittenOnNextSwap() throws Exception {
      XSwappableObjectList<String> list = createObjectList();
      XObjectFragment<?> fragment = fragment(list, 1);
      assertTrue(fragment.swap(), "fragment was not swapped");
      assertEquals("v8200", list.get(8200));
      // no change, so this swap reuses the swap file
      assertTrue(fragment.swap(), "fragment was not swapped again");
      assertTrue(swapFile(fragment).exists());
      assertEquals("v8200", list.get(8200));

      list.set(8200, "NEW");
      assertTrue(fragment.swap(), "fragment was not swapped a third time");
      assertEquals("NEW", list.get(8200));
      list.dispose();
   }

   @Test
   void setAfterFailedSwapLeftPartialFileIsWrittenOnNextSwap() throws Exception {
      XObjectFragment<String> fragment = createLargeObjectFragment();
      File file = swapFile(fragment);
      File file1 = fragment.getFile(fragment.prefix + "_1.tdat");
      // a directory in place of the second swap file makes the swap fail after the first one
      assertTrue(file1.mkdir(), "second swap file was not blocked");

      try {
         assertTrue(fragment.swap(), "fragment was not swapped");
         assertTrue(file.exists(), "first swap file was not written");
         assertEquals(0, swapFileCount(fragment), "partial swap counted its swap files");
      }
      finally {
         assertTrue(file1.delete());
      }

      set(fragment, 50, "NEW");
      assertFalse(file.exists(), "partial swap file was kept after set()");

      assertTrue(fragment.swap(), "fragment was not swapped again");
      assertEquals("NEW", fragment.getSafely(50));
      assertEquals(largeValue(99), fragment.getSafely(99));
      fragment.dispose();
   }

   @Test
   void setAfterFailedDeleteIsWrittenOnNextSwap() throws Exception {
      // the delayed removal would delete whatever is at the path later, including a new file
      doNothing().when(fileSystemService).remove(any(File.class), anyInt());
      XSwappableObjectList<String> list = createObjectList();

      try {
         XObjectFragment<?> fragment = fragment(list, 1);
         File file = swapFile(fragment);
         assertTrue(fragment.swap(), "fragment was not swapped");
         assertEquals("v8200", list.get(8200));

         withReadOnlyDirectory(file.getParentFile(), () -> list.set(8200, "NEW"));
         assertTrue(file.exists(), "swap file was deleted from a read-only directory");
         verify(fileSystemService).remove(eq(file), anyInt());

         // the file is still there, so this set() must delete it
         list.set(8201, "NEW2");
         assertFalse(file.exists(), "swap file was kept after set()");

         assertTrue(fragment.swap(), "fragment was not swapped again");
         assertEquals("NEW", list.get(8200));
         assertEquals("NEW2", list.get(8201));
      }
      finally {
         list.dispose();
      }
   }

   @Test
   void setWhileFragmentsAreSwappedKeepsEveryValue() throws Exception {
      XSwappableObjectList<String> list = createObjectList();
      XObjectFragment<?> fragment0 = fragment(list, 0);
      XObjectFragment<?> fragment1 = fragment(list, 1);
      String[] expected = new String[OBJ_COUNT];
      AtomicBoolean stop = new AtomicBoolean();
      AtomicInteger swaps = new AtomicInteger();
      AtomicReference<Throwable> error = new AtomicReference<>();

      for(int i = 0; i < OBJ_COUNT; i++) {
         expected[i] = "v" + i;
      }

      // swap the fragments out while set() runs, reading one back now and then so the
      // swap reusing the swap file is taken as well
      Thread swapper = new Thread(() -> {
         try {
            while(!stop.get()) {
               if(fragment0.swap()) {
                  swaps.incrementAndGet();
               }

               if(fragment1.swap()) {
                  swaps.incrementAndGet();
               }

               if((swaps.get() & 3) == 0) {
                  fragment1.getSafely(5);
               }
            }
         }
         catch(Throwable ex) {
            error.set(ex);
         }
      });
      swapper.start();
      Random random = new Random(7);
      long deadline = System.currentTimeMillis() + 30000;

      try {
         // keep setting until the fragments were swapped a number of times
         for(int i = 0; i < 2000 || swaps.get() < 50 && System.currentTimeMillis() < deadline;
             i++)
         {
            int idx = random.nextInt(2 * 8192);
            list.set(idx, "NEW" + i);
            expected[idx] = "NEW" + i;
         }
      }
      finally {
         stop.set(true);
         swapper.join();
      }

      assertNull(error.get(), "swap failed");
      assertTrue(swaps.get() >= 50, "fragments were not swapped");
      // read the values back from the swap files, a fragment the swapper left swapped out
      // is not swapped again
      fragment0.swap();
      fragment1.swap();

      for(int i = 0; i < OBJ_COUNT; i++) {
         assertEquals(expected[i], list.get(i), "value at " + i);
      }

      list.dispose();
   }

   /**
    * Count the swap file paths of the fragment built since the invocations were cleared.
    */
   private long swapFileLookups(XObjectFragment<?> fragment) {
      String prefix = fragment.prefix + "_";

      return mockingDetails(fileSystemService).getInvocations().stream()
         .filter(i -> "getCacheFile".equals(i.getMethod().getName()))
         .map(Invocation::getArguments)
         .filter(args -> args[0] instanceof String name && name.startsWith(prefix))
         .count();
   }

   /**
    * Set a value the way XSwappableObjectList.set() does.
    */
   private static void set(XObjectFragment<?> fragment, int r, Object obj) {
      synchronized(fragment) {
         fragment.access();
         fragment.change();
         fragment.set(r, obj);
      }
   }

   private static XSwappableObjectList<String> createObjectList() {
      XSwappableObjectList<String> list = new XSwappableObjectList<>(null);

      for(int i = 0; i < OBJ_COUNT; i++) {
         list.add("v" + i);
      }

      list.complete();
      return list;
   }

   /**
    * Create a fragment too large for one swap file block.
    */
   private static XObjectFragment<String> createLargeObjectFragment() {
      XObjectFragment<String> fragment = new XObjectFragment<>((char) 100, (char) 100, String.class);

      for(int i = 0; i < 100; i++) {
         fragment.add(largeValue(i));
      }

      fragment.complete();
      return fragment;
   }

   private static String largeValue(int i) {
      // random letters, so the block is not compressed below the swap buffer size
      Random random = new Random(i);
      StringBuilder value = new StringBuilder().append(i).append(':');

      for(int k = 0; k < 6000; k++) {
         value.append((char) ('a' + random.nextInt(26)));
      }

      return value.toString();
   }

   private static File swapFile(XObjectFragment<?> fragment) {
      return fragment.getFile(fragment.prefix + "_0.tdat");
   }

   private static void withReadOnlyDirectory(File dir, Runnable action) {
      // windows does not change the permissions of a directory
      assumeTrue(dir.setWritable(false), "directory write permission is not supported");

      try {
         // root creates and deletes files in it anyway
         assumeFalse(canCreate(new File(dir, "probe-" + UUID.randomUUID() + ".tmp")),
                     "cache directory is still writable");
         action.run();
      }
      finally {
         // restore without an assert that could replace the test's own failure
         dir.setWritable(true);
      }
   }

   private static boolean canCreate(File file) {
      try {
         boolean created = file.createNewFile();
         file.delete();
         return created;
      }
      catch(IOException ex) {
         return false;
      }
   }

   private static int swapFileCount(XObjectFragment<?> fragment) throws Exception {
      Field field = XObjectFragment.class.getDeclaredField("swapFileCount");
      field.setAccessible(true);
      return field.getInt(fragment);
   }

   private static XObjectFragment<?> fragment(XSwappableObjectList<?> list, int idx)
      throws Exception
   {
      Field field = XSwappableObjectList.class.getDeclaredField("fragments");
      field.setAccessible(true);
      return ((XObjectFragment<?>[]) field.get(list))[idx];
   }

   @MockitoSpyBean
   private FileSystemService fileSystemService;

   // three object fragments of 8192 values, the last one holds 10
   private static final int OBJ_COUNT = 2 * 8192 + 10;
}
