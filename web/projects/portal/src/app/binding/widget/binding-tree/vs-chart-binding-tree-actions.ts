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
import { AssemblyAction } from "../../../common/action/assembly-action";
import { AssemblyActionGroup } from "../../../common/action/assembly-action-group";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { ModelService } from "../../../widget/services/model.service";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { ChartBindingModel } from "../../data/chart/chart-binding-model";
import { ConvertChartRefEvent } from "../../event/convert-chart-ref-event";
import { BindingTreeService } from "./binding-tree.service";
import { VSDataEditorBindingTreeActions } from "./vs-data-editor-binding-tree-actions";

/**
 * Base class for chart-specific actions shared by all contexts.
 */
export class VSChartBindingTreeActions extends VSDataEditorBindingTreeActions {

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

   /** @inheritDoc */
   protected createMenuActions(actions: AssemblyAction[],
                               groups: AssemblyActionGroup[]): AssemblyActionGroup[]
   {
      actions = actions.concat([
         {
            id: () => "binding tree set-geographic",
            label: () => "_#(js:Set Geographic)",
            icon: () => "fa fa-eye-slash",
            enabled: () => true,
            visible: () => this.isGeographicVisible(false, this.bindingInfo),
            action: () => this.changeGeographic(this.SET_GEOGRAPHIC, this.bindingInfo)
         },
         {
            id: () => "binding tree clear-geographic",
            label: () => "_#(js:Edit Geographic)",
            icon: () => "fa fa-eye-slash",
            enabled: () => true,
            visible: () => this.isGeographicVisible(true, this.bindingInfo),
            action: () => this.editGeographic(this.bindingInfo)
         },
         {
            id: () => "binding tree clear-geographic",
            label: () => "_#(js:Clear Geographic)",
            icon: () => "fa fa-eye-slash",
            enabled: () => true,
            visible: () => this.isGeographicVisible(true, this.bindingInfo),
            action: () => this.changeGeographic(this.CLEAR_GEOGRAPHIC, this.bindingInfo)
         }
      ]);

      return super.createMenuActions(actions, groups);
   }

   protected isConvertToMeasureVisible(entry: AssetEntry): boolean {
      return this.isDimensionColumn(entry);
   }

   protected isConvertToDimensionVisible(entry: AssetEntry): boolean {
      return this.isMeasureColumn(entry) && entry.properties["basedOnDetail"] != "false";
   }

   protected convertRef(convertType: number): void {
      let name: string = this.bindingInfo.assemblyName;
      let table: string = this.treeService.getTableName(this.currentEntry);
      let binding: ChartBindingModel = this.bindingInfo.bindingModel;
      let event: ConvertChartRefEvent =
         new ConvertChartRefEvent(name, this.getRefNamesForConversion(convertType), convertType,
            table, false, binding);

      this.sendEvent("vs/chart/convertRef", event, table);
   }
}
