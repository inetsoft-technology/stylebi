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
package inetsoft.util.swap;

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77636, XSwappableIntList.size(int) and XSwappableObjectList.size(int) did not cut a tail
 * fragment that was swapped out, and a fragment that was read back reused its old swap file on
 * the next swap, so values added after the cut, or changed by set(), were lost.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class XSwappableListTruncateTest {
   @Test
   void intListCutsSwappedOutTail() throws Exception {
      XSwappableIntList list = createIntList();
      XIntFragment tail = fragment(list, 2);
      assertTrue(tail.swap(), "tail was not swapped");

      list.size(INT_CUT);
      list.add(777777);

      assertEquals(INT_CUT + 1, list.size());
      assertEquals(INT_CUT - 1, list.get(INT_CUT - 1));
      assertEquals(777777, list.get(INT_CUT));
      list.dispose();
   }

   @Test
   void intListCutAfterSwapInIsWrittenOnNextSwap() throws Exception {
      XSwappableIntList list = createIntList();
      assertTrue(fragment(list, 2).swap(), "tail was not swapped");
      assertEquals(INT_COUNT - 1, list.get(INT_COUNT - 1));

      list.size(INT_CUT);
      list.add(777777);
      list.complete();
      XIntFragment tail = fragment(list, 2);
      // a no-op once complete() completes the new tail
      tail.complete();
      assertTrue(tail.swap(), "tail was not swapped again");

      assertEquals(INT_CUT + 1, list.size());
      assertEquals(INT_CUT - 1, list.get(INT_CUT - 1));
      assertEquals(777777, list.get(INT_CUT));
      assertEquals(INT_CUT + 1 - 2 * 32768, fragment(list, 2).available());
      assertTrue(list.isCompleted());
      list.dispose();
   }

   @Test
   void objectListCutsSwappedOutTail() throws Exception {
      XSwappableObjectList<String> list = createObjectList();
      XObjectFragment<?> tail = fragment(list, 2);
      assertTrue(tail.swap(), "tail was not swapped");

      list.size(OBJ_CUT);
      list.add("NEW");

      assertEquals(OBJ_CUT + 1, list.size());
      assertEquals("v" + (OBJ_CUT - 1), list.get(OBJ_CUT - 1));
      assertEquals("NEW", list.get(OBJ_CUT));
      list.dispose();
   }

   @Test
   void objectListCutAfterSwapInIsWrittenOnNextSwap() throws Exception {
      XSwappableObjectList<String> list = createObjectList();
      assertTrue(fragment(list, 2).swap(), "tail was not swapped");
      assertEquals("v" + (OBJ_COUNT - 1), list.get(OBJ_COUNT - 1));

      list.size(OBJ_CUT);
      list.add("NEW");
      list.complete();
      XObjectFragment<?> tail = fragment(list, 2);
      // a no-op once complete() completes the new tail
      tail.complete();
      assertTrue(tail.swap(), "tail was not swapped again");

      assertEquals(OBJ_CUT + 1, list.size());
      assertEquals("v" + (OBJ_CUT - 1), list.get(OBJ_CUT - 1));
      assertEquals("NEW", list.get(OBJ_CUT));
      assertEquals(OBJ_CUT + 1 - 2 * 8192, fragment(list, 2).available());
      assertTrue(list.isCompleted());
      list.dispose();
   }

   @Test
   void objectListSetAfterSwapInIsWrittenOnNextSwap() throws Exception {
      XSwappableObjectList<String> list = createObjectList();
      XObjectFragment<?> fragment = fragment(list, 1);
      assertTrue(fragment.swap(), "fragment was not swapped");
      assertEquals("v8200", list.get(8200));

      list.set(8200, "NEW");
      assertTrue(fragment.swap(), "fragment was not swapped again");
      assertEquals("NEW", list.get(8200));
      assertEquals("v8201", list.get(8201));

      // and again, after the changed values were written to new swap files
      list.set(8201, "NEW2");
      assertTrue(fragment.swap(), "fragment was not swapped a third time");
      assertEquals("NEW", list.get(8200));
      assertEquals("NEW2", list.get(8201));
      assertEquals("v8202", list.get(8202));
      list.dispose();
   }

   @Test
   void intListCutAtFragmentBoundaryRemovesOldSwapFiles() throws Exception {
      XSwappableIntList list = createIntList();
      File[] files = new File[3];

      for(int i = 0; i < 3; i++) {
         XIntFragment fragment = fragment(list, i);
         assertTrue(fragment.swap(), "fragment " + i + " was not swapped");
         files[i] = fragment.getFile(fragment.prefix + ".tdat");
         assertTrue(files[i].exists());
      }

      list.size(32768);

      for(int i = 0; i < 3; i++) {
         assertFalse(files[i].exists(), "swap file " + i + " was left behind");
      }

      list.add(-1);
      list.complete();
      assertTrue(fragment(list, 0).swap(), "first fragment was not swapped again");
      assertTrue(fragment(list, 1).swap(), "new tail was not swapped");

      assertEquals(32769, list.size());
      assertEquals(32767, list.get(32767));
      assertEquals(-1, list.get(32768));
      list.dispose();
   }

   @Test
   void intListCutInsideEarlierFragment() throws Exception {
      XSwappableIntList list = createIntList();
      assertTrue(fragment(list, 1).swap(), "fragment 1 was not swapped");
      assertTrue(fragment(list, 2).swap(), "fragment 2 was not swapped");

      list.size(32768 + 100);
      list.add(777777);

      // add past the next fragment boundary
      for(int i = 32768 + 101; i < INT_COUNT; i++) {
         list.add(i);
      }

      list.complete();
      assertTrue(fragment(list, 1).swap(), "fragment 1 was not swapped again");
      assertTrue(fragment(list, 2).swap(), "new tail was not swapped");

      assertEquals(INT_COUNT, list.size());
      assertEquals(32768 + 99, list.get(32768 + 99));
      assertEquals(777777, list.get(32768 + 100));
      assertEquals(32768 + 101, list.get(32768 + 101));
      assertEquals(INT_COUNT - 1, list.get(INT_COUNT - 1));
      list.dispose();
   }

   @Test
   void intListCutToZero() throws Exception {
      XSwappableIntList list = createIntList();
      assertTrue(fragment(list, 2).swap(), "tail was not swapped");

      list.size(0);
      list.add(5);
      list.add(6);
      list.complete();
      assertTrue(fragment(list, 0).swap(), "new fragment was not swapped");

      assertEquals(2, list.size());
      assertEquals(5, list.get(0));
      assertEquals(6, list.get(1));
      list.dispose();
   }

   @Test
   void objectFragmentChangeWhileSwappedOutKeepsValues() throws Exception {
      XSwappableObjectList<String> list = createObjectList();
      XObjectFragment<?> fragment = fragment(list, 1);
      assertTrue(fragment.swap(), "fragment was not swapped");
      File file = fragment.getFile(fragment.prefix + "_0.tdat");
      assertTrue(file.exists());

      // change() reads the values back before it removes the swap files
      fragment.change();
      assertFalse(file.exists(), "stale swap file was left behind");

      for(int i = 8192; i < 2 * 8192; i++) {
         assertEquals("v" + i, list.get(i));
      }

      assertTrue(fragment.swap(), "fragment was not swapped again");
      assertTrue(file.exists(), "values were not written on the next swap");

      for(int i = 8192; i < 2 * 8192; i++) {
         assertEquals("v" + i, list.get(i));
      }

      list.dispose();
   }

   private static XSwappableIntList createIntList() {
      XSwappableIntList list = new XSwappableIntList();

      for(int i = 0; i < INT_COUNT; i++) {
         list.add(i);
      }

      list.complete();
      return list;
   }

   private static XSwappableObjectList<String> createObjectList() {
      XSwappableObjectList<String> list = new XSwappableObjectList<>(null);

      for(int i = 0; i < OBJ_COUNT; i++) {
         list.add("v" + i);
      }

      list.complete();
      return list;
   }

   private static XIntFragment fragment(XSwappableIntList list, int idx) throws Exception {
      return ((XIntFragment[]) fragments(XSwappableIntList.class, list))[idx];
   }

   private static XObjectFragment<?> fragment(XSwappableObjectList<?> list, int idx)
      throws Exception
   {
      return ((XObjectFragment<?>[]) fragments(XSwappableObjectList.class, list))[idx];
   }

   private static Object fragments(Class<?> cls, Object list) throws Exception {
      Field field = cls.getDeclaredField("fragments");
      field.setAccessible(true);
      return field.get(list);
   }

   // three int fragments of 32768 values, the last one holds 10
   private static final int INT_COUNT = 2 * 32768 + 10;
   private static final int INT_CUT = INT_COUNT - 5;
   // three object fragments of 8192 values, the last one holds 10
   private static final int OBJ_COUNT = 2 * 8192 + 10;
   private static final int OBJ_CUT = OBJ_COUNT - 8;
}
