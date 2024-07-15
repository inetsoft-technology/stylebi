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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { UntypedFormGroup } from "@angular/forms";

import { CheckboxPropertyDialogModel } from "../../data/vs/checkbox-property-dialog-model";
import { ScriptPaneTreeModel } from "../../../widget/dialog/script-pane/script-pane-tree-model";
import { ComboBoxEditorValidationService } from "./combo-box-editor-validation.service";
import { VSTrapService } from "../../../vsobjects/util/vs-trap.service";
import { TrapInfo } from "../../../common/data/trap-info";
import { UIContextService } from "../../../common/services/ui-context.service";
import { PropertyDialogService } from "../../../vsobjects/util/property-dialog.service";
import { PropertyDialog } from "./property-dialog.component";

const CHECK_TRAP_URI: string = "../api/composer/vs/checkbox-property-dialog-model/checkTrap/";

@Component({
   selector: "checkbox-property-dialog",
   templateUrl: "checkbox-property-dialog.component.html",
})
export class CheckboxPropertyDialog extends PropertyDialog implements OnInit {
   @Input() model: CheckboxPropertyDialogModel;
   @Input() scriptTreeModel: ScriptPaneTreeModel;
   isApply: boolean = false;
   isCollapse: boolean = false;
   form: UntypedFormGroup;
   generalTab: string = "checkbox-property-dialog-general-tab";
   scriptTab: string = "checkbox-property-dialog-script-tab";
   formValid = () => this.form && this.form.valid;

   constructor(private comboBoxEditorValidationService: ComboBoxEditorValidationService,
               protected trapService: VSTrapService,
               protected uiContextService: UIContextService,
               protected propertyDialogService: PropertyDialogService)
   {
      super(uiContextService, trapService, propertyDialogService);
      this.form = new UntypedFormGroup({
         checkBoxGeneralForm: new UntypedFormGroup({})
      });
   }

   get defaultTab(): string {
      return this.openToScript ? this.scriptTab
         : this.uiContextService.getDefaultTab("checkbox-property-dialog", this.generalTab);
   }

   set defaultTab(tab: string) {
      this.uiContextService.setDefaultTab("checkbox-property-dialog", tab);
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

   protected closing(isApply: boolean, collapse: boolean = false) {
      this.submit(isApply, collapse);
   }

   protected getScripts(): string[] {
      return [this.model.vsAssemblyScriptPaneModel.expression];
   }

   private submit(isApply: boolean, collapse: boolean) {
      const comboBoxEditorModel =
         this.model.checkboxGeneralPaneModel.listValuesPaneModel.comboBoxEditorModel;
      comboBoxEditorModel.valid =
         this.comboBoxEditorValidationService.validateEmbeddedValues(comboBoxEditorModel);

      if(comboBoxEditorModel.valid) {
         this.comboBoxEditorValidationService.validateQueryValues(comboBoxEditorModel)
            .then((result: boolean) => {
               if(result === true) {
                  this.checkTrap(isApply, collapse);
               }
            });
      }
   }
}
