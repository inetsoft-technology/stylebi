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

import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.VMOption;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #77593: XSwapper turns on G1's periodic collection so that an idle node's heap gets
 * collected and the memory state stops counting garbage that died since the last GC. The
 * real flag can't be put back to its DEFAULT origin once set, so these tests use a mock
 * HotSpotDiagnosticMXBean.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome()
@Tag("core")
class XSwapperPeriodicGCTest {
   @Test
   void setsIntervalOnG1WhenOriginIsDefault() {
      final HotSpotDiagnosticMXBean bean = mockBean(VMOption.Origin.DEFAULT, true);

      assertTrue(XSwapper.enablePeriodicGC(() -> 300000L, () -> true, () -> bean));
      verify(bean).setVMOption(PERIODIC_GC, "300000");
   }

   @Test
   void keepsUserSetInterval() {
      for(VMOption.Origin origin : new VMOption.Origin[] {
         VMOption.Origin.VM_CREATION, VMOption.Origin.ENVIRON_VAR,
         VMOption.Origin.CONFIG_FILE, VMOption.Origin.ATTACH_ON_DEMAND,
         VMOption.Origin.OTHER })
      {
         final HotSpotDiagnosticMXBean bean = mockBean(origin, true);

         assertFalse(XSwapper.enablePeriodicGC(() -> 300000L, () -> true, () -> bean), origin.name());
         verify(bean, never()).setVMOption(anyString(), anyString());
      }
   }

   @Test
   void skipsWhenAlreadySetThroughManagement() {
      // a second swapper in the same JVM sees the value the first one set
      final HotSpotDiagnosticMXBean bean = mockBean(VMOption.Origin.MANAGEMENT, true);

      assertFalse(XSwapper.enablePeriodicGC(() -> 300000L, () -> true, () -> bean));
      verify(bean, never()).setVMOption(anyString(), anyString());
   }

   @Test
   void skipsReadOnlyOption() {
      final HotSpotDiagnosticMXBean bean = mockBean(VMOption.Origin.DEFAULT, false);

      assertFalse(XSwapper.enablePeriodicGC(() -> 300000L, () -> true, () -> bean));
      verify(bean, never()).setVMOption(anyString(), anyString());
   }

   @Test
   void skipsOtherCollectors() {
      final HotSpotDiagnosticMXBean bean = mockBean(VMOption.Origin.DEFAULT, true);

      assertFalse(XSwapper.enablePeriodicGC(() -> 300000L, () -> false, () -> bean));
      verifyNoInteractions(bean);
   }

   @Test
   void zeroIntervalDisablesIt() {
      final HotSpotDiagnosticMXBean bean = mockBean(VMOption.Origin.DEFAULT, true);

      assertFalse(XSwapper.enablePeriodicGC(() -> 0L, () -> true, () -> bean));
      verifyNoInteractions(bean);
   }

   @Test
   void toleratesFailures() {
      assertFalse(XSwapper.enablePeriodicGC(() -> 300000L, () -> true, () -> {
         throw new NoClassDefFoundError("com/sun/management/HotSpotDiagnosticMXBean");
      }));
      assertFalse(XSwapper.enablePeriodicGC(() -> 300000L, () -> true, () -> null));

      final HotSpotDiagnosticMXBean missing = mock(HotSpotDiagnosticMXBean.class);
      when(missing.getVMOption(PERIODIC_GC))
         .thenThrow(new IllegalArgumentException("VM option does not exist"));
      assertFalse(XSwapper.enablePeriodicGC(() -> 300000L, () -> true, () -> missing));

      // a failing interval or collector check must not escape into the swapper constructor
      assertFalse(XSwapper.enablePeriodicGC(() -> {
         throw new IllegalStateException("property engine unavailable");
      }, () -> true, () -> mockBean(VMOption.Origin.DEFAULT, true)));
      assertFalse(XSwapper.enablePeriodicGC(() -> 300000L, () -> {
         throw new SecurityException("management access denied");
      }, () -> mockBean(VMOption.Origin.DEFAULT, true)));

      final HotSpotDiagnosticMXBean rejected = mockBean(VMOption.Origin.DEFAULT, true);
      doThrow(new IllegalArgumentException("not writeable"))
         .when(rejected).setVMOption(anyString(), anyString());
      assertFalse(XSwapper.enablePeriodicGC(() -> 300000L, () -> true, () -> rejected));
   }

   @Test
   void detectsG1() {
      assertTrue(XSwapper.isG1GC(List.of(gc("G1 Young Generation"), gc("G1 Concurrent GC"),
                                         gc("G1 Old Generation"))));
      assertFalse(XSwapper.isG1GC(List.of(gc("PS Scavenge"), gc("PS MarkSweep"))));
      assertFalse(XSwapper.isG1GC(List.of(gc("ZGC Major Cycles"), gc("ZGC Minor Cycles"))));
      assertFalse(XSwapper.isG1GC(List.of(gc("Shenandoah Cycles"))));
   }

   @Test
   void swapperSetsTheRealOption() {
      // the swapper bean in this context, or one created earlier in this JVM, has run the check
      assumeTrue(XSwapper.isG1GC(ManagementFactory.getGarbageCollectorMXBeans()), "not G1");
      assumeTrue(ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                    .noneMatch(arg -> arg.contains(PERIODIC_GC)), "set on the command line");
      assertNotNull(XSwapper.getSwapper());

      final VMOption option = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class)
         .getVMOption(PERIODIC_GC);
      assertEquals(VMOption.Origin.MANAGEMENT, option.getOrigin());
      assertEquals("300000", option.getValue());
   }

   @Test
   void readsIntervalProperty() {
      try {
         assertEquals(300000L, XSwapper.getPeriodicGCInterval());
         SreeEnv.setProperty(PROPERTY, "0");
         assertEquals(0L, XSwapper.getPeriodicGCInterval());
         SreeEnv.setProperty(PROPERTY, "-5");
         assertEquals(0L, XSwapper.getPeriodicGCInterval());
         // seconds-long intervals are raised to a minute
         SreeEnv.setProperty(PROPERTY, "2000");
         assertEquals(60000L, XSwapper.getPeriodicGCInterval());
         SreeEnv.setProperty(PROPERTY, "600000");
         assertEquals(600000L, XSwapper.getPeriodicGCInterval());
         SreeEnv.setProperty(PROPERTY, "five minutes");
         assertEquals(300000L, XSwapper.getPeriodicGCInterval());
      }
      finally {
         SreeEnv.remove(PROPERTY);
      }
   }

   private static HotSpotDiagnosticMXBean mockBean(VMOption.Origin origin, boolean writeable) {
      final HotSpotDiagnosticMXBean bean = mock(HotSpotDiagnosticMXBean.class);
      when(bean.getVMOption(PERIODIC_GC))
         .thenReturn(new VMOption(PERIODIC_GC, "0", writeable, origin));
      return bean;
   }

   private static GarbageCollectorMXBean gc(String name) {
      final GarbageCollectorMXBean bean = mock(GarbageCollectorMXBean.class);
      when(bean.getName()).thenReturn(name);
      return bean;
   }

   private static final String PERIODIC_GC = "G1PeriodicGCInterval";
   private static final String PROPERTY = "swapper.idle.gc.interval";
}
