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
import { Component, Input, OnInit, EventEmitter, Output } from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { AssetEntryHelper } from "../../../common/data/asset-entry-helper";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { AssetRepositoryPaneModel } from "../../data/vs/asset-respository-pane-model";
import { AssetType } from "../../../../../../shared/data/asset-type";

@Component({
   selector: "asset-repository-pane",
   templateUrl: "asset-repository-pane.component.html",
})
export class AssetRepositoryPane implements OnInit {
   @Input() model: AssetRepositoryPaneModel;
   @Input() form: UntypedFormGroup;
   @Input() showReportRepository: boolean;
   @Input() defaultFolder: AssetEntry;
   @Input() readOnly: boolean = false;
   @Output() nodeSelected = new EventEmitter<TreeNodeModel>();
   readonly _selectNodeOnLoad = this.selectNodeOnLoad.bind(this);

   initForm(): void {
      this.form.addControl("name", new UntypedFormControl(this.model.name, [
         Validators.required,
         FormValidators.assetEntryBannedCharacters,
         FormValidators.assetNameStartWithCharDigit
      ]));
      this.form.addControl("entry", new UntypedFormControl(this.model.parentEntry, [
         Validators.required,
      ]));
   }

   ngOnInit(): void {
      this.initForm();
   }

   pathSelected(path: TreeNodeModel[]): void {
      if(path == null || path.length == 0) {
         return;
      }

      const selectedNode = path[0];
      const entry: AssetEntry = selectedNode.data as AssetEntry;

      if(selectedNode.leaf) {
         this.model.selectedEntry = entry.identifier;
         let name: string = null;

         if(entry.type == AssetType.WORKSHEET && !!entry.alias) {
            name = entry.alias;
         }

         if(!name) {
            name = AssetEntryHelper.getEntryName(entry);
         }

         this.model.name = name;
         this.model.parentEntry = path[1].data;
      }
      else {
         this.model.parentEntry = entry;
      }

      const formEntry = this.model.parentEntry.scope !== AssetEntryHelper.REPOSITORY_SCOPE ?
         this.model.parentEntry : null;
      this.form.setValue({entry: formEntry, name: this.model.name});
   }

   selectNodeOnLoad(root: TreeNodeModel): TreeNodeModel[] {
      return [root.children[0]];
   }
}
