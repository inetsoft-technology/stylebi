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

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

@Component
public class UtilizationMeterService implements MeterBinder {
   private final ScalingMetricsService service;

   public UtilizationMeterService(ScalingMetricsService service) {
      this.service = service;
   }

   @Override
   public void bindTo(MeterRegistry registry) {
      Gauge.builder("inetsoft.scaling.utilization", service::getJvmCpuUtilization)
         .description("The server utilization")
         .tag("scope", "jvm")
         .tag("type", "cpu")
         .register(registry);
      Gauge.builder("inetsoft.scaling.utilization", service::getJvmMemoryUtilization)
         .description("The server utilization")
         .tag("scope", "jvm")
         .tag("type", "memory")
         .register(registry);
      Gauge.builder("inetsoft.scaling.utilization", service::getSystemCpuUtilization)
         .description("The server utilization")
         .tag("scope", "container")
         .tag("type", "cpu")
         .register(registry);
      Gauge.builder("inetsoft.scaling.utilization", service::getSystemMemoryUtilization)
         .description("The server utilization")
         .tag("scope", "container")
         .tag("type", "memory")
         .register(registry);
      Gauge.builder("inetsoft.scaling.utilization", service::getSchedulerUtilization)
         .description("The server utilization")
         .tag("scope", "jvm")
         .tag("type", "scheduler")
         .register(registry);
      Gauge.builder("inetsoft.scaling.utilization", service::getCacheSwapMemoryUtilization)
         .description("The server utilization")
         .tag("scope", "jvm")
         .tag("type", "cacheSwapMemory")
         .register(registry);
      Gauge.builder("inetsoft.scaling.utilization", service::getCacheSwapWaitUtilization)
         .description("The server utilization")
         .tag("scope", "jvm")
         .tag("type", "cacheSwapWait")
         .register(registry);
      Gauge.builder("inetsoft.scaling.systemUtilization", service::getServerUtilization)
         .description("The server utilization")
         .register(registry);
   }
}
