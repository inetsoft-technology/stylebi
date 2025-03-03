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
import { Component, EventEmitter, Output, OnInit, NgZone, Input } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ComponentTool } from "../../common/util/component-tool";
import { LocalStorage } from "../../common/util/local-storage.util";
import { NewViewsheetDialogModel } from "../model/new-viewsheet-dialog-model";
import { TreeNodeModel } from "../../widget/tree/tree-node-model";
import { AssetType } from "../../../../../shared/data/asset-type";
import { VSWizardConstants } from "../model/vs-wizard-constants";

@Component({
   selector: "new-viewsheet-dialog",
   templateUrl: "new-viewsheet-dialog.component.html",
   styleUrls: ["new-viewsheet-dialog.component.scss"]
})
export class NewViewsheetDialog implements OnInit {
   @Output() onCommit = new EventEmitter<NewViewsheetDialogModel>();
   @Output() onCancel = new EventEmitter<null>();

   model: NewViewsheetDialogModel;

   constructor(private zone: NgZone, private modalService: NgbModal) {
   }

   ngOnInit(): void {
      this.model = <NewViewsheetDialogModel> {baseEntry: null};
   }

   changeSource(sourceNode: TreeNodeModel) {
      const folder = sourceNode.data && (sourceNode.data.type == AssetType.FOLDER ||
                                         sourceNode.data.type == AssetType.DATA_SOURCE ||
                                         sourceNode.data.type == AssetType.PHYSICAL_FOLDER ||
                                         sourceNode.data.type == AssetType.DATA_SOURCE_FOLDER ||
                                         sourceNode.data.type == AssetType.QUERY_FOLDER);
      this.model.baseEntry = folder ? null : sourceNode.data;
   }

   doubleclickNode(sourceNode: TreeNodeModel) {
      if(!!sourceNode && sourceNode.leaf) {
         this.changeSource(sourceNode);
         this.doSubmit(true);
      }
   }

   doSubmit(wizard: boolean): void {
      if(this.model.baseEntry != null && this.model.baseEntry.path != null && this.model.baseEntry.path.includes("^")) {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
            "_#(js:compose.vs.entry.contains.caret)");
      }
      else {
         this.model.openWizard = wizard;
         this.onCommit.emit(this.model);
      }
   }

   cancel(): void {
      this.onCancel.emit();
   }

   ok(): void {
      this.zone.run(() => {
         this.doSubmit(true);
      });
   }

   get wizardDisable() {
      return !this.model || !this.model.baseEntry;
   }
}
