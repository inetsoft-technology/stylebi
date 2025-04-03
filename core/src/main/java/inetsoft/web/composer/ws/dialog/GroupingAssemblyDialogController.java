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
package inetsoft.web.composer.ws.dialog;

import inetsoft.report.composition.AssetTreeModel;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.composer.AssetTreeController;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.model.ws.GroupingAssemblyDialogModel;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.*;

@Controller
public class GroupingAssemblyDialogController extends WorksheetController {

   public GroupingAssemblyDialogController(AssetRepository assetRepository,
                                           GroupingAssemblyDialogServiceProxy dialogServiceProxy) {
      this.assetRepository = assetRepository;
      this.dialogServiceProxy = dialogServiceProxy;
   }

   @RequestMapping(
      value = "/api/composer/ws/grouping-assembly-dialog-model/{runtimeid}",
      method = RequestMethod.GET)
   @ResponseBody
   public GroupingAssemblyDialogModel getGroupingAssemblyModel(
      @PathVariable("runtimeid") String runtimeId,
      @RequestParam(value = "grouping", required = false) String groupingName,
      Principal principal) throws Exception
   {
      return dialogServiceProxy.getGroupingAssemblyModel(Tool.byteDecode(runtimeId), groupingName, principal);
   }

   @RequestMapping(
      value = "/api/composer/ws/grouping-assembly-tree-model",
      method = RequestMethod.POST)
   @ResponseBody
   public TreeNodeModel getNodes(
      @RequestBody AssetEntry expandedEntry,
      Principal principal) throws Exception
   {
      TreeNodeModel result;

      AssetEntry.Selector selector = new AssetEntry.Selector(AssetEntry.Type.DATA);
      AssetEntry[] entries = AssetTreeController.getFilterFor(expandedEntry);

      AssetTreeModel.Node atmNode = new AssetTreeModel.Node(expandedEntry);
      AssetEntry[] children = assetRepository
         .getEntries(expandedEntry, principal, ResourceAction.READ, selector);

      boolean sqlEnabled = SecurityEngine.getSecurity().checkPermission(
         principal, ResourceType.PHYSICAL_TABLE, "*", ResourceAction.ACCESS);

      for(AssetEntry ae : children) {
         AssetTreeController.getSubEntries(assetRepository, principal, atmNode, ae,
            new ArrayList<>(Arrays.asList(entries)), selector, ResourceAction.READ);
      }

      result = AssetTreeController.convertToTreeNodeModel(
         atmNode, catalog, GroupingAssemblyDialogController::isLeaf, sqlEnabled, principal);
      return result;
   }

   private static boolean isLeaf(AssetEntry entry) {
      return !(entry.isActualFolder() || entry.isDataSource() ||
         entry.getType() == AssetEntry.Type.FOLDER);
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/ws/dialog/grouping-assembly-dialog-model")
   public void setGroupingAssemblyDialogProperties(
      @Payload GroupingAssemblyDialogModel model,
      Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      dialogServiceProxy.setGroupingAssemblyDialogProperties(getRuntimeId(), model, principal, commandDispatcher);
   }

   private AssetRepository assetRepository;
   private Catalog catalog = Catalog.getCatalog();

   private GroupingAssemblyDialogServiceProxy dialogServiceProxy;

}
