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

import inetsoft.report.XSessionManager;
import inetsoft.uql.util.XNodeTable;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class ExecutionMeterService {
   private final AtomicInteger queries = new AtomicInteger(0);

   public ExecutionMeterService(MeterRegistry registry) {
      Gauge.builder("inetsoft.executions", queries, AtomicInteger::doubleValue)
         .description("The number of executing assets")
         .tags("asset", "query")
         .register(registry);
   }

   @Scheduled(fixedRate = 5000L, initialDelay = 0L)
   public void updateGauges() {
      queries.set((int) Stream.concat(
         XSessionManager.getExecutingQueries().stream(),
         XNodeTable.getExecutingQueries().stream())
         .count());
   }
}
