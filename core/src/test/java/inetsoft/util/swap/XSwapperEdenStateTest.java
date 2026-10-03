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

import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.VMOption;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.test.*;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.management.*;
import java.security.Principal;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

/**
 * Bug #77608: the cache swap memory scaling metric reads the memory state without the G1
 * eden pool, which is mostly garbage and can take most of the free heap. Every other user of
 * the memory state keeps the reading with eden.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome()
@Tag("core")
class XSwapperEdenStateTest {
   // S1: 85% of the heap is used and 40% is eden. with eden that is critical (15% free),
   // without it good (55% free)
   @Test
   void excludesEdenGarbage() {
      final XSwapper swapper = spy(XSwapper.getSwapper());
      doReturn(XSwapper.CRITICAL_MEM).when(swapper).getMemoryState();

      assertEquals(XSwapper.CRITICAL_MEM, XSwapper.getMemoryState(0.15));
      assertEquals(550L, 1000L - XSwapper.getUsedExcludingEden(eden(400L), heap(850L)));
      assertEquals(XSwapper.GOOD_MEM,
                   swapper.getMemoryStateExcludingEden(1000L, eden(400L), heap(850L)));
   }

   // S2: just after a young collection eden is empty, so real pressure still reads critical
   @Test
   void livePressureStaysCritical() {
      final XSwapper swapper = spy(XSwapper.getSwapper());
      doReturn(XSwapper.CRITICAL_MEM).when(swapper).getMemoryState();

      assertEquals(XSwapper.CRITICAL_MEM,
                   swapper.getMemoryStateExcludingEden(1000L, eden(0L), heap(850L)));
   }

   // S3: no G1 eden pool, or the pool can't be read, gives the state with eden as is
   @Test
   void usesStateWithEdenWithoutPool() {
      final XSwapper swapper = spy(XSwapper.getSwapper());
      doReturn(XSwapper.BAD_MEM).when(swapper).getMemoryState();

      assertEquals(XSwapper.BAD_MEM, swapper.getMemoryStateExcludingEden(1000L, null, heap(850L)));

      final MemoryPoolMXBean throwing = mock(MemoryPoolMXBean.class);
      when(throwing.getUsage()).thenThrow(new IllegalStateException("pool is invalid"));
      assertEquals(XSwapper.BAD_MEM,
                   swapper.getMemoryStateExcludingEden(1000L, throwing, heap(850L)));

      final MemoryPoolMXBean invalid = mock(MemoryPoolMXBean.class);
      when(invalid.getUsage()).thenReturn(null);
      assertEquals(XSwapper.BAD_MEM,
                   swapper.getMemoryStateExcludingEden(1000L, invalid, heap(850L)));

      assertEquals(XSwapper.BAD_MEM, swapper.getMemoryStateExcludingEden(1000L, eden(400L), () -> {
         throw new NoClassDefFoundError("java/lang/management/MemoryUsage");
      }));
   }

   // S4: a young collection between the heap and eden reads can make eden larger than the
   // heap used memory
   @Test
   void clampsWhenYoungCollectionRunsBetweenReads() {
      final XSwapper swapper = spy(XSwapper.getSwapper());
      doReturn(XSwapper.CRITICAL_MEM).when(swapper).getMemoryState();

      assertEquals(0L, XSwapper.getUsedExcludingEden(eden(400L), heap(300L)));
      assertEquals(XSwapper.GOOD_MEM,
                   swapper.getMemoryStateExcludingEden(1000L, eden(400L), heap(300L)));
   }

   @Test
   void readsHeapBeforeEden() {
      final MemoryPoolMXBean eden = eden(400L);
      @SuppressWarnings("unchecked")
      final Supplier<MemoryUsage> heap = mock(Supplier.class);
      when(heap.get()).thenReturn(usage(850L));

      XSwapper.getUsedExcludingEden(eden, heap);

      final var order = inOrder(heap, eden);
      order.verify(heap).get();
      order.verify(eden).getUsage();
   }

   // S5: the memory state everything else reads doesn't use the state without eden
   @Test
   void memoryStateDoesNotUseStateWithoutEden() {
      final XSwapper swapper = spy(XSwapper.getSwapper());
      ReflectionTestUtils.setField(swapper, "stateTS", 0L);

      swapper.getMemoryState();

      verify(swapper, never()).getMemoryStateExcludingEden();
      verify(swapper, never()).getMemoryStateExcludingEden(anyLong(), any(), any());
   }

   // S6: the two states have separate caches, so neither leaks into the other
   @Test
   void cachesAreSeparate() {
      final XSwapper swapper = spy(XSwapper.getSwapper());
      final long later = System.currentTimeMillis() + 60000L;
      // a state with eden that was just read, critical
      ReflectionTestUtils.setField(swapper, "cachedState", XSwapper.CRITICAL_MEM);
      ReflectionTestUtils.setField(swapper, "stateTS", later);
      ReflectionTestUtils.setField(swapper, "edenStateTS", 0L);

      assertEquals(XSwapper.GOOD_MEM,
                   swapper.getMemoryStateExcludingEden(1000L, eden(400L), heap(850L)));
      assertEquals(XSwapper.CRITICAL_MEM, swapper.getMemoryState(),
                   "the state without eden was stored as the state with eden");
      assertEquals(later, ReflectionTestUtils.getField(swapper, "stateTS"));

      // a fresh state with eden doesn't replace the cached state without eden
      ReflectionTestUtils.setField(swapper, "edenStateTS", later);
      ReflectionTestUtils.setField(swapper, "stateTS", 0L);
      swapper.getMemoryState();
      assertEquals(XSwapper.GOOD_MEM,
                   swapper.getMemoryStateExcludingEden(1000L, eden(0L), heap(850L)),
                   "the state with eden was stored as the state without eden");

      // once its cache expires, the state without eden is read again
      ReflectionTestUtils.setField(swapper, "edenStateTS", 0L);
      assertEquals(XSwapper.CRITICAL_MEM,
                   swapper.getMemoryStateExcludingEden(1000L, eden(0L), heap(850L)));
   }

   @Test
   void findsOnlyG1EdenPool() {
      final MemoryPoolMXBean eden = pool(EDEN, MemoryType.HEAP, 0);
      final List<MemoryPoolMXBean> g1 = List.of(
         pool("CodeHeap 'non-nmethods'", MemoryType.NON_HEAP, 0), eden,
         pool("G1 Old Gen", MemoryType.HEAP, 0), pool("G1 Survivor Space", MemoryType.HEAP, 0));

      assertSame(eden, XSwapper.G1Eden.find(() -> g1, () -> bean(VMOption.Origin.DEFAULT)));
      assertSame(eden, XSwapper.G1Eden.find(() -> g1, () -> bean(VMOption.Origin.ERGONOMIC)));

      // Parallel and Serial
      assertNull(XSwapper.G1Eden.find(
         () -> List.of(pool("PS Eden Space", MemoryType.HEAP, 0)),
         () -> bean(VMOption.Origin.DEFAULT)));
      assertNull(XSwapper.G1Eden.find(
         () -> List.of(pool("Eden Space", MemoryType.HEAP, 0)),
         () -> bean(VMOption.Origin.DEFAULT)));
      // ZGC
      assertNull(XSwapper.G1Eden.find(
         () -> List.of(pool("ZHeap", MemoryType.HEAP, 0)), () -> bean(VMOption.Origin.DEFAULT)));
      assertNull(XSwapper.G1Eden.find(
         () -> List.of(pool(EDEN, MemoryType.NON_HEAP, 0)), () -> bean(VMOption.Origin.DEFAULT)));
      assertNull(XSwapper.G1Eden.find(() -> {
         throw new SecurityException("management access denied");
      }, () -> bean(VMOption.Origin.DEFAULT)));
   }

   @Test
   void usesStateWithEdenWithFixedYoungSize() {
      final MemoryPoolMXBean eden = pool(EDEN, MemoryType.HEAP, 0);

      // -Xmn and -XX:NewSize, on the command line, in JAVA_TOOL_OPTIONS or in a flags file
      for(VMOption.Origin origin : new VMOption.Origin[] {
         VMOption.Origin.VM_CREATION, VMOption.Origin.ENVIRON_VAR,
         VMOption.Origin.CONFIG_FILE, VMOption.Origin.ATTACH_ON_DEMAND,
         VMOption.Origin.MANAGEMENT, VMOption.Origin.OTHER })
      {
         assertTrue(XSwapper.G1Eden.fixedYoungSize(() -> bean(origin)), origin.name());
         assertNull(XSwapper.G1Eden.find(() -> List.of(eden), () -> bean(origin)), origin.name());
      }

      assertFalse(XSwapper.G1Eden.fixedYoungSize(() -> bean(VMOption.Origin.DEFAULT)));
      assertFalse(XSwapper.G1Eden.fixedYoungSize(() -> bean(VMOption.Origin.ERGONOMIC)));
   }

   @Test
   void usesStateWithEdenWhenYoungSizeCantBeChecked() {
      final MemoryPoolMXBean eden = pool(EDEN, MemoryType.HEAP, 0);

      // no jdk.management module
      assertTrue(XSwapper.G1Eden.fixedYoungSize(() -> {
         throw new NoClassDefFoundError("com/sun/management/HotSpotDiagnosticMXBean");
      }));
      assertNull(XSwapper.G1Eden.find(() -> List.of(eden), () -> {
         throw new NoClassDefFoundError("com/sun/management/HotSpotDiagnosticMXBean");
      }));
      assertTrue(XSwapper.G1Eden.fixedYoungSize(() -> null));

      final HotSpotDiagnosticMXBean missing = mock(HotSpotDiagnosticMXBean.class);
      when(missing.getVMOption(NEW_SIZE))
         .thenThrow(new IllegalArgumentException("VM option does not exist"));
      assertTrue(XSwapper.G1Eden.fixedYoungSize(() -> missing));
   }

   @Test
   void killSwitchRestoresStateWithEden() {
      assumeTrue(XSwapper.G1Eden.POOL != null, "no G1 eden pool in this JVM");
      final XSwapper swapper = spy(XSwapper.getSwapper());
      // the state with eden says critical; this test JVM's live data is far from it
      doReturn(XSwapper.CRITICAL_MEM).when(swapper).getMemoryState();

      try {
         assertTrue(XSwapper.isScalingMetricExcludeEden());
         assertNotEquals(XSwapper.CRITICAL_MEM, swapper.getMemoryStateExcludingEden());

         SreeEnv.setProperty(PROPERTY, "false");
         assertFalse(XSwapper.isScalingMetricExcludeEden());
         assertEquals(XSwapper.CRITICAL_MEM, swapper.getMemoryStateExcludingEden());

         SreeEnv.setProperty(PROPERTY, " FALSE ");
         assertFalse(XSwapper.isScalingMetricExcludeEden());

         // only false turns it off, so a typo keeps it on
         SreeEnv.setProperty(PROPERTY, "flase");
         assertTrue(XSwapper.isScalingMetricExcludeEden());
         SreeEnv.setProperty(PROPERTY, "true");
         assertTrue(XSwapper.isScalingMetricExcludeEden());
      }
      finally {
         SreeEnv.remove(PROPERTY);
      }
   }

   @Test
   void killSwitchIgnoresOrganizationValue() {
      final Principal oldContext = ThreadContext.getContextPrincipal();
      final String orgKey = "inetsoft.org." + ORG + "." + PROPERTY;

      try {
         SreeEnv.setProperty(orgKey, "false");
         ThreadContext.setContextPrincipal(
            new SRPrincipal(new IdentityID("admin", ORG), new IdentityID[0], new String[0], ORG,
                            Tool.getSecureRandom().nextLong()));

         // Bug #77623: the swapper keys are JVM-wide, so even an organization-scoped read on
         // this thread doesn't see the organization's value
         assertNull(SreeEnv.getProperty(PROPERTY));
         assertTrue(XSwapper.isScalingMetricExcludeEden());
      }
      finally {
         ThreadContext.setContextPrincipal(oldContext);
         SreeEnv.remove(orgKey);
      }
   }

   @Test
   void resolvesRealG1EdenPool() {
      assumeTrue(ManagementFactory.getGarbageCollectorMXBeans().stream()
                    .anyMatch(gc -> "G1 Young Generation".equals(gc.getName())), "not G1");
      assumeTrue(ModuleLayer.boot().findModule("jdk.management").isPresent(),
                 "no jdk.management module");
      assumeTrue(ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                    .noneMatch(arg -> arg.startsWith("-Xmn") || arg.contains("NewSize=")),
                 "young generation size set on the command line");

      // a gate that is always closed would turn the fix off without failing the tests above
      assertFalse(XSwapper.G1Eden.fixedYoungSize(
         () -> ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class)));
      final MemoryPoolMXBean eden = XSwapper.G1Eden.POOL;
      assertNotNull(eden, "a JDK renamed the G1 eden pool");
      assertEquals(EDEN, eden.getName());

      final MemoryPoolMXBean old = realPool("G1 Old Gen");
      final MemoryPoolMXBean survivor = realPool("G1 Survivor Space");
      final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
      long diff = Long.MAX_VALUE;

      // a collection can run between the reads, so retry
      for(int i = 0; i < 5 && diff > REGION_MAX; i++) {
         final long heapUsed = memory.getHeapMemoryUsage().getUsed();
         final long edenUsed = eden.getUsage().getUsed();
         final long live = old.getUsage().getUsed() + survivor.getUsage().getUsed();
         diff = Math.abs(heapUsed - edenUsed - live);
      }

      assertTrue(diff <= REGION_MAX, "heap - eden differs from old + survivor by " + diff);
   }

   private static MemoryPoolMXBean realPool(String name) {
      return ManagementFactory.getMemoryPoolMXBeans().stream()
         .filter(pool -> name.equals(pool.getName()))
         .findFirst()
         .orElseThrow();
   }

   private static MemoryPoolMXBean eden(long used) {
      return pool(EDEN, MemoryType.HEAP, used);
   }

   private static MemoryPoolMXBean pool(String name, MemoryType type, long used) {
      final MemoryPoolMXBean pool = mock(MemoryPoolMXBean.class);
      when(pool.getName()).thenReturn(name);
      when(pool.getType()).thenReturn(type);
      when(pool.getUsage()).thenReturn(usage(used));
      return pool;
   }

   private static Supplier<MemoryUsage> heap(long used) {
      return () -> usage(used);
   }

   private static MemoryUsage usage(long used) {
      return new MemoryUsage(0L, used, 1000L, 1000L);
   }

   private static HotSpotDiagnosticMXBean bean(VMOption.Origin origin) {
      final HotSpotDiagnosticMXBean bean = mock(HotSpotDiagnosticMXBean.class);
      when(bean.getVMOption(NEW_SIZE)).thenReturn(new VMOption(NEW_SIZE, "0", false, origin));
      return bean;
   }

   // the largest G1 region size
   private static final long REGION_MAX = 32L * 1024L * 1024L;
   private static final String EDEN = "G1 Eden Space";
   private static final String NEW_SIZE = "NewSize";
   private static final String PROPERTY = "swapper.scalingMetric.excludeEden";
   private static final String ORG = "orga";
}
