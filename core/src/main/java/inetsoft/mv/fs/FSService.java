/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.mv.fs;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.util.ConfigurationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * FSService, the instance provides file system services.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public final class FSService {
   /**
    * Get the server node.
    */
   public static XServerNode getServer() {
      return getService().getServer0();
   }

   /**
    * Get the data node.
    */
   public static XDataNode getDataNode() {
      return getService().getDataNode0();
   }

   /**
    * Get the config.
    */
   public static FSConfig getConfig() {
      return getService().config;
   }

   /**
    * Get the service.
    */
   public static FSService getService() {
      if(service == null) {
         slock.lock();

         try {
            if(service == null) {
               service = new FSService();
            }
         }
         finally {
            slock.unlock();
         }
      }

      return service;
   }

   /**
    * Refresh the in-memory information from disk.
    */
   public static void refresh() {
      Cluster.getInstance().lockKey("mv.fs.update");

      try {
         getServer().getFSystem().refresh(getDataNode().getBSystem(), true);
      }
      finally {
         Cluster.getInstance().unlockKey("mv.fs.update");
      }
   }

   /**
    * Refresh the in-memory information from disk for nodes on cluster.
    */
   public static void refreshCluster(boolean wait) {
      Future<Collection<Void>> future = Cluster.getInstance().submitAll(new FSUpdater());

      if(wait) {
         try {
            future.get();
         }
         catch(Exception ex) {
            LOG.warn("Failed to refresh cluster", ex);
         }
      }
   }

   /**
    * Create an instance of FSService.
    */
   private FSService() {
      super();
   }

   /**
    * Get the data node.
    */
   private XDataNode getDataNode0() {
      if(data == null) {
         lock.lock();

         try {
            if(data == null) {
               data = new XDataNode(config);
            }
         }
         finally {
            lock.unlock();
         }
      }

      return data;
   }

   /**
    * Get the server node.
    */
   private XServerNode getServer0() {
      if(server == null) {
         lock.lock();

         try {
            if(!isServer0()) {
               return null;
            }

            if(server == null) {
               server = createServer();
            }
         }
         finally {
            lock.unlock();
         }
      }

      return server;
   }

   /**
    * Instantiate the data server.
    */
   private XServerNode createServer() {
      LOG.info("Starting data server");
      XServerNode server = new XServerNode(config);

      return server;
   }

   /**
    * Check if this host is the server node of the distributed system.
    */
   private boolean isServer0() {
      return true;
   }

   /**
    * The default implementation of FSConfig.
    */
   private static final class FSConfigImpl implements FSConfig {
      public FSConfigImpl() {
         super();
      }

      @Override
      public boolean isDesktop() {
         String val = SreeEnv.getProperty("fs.desktop");
         return "true".equals(val);
      }

      @Override
      public int getJobTimeout() {
         String val = SreeEnv.getProperty("fs.job.period");
         return val != null ? Integer.parseInt(val) : 10 * 60 * 60000;
      }

      @Override
      public int getJobCheckPeriod() {
         String val = SreeEnv.getProperty("fs.job.check.period");
         return val != null ? Integer.parseInt(val) : 500;
      }

      @Override
      public int getExpired() {
         String val = SreeEnv.getProperty("fs.map.expired");
         return val != null ? Integer.parseInt(val) : 10 * 60 * 60000;
      }

      // @temp larryl, this should be merged with MVClusterUtil.getWorkDir
      @Override
      public String getWorkDir(String node) {
         String val = SreeEnv.getProperty("mv.data.directory");

         if(val == null) { // backward compatibility (< 12.2)
            val = SreeEnv.getProperty("fs.workdir." + node);
         }

         if(val == null) {
            String home = ConfigurationContext.getContext().getHome();
            val = (home == null ? "/usr" : home) + "/bs";
         }

         return val;
      }

      private Set<FSConfigObserver> observers = new HashSet<>();
   }

   private static class FSUpdater implements Callable<Void>, Serializable {
      @Override
      public Void call() {
         try {
            // make sure new mv files are visible
            FSService.refresh();
         }
         catch(Exception e) {
            LOG.warn("Failed to update file system", e);
            throw new RuntimeException("Failed to update file system");
         }

         return null;
      }
   }

   private static FSService service;
   private static Lock slock = new ReentrantLock();
   private FSConfig config = new FSConfigImpl();
   private XServerNode server;
   private XDataNode data;
   private Lock lock = new ReentrantLock();
   private static final Logger LOG =
      LoggerFactory.getLogger(FSService.class);
}
