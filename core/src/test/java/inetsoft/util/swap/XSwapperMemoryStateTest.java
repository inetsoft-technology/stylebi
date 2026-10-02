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

import java.lang.management.*;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

/**
 * Bug #77608: under G1 the memory state leaves out the eden pool, which is mostly garbage
 * and can take most of the free heap, so a moderate live set doesn't read as critical. The
 * context is only for the <tt>swapper.memory.excludeEden</tt> property.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class, initializers = ConfigurationContextInitializer.class)
@SreeHome()
@Tag("core")
class XSwapperMemoryStateTest {
   @Test
   void excludesEdenGarbage() {
      // 60% of the heap is live in the old generation and eden is full of garbage. with eden
      // the free ratio is 0.05 (critical), without it 0.40 (normal)
      final MemoryPoolMXBean eden = pool(EDEN, MemoryType.HEAP, 350 * MB);

      assertEquals(400 * MB, XSwapper.getFreeSpace(1000 * MB, 50 * MB, eden, heap(950 * MB)));
   }

   @Test
   void usesRawWithoutEdenPool() {
      assertEquals(50 * MB, XSwapper.getFreeSpace(1000 * MB, 50 * MB, null, heap(950 * MB)));
   }

   @Test
   void usesRawWhenReadingFails() {
      final MemoryPoolMXBean throwing = mock(MemoryPoolMXBean.class);
      when(throwing.getUsage()).thenThrow(new IllegalStateException("pool is invalid"));
      assertEquals(50 * MB, XSwapper.getFreeSpace(1000 * MB, 50 * MB, throwing, heap(950 * MB)));

      final MemoryPoolMXBean invalid = mock(MemoryPoolMXBean.class);
      when(invalid.getUsage()).thenReturn(null);
      assertEquals(50 * MB, XSwapper.getFreeSpace(1000 * MB, 50 * MB, invalid, heap(950 * MB)));

      final MemoryPoolMXBean eden = pool(EDEN, MemoryType.HEAP, 350 * MB);
      assertEquals(50 * MB, XSwapper.getFreeSpace(1000 * MB, 50 * MB, eden, () -> {
         throw new NoClassDefFoundError("java/lang/management/MemoryUsage");
      }));
   }

   @Test
   void clampsWhenYoungCollectionRunsBetweenReads() {
      // the heap is read before a young collection and eden after it, so eden can exceed
      // the heap used memory. it must not read more free memory than the heap has
      final MemoryPoolMXBean eden = pool(EDEN, MemoryType.HEAP, 400 * MB);

      assertEquals(1000 * MB, XSwapper.getFreeSpace(1000 * MB, 50 * MB, eden, heap(300 * MB)));
   }

   @Test
   void readsHeapBeforeEden() {
      final MemoryPoolMXBean eden = pool(EDEN, MemoryType.HEAP, 350 * MB);
      @SuppressWarnings("unchecked")
      final Supplier<MemoryUsage> heap = mock(Supplier.class);
      when(heap.get()).thenReturn(usage(950 * MB));

      XSwapper.getFreeSpace(1000 * MB, 50 * MB, eden, heap);

      final var order = inOrder(heap, eden);
      order.verify(heap).get();
      order.verify(eden).getUsage();
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
   void usesRawWithFixedYoungSize() {
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
   void usesRawWhenYoungSizeCantBeChecked() {
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
   void killSwitchRestoresRaw() {
      final MemoryPoolMXBean eden = pool(EDEN, MemoryType.HEAP, 350 * MB);

      try {
         assertTrue(XSwapper.isExcludeEden());
         assertEquals(400 * MB, XSwapper.getFreeSpace(
            1000 * MB, 50 * MB, XSwapper.isExcludeEden() ? eden : null, heap(950 * MB)));

         SreeEnv.setProperty(PROPERTY, "false");
         assertFalse(XSwapper.isExcludeEden());
         assertEquals(50 * MB, XSwapper.getFreeSpace(
            1000 * MB, 50 * MB, XSwapper.isExcludeEden() ? eden : null, heap(950 * MB)));

         SreeEnv.setProperty(PROPERTY, " FALSE ");
         assertFalse(XSwapper.isExcludeEden());

         // only false turns it off, so a typo keeps it on
         SreeEnv.setProperty(PROPERTY, "flase");
         assertTrue(XSwapper.isExcludeEden());
         SreeEnv.setProperty(PROPERTY, "true");
         assertTrue(XSwapper.isExcludeEden());
      }
      finally {
         SreeEnv.remove(PROPERTY);
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
      return new MemoryUsage(0L, used, 1000 * MB, 1000 * MB);
   }

   private static HotSpotDiagnosticMXBean bean(VMOption.Origin origin) {
      final HotSpotDiagnosticMXBean bean = mock(HotSpotDiagnosticMXBean.class);
      when(bean.getVMOption(NEW_SIZE)).thenReturn(new VMOption(NEW_SIZE, "0", false, origin));
      return bean;
   }

   private static final long MB = 1024L * 1024L;
   // the largest G1 region size
   private static final long REGION_MAX = 32 * MB;
   private static final String EDEN = "G1 Eden Space";
   private static final String NEW_SIZE = "NewSize";
   private static final String PROPERTY = "swapper.memory.excludeEden";
}
