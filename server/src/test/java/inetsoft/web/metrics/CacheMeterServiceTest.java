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
package inetsoft.web.metrics;

import inetsoft.util.swap.XSwappableMonitor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77684: the swapper can deliver counts to the monitor between registerMonitor() and
 * bindTo(). Those counts must be dropped instead of throwing, and counts after bindTo() must be
 * recorded.
 * <p>
 * The service is built without its @PostConstruct, so no XSwapper or Spring bean is involved.
 */
class CacheMeterServiceTest {
   @Test
   void countsBeforeBindToAreDropped() {
      CacheMeterService service = new CacheMeterService(null);

      assertDoesNotThrow(() -> countAll(service));

      SimpleMeterRegistry registry = new SimpleMeterRegistry();
      service.bindTo(registry);
      assertEquals(0D, total(registry, "inetsoft.cache.requests"));
      assertEquals(0D, total(registry, "inetsoft.cache.transfer"));
   }

   @Test
   void countsAfterBindToAreRecorded() {
      CacheMeterService service = new CacheMeterService(null);
      SimpleMeterRegistry registry = new SimpleMeterRegistry();
      service.bindTo(registry);

      countAll(service);

      assertEquals(1D, count(registry, "inetsoft.cache.requests", "result", "hit", "sheet"));
      assertEquals(2D, count(registry, "inetsoft.cache.requests", "result", "hit", "data"));
      assertEquals(3D, count(registry, "inetsoft.cache.requests", "result", "miss", "sheet"));
      assertEquals(4D, count(registry, "inetsoft.cache.requests", "result", "miss", "data"));
      assertEquals(5D, count(registry, "inetsoft.cache.transfer", "direction", "read", "sheet"));
      assertEquals(6D, count(registry, "inetsoft.cache.transfer", "direction", "read", "data"));
      assertEquals(7D, count(registry, "inetsoft.cache.transfer", "direction", "write", "sheet"));
      assertEquals(8D, count(registry, "inetsoft.cache.transfer", "direction", "write", "data"));
   }

   private static void countAll(CacheMeterService service) {
      service.countHits(XSwappableMonitor.SHEET, 1);
      service.countHits(XSwappableMonitor.DATA, 2);
      service.countMisses(XSwappableMonitor.SHEET, 3);
      service.countMisses(XSwappableMonitor.DATA, 4);
      service.countRead(5L, XSwappableMonitor.SHEET);
      service.countRead(6L, XSwappableMonitor.DATA);
      service.countWrite(7L, XSwappableMonitor.SHEET);
      service.countWrite(8L, XSwappableMonitor.DATA);
   }

   private static double count(SimpleMeterRegistry registry, String name, String tag,
                               String value, String cache)
   {
      return registry.get(name).tag(tag, value).tag("cache", cache).counter().count();
   }

   private static double total(SimpleMeterRegistry registry, String name) {
      return registry.get(name).counters().stream().mapToDouble(c -> c.count()).sum();
   }
}
