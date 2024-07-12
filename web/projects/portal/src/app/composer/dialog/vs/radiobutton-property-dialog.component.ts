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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { RadioButtonPropertyDialogModel } from "../../data/vs/radiobutton-property-dialog-model";
import { UntypedFormGroup } from "@angular/forms";
import { ScriptPaneTreeModel } from "../../../widget/dialog/script-pane/script-pane-tree-model";
import { TrapInfo } from "../../../common/data/trap-info";
import { VSTrapService } from "../../../vsobjects/util/vs-trap.service";
import { ComboBoxEditorValidationService } from "./combo-box-editor-validation.service";
import { UIContextService } from "../../../common/services/ui-context.service";
import { XSchema } from "../../../common/data/xschema";
import { PropertyDialogService } from "../../../vsobjects/util/property-dialog.service";
import { PropertyDialog } from "./property-dialog.component";

const CHECK_TRAP_URI: string = "../api/composer/vs/radiobutton-property-dialog-model/checkTrap/";

@Component({
   selector: "radiobutton-property-dialog",
   templateUrl: "radiobutton-property-dialog.component.html",
})
export class RadioButtonPropertyDialog extends PropertyDialog {
   @Input() model: RadioButtonPropertyDialogModel;
   @Input() scriptTreeModel: ScriptPaneTreeModel;
   form: UntypedFormGroup;
   generalTab: string = "radiobutton-property-dialog-general-tab";
   scriptTab: string = "radiobutton-property-dialog-script-tab";
   formValid = () => this.form && this.form.valid;

   public constructor(protected uiContextService: UIContextService,
                      private comboBoxEditorValidationService: ComboBoxEditorValidationService,
                      protected trapService: VSTrapService,
                      protected propertyDialogService: PropertyDialogService)
   {
      super(uiContextService, trapService, propertyDialogService);
      this.form = new UntypedFormGroup({
         radioButtonGeneralPaneForm: new UntypedFormGroup({})
      });
   }

   get defaultTab(): string {
      return this.openToScript ? this.scriptTab
         : this.uiContextService.getDefaultTab("radiobutton-property-dialog", this.generalTab);
   }

   set defaultTab(tab: string) {
      this.uiContextService.setDefaultTab("radiobutton-property-dialog", tab);
   }

   checkTrap(isApply: boolean, collapse: boolean): void {
      const trapInfo = new TrapInfo(CHECK_TRAP_URI, this.assemblyName, this.runtimeId,
         this.model);
      const payload = {collapse: collapse, result: this.model};
      const submit = () => isApply ? this.onApply.emit(payload) : this.onCommit.emit(this.model);

      this.trapService.checkTrap(
         trapInfo,
         () => submit(),
         () => {},
         () => submit());
   }

   checkDataType(): void {
      const columnValue: string =
         this.model.dataInputPaneModel ? this.model.dataInputPaneModel.columnValue : null;
      const comboBoxEditorModel =
         this.model.radioButtonGeneralPaneModel.listValuesPaneModel.comboBoxEditorModel;
      const isStandardDataType: boolean = XSchema.standardDataTypeList.some(
         (dataTypeObject) => columnValue == dataTypeObject.data);

      if(columnValue && columnValue != comboBoxEditorModel.dataType && isStandardDataType) {
         comboBoxEditorModel.dataType = columnValue;
         comboBoxEditorModel.variableListDialogModel.dataType = columnValue;
         comboBoxEditorModel.selectionListDialogModel.selectionListEditorModel.dataType = columnValue;
      }
   }

   protected closing(isApply: boolean, collapse: boolean = false) {
      this.submit(isApply, collapse);
   }

   protected getScripts(): string[] {
      return [this.model.vsAssemblyScriptPaneModel.expression];
   }

   private submit(isApply: boolean, collapse: boolean) {
      const comboBoxEditorModel =
         this.model.radioButtonGeneralPaneModel.listValuesPaneModel.comboBoxEditorModel;
      comboBoxEditorModel.valid =
         this.comboBoxEditorValidationService.validateEmbeddedValues(comboBoxEditorModel);

       if(comboBoxEditorModel.valid) {
          this.comboBoxEditorValidationService.validateQueryValues(comboBoxEditorModel)
              .then((result: boolean) => {
                 if (result === true) {
                    this.checkDataType();
                    this.checkTrap(isApply, collapse);
                 }
              });
       }
   }

   cancel(): void {
      this.onCancel.emit("cancel");
   }
}
