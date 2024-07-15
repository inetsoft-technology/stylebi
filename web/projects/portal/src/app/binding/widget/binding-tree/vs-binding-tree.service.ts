/*
 * inetsoft-web - StyleBI is a business intelligence web application.
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
import { Injectable, Optional } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ModelService } from "../../../widget/services/model.service";
import { BindingService } from "../../services/binding.service";
import { BindingTreeService } from "./binding-tree.service";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { AssetEntryHelper } from "../../../common/data/asset-entry-helper";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { VSChartBindingTreeActions } from "./vs-chart-binding-tree-actions";
import { VSTableBindingTreeActions } from "./vs-table-binding-tree-actions";
import { VSCrosstabBindingTreeActions } from "./vs-crosstab-binding-tree-actions";
import { ContextMenuActions } from "../../../widget/context-menu/context-menu-actions";
import { VirtualScrollService } from "../../../widget/tree/virtual-scroll.service";
import { ViewsheetClientService } from "../../../common/viewsheet-client/viewsheet-client.service";

@Injectable()
export class VSBindingTreeService extends BindingTreeService {
   constructor(public bindingService: BindingService)
   {
      super();
   }

   resetTreeModel(bindingTreeModel: TreeNodeModel, refreshVirtualScroll: boolean = true): void {
      this.expandChildren(bindingTreeModel);
      super.resetTreeModel(bindingTreeModel);
   }

   protected expandChildren(root: TreeNodeModel): void {
      if(this._bindingTreeModel) {
         this.copyExpanded(this._bindingTreeModel, root);
      }

      let sinfo = !!this.bindingService.bindingModel ?
         this.bindingService.bindingModel.source : null;
      let tableName: string = sinfo ? sinfo.source : null;

      for(let child of root.children) {
         let entry: AssetEntry = child.data;
         let cube: boolean = entry.properties["CUBE_TABLE"] == "true";
         let tname: string =
            cube ? entry.properties["CUBE_TABLE"] : AssetEntryHelper.getEntryName(entry);

         if(tableName == tname) {
            this.expandAllChildren(child);
            break;
         }
      }
   }

   getBindingTreeActions(selectedNode: TreeNodeModel, selectedNodes: TreeNodeModel[],
                         dialogService: NgbModal, modelService: ModelService,
                         service: ViewsheetClientService, bindingInfo: any): ContextMenuActions
   {
      switch(bindingInfo.objectType) {
         case "VSChart":
            return new VSChartBindingTreeActions(selectedNode, selectedNodes, dialogService,
            this, modelService, service, bindingInfo);
         case "VSTable":
            return new VSTableBindingTreeActions(selectedNode, selectedNodes, dialogService,
            this, modelService, service, bindingInfo);
         case "VSCrosstab":
         case "VSCalcTable":
            return new VSCrosstabBindingTreeActions(selectedNode, selectedNodes, dialogService,
            this, modelService, service, bindingInfo);
         default:
            return null;
       }
   }
}
