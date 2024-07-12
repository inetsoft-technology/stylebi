/*
 * inetsoft-web - StyleBI is a business intelligence web application.
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
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { BindingTreeService } from "./binding-tree.service";
import { ModelService } from "../../../widget/services/model.service";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { VSDataEditorBindingTreeActions } from "./vs-data-editor-binding-tree-actions";
import { GeoProvider } from "../../../common/data/geo-provider";
import { ViewsheetClientService } from "../../../common/viewsheet-client/viewsheet-client.service";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";

/**
 * Base class for chart-specific actions shared by all contexts.
 */
export class VSTableBindingTreeActions extends VSDataEditorBindingTreeActions {
   constructor(protected selectedNode: TreeNodeModel,
               protected selectedNodes: TreeNodeModel[],
               protected dialogService: NgbModal,
               protected treeService: BindingTreeService,
               protected modelService: ModelService,
               protected clientService: ViewsheetClientService,
               protected bindingInfo: any)
   {
      super(selectedNode, selectedNodes, dialogService, treeService, modelService,
         clientService, bindingInfo);
   }

   protected isConvertToDimensionVisible(entry: AssetEntry): boolean {
      return false;
   }

   protected isConvertToMeasureVisible(entry: AssetEntry): boolean {
      return false;
   }

   convertRef(type: number): void {
   }
}