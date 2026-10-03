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
import inetsoft.uql.XConstants;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77635, SelectionList.getList() read a swapped list back into memory without
 * waitForMemory(). A child list read back while this thread holds the lock of its parent list
 * must not wait, since the swapper may need to swap the parent.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome()
@Tag("core")
class SelectionListSwapInTest {
   @Test
   void swapInWaitsForMemoryBeforeReadingBack() {
      SelectionList list = createSwappedList("v");
      XSwapper swapper = spy(XSwapper.getSwapper());
      List<Boolean> validAtWait = new ArrayList<>();
      doAnswer(invocation -> {
         validAtWait.add(list.isValid());
         return null;
      }).when(swapper).waitForMemory();
      list.swapper = swapper;

      assertEquals("v5", list.getSelectionValue(5).getValue());
      assertEquals(List.of(false), validAtWait,
                   "waitForMemory() must be called once, before the list is read back");
      list.dispose();
   }

   @Test
   void unlockedSwapInWaitsOutsideOfLock() {
      SelectionList list = createSwappedList("v");
      XSwapper swapper = spy(XSwapper.getSwapper());
      List<Boolean> lockedAtWait = new ArrayList<>();
      doAnswer(invocation -> {
         lockedAtWait.add(Thread.holdsLock(list));
         return null;
      }).when(swapper).waitForMemory();
      list.swapper = swapper;

      assertNotNull(list.findValue("v5"));
      assertEquals(List.of(false), lockedAtWait);
      list.dispose();
   }

   @Test
   void childSwapInUnderParentLockDoesNotWait() {
      XSwapper swapper = spy(XSwapper.getSwapper());
      doNothing().when(swapper).waitForMemory();
      SelectionList parent = new SelectionList();
      List<SelectionList> children = new ArrayList<>();
      parent.swapper = swapper;

      for(int i = 0; i < 3; i++) {
         CompositeSelectionValue value = new CompositeSelectionValue("p" + i, "p" + i);
         SelectionList child = createSwappedList("c" + i + "_");
         child.swapper = swapper;
         value.setSelectionList(child);
         parent.addSelectionValue(value);
         children.add(child);
      }

      parent.complete();

      assertEquals(3 + 3 * VALUES, parent.getAllSelectionValues().length);

      for(SelectionList child : children) {
         assertTrue(child.swap(), "child list was not swapped");
      }

      parent.sort(XConstants.SORT_DESC);
      assertNotEquals("c0_0", children.get(0).getSelectionValue(0).getValue(), "child list was not sorted");
      verify(swapper, never()).waitForMemory();

      // a child read back on its own waits
      assertTrue(children.get(0).swap(), "child list was not swapped");
      assertEquals(VALUES, children.get(0).getSelectionValues().length);
      verify(swapper, times(1)).waitForMemory();
      parent.dispose();
      children.forEach(SelectionList::dispose);
   }

   @Test
   void accessOfResidentListDoesNotWait() {
      SelectionList list = createList("v");
      XSwapper swapper = spy(XSwapper.getSwapper());
      list.swapper = swapper;

      assertEquals("v5", list.getSelectionValue(5).getValue());
      verify(swapper, never()).waitForMemory();
      list.dispose();
   }

   private static SelectionList createSwappedList(String prefix) {
      SelectionList list = createList(prefix);
      assertTrue(list.swap(), "list was not swapped");
      assertFalse(list.isValid(), "list is still in memory");
      return list;
   }

   private static SelectionList createList(String prefix) {
      SelectionList list = new SelectionList();

      for(int i = 0; i < VALUES; i++) {
         list.addSelectionValue(new SelectionValue(prefix + i, prefix + i));
      }

      list.complete();
      return list;
   }

   private static final int VALUES = 60;
}
