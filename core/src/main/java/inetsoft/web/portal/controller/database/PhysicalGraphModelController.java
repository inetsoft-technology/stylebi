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

import inetsoft.uql.erm.*;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.portal.model.database.*;
import inetsoft.web.portal.model.database.events.*;
import inetsoft.web.portal.model.database.graph.TableDetailJoinInfo;
import inetsoft.web.portal.model.database.graph.TableJoinInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.util.List;
import java.util.*;

import static inetsoft.web.portal.model.database.JoinGraphModel.SCALE_X;
import static inetsoft.web.portal.model.database.JoinGraphModel.SCALE_Y;

@RestController
@Lazy
public class PhysicalGraphModelController {
   @Autowired
   public PhysicalGraphModelController(RuntimePartitionService runtimePartitionService,
                                       PhysicalModelService physicalModelService,
                                       PhysicalGraphModelService graphService,
                                       PhysicalModelManagerService physicalModelManager)
   {
      this.runtimePartitionService = runtimePartitionService;
      this.physicalModelService = physicalModelService;
      this.graphService = graphService;
      this.physicalModelManager = physicalModelManager;
   }

   @PutMapping("/api/data/physicalmodel/graph/size/{runtimeId}")
   public void updateGraphPaneSize(@PathVariable("runtimeId") String runtimeId,
                                   @RequestBody Rectangle bounds)
   {
      graphService.updateGraphPaneSize(runtimeId, bounds.width, bounds.height);
   }

   @PutMapping("/api/data/physicalmodel/graph/node/width/{runtimeId}")
   public boolean updateGraphNodeWidth(@PathVariable("runtimeId") String runtimeId,
                                       @RequestParam("table") String table,
                                       @RequestParam("width") int width,
                                       @RequestParam(value = "alias", required = false) String alias)
   {
      width = (int) Math.round(width * 1.0 / SCALE_X);
      boolean changed = false;

      if(StringUtils.hasText(alias)) {
         RuntimePartitionService.RuntimeXPartition runtimePartition =
            this.runtimePartitionService.getRuntimePartition(runtimeId);
         XPartition partition = runtimePartition.getPartition();

         Rectangle box = partition.getRuntimeAliasTableBounds(alias).getBounds();

         if(box != null && box.width != width) {
            box.width = width;
            partition.setRuntimeAliasTableBounds(alias, box);
            changed = true;
         }
      }

      return graphService.updateGraphNodeWidth(runtimeId, table, width) || changed;
   }

   @PostMapping("/api/data/physicalmodel/graph")
   public JoinGraphModel physicalGraphModel(@RequestBody GetGraphModelEvent event)
      throws Exception
   {
      String ds = event.getDatasource();
      String physicalView = event.getPhysicalName();
      XDataModel dataModel = physicalModelService.getDataModel(ds, physicalView);
      XPartition partition = this.runtimePartitionService.getPartition(event.getRuntimeID());

      PhysicalModelDefinition pmModel =
         physicalModelService.createModel(ds, dataModel, partition,
            event.getTableJoinInfo() == null);

      return JoinGraphModel.convertModel(pmModel, event, partition);
   }

   @PutMapping("/api/data/physicalmodel/graph/layout/{col}/**")
   public void autoLayout(@PathVariable("col") boolean col,
                          @RemainingPath String runtimeId,
                          @RequestBody GetModelEvent event) throws Exception
   {
      graphService.layoutPhysicalModel(
         runtimeId, event.getDatasource(), event.getPhysicalName(), col);
   }

   @GetMapping("/api/data/physicalmodel/join-edit/open/{oldRuntimeId}")
   public String openJoinEditPane(@PathVariable("oldRuntimeId") String oldRuntimeId)
      throws Exception
   {
      return this.runtimePartitionService.openNewRuntimePartition(oldRuntimeId);
   }

   @GetMapping("/api/data/physicalmodel/join-edit/close")
   public void closeJoinEditPane(@RequestParam("originRuntimeId") String originRuntimeId,
                                 @RequestParam("newRuntimeId") String newRuntimeId,
                                 @RequestParam(value="save", required = false) boolean save)
   {
      this.runtimePartitionService.closeRuntimePartition(originRuntimeId, newRuntimeId, save);
   }

   /**
    * Move table. need fix scale
    * {@see PhysicalGraphModel#fixBounds}
    */
   @PutMapping("/api/data/physicalmodel/graph/move")
   public void moveTable(@RequestBody MoveGraphEvent event) {
      Rectangle rectangle = event.getBounds();

      if(rectangle == null) {
         return;
      }

      String runtimeId = event.getRuntimeId();
      String tableName = event.getTable();
      String aliasName = event.getAlias();

      RuntimePartitionService.RuntimeXPartition rp
         = this.runtimePartitionService.getRuntimePartition(runtimeId);
      XPartition partition = rp.getPartition();
      Rectangle oldBounds = null;

      if(tableName != null) {
         oldBounds = partition.getBounds(tableName);
      }
      else if(aliasName != null) {
         oldBounds = partition.getRuntimeAliasTableBounds(aliasName);
      }

      int width = oldBounds.width;
      int height = oldBounds.height; // rectangle.height is default portal graph height
      int x = (int) Math.round(rectangle.getX() / SCALE_X);
      int y = (int) Math.round(rectangle.getY() / SCALE_Y);

      rectangle = new Rectangle(x, y, width, height);

      if(tableName != null) {
         rp.setBounds(tableName, rectangle);
      }

      if(!StringUtils.isEmpty(aliasName)) {
         partition.setRuntimeAliasTableBounds(aliasName, rectangle);
      }
   }

