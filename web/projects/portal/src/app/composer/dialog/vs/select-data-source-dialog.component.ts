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
import { Component, Input, Output, EventEmitter } from "@angular/core";

import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { SelectDataSourceDialogModel } from "../../data/vs/select-data-source-dialog-model";
import { AssetType } from "../../../../../../shared/data/asset-type";

@Component({
   selector: "select-data-source-dialog",
   templateUrl: "select-data-source-dialog.component.html",
   styles: ["legend i { vertical-align: -15% }"]
})
export class SelectDataSourceDialog {
   @Input() model: SelectDataSourceDialogModel;
   @Output() onCommit: EventEmitter<SelectDataSourceDialogModel> =
      new EventEmitter<SelectDataSourceDialogModel>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();

   selectNode(node: TreeNodeModel): void {
      const folder = node.data && (node.data.type == AssetType.DATA_SOURCE ||
                                   node.data.type == AssetType.PHYSICAL_FOLDER ||
                                   node.data.type == AssetType.DATA_SOURCE_FOLDER ||
                                   node.data.type == AssetType.QUERY_FOLDER ||
                                   node.data.type == AssetType.FOLDER);
      this.model.dataSource = folder ? null : node.data;
   }

   doubleclickNode(node: TreeNodeModel): void {
      if(!!node && node.leaf) {
         this.selectNode(node);
         this.saveChanges();
      }
   }

   clearDisabled(): boolean {
      return !this.model || this.model.dataSource == null;
   }

   clear(): void {
      this.model.dataSource = null;
   }

   cancelChanges(): void {
      this.onCancel.emit("cancel");
   }

   saveChanges(): void {
      this.onCommit.emit(this.model);
   }
}
