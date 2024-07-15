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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { SubmitPropertyDialogModel } from "../../data/vs/submit-property-dialog-model";
import { UntypedFormGroup } from "@angular/forms";
import { SubmitGeneralPaneModel } from "../../data/vs/submit-general-pane-model";
import { ClickableScriptPaneModel } from "../../data/vs/clickable-script-pane-model";
import { GeneralPropPaneModel } from "../../../vsobjects/model/general-prop-pane-model";
import { LabelPropPaneModel } from "../../data/vs/label-prop-pane-model";
import { BasicGeneralPaneModel } from "../../../vsobjects/model/basic-general-pane-model";
import { ScriptPaneTreeModel } from "../../../widget/dialog/script-pane/script-pane-tree-model";
import { UIContextService } from "../../../common/services/ui-context.service";
import { PropertyDialogService } from "../../../vsobjects/util/property-dialog.service";
import { PropertyDialog } from "./property-dialog.component";

@Component({
   selector: "submit-property-dialog",
   templateUrl: "submit-property-dialog.component.html",
})
export class SubmitPropertyDialog extends PropertyDialog {
   @Input() model: SubmitPropertyDialogModel;
   @Input() scriptTreeModel: ScriptPaneTreeModel;
   form: UntypedFormGroup;
   generalTab: string = "submit-property-dialog-general-tab";
   scriptTab: string = "submit-property-dialog-script-tab";
   formValid = () => this.model && this.form && this.form.valid;

   public constructor(protected uiContextService: UIContextService,
                      protected propertyDialogService: PropertyDialogService)
   {
      super(uiContextService, null, propertyDialogService);
      this.form = new UntypedFormGroup({
         submitForm: new UntypedFormGroup({})
      });
   }

   get defaultTab(): string {
      return this.openToScript ? this.scriptTab
         : this.uiContextService.getDefaultTab("submit-property-dialog", this.generalTab);
   }

   set defaultTab(tab: string) {
      this.uiContextService.setDefaultTab("submit-property-dialog", tab);
   }

   protected getScripts(): string[] {
      return [this.model.clickableScriptPaneModel.scriptExpression,
              this.model.clickableScriptPaneModel.onClickExpression];
   }

   protected closing(isApply: boolean, collapse: boolean = false) {
      const payload = {collapse: collapse, result: this.model};
      isApply ? this.onApply.emit(payload) : this.onCommit.emit(this.model);
   }
}
