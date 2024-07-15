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
import { CrosstabPropertyDialogModel } from "../model/crosstab-property-dialog-model";
import { UntypedFormGroup } from "@angular/forms";
import { ScriptPaneTreeModel } from "../../widget/dialog/script-pane/script-pane-tree-model";
import { UIContextService } from "../../common/services/ui-context.service";
import { PropertyDialogService } from "../util/property-dialog.service";
import { PropertyDialog } from "../../composer/dialog/vs/property-dialog.component";
import {TableViewGeneralPaneModel} from "../model/table-view-general-pane-model";
import {CrosstabAdvancedPaneModel} from "../model/crosstab-advanced-pane-model";
import {HierarchyPropertyPaneModel} from "../model/hierarchy-property-pane-model";
import {VSAssemblyScriptPaneModel} from "../../widget/dialog/vsassembly-script-pane/vsassembly-script-pane-model";
import { ContextProvider } from "../context-provider.service";

@Component({
   selector: "crosstab-property-dialog",
   templateUrl: "crosstab-property-dialog.component.html",
})
export class CrosstabPropertyDialog extends PropertyDialog implements OnInit {
   @Input() model: CrosstabPropertyDialogModel;
   @Input() scriptTreeModel: ScriptPaneTreeModel;
   form: UntypedFormGroup;
   generalTab: string = "crosstab-view-property-dialog-general-tab";
   scriptTab: string = "crosstab-view-property-dialog-script-tab";
   formValid = () => this.form && this.form.valid;

   public constructor(protected uiContextService: UIContextService,
                      protected propertyDialogService: PropertyDialogService,
                      private contextProvider: ContextProvider)
   {
      super(uiContextService, null, propertyDialogService);
   }

   ngOnInit(): void {
      super.ngOnInit();
      this.form = new UntypedFormGroup({
         tableViewForm: new UntypedFormGroup({}),
         crosstabAdvancedForm: new UntypedFormGroup({})
      });
   }

   get composer(): boolean {
      return !!this.contextProvider.composer || !!this.contextProvider.composerBinding;
   }

   get defaultTab(): string {
      return this.openToScript ? this.scriptTab
         : this.uiContextService.getDefaultTab("crosstab-property-dialog", this.generalTab);
   }

   set defaultTab(tab: string) {
      this.uiContextService.setDefaultTab("crosstab-property-dialog", tab);
   }

   protected getScripts(): string[] {
      return [this.model.vsAssemblyScriptPaneModel.expression];
   }

   protected closing(isApply: boolean, collapse: boolean = false) {
      let hierarchyPropertyPaneModel = {
         isCube: this.model.hierarchyPropertyPaneModel?.isCube,
         hierarchyEditorModel: this.model.hierarchyPropertyPaneModel?.hierarchyEditorModel,
         columnList: [],
         dimensions: this.model.hierarchyPropertyPaneModel?.dimensions
      };

      // remove fields that are not used on the server side to reduce the transmission size
      let model = {
         tableViewGeneralPaneModel: this.model.tableViewGeneralPaneModel,
         crosstabAdvancedPaneModel: this.model.crosstabAdvancedPaneModel,
         hierarchyPropertyPaneModel: hierarchyPropertyPaneModel,
         vsAssemblyScriptPaneModel: this.model.vsAssemblyScriptPaneModel,
      };

      const payload = {collapse: collapse, result: model};
      isApply ? this.onApply.emit(payload) : this.onCommit.emit(model);
   }
}
