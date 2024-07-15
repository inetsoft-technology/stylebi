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
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { ModelService } from "../../../widget/services/model.service";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { BindingTreeService } from "./binding-tree.service";
import { VSBindingTreeActions } from "./vs-binding-tree-actions";

/**
 * Base class for chart-specific actions shared by all contexts.
 */
export class VSDataEditorBindingTreeActions extends VSBindingTreeActions
{
   constructor(protected selectedNode: TreeNodeModel,
               protected selectedNodes: TreeNodeModel[],
               protected dialogService: NgbModal,
               protected treeService: BindingTreeService,
               protected modelService: ModelService,
               protected clientService: ViewsheetClientService,
               protected bindingInfo: any)
   {
      super(null, selectedNode, selectedNodes, dialogService, treeService, modelService,
         bindingInfo.assemblyName, bindingInfo.grayedOutFields, false);

      this.runtimeId = this.clientService.runtimeId;
      this.socket = this.clientService;
   }
}