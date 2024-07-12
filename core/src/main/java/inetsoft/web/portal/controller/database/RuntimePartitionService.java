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
package inetsoft.web.portal.controller.database;

import com.google.common.collect.Sets;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XPartition;
import inetsoft.web.portal.model.database.graph.PhysicalGraphLayout;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.awt.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class RuntimePartitionService {
   public RuntimePartitionService() {
   }

   /**
    * create a new model.
    * @param partition
    * @param database
    * @return
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
    * @param dataModel
    * @param name
    * @return
    */
   public RuntimeXPartition openModel(XDataModel dataModel, String name, String parent) {
      boolean isExtended = !StringUtils.isEmpty(parent);
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
      mapLock.lock();

      try {
         rmap.put(runtimeXPartition.getId(), runtimeXPartition);
         heartBeatMap.put(runtimeXPartition.getId(), new Date());
      }
      finally {
         mapLock.unlock();
      }
   }

   public String generateRuntimeId(String name) {
      return name + System.currentTimeMillis();
   }

   /**
    * Update the physical model of runtime.
    * @param id
    * @param newPartition
    */
   public void updatePartition(String id, XPartition newPartition) {
      RuntimeXPartition runtimeXPartition = rmap.get(id);

      if(runtimeXPartition != null) {
         runtimeXPartition.setPartition(newPartition);
      }
   }

   /**
    * Get the physical model from runtime.
    * @param id
    * @return
    */
   public XPartition getPartition(String id) {
      RuntimeXPartition runtimeXPartition = rmap.get(id);

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
    * @param id
    * @return
    */
   public RuntimeXPartition getRuntimePartition(String id) {
      return rmap.get(id);
   }

   /**
    * Destroy the runtime.
    * @param id
    */
   public void destroy(String id) {
      mapLock.lock();

      try {
         rmap.remove(id);
         heartBeatMap.remove(id);
      }
      finally {
         mapLock.unlock();
      }
   }

   /**
    * Process the heartbeat.
    * @param id
    * @return
    */
   public boolean touch(String id) {
      boolean expired = isExpired(id);

      if(!expired) {
         heartBeatMap.put(id, new Date());
      }

      return expired;
   }

   /**
    * Destroy the time out runtime.
    */
   public void checkTimeout() {
      long now = System.currentTimeMillis();
      long minute3 = now - 180000; // 3 min ago

      mapLock.lock();

      try {
         Set<String> keys = new HashSet<>(rmap.keySet());

         keys.forEach(id -> {
            Date lastTime = heartBeatMap.get(id);
            boolean expired = lastTime.getTime() < minute3;

            if(expired) {
               destroy(id);
            }
         });
      }
      finally {
         mapLock.unlock();
      }
   }

   public boolean isExpired(String id) {
      if(StringUtils.isEmpty(id)) {
         return true;
      }

      if(rmap.get(id) != null) {
         return false;
      }

      return true;
   }

   public static class RuntimeXPartition implements Cloneable {
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
      private Set<String> movedTables = Sets.newConcurrentHashSet();
   }

   private static final Lock mapLock = new ReentrantLock();
   private static final Map<String, RuntimeXPartition> rmap = new ConcurrentHashMap<>();
   private static final Map<String, Date> heartBeatMap = new ConcurrentHashMap<>();
}
