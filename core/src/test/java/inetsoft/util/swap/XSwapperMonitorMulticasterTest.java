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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77658: every {@code XSwapper.MonitorMulticaster} method must forward to the same-named
 * method of each registered monitor, filtered by that method's own level attribute.
 * <p>
 * Bug #77684: a monitor that throws, from a count or from its level check, must neither keep
 * the count from the monitors after it nor propagate to the caller, and is warned about once.
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

      logger = (Logger) LoggerFactory.getLogger(XSwapper.class);
      appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);
   }

   @AfterEach
   void detachAppender() {
      logger.detachAppender(appender);
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

   @Test
   void throwingCountDoesNotStopLaterMonitorsOrReachCaller() throws Exception {
      XSwappableMonitor failing = register(true, true, true, true);
      doThrow(new NullPointerException("count")).when(failing).countHits(anyInt(), anyInt());
      doThrow(new NullPointerException("count")).when(failing).countMisses(anyInt(), anyInt());
      doThrow(new NullPointerException("count")).when(failing).countRead(anyLong(), anyInt());
      doThrow(new NullPointerException("count")).when(failing).countWrite(anyLong(), anyInt());
      XSwappableMonitor recording = register(true, true, true, true);

      assertDoesNotThrow(() -> multicaster.countHits(XSwappableMonitor.DATA, 1));
      assertDoesNotThrow(() -> multicaster.countMisses(XSwappableMonitor.DATA, 2));
      assertDoesNotThrow(() -> multicaster.countRead(3L, XSwappableMonitor.DATA));
      assertDoesNotThrow(() -> multicaster.countWrite(4L, XSwappableMonitor.DATA));

      verify(recording).countHits(XSwappableMonitor.DATA, 1);
      verify(recording).countMisses(XSwappableMonitor.DATA, 2);
      verify(recording).countRead(3L, XSwappableMonitor.DATA);
      verify(recording).countWrite(4L, XSwappableMonitor.DATA);
      assertEquals(1, warnings(), "a failing monitor is warned about once");
   }

   @Test
   void throwingLevelCheckDoesNotStopLaterMonitorsOrReachCaller() throws Exception {
      XSwappableMonitor failing = mock(XSwappableMonitor.class);
      when(failing.isLevelQualified(anyString())).thenThrow(new IllegalStateException("level"));
      addMonitor.invoke(multicaster, failing);
      XSwappableMonitor recording = register(true, true, true, true);

      assertDoesNotThrow(() -> multicaster.countHits(XSwappableMonitor.SHEET, 1));
      assertDoesNotThrow(() -> multicaster.countMisses(XSwappableMonitor.SHEET, 2));
      assertDoesNotThrow(() -> multicaster.countRead(3L, XSwappableMonitor.SHEET));
      assertDoesNotThrow(() -> multicaster.countWrite(4L, XSwappableMonitor.SHEET));
      assertTrue(multicaster.isLevelQualified(XSwappableMonitor.HITS));

      verify(recording).countHits(XSwappableMonitor.SHEET, 1);
      verify(recording).countMisses(XSwappableMonitor.SHEET, 2);
      verify(recording).countRead(3L, XSwappableMonitor.SHEET);
      verify(recording).countWrite(4L, XSwappableMonitor.SHEET);
      verify(failing, never()).countHits(anyInt(), anyInt());
      assertEquals(1, warnings(), "a failing monitor is warned about once");
   }

   @Test
   void throwingLevelCheckAloneIsNotQualified() throws Exception {
      XSwappableMonitor failing = mock(XSwappableMonitor.class);
      when(failing.isLevelQualified(anyString())).thenThrow(new IllegalStateException("level"));
      addMonitor.invoke(multicaster, failing);

      assertFalse(multicaster.isLevelQualified(XSwappableMonitor.READ));
   }

   @Test
   void eachFailingMonitorIsWarnedOnceUntilRemoved() throws Exception {
      XSwappableMonitor failingCount = register(true, true, true, true);
      doThrow(new NullPointerException("count")).when(failingCount).countHits(anyInt(), anyInt());
      XSwappableMonitor failingLevel = mock(XSwappableMonitor.class);
      when(failingLevel.isLevelQualified(anyString())).thenThrow(new IllegalStateException("level"));
      addMonitor.invoke(multicaster, failingLevel);
      XSwappableMonitor recording = register(true, true, true, true);

      for(int i = 0; i < 1000; i++) {
         multicaster.countHits(XSwappableMonitor.DATA, 1);
      }

      verify(recording, times(1000)).countHits(XSwappableMonitor.DATA, 1);
      assertEquals(2, warnings(), "each failing monitor is warned about once, not per count");

      Method removeMonitor =
         multicaster.getClass().getDeclaredMethod("removeMonitor", XSwappableMonitor.class);
      removeMonitor.setAccessible(true);
      removeMonitor.invoke(multicaster, failingCount);
      multicaster.countHits(XSwappableMonitor.DATA, 1);
      assertEquals(2, warnings(), "a removed monitor is no longer called");

      addMonitor.invoke(multicaster, failingCount);
      multicaster.countHits(XSwappableMonitor.DATA, 1);
      assertEquals(3, warnings(), "a monitor added again after removal is warned about again");
   }

   private long warnings() {
      return appender.list.stream().filter(e -> e.getLevel() == Level.WARN).count();
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
   private Logger logger;
   private ListAppender<ILoggingEvent> appender;
}
