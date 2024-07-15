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
import { TextInputPropertyDialogModel } from "../../data/vs/textinput-property-dialog-model";
import { UntypedFormGroup } from "@angular/forms";
import { ScriptPaneTreeModel } from "../../../widget/dialog/script-pane/script-pane-tree-model";
import { UIContextService } from "../../../common/services/ui-context.service";
import { PropertyDialogService } from "../../../vsobjects/util/property-dialog.service";
import { PropertyDialog } from "./property-dialog.component";

@Component({
   selector: "textinput-property-dialog",
   templateUrl: "textinput-property-dialog.component.html",
})
export class TextInputPropertyDialog extends PropertyDialog implements OnInit {
   @Input() model: TextInputPropertyDialogModel;
   @Input() scriptTreeModel: ScriptPaneTreeModel;
   form: UntypedFormGroup;
   generalTab: string = "textinput-property-dialog-general-tab";
   scriptTab: string = "textinput-property-dialog-script-tab";
   formValid = () => this.model && this.form && this.form.valid;

   public constructor(protected uiContextService: UIContextService,
                      protected propertyDialogService: PropertyDialogService)
   {
      super(uiContextService, null, propertyDialogService);
   }

   ngOnInit(): void {
      super.ngOnInit();
      this.initForm();
   }

   get defaultTab(): string {
      return this.openToScript ? this.scriptTab
         : this.uiContextService.getDefaultTab("textinput-property-dialog", this.generalTab);
   }

   set defaultTab(tab: string) {
      this.uiContextService.setDefaultTab("textinput-property-dialog", tab);
   }

   protected getScripts(): string[] {
      return [this.model.clickableScriptPaneModel.scriptExpression,
              this.model.clickableScriptPaneModel.onClickExpression];
   }

   protected closing(isApply: boolean, collapse: boolean = false) {
      const payload = {collapse: collapse, result: this.model};
      isApply ? this.onApply.emit(payload) : this.onCommit.emit(this.model);
   }

   initForm(): void {
      this.form = new UntypedFormGroup({
         textInputGeneralPaneForm: new UntypedFormGroup({
            generalPropPaneForm: new UntypedFormGroup({
               basicGeneralPaneForm: new UntypedFormGroup({})
            })
         }),
         dataInputPaneForm: new UntypedFormGroup({}),
         textInputColumnOptionPaneForm: new UntypedFormGroup({}),
      });
   }
}
