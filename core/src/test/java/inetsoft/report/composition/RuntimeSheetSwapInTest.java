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
package inetsoft.report.composition;

import inetsoft.test.*;
import inetsoft.uql.asset.Worksheet;
import inetsoft.util.swap.XSwappable;
import inetsoft.util.swap.XSwapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77635, a swapped undo/redo checkpoint of a RuntimeSheet was read back into memory
 * without waitForMemory().
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome()
@Tag("core")
class RuntimeSheetSwapInTest {
   @Test
   void swapInWaitsForMemoryOutsideOfLockBeforeReadingBack() throws Exception {
      RuntimeSheet.XSwappableSheetList points = new RuntimeSheet.XSwappableSheetList(null);
      points.add(new Worksheet());
      RuntimeSheet.XSwappableSheet swappable = getSwappable(points, 0);
      assertTrue(swappable.swap(), "checkpoint was not swapped");
      assertFalse(swappable.isValid(), "checkpoint is still in memory");

      XSwapper swapper = spy(XSwapper.getSwapper());
      List<Boolean> validAtWait = new ArrayList<>();
      List<Boolean> lockedAtWait = new ArrayList<>();
      doAnswer(invocation -> {
         validAtWait.add(swappable.isValid());
         lockedAtWait.add(Thread.holdsLock(swappable));
         return null;
      }).when(swapper).waitForMemory();
      setSwapper(swappable, swapper);

      assertInstanceOf(Worksheet.class, points.get(0));
      assertEquals(List.of(false), validAtWait,
                   "waitForMemory() must be called once, before the checkpoint is read back");
      assertEquals(List.of(false), lockedAtWait,
                   "waitForMemory() must not be called while holding the checkpoint lock");
      points.dispose();
   }

   @Test
   void accessOfResidentCheckpointDoesNotWait() throws Exception {
      RuntimeSheet.XSwappableSheetList points = new RuntimeSheet.XSwappableSheetList(null);
      points.add(new Worksheet());
      RuntimeSheet.XSwappableSheet swappable = getSwappable(points, 0);
      XSwapper swapper = spy(XSwapper.getSwapper());
      setSwapper(swappable, swapper);

      assertInstanceOf(Worksheet.class, points.get(0));
      verify(swapper, never()).waitForMemory();
      points.dispose();
   }

   @SuppressWarnings("unchecked")
   private static RuntimeSheet.XSwappableSheet getSwappable(RuntimeSheet.XSwappableSheetList points,
                                                            int index)
      throws Exception
   {
      Field field = RuntimeSheet.XSwappableSheetList.class.getDeclaredField("values");
      field.setAccessible(true);
      return ((List<RuntimeSheet.XSwappableSheet>) field.get(points)).get(index);
   }

   private static void setSwapper(XSwappable swappable, XSwapper swapper) throws Exception {
      Field field = XSwappable.class.getDeclaredField("swapper");
      field.setAccessible(true);
      field.set(swappable, swapper);
   }
}
