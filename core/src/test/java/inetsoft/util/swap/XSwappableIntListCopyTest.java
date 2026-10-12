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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77693, the XSwappableIntList(XSwappableIntList) copy constructor did not chain to this(),
 * so its fragment array was never created and copying a non-empty list threw a
 * NullPointerException on the first add.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class XSwappableIntListCopyTest {
   // one fragment holds 0x8000 values, so this spans three fragments
   private static final int MULTI_FRAGMENT_SIZE = 0x8000 * 2 + 5;

   @Test
   void copyOfSmallListHasSameValues() {
      XSwappableIntList source = createList(10);
      XSwappableIntList copy = new XSwappableIntList(source);

      assertSameValues(source, copy);
      source.dispose();
      copy.dispose();
   }

   @Test
   void copyOfListSpanningSeveralFragmentsHasSameValues() {
      XSwappableIntList source = createList(MULTI_FRAGMENT_SIZE);
      XSwappableIntList copy = new XSwappableIntList(source);

      assertSameValues(source, copy);
      source.dispose();
      copy.dispose();
   }

   @Test
   void copyIsIndependentOfSource() {
      XSwappableIntList source = createList(MULTI_FRAGMENT_SIZE);
      XSwappableIntList copy = new XSwappableIntList(source);

      copy.add(-1);
      copy.complete();

      assertEquals(MULTI_FRAGMENT_SIZE + 1, copy.size());
      assertEquals(-1, copy.get(MULTI_FRAGMENT_SIZE));
      assertEquals(MULTI_FRAGMENT_SIZE, source.size());

      source.dispose();
      assertTrue(source.isDisposed());
      assertFalse(copy.isDisposed());

      for(int i = 0; i < MULTI_FRAGMENT_SIZE; i++) {
         assertEquals(i * 3, copy.get(i), "value at " + i);
      }

      copy.dispose();
   }

   @Test
   void copyOfEmptyListCanBeAddedTo() {
      XSwappableIntList source = createList(0);
      XSwappableIntList copy = new XSwappableIntList(source);

      assertEquals(0, copy.size());
      copy.add(7);
      copy.complete();
      assertEquals(1, copy.size());
      assertEquals(7, copy.get(0));
      source.dispose();
      copy.dispose();
   }

   private static XSwappableIntList createList(int size) {
      XSwappableIntList list = new XSwappableIntList();

      for(int i = 0; i < size; i++) {
         list.add(i * 3);
      }

      list.complete();
      return list;
   }

   private static void assertSameValues(XSwappableIntList expected, XSwappableIntList actual) {
      assertEquals(expected.size(), actual.size());

      for(int i = 0; i < expected.size(); i++) {
         assertEquals(expected.get(i), actual.get(i), "value at " + i);
      }
   }
}
