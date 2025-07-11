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

package inetsoft.web.admin.server;

import inetsoft.sree.internal.cluster.*;
import inetsoft.util.config.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Duration;
import java.util.ServiceLoader;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Lazy(false)
public class NodeProtectionService implements MembershipListener {

   /**
    * Initializes the service by choosing which NodeProtector implementation to use depending on the config
    */
   @PostConstruct
   public void init() {
      NodeProtectionConfig config = InetsoftConfig.getInstance().getNodeProtection();

      if(config != null && config.getType() != null) {
         for(NodeProtectorFactory factory : ServiceLoader.load(NodeProtectorFactory.class)) {
            if(config.getType().equals(factory.getType())) {
               nodeProtector = factory.create();
               break;
            }
         }

         ClusterConfig clusterConfig = InetsoftConfig.getInstance().getCluster();

         if(clusterConfig != null && !clusterConfig.isClientMode() &&
            clusterConfig.getMinSingleInstanceUptime() > 0L)
         {
            cluster = Cluster.getInstance();
            minUptime = Duration.ofSeconds(clusterConfig.getMinSingleInstanceUptime());
            cluster.addMembershipListener(this);
            updateSingleInstance();
         }
      }
   }

   @PreDestroy
   public void close() throws Exception {
      nodeProtector.close();

      if(cluster != null) {
         cluster.removeMembershipListener(this);
         cluster = null;
      }
   }

   @Scheduled(fixedDelay = 30000, initialDelay = 30000)
   public void checkSingleInstanceTimeout() {
      if(nodeProtector != null && minUptime != null) {
         lock.lock();

         try {
            if(instanceProtected && !sessionProtected && isMinUptimeExpired()) {
               nodeProtector.updateNodeProtection(false);
            }
         }
         finally {
            lock.unlock();
         }
      }
   }

   /**
    * Enables or disables protection for the node, so it will not be removed by scale-in operations
    * @param enabled  whether to enable or disable protection
    */
   public void updateNodeProtection(boolean enabled) {
      if(nodeProtector != null) {
         lock.lock();

         try {
            sessionProtected = enabled;

            if(!instanceProtected || isMinUptimeExpired()) {
               nodeProtector.updateNodeProtection(enabled);
            }
         }
         finally {
            lock.unlock();
         }
      }
   }

   /**
    * Get the status of the current node's protection against scale-in operations
    * @return  true if protection is enabled
    */
   public boolean getNodeProtection() {
      return nodeProtector != null && nodeProtector.getNodeProtection();
   }

   private void updateSingleInstance() {
      if(nodeProtector != null && minUptime != null) {
         lock.lock();

         try {
            boolean singleInstance = isSingleInstance();

            if(singleInstance != instanceProtected) {
               instanceProtected = singleInstance;

               if(singleInstance && !sessionProtected && !isMinUptimeExpired()) {
                  nodeProtector.updateNodeProtection(true);
               }
               else if(!singleInstance && !sessionProtected) {
                  nodeProtector.updateNodeProtection(false);
               }
            }
         }
         finally {
            lock.unlock();
         }
      }
   }

   /**
    * Returns the node protection expiration time in case of a failure to extend protection time.
    */
   public long getExpirationTime() {
      if(nodeProtector != null) {
         return nodeProtector.getExpirationTime();
      }

      return 0;
   }

   public void memberAdded(MembershipEvent event) {
      if(!event.isClient()) {
         updateSingleInstance();
      }
   }

   public void memberRemoved(MembershipEvent event) {
      if(!event.isClient()) {
         updateSingleInstance();
      }
   }

   private boolean isSingleInstance() {
      return cluster.getClusterNodes(false).size() == 1;
   }

   private Duration getUptime() {
      RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
      long uptimeMillis = bean.getUptime();
      return Duration.ofMillis(uptimeMillis);
   }

   private boolean isMinUptimeExpired() {
      return minUptime != null && getUptime().compareTo(minUptime) <= 0;
   }

   private Cluster cluster = null;
   private Duration minUptime = null;
   private NodeProtector nodeProtector;
   private boolean sessionProtected = false;
   private boolean instanceProtected = false;
   private final Lock lock = new ReentrantLock();
}
