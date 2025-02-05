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

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.DistributedMap;
import inetsoft.util.config.InetsoftConfig;
import inetsoft.util.config.MetricsConfig;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Lazy(false)
public class MetricsPublisherService {
   public MetricsPublisherService(ScalingMetricsService metricsService) {
      this.metricsService = metricsService;
   }

   @PostConstruct
   public void init() {
      cluster = Cluster.getInstance();
      scalingMetricMap = Cluster.getInstance().getMap(SCALING_METRIC_MAP);
      MetricsConfig metricsConfig = InetsoftConfig.getInstance().getMetrics();

      if(metricsConfig != null && metricsConfig.getType() != null) {
         if(cluster.getLong(COUNTER_NAME).getAndIncrement() == 0) {
            // first instance, start task
            cluster.getScheduledExecutor().scheduleAtFixedRate(
               new PublishMetricsTask(), 120L, 60L, TimeUnit.SECONDS);
         }
      }
   }

   /**
    * Calculate and collect individual node metrics here which can later be combined in
    * PublishMetricsTask to calculate the final metric that will be used for scaling
    */
   @Scheduled(fixedRate = 60000L, initialDelay = 0L)
   public void calculateMetrics() {
      scalingMetricMap.put(cluster.getLocalMember(), metricsService.getServerUtilization());
   }

   private final ScalingMetricsService metricsService;
   private Cluster cluster;
   private DistributedMap<String, Double> scalingMetricMap;
   private static final String COUNTER_NAME = MetricsPublisherService.class.getName() + ".counter";
   public static final String SCALING_METRIC_MAP = MetricsPublisherService.class.getName() + ".scalingMetricMap";
}
