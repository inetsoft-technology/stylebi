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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77622, XIntFragment.access() read a swapped fragment back into memory without
 * waitForMemory(), unlike XObjectFragment, XStringFragment and the X*Column classes. Several
 * threads reading swapped XSwappableIntList data back at once could run the heap out of memory
 * before the swapper had a chance to make room.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class XIntFragmentSwapInTest {
   @Test
   void swapInWaitsForMemoryBeforeReadingBack() {
      XIntFragment fragment = createSwappedFragment();
      XSwapper swapper = spy(XSwapper.getSwapper());
      List<Boolean> validAtWait = new ArrayList<>();
      // record whether the data was already back in memory when the wait happened
      doAnswer(invocation -> {
         validAtWait.add(fragment.isValid());
         return null;
      }).when(swapper).waitForMemory();
      fragment.swapper = swapper;

      assertEquals(1005, fragment.getSafely(5));
      assertEquals(List.of(false), validAtWait,
                   "waitForMemory() must be called once, before the fragment is read back");
      fragment.dispose();
   }

   @Test
   void swapInIsHeldBackWhileMemoryIsCritical() {
      XIntFragment fragment = createSwappedFragment();
      XSwapper swapper = spy(XSwapper.getSwapper());
      doReturn(XSwapper.CRITICAL_MEM).when(swapper).getMemoryState();
      swapper.setMaxCriticalWait(500L);
      fragment.swapper = swapper;

      long start = System.currentTimeMillis();
      assertEquals(1005, fragment.getSafely(5));
      long elapsed = System.currentTimeMillis() - start;

      assertTrue(elapsed >= 500L, "read back without waiting for memory: " + elapsed + "ms");
      fragment.dispose();
   }

   @Test
   void fragmentReadBackDuringTheWaitIsNotReadAgain() {
      XIntFragment fragment = createSwappedFragment();
      XSwapper swapper = spy(XSwapper.getSwapper());
      AtomicInteger waits = new AtomicInteger();
      // another reader brings the fragment back while this one waits, and changes a value
      doAnswer(invocation -> {
         if(waits.incrementAndGet() == 1) {
            fragment.getArray()[5] = 42;
         }

         return null;
      }).when(swapper).waitForMemory();
      fragment.swapper = swapper;

      assertEquals(42, fragment.getSafely(5), "fragment was read back from the swap file again");
      fragment.dispose();
   }

   @Test
   void accessOfResidentFragmentDoesNotWait() {
      XIntFragment fragment = new XIntFragment(createValues());
      XSwapper swapper = spy(XSwapper.getSwapper());
      fragment.swapper = swapper;

      assertEquals(1005, fragment.getSafely(5));
      verify(swapper, never()).waitForMemory();
      fragment.dispose();
   }

   private static XIntFragment createSwappedFragment() {
      XIntFragment fragment = new XIntFragment(createValues());
      assertTrue(fragment.swap(), "fragment was not swapped");
      assertFalse(fragment.isValid(), "fragment is still in memory");
      return fragment;
   }

   private static int[] createValues() {
      int[] values = new int[100];

      for(int i = 0; i < values.length; i++) {
         values[i] = 1000 + i;
      }

      return values;
   }
}
