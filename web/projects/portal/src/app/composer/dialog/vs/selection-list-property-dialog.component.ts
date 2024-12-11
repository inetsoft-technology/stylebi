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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { SelectionListPropertyDialogModel } from "../../data/vs/selection-list-property-dialog-model";
import { UntypedFormGroup } from "@angular/forms";
import { ScriptPaneTreeModel } from "../../../widget/dialog/script-pane/script-pane-tree-model";
import { XSchema } from "../../../common/data/xschema";
import { Tool } from "../../../../../../shared/util/tool";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { TrapInfo } from "../../../common/data/trap-info";
import { VSTrapService } from "../../../vsobjects/util/vs-trap.service";
import { UIContextService } from "../../../common/services/ui-context.service";
import { OutputColumnRefModel } from "../../../vsobjects/model/output-column-ref-model";
import { ComponentTool } from "../../../common/util/component-tool";
import { PropertyDialogService } from "../../../vsobjects/util/property-dialog.service";
import { PropertyDialog } from "./property-dialog.component";
import { DataRef } from "../../../common/data/data-ref";
import { ModelService } from "../../../widget/services/model.service";

const CHECK_TRAP_URI: string = "../api/composer/vs/selection-list-property-dialog-model/checkTrap/";
const GET_LIST_GRAYED_FIELDS_URI: string = "../api/composer/vs/selection-list-property-dialog-model/getGrayedOutFields/";

@Component({
   selector: "selection-list-property-dialog",
   templateUrl: "selection-list-property-dialog.component.html",
})
export class SelectionListPropertyDialog extends PropertyDialog implements OnInit {
   @Input() model: SelectionListPropertyDialogModel;
   @Input() scriptTreeModel: ScriptPaneTreeModel;
   form: UntypedFormGroup;
   generalTab: string = "selection-list-property-dialog-general-tab";
   scriptTab: string = "selection-list-property-dialog-script-tab";
   objectName: string;
   formValid = () => this.model && this.form && this.form.valid;

   private originalSelectedColumn: OutputColumnRefModel;

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

      this.originalSelectedColumn = Tool.clone(this.model.selectionListPaneModel.selectedColumn);

      if(this.model.selectionListPaneModel.grayedOutFields != null) {
         this.getGrayedOutFields();
      }
   }

   get defaultTab(): string {
      return this.openToScript ? this.scriptTab
         : this.uiContextService.getDefaultTab("selection-list-property-dialog", this.generalTab);
   }

   set defaultTab(tab: string) {
      this.uiContextService.setDefaultTab("selection-list-property-dialog", tab);
   }

   protected closing(isApply: boolean, collapse: boolean = false) {
      this.submit(isApply, collapse);
   }

   protected getScripts(): string[] {
      return [this.model.vsAssemblyScriptPaneModel.expression];
   }

   private submit(isApply: boolean, collapse: boolean) {
      const model2: SelectionListPropertyDialogModel = Tool.clone(this.model);
      model2.selectionListPaneModel.targetTree = null;
      const trapInfo = new TrapInfo(CHECK_TRAP_URI, this.objectName, this.runtimeId, model2);

      this.trapService.checkTrap(trapInfo,
                                 () => this.dataBindingOK(model2, isApply, collapse),
                                 () => {},
                                 () => this.dataBindingOK(model2, isApply, collapse)
      );
   }

   private getGrayedOutFields() {
      let url = GET_LIST_GRAYED_FIELDS_URI +
         this.objectName + "/" + Tool.encodeURIPath(this.runtimeId);

      this.modelService.sendModel<DataRef[]>(url, this.model).subscribe(
      (result) => {
         this.model.selectionListPaneModel.grayedOutFields = result.body;
         console.log("====000======",this.model.selectionListPaneModel.grayedOutFields);
      });
   }

   getGrayedOutValues(): string[] {
      let grayedOutFlds = this.model.selectionListPaneModel.grayedOutFields;
      let values: string[] = [];

      if(grayedOutFlds == null) {
         return values;
      }

      for(let i = 0; i < grayedOutFlds.length; i++) {
         values.push(grayedOutFlds[i].name);
      }

      return values;
   }

   dataBindingOK(model: SelectionListPropertyDialogModel, isApply: boolean,
                 collapse: boolean): void
   {
      let newSelectedColumn: OutputColumnRefModel
         = model.selectionListPaneModel.selectedColumn;
      const payload = {collapse: collapse, result: this.model};

      this.objectName =
         this.model.selectionGeneralPaneModel.generalPropPaneModel.basicGeneralPaneModel.name;

      if(newSelectedColumn && this.isNewDateColumn(this.originalSelectedColumn, newSelectedColumn))
      {
         ComponentTool.showConfirmDateTypeBindingDialog(this.modalService).then(
            (continueWithBinding) => {
               if(continueWithBinding) {
                  isApply ? this.onApply.emit(payload) : this.onCommit.emit(model);
               }
            }
         );
      }
      else {
         isApply ? this.onApply.emit(payload) : this.onCommit.emit(model);
      }
   }

   private isNewDateColumn(oColumn: OutputColumnRefModel, nColumn: OutputColumnRefModel): boolean {
      if(!oColumn) {
         return false;
      }

      return XSchema.isDateType(nColumn.dataType) &&
         !(oColumn.dataType == nColumn.dataType && oColumn.table == nColumn.table &&
           oColumn.attribute == nColumn.attribute);
   }
}
