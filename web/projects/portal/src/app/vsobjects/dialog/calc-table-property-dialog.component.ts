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
import { Component, Input, Output, EventEmitter, OnInit } from "@angular/core";
import { CalcTablePropertyDialogModel } from "../model/calc-table-property-dialog-model";
import { UntypedFormGroup } from "@angular/forms";
import { ScriptPaneTreeModel } from "../../widget/dialog/script-pane/script-pane-tree-model";
import { UIContextService } from "../../common/services/ui-context.service";
import { PropertyDialogService } from "../util/property-dialog.service";
import { PropertyDialog } from "../../composer/dialog/vs/property-dialog.component";

@Component({
   selector: "calc-table-property-dialog",
   templateUrl: "calc-table-property-dialog.component.html",
   styleUrls: ["calc-table-property-dialog.component.scss"]
})
export class CalcTablePropertyDialog extends PropertyDialog implements OnInit {
   @Input() model: CalcTablePropertyDialogModel;
   @Input() scriptTreeModel: ScriptPaneTreeModel;
   initialHeaderRows: number;
   initialHeaderColumns: number;
   showHeaderRowWarning: boolean = false;
   showHeaderColumnWarning: boolean = false;
   controller: string = "../api/composer/vs/calc-table-property-dialog-model/";
   form: UntypedFormGroup;
   generalTab: string = "calc-table-property-dialog-general-tab";
   scriptTab: string = "calc-table-property-dialog-script-tab";
   advancedTab = "calc-table-property-dialog-advanced-tab";
   formValid = () => this.form && this.form.valid;

   public constructor(protected uiContextService: UIContextService,
                      protected propertyDialogService: PropertyDialogService)
   {
      super(uiContextService, null, propertyDialogService);
   }

   ngOnInit(): void {
      super.ngOnInit();
      this.form = new UntypedFormGroup({
         tableViewForm: new UntypedFormGroup({}),
         calcTableAdvancedForm: new UntypedFormGroup({})
      });
      this.initialHeaderRows = this.model.calcTableAdvancedPaneModel.headerRowCount;
      this.initialHeaderColumns = this.model.calcTableAdvancedPaneModel.headerColCount;

      if(this.openToScript) {
         this.defaultTab = this.scriptTab;
      }
   }

   get defaultTab(): string {
      return this.uiContextService.getDefaultTab("calc-table-property-dialog", this.generalTab);
   }

   set defaultTab(tab: string) {
      this.uiContextService.setDefaultTab("calc-table-property-dialog", tab);
   }

   get visibleRows(): number {
      return !!this.model ? this.model.calcTableAdvancedPaneModel.approxVisibleRows : 0;
   }

   get visibleCols(): number {
      return !!this.model ? this.model.calcTableAdvancedPaneModel.approxVisibleCols : 0;
   }

   private checkData(commit: boolean, collapse: boolean = false): void {
      let headerRows = this.model.calcTableAdvancedPaneModel.headerRowCount;
      let visibleRows = Math.min(this.model.calcTableAdvancedPaneModel.rowCount,
         this.visibleRows + this.initialHeaderRows) - headerRows;
      let headerCols = this.model.calcTableAdvancedPaneModel.headerColCount;
      let visibleCols = Math.min(this.model.calcTableAdvancedPaneModel.colCount,
         this.visibleCols + this.initialHeaderColumns) - headerCols;

      if(visibleRows > 0 && visibleCols > 0) {
         this.showHeaderRowWarning = false;
         this.showHeaderColumnWarning = false;

         if(commit) {
            this.onCommit.emit(this.model);
         }
         else {
            this.onApply.emit({collapse: collapse, result: this.model});
         }
      }
      else {
         this.showHeaderRowWarning = visibleRows <= 0;
         this.showHeaderColumnWarning = visibleCols <= 0;
         this.defaultTab = this.advancedTab;
      }
   }

   protected getScripts(): string[] {
      return [this.model.vsAssemblyScriptPaneModel.expression];
   }

   protected closing(isApply: boolean, collapse: boolean = false) {
      this.checkData(!isApply, collapse);
   }
}
