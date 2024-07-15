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
import { AfterViewInit, Component, Input, OnInit, ViewChild } from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { Tool } from "../../../../../../shared/util/tool";
import { StyleConstants } from "../../../common/util/style-constants";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { MODE } from "../../../vsobjects/objects/selection/selection-tree-controller";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { SelectionGeneralPaneModel } from "../../data/vs/selection-general-pane-model";
import { SelectionTreePaneModel } from "../../data/vs/selection-tree-pane-model";

@Component({
   selector: "selection-general-pane",
   templateUrl: "selection-general-pane.component.html",
})
export class SelectionGeneralPane implements OnInit, AfterViewInit {
   @Input() model: SelectionGeneralPaneModel;
   @Input() form: UntypedFormGroup;
   @Input() vsId: string;
   @Input() variableValues: string[];
   @Input() columnTreeRoot: TreeNodeModel = null;
   @Input() functionTreeRoot: TreeNodeModel = null;
   @Input() operatorTreeRoot: TreeNodeModel = null;
   @Input() scriptDefinitions: any = null;
   @Input() treeModel: SelectionTreePaneModel;
   levels: string[] = [];
   selectedLevelIndex = -1;
   levelToAdd: string;
   listHeight: UntypedFormControl;
   styleConstants: StyleConstants[] = [StyleConstants.SORT_ASC, StyleConstants.SORT_DESC,
      StyleConstants.SORT_SPECIFIC];

   constructor() {
   }

   initForm(): void {
      this.listHeight = new UntypedFormControl(this.model.listHeight, [
         FormValidators.positiveNonZeroIntegerInRange,
         Validators.required,
      ]);
      this.form.addControl("listHeight", this.listHeight);
      this.form.addControl("generalForm", new UntypedFormGroup({}));
      this.form.addControl("sizePositionPaneForm", new UntypedFormGroup({}));
   }

   setEnabled(): void {
      if(this.model.showType == 1) {
         this.listHeight.enable();
      }
      else {
         this.listHeight.disable();
      }
   }

   ngOnInit(): void {
      this.initForm();
      this.setEnabled();
   }

   ngAfterViewInit(): void {
      if(this.treeModel != null && this.treeModel.selectedColumns &&
         this.treeModel.selectedColumns.length > 0)
      {
         this.levels = this.treeModel.selectedColumns.map((column) => {
            return column.attribute.substr(column.attribute.indexOf(":") + 1);
         });
      }

      if(this.model.singleSelectionLevels != null) {
         this.model.singleSelectionLevels = this.model.singleSelectionLevels.filter(
            (level) => this.levels.includes(level));
      }
   }

   refreshSingleSelectionLevels(): void {
      if(this.model.singleSelection) {
         this.model.singleSelectionLevels = Tool.clone(this.levels);
      }
      else {
         this.model.singleSelectionLevels = [];
      }
   }

   addSingleSelection(): void {
      if(this.model.singleSelectionLevels == null) {
         this.model.singleSelectionLevels = [];
      }

      if(this.levelToAdd != null && !this.model.singleSelectionLevels.includes(this.levelToAdd)) {
         this.model.singleSelectionLevels.push(this.levelToAdd);
      }
   }

   deleteSingleSelection(): void {
      if(this.selectedLevelIndex != -1) {
         this.model.singleSelectionLevels.splice(this.selectedLevelIndex, 1);
         this.selectedLevelIndex = -1;
      }
   }

   selectLevelToAdd(level: string): void {
      this.levelToAdd = level;
   }

   selectLevel(index: number): void {
      this.selectedLevelIndex = index;
   }

   get columnMode(): boolean {
      return this.treeModel?.mode === MODE.COLUMN;
   }

   selectFirstDisable(): boolean {
      if(!this.treeModel || !this.columnMode) {
         return this.model.singleSelection;
      }

      return this.levels?.length == this.model.singleSelectionLevels?.length;
   }
}
