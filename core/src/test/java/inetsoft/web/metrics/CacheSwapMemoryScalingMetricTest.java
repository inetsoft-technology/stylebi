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
package inetsoft.web.metrics;

import inetsoft.test.*;
import inetsoft.util.swap.XSwapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * Bug #77608, under G1 the swapper's memory state counts eden, which G1 lets fill most of the
 * free heap with garbage before each young GC. The autoscaling metric read that state, so a
 * node with a modest live set reported high utilization from garbage alone.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class CacheSwapMemoryScalingMetricTest {
   @Test
   void metricDoesNotFollowTheEdenInclusiveState() {
      assumeTrue(isG1(), "the eden-excluded reading only applies under G1");
      XSwapper swapper = spy(XSwapper.getSwapper());
      // the eden-inclusive state says critical; this test JVM's live data is far from it
      doReturn(XSwapper.CRITICAL_MEM).when(swapper).getMemoryState();
      CacheSwapMemoryScalingMetric metric = new CacheSwapMemoryScalingMetric(false, 0, swapper);

      double value = metric.calculate();

      assertTrue(value < 1D, "metric reported the eden-inclusive critical state: " + value);
   }

   // the metric is the band of the state without eden, (4 - state) / 4, not a linear utilization
   @Test
   void metricIsTheBandOfTheStateWithoutEden() {
      XSwapper swapper = spy(XSwapper.getSwapper());
      doReturn(XSwapper.CRITICAL_MEM).when(swapper).getMemoryState();
      CacheSwapMemoryScalingMetric metric = new CacheSwapMemoryScalingMetric(false, 0, swapper);

      doReturn(XSwapper.GOOD_MEM).when(swapper).getMemoryStateExcludingEden();
      assertEquals(0D, metric.calculate());
      doReturn(XSwapper.NORM_MEM).when(swapper).getMemoryStateExcludingEden();
      assertEquals(0.25D, metric.calculate());
      doReturn(XSwapper.BAD_MEM).when(swapper).getMemoryStateExcludingEden();
      assertEquals(0.75D, metric.calculate());
      // real pressure, with eden empty after a young collection, still reaches the metric
      doReturn(XSwapper.CRITICAL_MEM).when(swapper).getMemoryStateExcludingEden();
      assertEquals(1D, metric.calculate());
   }

   private static boolean isG1() {
      for(MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
         if("G1 Eden Space".equals(pool.getName())) {
            return true;
         }
      }

      return false;
   }
}
