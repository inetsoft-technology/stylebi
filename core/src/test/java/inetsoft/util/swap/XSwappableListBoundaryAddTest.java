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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77685, an add to a completed list is refused at every size, not only mid-block: on an
 * empty list, on and around each fragment boundary (32768 ints / 8192 objects). A disposed list
 * ignores the add at every size. A list that isn't completed, or was reopened by size(int), still
 * accepts adds across the boundaries.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome()
@Tag("core")
class XSwappableListBoundaryAddTest {
   @ParameterizedTest
   @ValueSource(ints = { 0, 1, INT_BLOCK - 1, INT_BLOCK, INT_BLOCK + 1, 2 * INT_BLOCK,
                         3 * INT_BLOCK, 3 * INT_BLOCK + 12345 })
   void intListRefusesAddAfterComplete(int count) {
      XSwappableIntList list = new XSwappableIntList();

      for(int i = 0; i < count; i++) {
         list.add(i);
      }

      list.complete();

      for(int k = 0; k < 3; k++) {
         IllegalStateException ex =
            assertThrows(IllegalStateException.class, () -> list.add(-1));
         assertEquals("Cannot add a value to a completed swappable list: size=" + count,
                      ex.getMessage());
         assertEquals(count, list.size());
      }

      assertTrue(list.isCompleted());

      for(int i = 0; i < count; i++) {
         assertEquals(i, list.get(i));
      }

      list.dispose();
   }

   @ParameterizedTest
   @ValueSource(ints = { 0, 1, OBJ_BLOCK - 1, OBJ_BLOCK, OBJ_BLOCK + 1, 2 * OBJ_BLOCK,
                         3 * OBJ_BLOCK, 3 * OBJ_BLOCK + 1234 })
   void objectListRefusesAddAfterComplete(int count) {
      XSwappableObjectList<String> list = new XSwappableObjectList<>(null);

      for(int i = 0; i < count; i++) {
         list.add("v" + i);
      }

      list.complete();

      for(int k = 0; k < 3; k++) {
         IllegalStateException ex =
            assertThrows(IllegalStateException.class, () -> list.add("NEW"));
         assertEquals("Cannot add a value to a completed swappable list: size=" + count,
                      ex.getMessage());
         assertEquals(count, list.size());
      }

      assertThrows(IllegalStateException.class, () -> list.addAll(List.of("A")));
      assertEquals(count, list.size());
      assertTrue(list.isCompleted());

      int n = 0;

      for(Iterator<String> it = list.iterator(); it.hasNext(); n++) {
         assertEquals("v" + n, it.next());
      }

      assertEquals(count, n);
      list.dispose();
   }

   @ParameterizedTest
   @ValueSource(ints = { 0, 10, INT_BLOCK, 2 * INT_BLOCK })
   void disposedIntListIgnoresAdd(int count) {
      XSwappableIntList list = new XSwappableIntList();

      for(int i = 0; i < count; i++) {
         list.add(i);
      }

      list.complete();
      list.dispose();

      assertEquals(-1, assertDoesNotThrow(() -> list.add(5)));
      assertEquals(-1, assertDoesNotThrow(() -> list.add(6)));
      assertEquals(count, list.size());
   }

   @ParameterizedTest
   @ValueSource(ints = { 0, 10, OBJ_BLOCK, 2 * OBJ_BLOCK })
   void disposedObjectListIgnoresAdd(int count) {
      XSwappableObjectList<String> list = new XSwappableObjectList<>(null);

      for(int i = 0; i < count; i++) {
         list.add("v" + i);
      }

      // a list disposed without being completed ignores the add too
      if(count != 10) {
         list.complete();
      }

      list.dispose();

      assertEquals(-1, assertDoesNotThrow(() -> list.add("NEW")));
      assertDoesNotThrow(() -> list.addAll(List.of("A", "B")));
      assertEquals(count, list.size());
   }

   @ParameterizedTest
   @ValueSource(ints = { 0, INT_BLOCK, 2 * INT_BLOCK })
   void truncatedIntListAcceptsAddsAcrossBoundary(int cut) {
      XSwappableIntList list = new XSwappableIntList();
      int count = 3 * INT_BLOCK + 5;

      for(int i = 0; i < count; i++) {
         assertEquals(i, list.add(i));
      }

      list.complete();
      list.size(cut);
      assertFalse(list.isCompleted());

      for(int i = cut; i < count; i++) {
         assertEquals(i, list.add(i + 1000000));
      }

      list.complete();
      assertThrows(IllegalStateException.class, () -> list.add(-1));
      assertEquals(count, list.size());

      for(int i = 0; i < count; i++) {
         assertEquals(i < cut ? i : i + 1000000, list.get(i));
      }

      list.dispose();
   }

   @ParameterizedTest
   @ValueSource(ints = { 0, OBJ_BLOCK, 2 * OBJ_BLOCK })
   void truncatedObjectListAcceptsAddsAcrossBoundary(int cut) {
      XSwappableObjectList<String> list = new XSwappableObjectList<>(null);
      int count = 3 * OBJ_BLOCK + 5;

      for(int i = 0; i < count; i++) {
         assertEquals(i, list.add("v" + i));
      }

      list.complete();
      list.size(cut);
      assertFalse(list.isCompleted());

      for(int i = cut; i < count; i++) {
         assertEquals(i, list.add("w" + i));
      }

      list.complete();
      assertThrows(IllegalStateException.class, () -> list.add("X"));
      assertEquals(count, list.size());

      for(int i = 0; i < count; i++) {
         assertEquals((i < cut ? "v" : "w") + i, list.get(i));
      }

      list.dispose();
   }

   private static final int INT_BLOCK = 32768;
   private static final int OBJ_BLOCK = 8192;
}
