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
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from "@angular/core";
import { UntypedFormGroup } from "@angular/forms";
import { Subscription } from "rxjs";
import { TrapInfo } from "../../../common/data/trap-info";
import { UIContextService } from "../../../common/services/ui-context.service";
import { VSTrapService } from "../../../vsobjects/util/vs-trap.service";
import { ScriptPaneTreeModel } from "../../../widget/dialog/script-pane/script-pane-tree-model";
import { TextPropertyDialogModel } from "../../data/vs/text-property-dialog-model";
import { PropertyDialogService } from "../../../vsobjects/util/property-dialog.service";
import { PropertyDialog } from "./property-dialog.component";

const CHECK_TRAP_URI: string = "../api/composer/vs/text-property-dialog-model/checkTrap/";

@Component({
   selector: "text-property-dialog",
   templateUrl: "text-property-dialog.component.html"
})
export class TextPropertyDialog extends PropertyDialog implements OnInit {
   @Input() model: TextPropertyDialogModel;
   @Input() scriptTreeModel: ScriptPaneTreeModel;
   form: UntypedFormGroup;
   generalTab: string = "text-property-dialog-general-tab";
   scriptTab: string = "text-property-dialog-script-tab";
   formValid = () => this.form && this.form.valid || this.layoutObject;

   public constructor(protected uiContextService: UIContextService,
                      protected trapService: VSTrapService,
                      protected propertyDialogService: PropertyDialogService)
   {
      super(uiContextService, trapService, propertyDialogService);
   }

   ngOnInit(): void {
      super.ngOnInit();
      this.form = new UntypedFormGroup({
         textGeneralForm: new UntypedFormGroup({})
      });
   }

   get defaultTab(): string {
      return this.openToScript ? this.scriptTab
         : this.uiContextService.getDefaultTab("text-property-dialog", this.generalTab);
   }

   set defaultTab(tab: string) {
      this.uiContextService.setDefaultTab("text-property-dialog", tab);
   }

   private checkTrap(isApply: boolean, collapse: boolean = false): void {
      const trapInfo = new TrapInfo(CHECK_TRAP_URI, this.assemblyName, this.runtimeId,
         this.model);

      const payload = {collapse: collapse, result: this.model};
      const submit = () => isApply ? this.onApply.emit(payload) : this.onCommit.emit(this.model);

      this.trapService.checkTrap(trapInfo, submit, () => {}, submit);
   }

   protected closing(isApply: boolean, collapse: boolean = false) {
      this.checkTrap(isApply, collapse);
   }

   protected getScripts(): string[] {
      return [this.model.clickableScriptPaneModel.scriptExpression,
              this.model.clickableScriptPaneModel.onClickExpression];
   }
}
