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
package inetsoft.web.portal.controller.database;

import com.google.common.collect.Sets;
import inetsoft.cluster.ClusterProxy;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XPartition;
import inetsoft.web.portal.model.database.graph.PhysicalGraphLayout;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.cache.Cache;
import javax.cache.expiry.Duration;
import javax.cache.expiry.TouchedExpiryPolicy;
import java.awt.*;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@ClusterProxy
public class RuntimePartitionService {
   public RuntimePartitionService() {
      cache = Cluster.getInstance().getCache(
         CACHE_NAME, false, new TouchedExpiryPolicy(new Duration(TimeUnit.MINUTES, 3L)));
   }

   /**
    * create a new model.
    */
   public RuntimeXPartition createModel(XPartition partition, String database) {
      RuntimeXPartition runtimeXPartition =
         new RuntimeXPartition(partition, generateRuntimeId(partition.getName()),
            database);

      saveRuntimePartition(runtimeXPartition);

      return runtimeXPartition;
   }

   /**
    * Open the physical model create a runtime.
    */
   public RuntimeXPartition openModel(XDataModel dataModel, String name, String parent) {
      boolean isExtended = StringUtils.hasText(parent);
      XPartition partition = !isExtended ? dataModel.getPartition(name) :
         dataModel.getPartition(parent);

      if(partition == null) {
         return null;
      }

      if(isExtended) {
         partition = partition.getPartition(name);

         if(partition == null) {
            return null;
         }
      }

      RuntimeXPartition runtimeXPartition =
         new RuntimeXPartition((XPartition) partition.deepClone(true), generateRuntimeId(name),
            dataModel.getDataSource());

      saveRuntimePartition(runtimeXPartition);

      return runtimeXPartition;
   }

   public String openNewRuntimePartition(String oldId) throws Exception {
      RuntimeXPartition partition = (RuntimeXPartition) this.getRuntimePartition(oldId).clone();
      String newId = generateRuntimeId(partition.partition.getName());
      partition.setId(newId);

      saveRuntimePartition(partition);

      return newId;
   }

   public void closeRuntimePartition(String originRuntimeId, String newRuntimeId, boolean save) {
      RuntimePartitionService.RuntimeXPartition newPartition
         = this.getRuntimePartition(newRuntimeId);
      RuntimePartitionService.RuntimeXPartition oldPartition
         = this.getRuntimePartition(originRuntimeId);

      if(oldPartition == null || newPartition == null) {
         return;
      }

      if(save) {
         newPartition.setId(originRuntimeId);
         saveRuntimePartition(newPartition);
      }

      touch(originRuntimeId);
      destroy(newRuntimeId);
   }

   public void saveRuntimePartition(RuntimeXPartition runtimeXPartition) {
      cache.put(runtimeXPartition.getId(), runtimeXPartition);
   }

   public static String generateRuntimeId(String name) {
      return name + UUID.randomUUID().toString().replace("-", "");
   }

   /**
    * Update the physical model of runtime.
    */
   public void updatePartition(String id, XPartition newPartition) {
      RuntimeXPartition runtimeXPartition = cache.get(id);

      if(runtimeXPartition != null) {
         runtimeXPartition.setPartition(newPartition);
         cache.put(id, runtimeXPartition);
      }
   }

   /**
    * Get the physical model from runtime.
    */
   public XPartition getPartition(String id) {
      RuntimeXPartition runtimeXPartition = cache.get(id);

      if(runtimeXPartition == null) {
         return null;
      }

      XPartition partition = runtimeXPartition.getPartition();

      if(partition == null) {
         throw new RuntimeException("Partition is null for id: " + id);
      }

      return partition;
   }

   /**
    * get run time partition.
    */
   public RuntimeXPartition getRuntimePartition(String id) {
      return cache.get(id);
   }

   /**
    * Destroy the runtime.
    */
   public void destroy(String id) {
      cache.remove(id);
   }

   /**
    * Process the heartbeat.
    */
   public boolean touch(String id) {
      return cache.get(id) != null;
   }

   public boolean isExpired(String id) {
      if(!StringUtils.hasText(id)) {
         return true;
      }

      return cache.get(id) == null;
   }

   private final Cache<String, RuntimeXPartition> cache;
   public static final String CACHE_NAME =
      "inetsoft.web.portal.controller.database.RuntimePartitionService.cache";

   public static class RuntimeXPartition implements Cloneable, Serializable {
      public RuntimeXPartition() {
      }

      public RuntimeXPartition(XPartition partition, String id, String dataSource) {
         this.partition = partition;
         this.id = id;
         this.dataSource = dataSource;
      }

      public XPartition getPartition() {
         return partition;
      }

      public void setPartition(XPartition partition) {
         this.partition = partition;
      }

      public String getId() {
         return id;
      }

      public void setId(String id) {
         this.id = id;
      }

      public String getDataSource() {
         return dataSource;
      }

      @Override
      protected Object clone() throws CloneNotSupportedException {
         RuntimeXPartition clone = (RuntimeXPartition) super.clone();
         clone.partition = (XPartition) this.partition.clone();
         clone.dataSource = this.dataSource;
         clone.id = this.id;
         clone.graphWidth = this.graphWidth;
         clone.graphHeight = this.graphHeight;

         return clone;
      }

      public Set<String> getMovedTables() {
         return Collections.unmodifiableSet(movedTables);
      }

      public void addMovedTable(String table) {
         this.movedTables.add(table);
      }

      public boolean removeMovedTable(String name) {
         return movedTables.remove(name);
      }

      public void clearMovedTables() {
         movedTables.clear();
      }

      public void setBounds(String tableName, Rectangle bounds) {
         this.setBounds(tableName, bounds, false);
      }

      public void setBounds(String tableName, Rectangle bounds, boolean runtime) {
         partition.setBounds(tableName, bounds, runtime);
         this.addMovedTable(tableName);
      }

      public int getGraphWidth() {
         return graphWidth;
      }

      public void setGraphWidth(int graphWidth) {
         this.graphWidth = graphWidth;
      }

      public int getGraphHeight() {
         return graphHeight;
      }

      public void setGraphHeight(int graphHeight) {
         this.graphHeight = graphHeight;
      }

      private XPartition partition;
      private String id;
      private String dataSource;
      private int graphWidth = PhysicalGraphLayout.DEFAULT_VIEWPORT_WIDTH;
      private int graphHeight = PhysicalGraphLayout.DEFAULT_VIEWPORT_HEIGHT;
      private final Set<String> movedTables = Sets.newConcurrentHashSet();
   }
}
