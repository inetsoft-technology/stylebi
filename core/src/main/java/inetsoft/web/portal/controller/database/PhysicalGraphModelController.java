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
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import inetsoft.sree.security.*;

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

   @Secured(@RequiredPermission(
      resourceType = ResourceType.PORTAL_TAB,
      resource = "Data",
      actions = ResourceAction.ACCESS
   ))
   @PutMapping("/api/data/physicalmodel/graph/size/{runtimeId}")
   public void updateGraphPaneSize(@PathVariable("runtimeId") String runtimeId,
                                   @RequestBody Rectangle bounds)
   {
      graphService.updateGraphPaneSize(runtimeId, bounds.width, bounds.height);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.PORTAL_TAB,
      resource = "Data",
      actions = ResourceAction.ACCESS
   ))
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
         int aliasWidth = width;

         // the width is sent for every node shown, only lock the runtime when it changes
         if(setAliasWidth(runtimePartition.getPartition(), alias, aliasWidth)) {
            changed = Boolean.TRUE.equals(runtimePartitionService.update(
               runtimeId, rp -> setAliasWidth(rp.getPartition(), alias, aliasWidth)));
         }
      }

      return graphService.updateGraphNodeWidth(runtimeId, table, width) || changed;
   }

   private static boolean setAliasWidth(XPartition partition, String alias, int width) {
      Rectangle rectangle = partition.getRuntimeAliasTableBounds(alias);

      if(rectangle != null) {
         Rectangle box = rectangle.getBounds();

         if(box != null && box.width != width) {
            box.width = width;
            partition.setRuntimeAliasTableBounds(alias, box);
            return true;
         }
      }

      return false;
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.PORTAL_TAB,
      resource = "Data",
      actions = ResourceAction.ACCESS
   ))
   @PostMapping("/api/data/physicalmodel/graph")
   public JoinGraphModel physicalGraphModel(@RequestBody GetGraphModelEvent event)
      throws Exception
   {
      String ds = event.getDatasource();
      String physicalView = event.getPhysicalName();
      RuntimePartitionService.checkDataSource(
         runtimePartitionService.getRuntimePartition(event.getRuntimeID()), ds);
      XDataModel dataModel = physicalModelService.getDataModel(ds, physicalView);
      XPartition partition = this.runtimePartitionService.getPartition(event.getRuntimeID());
      boolean autoJoin = event.getTableJoinInfo() != null &&
         event.getTableJoinInfo().isAutoCreateColumnJoin();
      Set<XRelationship> oldJoins = Collections.newSetFromMap(new IdentityHashMap<>());

      if(autoJoin) {
         oldJoins.addAll(Collections.list(partition.getRelationships()));
      }

      PhysicalModelDefinition pmModel =
         physicalModelService.createModel(ds, dataModel, partition,
            event.getTableJoinInfo() == null);

      JoinGraphModel graphModel = JoinGraphModel.convertModel(pmModel, event, partition);

      if(autoJoin) {
         // Bug #78428, the auto joins are created on a copy of the runtime from meta-data, add
         // only them to the runtime
         List<XRelationship> autoJoins = new ArrayList<>();
         Collections.list(partition.getRelationships()).stream()
            .filter(join -> !oldJoins.contains(join))
            .forEach(autoJoins::add);

         if(!autoJoins.isEmpty()) {
            runtimePartitionService.update(event.getRuntimeID(), rp -> {
               XPartition fresh = rp.getPartition();

               for(XRelationship join : autoJoins) {
                  if(!JoinGraphModel.joinExist(fresh, join)) {
                     fresh.addRelationship(join);
                  }
               }

               return null;
            });
         }
      }

      return graphModel;
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.PORTAL_TAB,
      resource = "Data",
      actions = ResourceAction.ACCESS
   ))
   @PutMapping("/api/data/physicalmodel/graph/layout/{col}/**")
   public void autoLayout(@PathVariable("col") boolean col,
                          @RemainingPath String runtimeId,
                          @RequestBody GetModelEvent event) throws Exception
   {
      graphService.layoutPhysicalModel(
         runtimeId, event.getDatasource(), event.getPhysicalName(), col);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.PORTAL_TAB,
      resource = "Data",
      actions = ResourceAction.ACCESS
   ))
   @GetMapping("/api/data/physicalmodel/join-edit/open/{oldRuntimeId}")
   public String openJoinEditPane(@PathVariable("oldRuntimeId") String oldRuntimeId)
      throws Exception
   {
      return this.runtimePartitionService.openNewRuntimePartition(oldRuntimeId);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.PORTAL_TAB,
      resource = "Data",
      actions = ResourceAction.ACCESS
   ))
   @GetMapping("/api/data/physicalmodel/join-edit/close")
   public void closeJoinEditPane(@RequestParam("originRuntimeId") String originRuntimeId,
                                 @RequestParam("newRuntimeId") String newRuntimeId,
                                 @RequestParam(value="save", required = false) boolean save)
   {
      this.runtimePartitionService.closeRuntimePartition(originRuntimeId, newRuntimeId, save, rp -> {
         XPartition partition = rp.getPartition();

         // the pane keeps the auto-alias of a deleted join so that re-creating it keeps the
         // same alias, remove the ones that are not joined anymore
         if(partition != null) {
            physicalModelManager.removeOrphanAutoAliases(partition);
            // tables joined in the pane (created or dragged) keep the alias the user chose,
            // don't restore the auto-aliases removed from them earlier
            rp.forgetJoinedRemovedIncomingJoins();
         }
      });
   }

   /**
    * Move table. need fix scale
    * {@see PhysicalGraphModel#fixBounds}
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.PORTAL_TAB,
      resource = "Data",
      actions = ResourceAction.ACCESS
   ))
   @PutMapping("/api/data/physicalmodel/graph/move")
   public void moveTable(@RequestBody MoveGraphEvent event) {
      Rectangle rectangle = event.getBounds();

      if(rectangle == null) {
         return;
      }

      String runtimeId = event.getRuntimeId();
      String tableName = event.getTable();
      String aliasName = event.getAlias();
      Rectangle moved = rectangle;

      runtimePartitionService.update(runtimeId, rp -> {
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
         int x = (int) Math.round(moved.getX() / SCALE_X);
         int y = (int) Math.round(moved.getY() / SCALE_Y);

         Rectangle bounds = new Rectangle(x, y, width, height);

         if(tableName != null) {
            rp.setBounds(tableName, bounds);
         }

         if(!StringUtils.isEmpty(aliasName)) {
            partition.setRuntimeAliasTableBounds(aliasName, bounds);
         }

         return null;
      });
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.PORTAL_TAB,
      resource = "Data",
      actions = ResourceAction.ACCESS
   ))
   @PostMapping("/api/data/physicalmodel/join")
   public void createJoin(@RequestBody TableDetailJoinInfo joinInfo) throws Exception {
      XPartition partition = this.runtimePartitionService.getPartition(joinInfo.getRuntimeId());

      // Bug #78428, the default join reads meta-data, so it is made from a copy of the runtime
      XRelationship join = PhysicalModelService.createJoin(joinInfo.getSourceTable(),
         JoinGraphModel.makeDefaultJoin(joinInfo.getSourceTable(), joinInfo.getSourceColumn(),
            joinInfo.getTargetTable(), joinInfo.getTargetColumn(),
            physicalModelService.getMetaDataProvider(joinInfo.getRuntimeId()),
            partition));

      runtimePartitionService.update(joinInfo.getRuntimeId(), rp -> {
         rp.getPartition().addRelationship(join);
         return null;
      });
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.PORTAL_TAB,
      resource = "Data",
      actions = ResourceAction.ACCESS
   ))
   @DeleteMapping("/api/data/physicalmodel/table/{runtimeId}")
   public void clearTable(@PathVariable("runtimeId") String runtimeId) {
      runtimePartitionService.update(runtimeId, rp -> {
         XPartition partition = rp.getPartition();
         partition.clearTable();
         partition.clearRelationship();
         rp.clearRemovedIncomingJoins();
         return null;
      });
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.PORTAL_TAB,
      resource = "Data",
      actions = ResourceAction.ACCESS
   ))
   @DeleteMapping("/api/data/physicalmodel/join/{runtimeId}")
   public void clearJoin(@PathVariable("runtimeId") String runtimeId) {
      runtimePartitionService.update(runtimeId, rp -> {
         XPartition partition = rp.getPartition();
         partition.clearRelationship();
         partition.removeAllAutoAliases();
         rp.clearRemovedIncomingJoins();
         return null;
      });
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.PORTAL_TAB,
      resource = "Data",
      actions = ResourceAction.ACCESS
   ))
   @PostMapping("/api/data/physicalmodel/join/delete")
   public void deleteJoin(@RequestBody TableDetailJoinInfo joinInfo) {
      // called by the join edit pane, keep the auto-alias until the pane is closed
      deleteJoins(joinInfo, false);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.PORTAL_TAB,
      resource = "Data",
      actions = ResourceAction.ACCESS
   ))
   @PostMapping("/api/data/physicalmodel/joins/delete")
   public void deleteJoins(@RequestBody TableJoinInfo joinInfo)
   {
      deleteJoins(joinInfo, true);
   }

   private void deleteJoins(TableJoinInfo joinInfo, boolean removeAutoAlias) {
      String sourceTable = joinInfo.getSourceTable();
      String targetTable = joinInfo.getTargetTable();

      runtimePartitionService.update(joinInfo.getRuntimeId(), rp -> {
         XPartition partition = rp.getPartition();

         // fix source and target table as design mode table.
         XPartition applyAliasPartition = partition.applyAutoAliases();
         joinInfo.setSourceTable(DatabaseModelUtil.getOutgoingAutoAliasSourceOrTable(
            sourceTable, partition, applyAliasPartition));
         joinInfo.setTargetTable(DatabaseModelUtil.getOutgoingAutoAliasSourceOrTable(
            targetTable, partition, applyAliasPartition));

         List<XRelationship> joins = this.findJoin(partition, joinInfo);

         joins.forEach(join -> physicalModelManager.deleteJoin(partition, join, removeAutoAlias));
         return null;
      });
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.PORTAL_TAB,
      resource = "Data",
      actions = ResourceAction.ACCESS
   ))
   @GetMapping("/api/data/physicalmodel/graph/node/refresh")
   public void refreshTable(String runtimeId, String table) {
      XPartition partition = this.runtimePartitionService.getPartition(runtimeId);
      XPartition.PartitionTable partitionTable = partition.getPartitionTable(table);

      if(partitionTable != null) {
         // Bug #78428, delete the cached meta-data file before locking the runtime, the
         // update then only removes the meta-data cached in the runtime
         partitionTable.removeMetaData();

         runtimePartitionService.update(runtimeId, rp -> {
            XPartition.PartitionTable freshTable = rp.getPartition().getPartitionTable(table);

            if(freshTable != null) {
               freshTable.removeMetaData();
            }

            return null;
         });
      }
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.PORTAL_TAB,
      resource = "Data",
      actions = ResourceAction.ACCESS
   ))
   @PutMapping("/api/data/physicalmodel/join")
   public void editJoin(@RequestBody EditJoinEvent event) {
      runtimePartitionService.update(event.getDetailJoinInfo().getRuntimeId(), rp -> {
         List<XRelationship> joins = findJoin(rp.getPartition(), event.getDetailJoinInfo());

         if(joins.isEmpty()) {
            return null;
         }

         XRelationship join = joins.getFirst();
         int dependentCardinality = join.getDependentCardinality();
         int independentCardinality = join.getIndependentCardinality();
         event.getJoinModel().store(join.getDependentTable(), join);

         // a legacy join without a stored cardinality (0/0) is sent as null, keep it as is
         if(event.getJoinModel().getCardinality() == null) {
            join.setDependentCardinality(dependentCardinality);
            join.setIndependentCardinality(independentCardinality);
         }

         return null;
      });
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.PORTAL_TAB,
      resource = "Data",
      actions = ResourceAction.ACCESS
   ))
   @PutMapping("/api/data/physicalmodel/graph/alias/status")
   public boolean hasDuplicateCheck(@RequestBody CheckTableAliasEvent event) {
      return physicalModelManager.hasDuplicateCheck(event);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.PORTAL_TAB,
      resource = "Data",
      actions = ResourceAction.ACCESS
   ))
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

   private List<XRelationship> findJoin(XPartition partition, TableJoinInfo joinInfo) {
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
