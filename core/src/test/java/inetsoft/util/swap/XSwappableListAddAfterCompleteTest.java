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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77655, XIntFragment.add() and XObjectFragment.add() silently dropped a value added to a
 * completed tail fragment that was swapped out, while the list still counted it. A tail that was
 * swapped and read back lost the value on the next swap, which reused the old swap file.
 *
 * <p>Bug #77685, that guard is in the tail fragment, so an add to a completed list whose size is
 * 0 or an exact multiple of the fragment size opened a new tail fragment and was accepted. The
 * new tail was never completed (never swappable) and accepted the rest of its block too.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome()
@Tag("core")
class XSwappableListAddAfterCompleteTest {
   @Test
   void intListAddAfterCompleteOnSwappedTailFails() throws Exception {
      XSwappableIntList list = createIntList();
      assertTrue(fragment(list, 2).swap(), "tail was not swapped");

      assertThrows(IllegalStateException.class, () -> list.add(777777));

      assertEquals(INT_COUNT, list.size());
      assertEquals(INT_COUNT - 1, list.get(INT_COUNT - 1));
      list.dispose();
   }

   @Test
   void intListAddAfterCompleteOnReadBackTailFails() throws Exception {
      XSwappableIntList list = createIntList();
      XIntFragment tail = fragment(list, 2);
      assertTrue(tail.swap(), "tail was not swapped");
      assertEquals(INT_COUNT - 1, list.get(INT_COUNT - 1));

      assertThrows(IllegalStateException.class, () -> list.add(777777));
      assertTrue(tail.swap(), "tail was not swapped again");

      assertEquals(INT_COUNT, list.size());
      assertEquals(INT_COUNT - 1, list.get(INT_COUNT - 1));
      list.dispose();
   }

   @Test
   void objectListAddAfterCompleteOnSwappedTailFails() throws Exception {
      XSwappableObjectList<String> list = createObjectList();
      assertTrue(fragment(list, 2).swap(), "tail was not swapped");

      assertThrows(IllegalStateException.class, () -> list.add("NEW"));

      assertEquals(OBJ_COUNT, list.size());
      assertEquals("v" + (OBJ_COUNT - 1), list.get(OBJ_COUNT - 1));
      list.dispose();
   }

   @Test
   void objectListAddAfterCompleteOnReadBackTailFails() throws Exception {
      XSwappableObjectList<String> list = createObjectList();
      XObjectFragment<?> tail = fragment(list, 2);
      assertTrue(tail.swap(), "tail was not swapped");
      assertEquals("v" + (OBJ_COUNT - 1), list.get(OBJ_COUNT - 1));

      assertThrows(IllegalStateException.class, () -> list.add("NEW"));
      assertTrue(tail.swap(), "tail was not swapped again");

      assertEquals(OBJ_COUNT, list.size());
      assertEquals("v" + (OBJ_COUNT - 1), list.get(OBJ_COUNT - 1));
      list.dispose();
   }

   @Test
   void addBeforeCompleteKeepsAllValues() throws Exception {
      XSwappableIntList ints = new XSwappableIntList();
      XSwappableObjectList<String> objs = new XSwappableObjectList<>(null);

      for(int i = 0; i < INT_COUNT; i++) {
         ints.add(i);
      }

      for(int i = 0; i < OBJ_COUNT; i++) {
         objs.add("v" + i);
      }

      // the full fragments are completed by add() and can be swapped while the list grows
      assertTrue(fragment(ints, 0).swap(), "int fragment was not swapped");
      assertTrue(fragment(objs, 0).swap(), "object fragment was not swapped");
      ints.add(777777);
      objs.add("NEW");
      ints.complete();
      objs.complete();
      assertTrue(fragment(ints, 2).swap(), "int tail was not swapped");
      assertTrue(fragment(objs, 2).swap(), "object tail was not swapped");

      assertEquals(INT_COUNT + 1, ints.size());
      assertEquals(0, ints.get(0));
      assertEquals(INT_COUNT - 1, ints.get(INT_COUNT - 1));
      assertEquals(777777, ints.get(INT_COUNT));
      assertEquals(OBJ_COUNT + 1, objs.size());
      assertEquals("v0", objs.get(0));
      assertEquals("v" + (OBJ_COUNT - 1), objs.get(OBJ_COUNT - 1));
      assertEquals("NEW", objs.get(OBJ_COUNT));
      ints.dispose();
      objs.dispose();
   }

   @Test
   void addAfterDisposeIsIgnored() {
      XSwappableIntList ints = createIntList();
      XSwappableObjectList<String> objs = createObjectList();
      ints.dispose();
      objs.dispose();

      assertDoesNotThrow(() -> ints.add(777777));
      assertDoesNotThrow(() -> objs.add("NEW"));
   }

   // the int list holds 32768 values per fragment: empty, mid-block and an exact boundary
   @ParameterizedTest
   @ValueSource(ints = { 0, 10, 2 * 32768 })
   void intListAddAfterCompleteFails(int count) throws Exception {
      XSwappableIntList list = new XSwappableIntList();

      for(int i = 0; i < count; i++) {
         list.add(i);
      }

      list.complete();

      IllegalStateException ex =
         assertThrows(IllegalStateException.class, () -> list.add(777777));
      assertTrue(ex.getMessage().contains("completed swappable list"), ex.getMessage());
      // a second add is refused as well, no open tail fragment was left behind
      assertThrows(IllegalStateException.class, () -> list.add(777778));

      assertEquals(count, list.size());
      assertTrue(list.isCompleted());
      assertNull(fragment(list, (count + 32767) / 32768), "a new tail fragment was opened");

      if(count > 0) {
         assertEquals(count - 1, list.get(count - 1));
      }

      list.dispose();
   }

   // the object list holds 8192 values per fragment: empty, mid-block and an exact boundary
   @ParameterizedTest
   @ValueSource(ints = { 0, 10, 2 * 8192 })
   void objectListAddAfterCompleteFails(int count) throws Exception {
      XSwappableObjectList<String> list = new XSwappableObjectList<>(null);

      for(int i = 0; i < count; i++) {
         list.add("v" + i);
      }

      list.complete();

      IllegalStateException ex =
         assertThrows(IllegalStateException.class, () -> list.add("NEW"));
      assertTrue(ex.getMessage().contains("completed swappable list"), ex.getMessage());
      assertThrows(IllegalStateException.class, () -> list.addAll(List.of("A", "B")));

      assertEquals(count, list.size());
      assertTrue(list.isCompleted());
      assertNull(fragment(list, (count + 8191) / 8192), "a new tail fragment was opened");

      if(count > 0) {
         assertEquals("v" + (count - 1), list.get(count - 1));
      }

      list.dispose();
   }

   @Test
   void addAfterDisposeOnBoundaryIsIgnored() {
      XSwappableIntList ints = new XSwappableIntList();
      XSwappableObjectList<String> objs = new XSwappableObjectList<>(null);

      for(int i = 0; i < 2 * 32768; i++) {
         ints.add(i);
      }

      for(int i = 0; i < 2 * 8192; i++) {
         objs.add("v" + i);
      }

      ints.complete();
      objs.complete();
      ints.dispose();
      objs.dispose();

      // the add is ignored, it neither throws nor reports an index
      assertEquals(-1, assertDoesNotThrow(() -> ints.add(777777)));
      assertEquals(-1, assertDoesNotThrow(() -> objs.add("NEW")));
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
   // three object fragments of 8192 values, the last one holds 10
   private static final int OBJ_COUNT = 2 * 8192 + 10;
}
