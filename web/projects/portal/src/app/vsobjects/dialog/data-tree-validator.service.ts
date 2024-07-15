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
import { Injectable } from "@angular/core";
import { ModelService } from "../../widget/services/model.service";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { TreeNodeModel } from "../../widget/tree/tree-node-model";
import { AssetEntry } from "../../../../../shared/data/asset-entry";
import { HttpParams } from "@angular/common/http";
import { LoadAssetTreeNodesValidator } from "../../widget/asset-tree/load-asset-tree-nodes-validator";
import { VariableInputDialogModel } from "../../widget/dialog/variable-input-dialog/variable-input-dialog-model";
import { SlideOutOptions } from "../../widget/slide-out/slide-out-options";
import { VariableInputDialog } from "../../widget/dialog/variable-input-dialog/variable-input-dialog.component";
import { CollectParametersOverEvent } from "../../common/event/collect-parameters-over-event";
import { MessageCommand } from "../../common/viewsheet-client/message-command";
import { ComponentTool } from "../../common/util/component-tool";

const GET_PARAMETERS_URI = "../api/vs/bindingtree/getConnectionParameters";
const SET_CONNECTION_VARIABLES = "../api/composer/asset_tree/set-connection-variables";

@Injectable()
export class DataTreeValidatorService {
   constructor(private modelService: ModelService,
               private modalService: NgbModal)
   {
   }

   validateTreeNode(node: TreeNodeModel, runtimeId: string): void {
      if(runtimeId && ((node.data && node.data.properties &&
         node.data.properties["CUBE_TABLE"] == "true") || node.type == "cube"))
      {
      let entry: AssetEntry = null;
      let params = new HttpParams().set("rid", runtimeId);

      if(node.type == "cube") {
         params = params.set("cubeData", node.data);
      }
      else {
         entry = node.data;
      }

      this.modelService.sendModel<LoadAssetTreeNodesValidator>(GET_PARAMETERS_URI, entry, params)
         .subscribe((res) => {
            const nodeValidator: LoadAssetTreeNodesValidator = res.body;

            if(nodeValidator && nodeValidator.parameters && nodeValidator.parameters.length) {
               let dialogModel = <VariableInputDialogModel> {varInfos: nodeValidator.parameters};
               this.openVariableInputDialog(dialogModel);
            }
         });
      }
   }

   private openVariableInputDialog(dialogModel: VariableInputDialogModel) {
      let options: SlideOutOptions = {backdrop: "static"};

      const dialog = ComponentTool.showDialog(this.modalService, VariableInputDialog,
         (model: VariableInputDialogModel) => {
            let event: CollectParametersOverEvent = new CollectParametersOverEvent(model.varInfos);
            this.modelService.sendModel<MessageCommand>(SET_CONNECTION_VARIABLES, event).subscribe(
               (res) => {
                  const messageCommand: MessageCommand = res.body;

                  if(!!messageCommand.message) {
                     ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
                        messageCommand.message);
                  }
               },
               () => this.openVariableInputDialog(dialogModel)
            );
         }, options);
      dialog.model = dialogModel;
   }
}
