/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
import { Component, EventEmitter, OnInit, Output } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { AssetEntry } from "../../../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../../../shared/data/asset-type";
import { ComponentTool } from "../../../../common/util/component-tool";
import { TreeNodeModel } from "../../../../widget/tree/tree-node-model";

export interface NewVisualizationDialogModel {
   baseEntries: AssetEntry[];
}

@Component({
   selector: "new-visualization-dialog",
   templateUrl: "new-visualization-dialog.component.html",
   styleUrls: ["new-visualization-dialog.component.scss"]
})
export class NewVisualizationDialog implements OnInit {
   @Output() onCommit = new EventEmitter<NewVisualizationDialogModel>();
   @Output() onCancel = new EventEmitter<null>();

   model: NewVisualizationDialogModel;

   constructor(private modalService: NgbModal) {
   }

   ngOnInit(): void {
      this.model = { baseEntries: [] };
   }

   changeSources(sourceNodes: TreeNodeModel[]): void {
      this.model.baseEntries = sourceNodes
         .filter(node => !this.isFolder(node.data))
         .map(node => node.data);
   }

   doubleclickNode(sourceNode: TreeNodeModel): void {
      if(!!sourceNode && sourceNode.leaf && !this.isFolder(sourceNode.data)) {
         this.model.baseEntries = [sourceNode.data];
         this.doSubmit();
      }
   }

   private isFolder(entry: AssetEntry): boolean {
      return entry && (entry.type == AssetType.FOLDER ||
         entry.type == AssetType.DATA_SOURCE ||
         entry.type == AssetType.PHYSICAL_FOLDER ||
         entry.type == AssetType.DATA_SOURCE_FOLDER ||
         entry.type == AssetType.QUERY_FOLDER);
   }

   doSubmit(): void {
      const invalidEntry = this.model.baseEntries.find(
         entry => entry.path != null && entry.path.includes("^")
      );

      if(invalidEntry) {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
            "_#(js:compose.vs.entry.contains.caret)");
      }
      else {
         this.onCommit.emit(this.model);
      }
   }

   cancel(): void {
      this.onCancel.emit();
   }

   ignore(): void {
      //support do not selected any sources
      this.model.baseEntries = [];
      this.ok();
   }

   ok(): void {
      this.doSubmit();
   }

   get okDisable(): boolean {
      return !this.model || !this.model.baseEntries || this.model.baseEntries.length === 0;
   }
}
