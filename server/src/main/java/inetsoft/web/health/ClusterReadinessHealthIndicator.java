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

package inetsoft.web.health;

import inetsoft.sree.internal.cluster.Cluster;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.availability.ReadinessStateHealthIndicator;
import org.springframework.boot.availability.*;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ClusterReadinessHealthIndicator extends ReadinessStateHealthIndicator {
   public ClusterReadinessHealthIndicator(ApplicationAvailability availability) {
      super(availability);
   }

   @PostConstruct
   public void initClusterReadiness() {
      this.executor = Executors.newSingleThreadExecutor();
      this.executor.submit(() -> {
         try {
            Cluster.getInstance();
            clusterReady.set(true);
            LOG.info("Cluster is ready");
         }
         catch(Exception e) {
            LOG.error("Failed to initialize cluster, pod will not accept traffic", e);
         }
         finally {
            ExecutorService exec = this.executor;

            if(exec != null) {
               exec.shutdown();
               this.executor = null;
            }
         }
      });
   }

   @PreDestroy
   public void stopExecutor() {
      ExecutorService exec = this.executor;

      if(exec != null) {
         exec.shutdown();
         this.executor = null;
      }
   }

   @Override
   protected AvailabilityState getState(ApplicationAvailability applicationAvailability) {
      AvailabilityState state = super.getState(applicationAvailability);

      if(state == ReadinessState.ACCEPTING_TRAFFIC) {
         return clusterReady.get() ? state : ReadinessState.REFUSING_TRAFFIC;
      }

      return state;
   }

   private final AtomicBoolean clusterReady = new AtomicBoolean(false);
   private ExecutorService executor;
   private static final Logger LOG = LoggerFactory.getLogger(ClusterReadinessHealthIndicator.class);
}
