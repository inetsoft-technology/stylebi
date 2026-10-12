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

import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XPartition;
import inetsoft.web.portal.model.database.*;
import inetsoft.web.portal.model.database.graph.PhysicalGraphLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@Component
public class PhysicalGraphModelService {
   @Autowired
   public PhysicalGraphModelService(RuntimePartitionService partitionService,
                                    PhysicalModelService modelService,
                                    PhysicalModelManagerService managerService)
   {
      this.partitionService = partitionService;
      this.modelService = modelService;
      this.managerService = managerService;
   }

   public void updateGraphPaneSize(String runtimeId, int width, int height) {
      Boolean updated = partitionService.update(runtimeId, runtimePartition -> {
         runtimePartition.setGraphWidth(width);
         runtimePartition.setGraphHeight(height);
         return Boolean.TRUE;
      });

      if(updated == null) {
         LOG.info("Runtime partition is null for {}!", runtimeId);
      }
   }

   public boolean updateGraphNodeWidth(String runtimeId, String table, int width) {
      RuntimePartitionService.RuntimeXPartition runtimePartition =
         this.partitionService.getRuntimePartition(runtimeId);

      if(runtimePartition == null) {
         LOG.info("Runtime partition is null for {}!", runtimeId);

         return false;
      }

      // the width is sent for every node shown, only lock the runtime when it changes
      if(!setGraphNodeWidth(runtimePartition, table, width)) {
         return false;
      }

      return Boolean.TRUE.equals(
         partitionService.update(runtimeId, rp -> setGraphNodeWidth(rp, table, width)));
   }

   private static boolean setGraphNodeWidth(RuntimePartitionService.RuntimeXPartition runtime,
                                            String table, int width)
   {
      XPartition partition = runtime.getPartition();
      // only self node.
      XPartition.PartitionTable partitionTable = partition.getPartitionTable(table, true);

      if(partitionTable == null) {
         return false;
      }

      Rectangle bounds = partition.getBounds(partitionTable);

      if(bounds.width != width) {
         bounds.width = width;
         return true;
      }

      return false;
   }

   public void layoutPhysicalModel(String runtimeId, String dataSource, String modelName)
      throws Exception
   {
      layoutPhysicalModel(runtimeId, dataSource, modelName, false);
   }

   public void layoutPhysicalModel(String runtimeId, String dataSource, String modelName,
                                   boolean colPriority)
      throws Exception
   {
      XDataModel dataModel = modelService.getDataModel(dataSource, modelName);
      // Bug #78428, the layout reads meta-data, so it is computed on a copy of the runtime,
      // and only the bounds it sets are applied to the runtime
      RuntimePartitionService.RuntimeXPartition rPartition
         = this.partitionService.getRuntimePartition(runtimeId);
      RuntimePartitionService.checkDataSource(rPartition, dataSource);
      XPartition partition = rPartition.getPartition();

      PhysicalModelDefinition pmModel = modelService.createModel(
         dataSource, dataModel, partition, true);

      JoinGraphModel physicalGraphModel
         = JoinGraphModel.convertModel(pmModel, null, partition, true);

      GraphViewModel graphModel = physicalGraphModel.getGraphViewModel();
      List<LayoutBounds> layout = new ArrayList<>();

      new PhysicalGraphLayout(graphModel, rPartition, colPriority) {
         @Override
         public void setBounds(String name, Rectangle bounds, boolean runtime) {
            super.setBounds(name, bounds, runtime);
            layout.add(new LayoutBounds(name, bounds.getBounds(), runtime));
         }
      }.layout();

      partitionService.update(runtimeId, rp -> {
         XPartition fresh = rp.getPartition();

         for(LayoutBounds bounds : layout) {
            // skip a table removed meanwhile
            if(bounds.runtime() && fresh.getRuntimeAliasTableBounds(bounds.name()) != null ||
               fresh.getPartitionTable(bounds.name()) != null)
            {
               rp.setBounds(bounds.name(), bounds.bounds().getBounds(), bounds.runtime());
            }
         }

         return null;
      });
   }

   public void createAlias(String runtimeId, String table, String alias) throws Exception {
      // Bug #78428, fixing the bounds reads meta-data, so they are computed on a copy of the
      // runtime, and the alias is then added to the runtime with them
      RuntimePartitionService.RuntimeXPartition runtimePartition =
         this.partitionService.getRuntimePartition(runtimeId);
      XPartition partition = runtimePartition.getPartition();
      addAliasTable(partition, table, alias);
      Rectangle bounds = modelService.fixTableBounds(runtimeId, partition, alias);

      partitionService.update(runtimeId, rp -> {
         XPartition fresh = rp.getPartition();
         addAliasTable(fresh, table, alias);

         if(bounds != null && fresh.getPartitionTable(alias) != null) {
            fresh.setBounds(alias, bounds.getBounds());
         }

         return null;
      });
   }

   private static void addAliasTable(XPartition partition, String table, String alias) {
      String sourceTable = DatabaseModelUtil.getOutgoingAutoAliasSource(table, partition);
      XPartition.PartitionTable temp = partition.getPartitionTable(sourceTable);

      if(temp != null && temp.getType() == inetsoft.uql.erm.PartitionTable.VIEW) {
         partition.addTable(
            alias, temp.getType(), temp.getSql(), temp.getCatalog(), temp.getSchema());
      }
      else if(temp != null) {
         partition.addTable(alias, temp.getCatalog(), temp.getSchema());
      }
      else {
         partition.addTable(alias, new Rectangle(-1, -1, -1, -1));
      }

      partition.setAliasTable(alias, sourceTable);
   }

   public void editAlias(String alias, String oldAlias, String runtimeId) {
      partitionService.update(runtimeId, runtimePartition -> {
         managerService.editAlias(alias, oldAlias, runtimePartition);
         return null;
      });
   }

   /**
    * The bounds of a table set by an auto layout.
    */
   private record LayoutBounds(String name, Rectangle bounds, boolean runtime) {
   }

   private final RuntimePartitionService partitionService;
   private final PhysicalModelService modelService;
   private final PhysicalModelManagerService managerService;
   private static final Logger LOG = LoggerFactory.getLogger(PhysicalGraphModelService.class);
}
