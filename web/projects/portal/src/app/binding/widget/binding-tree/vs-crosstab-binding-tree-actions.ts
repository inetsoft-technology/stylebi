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
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { BindingTreeService } from "./binding-tree.service";
import { BaseTableBindingModel } from "../../data/table/base-table-binding-model";
import { ConvertTableRefEvent } from "../../event/convert-table-ref-event";
import { ModelService } from "../../../widget/services/model.service";
import { SourceInfo } from "../../data/source-info";
import { SourceInfoType } from "../../data/source-info-type";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { VSDataEditorBindingTreeActions } from "./vs-data-editor-binding-tree-actions";
import { ViewsheetClientService } from "../../../common/viewsheet-client/viewsheet-client.service";

/**
 * Base class for chart-specific actions shared by all contexts.
 */
export class VSCrosstabBindingTreeActions extends VSDataEditorBindingTreeActions {
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
      if(this.isAssemblyTreeNode(entry)) {
         return false;
      }

      return this.isMeasureColumn(entry) && entry.properties["basedOnDetail"] != "false";
   }

   protected isConvertToMeasureVisible(entry: AssetEntry): boolean {
      if(this.isAssemblyTreeNode(entry)) {
         return false;
      }

      return this.isDimensionColumn(entry);
   }

   convertRef(type: number): void {
      let entry: AssetEntry = this.currentEntry;
      let name: string = this.bindingInfo.assemblyName;
      let bindingModel: BaseTableBindingModel = this.bindingInfo.bindingModel;
      let sinfo: SourceInfo = bindingModel.source;
      let ntbl: string = this.treeService.getTableName(entry);
      let otbl: string = (sinfo != null) ? sinfo.source : ntbl;
      const sourceType = !!entry.properties["type"] ? entry.properties["type"] : SourceInfoType.ASSET;

      sinfo = <SourceInfo> {
         type: sourceType,
         prefix: null,
         source: ntbl
      };

      let sourceChange: boolean = ntbl != otbl;
      let event: ConvertTableRefEvent = new ConvertTableRefEvent(name,
         this.getRefNamesForConversion(type), type, sinfo, sourceChange, ntbl, false);

      this.clientService.sendEvent("/events/vs/table/convertRef", event);
   }
}
