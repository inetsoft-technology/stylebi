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
import { Component, OnInit, Input, Output, EventEmitter } from "@angular/core";
import { SelectionTreePropertyDialogModel } from "../../data/vs/selection-tree-property-dialog-model";
import { UntypedFormGroup } from "@angular/forms";
import { ScriptPaneTreeModel } from "../../../widget/dialog/script-pane/script-pane-tree-model";
import { Tool } from "../../../../../../shared/util/tool";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { XSchema } from "../../../common/data/xschema";
import { VSTrapService } from "../../../vsobjects/util/vs-trap.service";
import { TrapInfo } from "../../../common/data/trap-info";
import { UIContextService } from "../../../common/services/ui-context.service";
import { OutputColumnRefModel } from "../../../vsobjects/model/output-column-ref-model";
import { ComponentTool } from "../../../common/util/component-tool";
import { PropertyDialogService } from "../../../vsobjects/util/property-dialog.service";
import { PropertyDialog } from "./property-dialog.component";
import { VSTableTrapModel } from "../../../vsobjects/model/vs-table-trap-model";
import { ModelService } from "../../../widget/services/model.service";
import { DataRef } from "../../../common/data/data-ref";

const CHECK_TRAP_URI: string = "../api/composer/vs/selection-tree-property-dialog-model/checkTrap/";
const GET_GRAYED_OUT_FIELDS_URI: string = "../api/composer/vs/selection-tree-property-dialog-model/getGrayedOutFields/";

@Component({
   selector: "selection-tree-property-dialog",
   templateUrl: "selection-tree-property-dialog.component.html",
})
export class SelectionTreePropertyDialog extends PropertyDialog implements OnInit {
   @Input() model: SelectionTreePropertyDialogModel;
   @Input() scriptTreeModel: ScriptPaneTreeModel;
   form: UntypedFormGroup;
   generalTab: string = "selection-tree-property-dialog-general-tab";
   scriptTab: string = "selection-tree-property-dialog-script-tab";
   objectName: string;
   formValid = () => this.model && this.form && this.form.valid;

   private originalSelectedColumns: OutputColumnRefModel[];
   private originalSingleLevels: string[];

   public constructor(protected uiContextService: UIContextService,
                      private modalService: NgbModal,
                      protected trapService: VSTrapService,
                      protected propertyDialogService: PropertyDialogService,
                      private modelService: ModelService)
   {
      super(uiContextService, trapService, propertyDialogService);
   }

   ngOnInit(): void {
      super.ngOnInit();
      this.form = new UntypedFormGroup({
         selectionForm: new UntypedFormGroup({})
      });


      this.objectName =
         this.model.selectionGeneralPaneModel.generalPropPaneModel.basicGeneralPaneModel.name;

      this.originalSelectedColumns = Tool.clone(this.model.selectionTreePaneModel.selectedColumns);
      this.originalSingleLevels
         = Tool.clone(this.model.selectionGeneralPaneModel.singleSelectionLevels);
   }

   get defaultTab(): string {
      return this.openToScript ? this.scriptTab
         : this.uiContextService.getDefaultTab("selection-tree-property-dialog", this.generalTab);
   }

   set defaultTab(tab: string) {
      this.uiContextService.setDefaultTab("selection-tree-property-dialog", tab);
   }

   protected closing(isApply: boolean, collapse: boolean = false) {
      this.submit(isApply, collapse);
   }

   protected getScripts(): string[] {
      return [this.model.vsAssemblyScriptPaneModel.expression];
   }

   private submit(isApply: boolean, collapse: boolean) {
      const trapInfo = new TrapInfo(CHECK_TRAP_URI, this.objectName, this.runtimeId,
                                    this.model.selectionTreePaneModel);

      this.trapService.checkTrap(trapInfo,
                                 () => this.dataBindingOK(isApply, collapse),
                                 () => {},
                                 () => this.dataBindingOK(isApply, collapse)
      );
   }

   private getGrayedOutField() {
      let url = GET_GRAYED_OUT_FIELDS_URI +
            this.objectName + "/" + Tool.encodeURIPath(this.runtimeId);

      this.modelService.sendModel<DataRef[]>(url, this.model.selectionTreePaneModel).subscribe(
         (result) => {
          this.model.selectionTreePaneModel.grayedOutFields = result.body;
      });
   }

   dataBindingOK(isApply: boolean, collapse: boolean): void {
      this.objectName =
         this.model.selectionGeneralPaneModel.generalPropPaneModel.basicGeneralPaneModel.name;

      if(this.model.selectionTreePaneModel.selectedColumns.find(
            (column) => this.isNewDateColumn(column))) {
         ComponentTool.showConfirmDateTypeBindingDialog(this.modalService).then(
            (continueWithBinding) => {
               if(continueWithBinding) {
                  this.sendModel(isApply, collapse);
               }
            }
         );
      }
      else {
         this.sendModel(isApply, collapse);
      }
   }

   private isNewDateColumn(newColumn: OutputColumnRefModel): boolean {
      if(XSchema.isDateType(newColumn.dataType)) {
         for(let oldColumn of this.originalSelectedColumns) {
            if(SelectionTreePropertyDialog.isSameColumn(oldColumn, newColumn)) {
               return false;
            }
         }

         return true;
      }

      return false;
   }

   private static isSameColumn(oColumn: OutputColumnRefModel,
                               nColumn: OutputColumnRefModel): boolean
   {
      return !(oColumn.dataType == nColumn.dataType && oColumn.table == nColumn.table &&
         oColumn.attribute == nColumn.attribute);
   }

   private sendModel(isApply: boolean, collapse: boolean): void {
      // Remove unnecessary properties from the model
      let model = Tool.clone(this.model);
      delete model.selectionTreePaneModel.targetTree;

      isApply
         ? this.onApply.emit({collapse: collapse, result: model})
         : this.onCommit.emit(model);
   }

   get addSingleLevelsEnable(): boolean {
      return !!this.model?.selectionGeneralPaneModel?.singleSelection
         && Tool.isEmpty(this.originalSingleLevels);
   }

   onAddColumn(columns: string | string[]) {
      if(!this.addSingleLevelsEnable) {
         this.getGrayedOutField();
         return;
      }

      if(!!!this.model.selectionGeneralPaneModel.singleSelectionLevels) {
         this.model.selectionGeneralPaneModel.singleSelectionLevels = [];
      }

      this.model.selectionGeneralPaneModel.singleSelectionLevels =
         this.model.selectionGeneralPaneModel.singleSelectionLevels.concat(columns);
      this.getGrayedOutField();
   }

   getGrayedOutValues(): string[] {
      if(this.model == null || this.model.selectionTreePaneModel == null ||
         this.model.selectionTreePaneModel.selectedTable == null)
      {
         return [];
      }

      let grayedOutFlds = this.model.selectionTreePaneModel.grayedOutFields;
      let values: string[] = [];

      if(grayedOutFlds == null) {
         return values;
      }

      let model: boolean = this.model.selectionTreePaneModel.selectedColumns != null &&
         this.model.selectionTreePaneModel.selectedColumns[0] != null &&
         this.model.selectionTreePaneModel.selectedColumns[0].attribute.indexOf(":") > 0;

      for(let i = 0; i < grayedOutFlds.length; i++) {
         if(model) {
            values.push(grayedOutFlds[i].name);
         }
         else if(grayedOutFlds[i].entity == this.model.selectionTreePaneModel.selectedTable) {
            values.push(grayedOutFlds[i].attribute);
         }
      }

      return values;
   }
}
