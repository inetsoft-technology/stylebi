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
import { VSBindingTreeActions } from "../../binding/widget/binding-tree/vs-binding-tree-actions";
import { AssetEntryHelper } from "../../common/data/asset-entry-helper";
import { TreeNodeModel } from "../../widget/tree/tree-node-model";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ModelService } from "../../widget/services/model.service";
import { ViewsheetClientService } from "../../common/viewsheet-client";
import { AssemblyAction } from "../../common/action/assembly-action";
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";
import { AssetEntry } from "../../../../../shared/data/asset-entry";
import { VSWizardTreeInfoModel } from "./vs-wizard-tree-info-model";
import { ConvertColumnEvent } from "./event/convert-column-event";
import { VSWizardBindingTreeService } from "../services/vs-wizard-binding-tree.service";
import { VsWizardEditModes } from "./vs-wizard-edit-modes";

const VS_WIZARD_CONVERT_COLUMN = "/events/vs/wizard/convertColumn";

/**
 * Base class for vs wizard actions shared by all contexts.
 */
export class VSWizardBindingTreeActions extends VSBindingTreeActions {
   constructor(protected selectedNode: TreeNodeModel,
               protected selectedNodes: TreeNodeModel[],
               protected dialogService: NgbModal,
               protected treeService: VSWizardBindingTreeService,
               protected modelService: ModelService,
               protected clientService: ViewsheetClientService,
               protected bindingInfo: any,
               private treeInfo: VSWizardTreeInfoModel,
               wizardOriginalMode?: VsWizardEditModes)
   {
      super(null, selectedNode, selectedNodes, dialogService, treeService, modelService,
            bindingInfo.assemblyName, treeInfo ? treeInfo.grayedOutFields : [], true,
            wizardOriginalMode);

      this.runtimeId = this.clientService.runtimeId;
      this.socket = this.clientService;
   }

   protected createMenuActions(actions: AssemblyAction[], groups: AssemblyActionGroup[]): AssemblyActionGroup[] {
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

   /**
    * Convert action move to aggregate pane.
    */
   protected isConvertToMeasureVisible(entry: AssetEntry): boolean {
      return false;
   }

   protected isConvertToDimensionVisible(entry: AssetEntry): boolean {
      return false;
   }
}
