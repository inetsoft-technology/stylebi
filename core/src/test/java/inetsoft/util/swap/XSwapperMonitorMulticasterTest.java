/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77658: every {@code XSwapper.MonitorMulticaster} method must forward to the same-named
 * method of each registered monitor, filtered by that method's own level attribute.
 * <p>
 * The multicaster is built by reflection so the test never touches the {@link XSwapper}
 * singleton or any Spring bean.
 */
@Tag("core")
class XSwapperMonitorMulticasterTest {
   @BeforeEach
   void createMulticaster() throws Exception {
      Class<?> cls = Class.forName("inetsoft.util.swap.XSwapper$MonitorMulticaster");
      Constructor<?> ctor = cls.getDeclaredConstructor();
      ctor.setAccessible(true);
      multicaster = (XSwappableMonitor) ctor.newInstance();
      addMonitor = cls.getDeclaredMethod("addMonitor", XSwappableMonitor.class);
      addMonitor.setAccessible(true);
   }

   @Test
   void eachCountLandsOnMatchingMonitorMethod() throws Exception {
      XSwappableMonitor monitor = register(true, true, true, true);

      multicaster.countHits(XSwappableMonitor.DATA, 5);
      multicaster.countMisses(XSwappableMonitor.SHEET, 7);
      multicaster.countRead(100L, XSwappableMonitor.DATA);
      multicaster.countWrite(200L, XSwappableMonitor.SHEET);

      verify(monitor).countHits(XSwappableMonitor.DATA, 5);
      verify(monitor).countMisses(XSwappableMonitor.SHEET, 7);
      verify(monitor).countRead(100L, XSwappableMonitor.DATA);
      verify(monitor).countWrite(200L, XSwappableMonitor.SHEET);
      verify(monitor, times(1)).countHits(anyInt(), anyInt());
      verify(monitor, times(1)).countMisses(anyInt(), anyInt());
      verify(monitor, times(1)).countRead(anyLong(), anyInt());
      verify(monitor, times(1)).countWrite(anyLong(), anyInt());
   }

   @Test
   void eachCountIsFilteredByItsOwnAttribute() throws Exception {
      XSwappableMonitor hitsOnly = register(true, false, false, false);
      XSwappableMonitor missesOnly = register(false, true, false, false);
      XSwappableMonitor readOnly = register(false, false, true, false);
      XSwappableMonitor writtenOnly = register(false, false, false, true);

      multicaster.countHits(XSwappableMonitor.DATA, 1);
      multicaster.countMisses(XSwappableMonitor.DATA, 2);
      multicaster.countRead(3L, XSwappableMonitor.DATA);
      multicaster.countWrite(4L, XSwappableMonitor.DATA);

      verify(hitsOnly).countHits(XSwappableMonitor.DATA, 1);
      verify(missesOnly).countMisses(XSwappableMonitor.DATA, 2);
      verify(readOnly).countRead(3L, XSwappableMonitor.DATA);
      verify(writtenOnly).countWrite(4L, XSwappableMonitor.DATA);

      verify(missesOnly, never()).countHits(anyInt(), anyInt());
      verify(readOnly, never()).countHits(anyInt(), anyInt());
      verify(writtenOnly, never()).countHits(anyInt(), anyInt());
      verify(hitsOnly, never()).countMisses(anyInt(), anyInt());
      verify(readOnly, never()).countMisses(anyInt(), anyInt());
      verify(writtenOnly, never()).countMisses(anyInt(), anyInt());
      verify(hitsOnly, never()).countRead(anyLong(), anyInt());
      verify(missesOnly, never()).countRead(anyLong(), anyInt());
      verify(writtenOnly, never()).countRead(anyLong(), anyInt());
      verify(hitsOnly, never()).countWrite(anyLong(), anyInt());
      verify(missesOnly, never()).countWrite(anyLong(), anyInt());
      verify(readOnly, never()).countWrite(anyLong(), anyInt());
   }

   private XSwappableMonitor register(boolean hits, boolean misses, boolean read,
                                      boolean written) throws Exception
   {
      XSwappableMonitor monitor = mock(XSwappableMonitor.class);
      when(monitor.isLevelQualified(XSwappableMonitor.HITS)).thenReturn(hits);
      when(monitor.isLevelQualified(XSwappableMonitor.MISSES)).thenReturn(misses);
      when(monitor.isLevelQualified(XSwappableMonitor.READ)).thenReturn(read);
      when(monitor.isLevelQualified(XSwappableMonitor.WRITTEN)).thenReturn(written);
      addMonitor.invoke(multicaster, monitor);
      return monitor;
   }

   private XSwappableMonitor multicaster;
   private Method addMonitor;
}