   @PostMapping("/api/data/physicalmodel/join")
   public void createJoin(@RequestBody TableDetailJoinInfo joinInfo) throws Exception {
      XPartition partition = this.runtimePartitionService.getPartition(joinInfo.getRuntimeId());

      XRelationship join = PhysicalModelService.createJoin(joinInfo.getSourceTable(),
         JoinGraphModel.makeDefaultJoin(joinInfo.getSourceTable(), joinInfo.getSourceColumn(),
            joinInfo.getTargetTable(), joinInfo.getTargetColumn(),
            physicalModelService.getMetaDataProvider(joinInfo.getRuntimeId()),
            partition));

      partition.addRelationship(join);
   }

   @DeleteMapping("/api/data/physicalmodel/table/{runtimeId}")
   public void clearTable(@PathVariable("runtimeId") String runtimeId) {
      XPartition partition = this.runtimePartitionService.getPartition(runtimeId);
      partition.clearTable();
      partition.clearRelationship();
   }

   @DeleteMapping("/api/data/physicalmodel/join/{runtimeId}")
   public void clearJoin(@PathVariable("runtimeId") String runtimeId) {
      XPartition partition = this.runtimePartitionService.getPartition(runtimeId);
      partition.clearRelationship();
      partition.removeAllAutoAliases();
   }

   @PostMapping("/api/data/physicalmodel/join/delete")
   public void deleteJoin(@RequestBody TableDetailJoinInfo joinInfo) {
      deleteJoins(joinInfo);
   }

   @PostMapping("/api/data/physicalmodel/joins/delete")
   public void deleteJoins(@RequestBody TableJoinInfo joinInfo)
   {
      XPartition partition = this.runtimePartitionService.getPartition(joinInfo.getRuntimeId());

      // fix source and target table as design mode table.
      XPartition applyAliasPartition = partition.applyAutoAliases();
      joinInfo.setSourceTable(DatabaseModelUtil.getOutgoingAutoAliasSourceOrTable(
         joinInfo.getSourceTable(), partition, applyAliasPartition));
      joinInfo.setTargetTable(DatabaseModelUtil.getOutgoingAutoAliasSourceOrTable(
         joinInfo.getTargetTable(), partition, applyAliasPartition));

      List<XRelationship> joins = this.findJoin(joinInfo);

      joins.forEach(join -> physicalModelManager.deleteJoin(partition, join));
   }

   @GetMapping("/api/data/physicalmodel/graph/node/refresh")
   public void refreshTable(String runtimeId, String table) {
      XPartition partition = this.runtimePartitionService.getPartition(runtimeId);
      XPartition.PartitionTable partitionTable = partition.getPartitionTable(table);

      if(partitionTable != null) {
         partitionTable.removeMetaData();
      }
   }

   @PutMapping("/api/data/physicalmodel/join")
   public void editJoin(@RequestBody EditJoinEvent event) {
      List<XRelationship> joins = findJoin(event.getDetailJoinInfo());

      if(joins.size() < 1) {
         return;
      }

      XRelationship join = joins.get(0);

      event.getJoinModel().store(join.getDependentTable(), join);
   }

   @PutMapping("/api/data/physicalmodel/graph/alias/status")
   public boolean hasDuplicateCheck(@RequestBody CheckTableAliasEvent event) {
      return physicalModelManager.hasDuplicateCheck(event);
   }

   @PostMapping("/api/data/physicalmodel/graph/alias")
   public StringWrapper createAlias(@RequestParam("runtimeId") String runtimeId,
                                    @RequestParam("table") String table,
                                    @RequestParam("alias") String alias,
                                    @RequestParam(value = "oldAlias", required = false) String oldAlias)
      throws Exception
   {
      String invalidMessage = physicalModelManager.checkAliasValid(runtimeId, alias, oldAlias);

      if(invalidMessage != null) {
         StringWrapper wrapper = new StringWrapper();
         wrapper.setBody(invalidMessage);
         return wrapper;
      }

      if(StringUtils.isEmpty(oldAlias)) {
         graphService.createAlias(runtimeId, table, alias);
         return null;
      }

      graphService.editAlias(alias, oldAlias, runtimeId);

      return null;
   }

   private List<XRelationship> findJoin(TableJoinInfo joinInfo) {
      XPartition partition = this.runtimePartitionService.getPartition(joinInfo.getRuntimeId());
      Enumeration<XRelationship> relationships = partition.getRelationships(true);

      List<XRelationship> result = new ArrayList<>();

      while(relationships.hasMoreElements()) {
         XRelationship xRelationship = relationships.nextElement();

         if(findIt(joinInfo, xRelationship)) {
            result.add(xRelationship);
         }
      }

      return result;
   }

   private boolean findIt(TableJoinInfo joinInfo, XRelationship xRelationship) {
      String sourceTable = xRelationship.getDependentTable();
      String targetTable = xRelationship.getIndependentTable();

      boolean result = sourceTable.equals(joinInfo.getSourceTable())
         && targetTable.equals(joinInfo.getTargetTable());

      if(result && (joinInfo instanceof TableDetailJoinInfo)) {
         TableDetailJoinInfo info = (TableDetailJoinInfo) joinInfo;
         String sourceColumn = xRelationship.getDependentColumn();
         String targetColumn = xRelationship.getIndependentColumn();

         result = sourceColumn.equals(info.getSourceColumn())
            && targetColumn.equals(info.getTargetColumn());
      }

      return result;
   }

   private final RuntimePartitionService runtimePartitionService;
   private final PhysicalModelService physicalModelService;
   private final PhysicalGraphModelService graphService;
   private final PhysicalModelManagerService physicalModelManager;
}
