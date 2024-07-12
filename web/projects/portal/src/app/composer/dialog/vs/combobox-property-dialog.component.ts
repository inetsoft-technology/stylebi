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
import { Component, EventEmitter, Input, Output, OnInit } from "@angular/core";
import { UntypedFormGroup } from "@angular/forms";

import { ComboboxPropertyDialogModel } from "../../data/vs/combobox-property-dialog-model";
import { ScriptPaneTreeModel } from "../../../widget/dialog/script-pane/script-pane-tree-model";
import { ComboBoxEditorValidationService } from "./combo-box-editor-validation.service";
import { VSTrapService } from "../../../vsobjects/util/vs-trap.service";
import { TrapInfo } from "../../../common/data/trap-info";
import { UIContextService } from "../../../common/services/ui-context.service";
import { XSchema } from "../../../common/data/xschema";
import { PropertyDialogService } from "../../../vsobjects/util/property-dialog.service";
import { PropertyDialog } from "./property-dialog.component";

const CHECK_TRAP_URI: string = "../api/composer/vs/combobox-property-dialog-model/checkTrap/";

@Component({
   selector: "combobox-property-dialog",
   templateUrl: "combobox-property-dialog.component.html",
})
export class ComboBoxPropertyDialog extends PropertyDialog implements OnInit {
   @Input() model: ComboboxPropertyDialogModel;
   @Input() scriptTreeModel: ScriptPaneTreeModel;
   form: UntypedFormGroup;
   generalTab: string = "combobox-property-dialog-general-tab";
   scriptTab: string = "combobox-property-dialog-script-tab";
   valid: boolean = true;
   formValid = () => this.form && this.form.valid && this.valid;
   private timeInstantCombo = false;

   public constructor(protected uiContextService: UIContextService,
                      private comboBoxEditorValidationService: ComboBoxEditorValidationService,
                      protected trapService: VSTrapService,
                      protected propertyDialogService: PropertyDialogService)
   {
      super(uiContextService, trapService, propertyDialogService);
      this.form = new UntypedFormGroup({
         comboBoxGeneralForm: new UntypedFormGroup({})
      });
   }

   ngOnInit() {
      super.ngOnInit();
      const combo = this.model.comboboxGeneralPaneModel.listValuesPaneModel.comboBoxEditorModel;
      this.timeInstantCombo = XSchema.TIME_INSTANT == combo.dataType && combo.calendar;
   }

   get defaultTab(): string {
      return this.openToScript ? this.scriptTab
         : this.uiContextService.getDefaultTab("combobox-property-dialog", this.generalTab);
   }

   set defaultTab(tab: string) {
      this.uiContextService.setDefaultTab("combobox-property-dialog", tab);
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
      const combo = this.model.comboboxGeneralPaneModel.listValuesPaneModel.comboBoxEditorModel;
      combo.valid = this.comboBoxEditorValidationService.validateEmbeddedValues(combo);
      const timeInstantCombo = XSchema.TIME_INSTANT == combo.dataType && combo.calendar;

      if(this.timeInstantCombo != timeInstantCombo) {
         const size = this.model.comboboxGeneralPaneModel.sizePositionPaneModel;
         const w = size.width;

         // enforce min width as in 12.2
         if(timeInstantCombo && w < 180) {
            size.width = 180;
         }
         // restore original width
         else if(!timeInstantCombo && w == 180) {
            size.width = 100;
         }
      }

      this.timeInstantCombo = timeInstantCombo;

     if(combo.valid) {
        this.comboBoxEditorValidationService.validateQueryValues(combo)
            .then((result: boolean) => {
               if (result === true) {
                  this.checkTrap(isApply, collapse);
               }
            });
     }
   }

   onValidChanged(valid: boolean) {
      this.valid = valid;
   }
}
