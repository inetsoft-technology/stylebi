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
import { UntypedFormGroup } from "@angular/forms";
import { TableViewPropertyDialogModel } from "../model/table-view-property-dialog-model";
import { ScriptPaneTreeModel } from "../../widget/dialog/script-pane/script-pane-tree-model";
import { UIContextService } from "../../common/services/ui-context.service";
import { PropertyDialogService } from "../util/property-dialog.service";
import { PropertyDialog } from "../../composer/dialog/vs/property-dialog.component";

@Component({
   selector: "table-view-property-dialog",
   templateUrl: "table-view-property-dialog.component.html",
})
export class TableViewPropertyDialog extends PropertyDialog implements OnInit {
   @Input() model: TableViewPropertyDialogModel;
   @Input() scriptTreeModel: ScriptPaneTreeModel;
   form: UntypedFormGroup;
   generalTab: string = "table-view-property-dialog-general-tab";
   scriptTab: string = "table-view-property-dialog-script-tab";
   formValid = () => this.form && this.form.valid;

   public constructor(protected uiContextService: UIContextService,
                      protected propertyDialogService: PropertyDialogService)
   {
      super(uiContextService, null, propertyDialogService);
   }

   ngOnInit(): void {
      super.ngOnInit();
      this.form = new UntypedFormGroup({
         tableViewGeneralPaneForm: new UntypedFormGroup({})
      });
   }

   get defaultTab(): string {
      return this.openToScript ? this.scriptTab
         : this.uiContextService.getDefaultTab("table-view-property-dialog", this.generalTab);
   }

   set defaultTab(tab: string) {
      this.uiContextService.setDefaultTab("table-view-property-dialog", tab);
   }

   protected getScripts(): string[] {
      return [this.model.vsAssemblyScriptPaneModel.expression];
   }

   protected closing(isApply: boolean, collapse: boolean = false) {
      const payload = {collapse: collapse, result: this.model};
      isApply ? this.onApply.emit(payload) : this.onCommit.emit(this.model);
   }
}
