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
      RuntimePartitionService.RuntimeXPartition runtimePartition =
         this.partitionService.getRuntimePartition(runtimeId);

      if(runtimePartition == null) {
         LOG.info("Runtime partition is null for {}!", runtimeId);
         return;
      }

      runtimePartition.setGraphWidth(width);
      runtimePartition.setGraphHeight(height);
   }

   public boolean updateGraphNodeWidth(String runtimeId, String table, int width) {
      RuntimePartitionService.RuntimeXPartition runtimePartition =
         this.partitionService.getRuntimePartition(runtimeId);

      if(runtimePartition == null) {
         LOG.info("Runtime partition is null for {}!", runtimeId);

         return false;
      }

      XPartition partition = runtimePartition.getPartition();
      // only self node.
      XPartition.PartitionTable partitionTable = partition.getPartitionTable(table, true);

      if(partitionTable == null) {
         return false;
      }

      Rectangle bounds = partition.getBounds(partitionTable);
      boolean changed = false;

      if(bounds.width != width) {
         bounds.width = width;
         changed = true;
      }

      return changed;
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
      RuntimePartitionService.RuntimeXPartition rPartition
         = this.partitionService.getRuntimePartition(runtimeId);
      XPartition partition = rPartition.getPartition();

      PhysicalModelDefinition pmModel = modelService.createModel(
         dataSource, dataModel, partition, true);

      JoinGraphModel physicalGraphModel
         = JoinGraphModel.convertModel(pmModel, null, partition, true);

      GraphViewModel graphModel = physicalGraphModel.getGraphViewModel();
      new PhysicalGraphLayout(graphModel, rPartition, colPriority).layout();
   }

   public void createAlias(String runtimeId, String table, String alias) throws Exception {
      XPartition partition = this.partitionService.getPartition(runtimeId);
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
      modelService.fixTableBounds(runtimeId, partition, alias);
   }

   public void editAlias(String alias, String oldAlias, String runtimeId) {
      RuntimePartitionService.RuntimeXPartition runtimePartition =
         this.partitionService.getRuntimePartition(runtimeId);
      managerService.editAlias(alias, oldAlias, runtimePartition);
   }

   private final RuntimePartitionService partitionService;
   private final PhysicalModelService modelService;
   private final PhysicalModelManagerService managerService;
   private static final Logger LOG = LoggerFactory.getLogger(PhysicalGraphModelService.class);
}
