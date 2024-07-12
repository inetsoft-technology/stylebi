/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.monitoring;

import inetsoft.sree.internal.cluster.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Service that handles scheduling status updates across services and cluster nodes.
 */
@Service
@Lazy(false)
public class MonitorSchedulingService implements MessageListener {
   @Autowired
   public MonitorSchedulingService(List<StatusUpdater> updaters,
                                   MonitoringDataService monitoringDataService)
   {
      this.updaters = updaters;
      this.monitoringDataService = monitoringDataService;
   }

   @PostConstruct
   public void startScheduling() {
      cluster = Cluster.getInstance();
      cluster.addMessageListener(this);

      if(cluster.getLong(COUNTER_NAME).getAndIncrement() == 0) {
         LocalDateTime now = LocalDateTime.now();
         int delay = (90 - now.getSecond()) % 30;

         // first instance, start task
         cluster.getScheduledExecutor().scheduleAtFixedRate(
            new MonitorSchedulingTask(), delay, 30L, TimeUnit.SECONDS);
      }
   }

   @PreDestroy
   public void stopScheduling() {
      if(cluster != null) {
         cluster.removeMessageListener(this);

         if(cluster.getLong(COUNTER_NAME).decrementAndGet() == 0) {
            // last instance, stop task
            cluster.getScheduledExecutor().shutdown();
            cluster.destroyScheduledExecutor();
         }
      }
   }

   @Override
   public void messageReceived(MessageEvent event) {
      if(event.getMessage() instanceof UpdateStatusMessage) {
         UpdateStatusMessage message = (UpdateStatusMessage) event.getMessage();
         long timestamp = message.getTimestamp();

         for(StatusUpdater updater : updaters) {
            try {
               updater.updateStatus(timestamp);
            }
            catch(Exception e) {
               LOG.warn("Failed to update status", e);
            }
         }

         monitoringDataService.update();
      }
   }

   private final List<StatusUpdater> updaters;
   private final MonitoringDataService monitoringDataService;
   private Cluster cluster;

   private static final String COUNTER_NAME = MonitorSchedulingService.class.getName() + ".counter";
   private static final String EXECUTOR_NAME =
      MonitorSchedulingService.class.getName() + ".executor";

   private static final Logger LOG = LoggerFactory.getLogger(MonitorSchedulingService.class);
}
