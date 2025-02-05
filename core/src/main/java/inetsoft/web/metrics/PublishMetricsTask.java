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

import java.io.Serializable;
import java.util.ServiceLoader;

import static inetsoft.web.metrics.MetricsPublisherService.SCALING_METRIC_MAP;

public class PublishMetricsTask implements Runnable, Serializable {
   public PublishMetricsTask() {
   }

   private void init() {
      if(cluster == null) {
         cluster = Cluster.getInstance();
      }

      if(publisher == null) {
         MetricsConfig metricsConfig = InetsoftConfig.getInstance().getMetrics();

         if(metricsConfig != null && metricsConfig.getType() != null) {
            for(MetricsPublisherFactory factory : ServiceLoader.load(MetricsPublisherFactory.class)) {
               if(metricsConfig.getType().equals(factory.getType())) {
                  publisher = factory.create();
                  break;
               }
            }
         }
      }
   }

   @Override
   public void run() {
      init();

      // collect all the metrics and compute the final metric that will be published
      DistributedMap<String, Double> map = cluster.getMap(SCALING_METRIC_MAP);
      int count = 0;
      double total = 0;

      for(String node : cluster.getClusterNodes()) {
         count++;
         total += map.getOrDefault(node, 0d);
      }

      total = total / count;
      publishMetric(total);
   }

   private void publishMetric(double clusterUtilization) {
      if(publisher != null) {
         publisher.pushMetrics(clusterUtilization);
      }
   }

   private transient Cluster cluster;
   private transient MetricsPublisher publisher;
}