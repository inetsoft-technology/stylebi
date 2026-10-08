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

import inetsoft.mv.data.BitDimIndex;
import inetsoft.report.composition.RuntimeSheet;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.table.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78043, swap files were looked up in the current cache directory on every read and
 * delete, so after <tt>replet.cache.directory</tt> was changed at runtime the data swapped
 * before the change could no longer be read, an undo checkpoint read back as null, a table
 * fragment was taken as lost, dispose() left the old files behind, and a fragment changed
 * after the change and read after a change back silently returned its old values.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SwapCacheDirectoryChangeTest {
   @BeforeEach
   void setUp(@TempDir Path tempDir) {
      savedCacheDir = SreeEnv.getProperty(CACHE_DIR);
      dirA = tempDir.resolve("a").toFile();
      dirB = tempDir.resolve("b").toFile();
      SreeEnv.setProperty(CACHE_DIR, dirA.getPath());
   }

   @AfterEach
   void tearDown() {
      if(savedCacheDir == null) {
         SreeEnv.remove(CACHE_DIR);
      }
      else {
         SreeEnv.setProperty(CACHE_DIR, savedCacheDir);
      }
   }

   @Test
   void intFragmentIsReadAfterDirectoryChange() {
      XIntFragment fragment = new XIntFragment(createValues());

      try {
         assertTrue(fragment.swap(), "fragment was not swapped");
         File file = new File(dirA, fragment.prefix + ".tdat");
         assertTrue(file.exists(), "swap file was not written to the first directory");

         changeDirectory(dirB);
         assertEquals(1005, fragment.getSafely(5));
         assertEquals(1099, fragment.getSafely(99));
         assertFalse(new File(dirB, fragment.prefix + ".tdat").exists());
      }
      finally {
         fragment.dispose();
      }
   }

   @Test
   void objectFragmentIsReadAfterDirectoryChange() {
      // too large for one swap file block, so it is written to several files
      XObjectFragment<String> fragment = new XObjectFragment<>((char) 100, (char) 100, String.class);

      for(int i = 0; i < 100; i++) {
         fragment.add(largeValue(i));
      }

      fragment.complete();
      XSwapper.getSwapper().deregister(fragment);

      try {
         assertTrue(fragment.swap(), "fragment was not swapped");
         assertTrue(new File(dirA, fragment.prefix + "_1.tdat").exists(),
                    "fragment was not written to several swap files");

         changeDirectory(dirB);
         assertEquals(largeValue(0), fragment.getSafely(0));
         assertEquals(largeValue(99), fragment.getSafely(99));
      }
      finally {
         fragment.dispose();
      }
   }

   @Test
   void objectListChangedAfterDirectoryChangeIsNotReadStaleAfterRevert() throws Exception {
      XSwappableObjectList<String> list = new XSwappableObjectList<>(null);

      for(int i = 0; i < 2 * 8192 + 10; i++) {
         list.add("v" + i);
      }

      list.complete();
      XObjectFragment<?> fragment = fragment(list, 1);

      try {
         assertTrue(fragment.swap(), "fragment was not swapped");
         assertEquals("v8200", list.get(8200));

         changeDirectory(dirB);
         // must delete the swap file of the old values, or a later read may find it
         list.set(8200, "NEW");
         assertTrue(fragment.swap(), "fragment was not swapped again");

         changeDirectory(dirA);
         assertEquals("NEW", list.get(8200), "old values were read back");
         assertEquals("v8201", list.get(8201));
      }
      finally {
         list.dispose();
      }
   }

   @Test
   void tableFragmentIsSwappedAgainAfterDirectoryChange() {
      XSwappableTable table = new XSwappableTable(2, false);
      table.addRow(new Object[] { "a", "b" });

      for(int i = 0; i < 50; i++) {
         table.addRow(new Object[] { i, "s" + i });
      }

      table.complete();
      XTableFragment fragment = table.getTables()[0];
      XSwapper.getSwapper().deregister(fragment);

      try {
         assertTrue(fragment.swap(false), "fragment was not swapped");
         assertTrue(new File(dirA, fragment.getSwapFile().getName()).exists());

         changeDirectory(dirB);
         // reads one column back, the other one is still swapped out
         assertEquals(4, table.getObject(5, 0));
         assertTrue(fragment.swap(false), "fragment was taken as lost");
         assertEquals("s4", table.getObject(5, 1));
         assertEquals(49, table.getObject(50, 0));
      }
      finally {
         table.dispose();
      }
   }

   @Test
   void bigObjectColumnIsReadAfterDirectoryChange() {
      XBigObjectColumn column = new XBigObjectColumn((char) 4, (char) 16, (char) 400);

      for(int i = 0; i < 400; i++) {
         column.addObject("value" + i);
      }

      column.complete();
      XSwapper.getSwapper().deregister(column);

      try {
         assertTrue(column.swap(), "column was not swapped");

         changeDirectory(dirB);
         assertEquals("value200", column.getObject(200));
         assertEquals("value0", column.getObject(0));
      }
      finally {
         column.dispose();
      }
   }

   @Test
   void runtimeSheetCheckpointIsReadAfterDirectoryChange() throws Exception {
      RuntimeSheet.XSwappableSheet sheet = new RuntimeSheet.XSwappableSheet(new Worksheet(), null);
      sheet.complete();
      XSwapper.getSwapper().deregister(sheet);

      try {
         assertTrue(sheet.swap(), "checkpoint was not swapped");
         assertFalse(sheet.isValid(), "checkpoint is still in memory");

         changeDirectory(dirB);
         Method getXml = RuntimeSheet.XSwappableSheet.class.getDeclaredMethod("getXml");
         getXml.setAccessible(true);
         assertNotNull(getXml.invoke(sheet), "checkpoint XML was lost");

         sheet.access();
         assertInstanceOf(Worksheet.class, sheet.get(), "checkpoint was lost");
      }
      finally {
         sheet.dispose();
      }
   }

   @Test
   void disposeDeletesFilesInOriginalDirectory() {
      XIntFragment fragment = new XIntFragment(createValues());
      BitDimIndex index = new BitDimIndex();
      index.addKey(5, 1);
      index.complete();
      XSwapper.getSwapper().deregister(index);

      assertTrue(fragment.swap(), "fragment was not swapped");
      assertTrue(index.swap(), "index was not swapped");
      File fragmentFile = new File(dirA, fragment.prefix + ".tdat");
      File indexFile = new File(dirA, index.prefix + ".tdat");
      assertTrue(fragmentFile.exists());
      assertTrue(indexFile.exists());

      changeDirectory(dirB);
      assertTrue(index.getRows(5, false).get(1));
      fragment.dispose();
      index.dispose();

      assertFalse(fragmentFile.exists(), "fragment swap file was left behind: " + fragmentFile);
      assertFalse(indexFile.exists(), "index swap file was left behind: " + indexFile);
   }

   @Test
   void newSwappableUsesChangedDirectory() {
      XIntFragment before = new XIntFragment(createValues());
      assertTrue(before.swap(), "fragment was not swapped");
      changeDirectory(dirB);
      XIntFragment after = new XIntFragment(createValues());

      try {
         assertTrue(after.swap(), "fragment was not swapped");
         assertTrue(new File(dirB, after.prefix + ".tdat").exists(),
                    "a new swappable did not use the changed directory");
         assertTrue(new File(dirA, before.prefix + ".tdat").exists());
      }
      finally {
         before.dispose();
         after.dispose();
      }
   }

   private void changeDirectory(File dir) {
      SreeEnv.setProperty(CACHE_DIR, dir.getPath());
   }

   private static int[] createValues() {
      int[] values = new int[100];

      for(int i = 0; i < values.length; i++) {
         values[i] = 1000 + i;
      }

      return values;
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

   private static XObjectFragment<?> fragment(XSwappableObjectList<?> list, int idx)
      throws Exception
   {
      Field field = XSwappableObjectList.class.getDeclaredField("fragments");
      field.setAccessible(true);
      return ((XObjectFragment<?>[]) field.get(list))[idx];
   }

   private String savedCacheDir;
   private File dirA;
   private File dirB;
   private static final String CACHE_DIR = "replet.cache.directory";
}
