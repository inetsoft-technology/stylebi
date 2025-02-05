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
import { Component, EventEmitter, Input, OnInit, Output, ViewChild } from "@angular/core";
import { TrapInfo } from "../../../common/data/trap-info";
import { ChartPropertyDialogModel } from "../../model/chart-property-dialog-model";
import { UntypedFormGroup } from "@angular/forms";
import { ScriptPaneTreeModel } from "../../../widget/dialog/script-pane/script-pane-tree-model";
import { VSTrapService } from "../../util/vs-trap.service";
import { ChartGeneralPane } from "./chart-general-pane.component";
import { UIContextService } from "../../../common/services/ui-context.service";
import { ContextProvider } from "../../context-provider.service";
import { PropertyDialogService } from "../../util/property-dialog.service";
import { PropertyDialog } from "../../../composer/dialog/vs/property-dialog.component";

const CHECK_TRAP_URI: string = "../api/composer/vs/chart-property-dialog-model/checkTrap/";

@Component({
   selector: "chart-property-dialog",
   templateUrl: "chart-property-dialog.component.html",
})
export class ChartPropertyDialog extends PropertyDialog implements OnInit {
   @Input() model: ChartPropertyDialogModel;
   @Input() scriptTreeModel: ScriptPaneTreeModel;
   @Input() viewer: boolean = false;
   @Input() chartType: number;

   @ViewChild(ChartGeneralPane) chartGeneralPane: ChartGeneralPane;
   form: UntypedFormGroup;
   generalTab: string = "general-tab";
   scriptTab: string = "script-tab";
   advancedTab: string = "advanced-tab";
   lineTab: string = "line-tab";
   hierarchyTab: string = "hierarchy-tab";
   formValid = () => this.isValid();

   public constructor(protected uiContextService: UIContextService,
                      protected propertyDialogService: PropertyDialogService,
                      protected trapService: VSTrapService,
                      private contextProvider: ContextProvider) {
      super(uiContextService, trapService, propertyDialogService);
   }

   ngOnInit(): void {
      super.ngOnInit();
      this.form = new UntypedFormGroup({
         chartGeneralPaneForm: new UntypedFormGroup({}),
         chartAdvancePaneForm: new UntypedFormGroup({}),
         chartLinePaneForm: new UntypedFormGroup({})
      });
   }

   get defaultTab(): string {
      const lastTab = this.uiContextService.getDefaultTab("chart-property-dialog", null);

      return this.openToScript ? this.scriptTab
         : this.verifyLastTab(lastTab) ? lastTab
         : !this.composer ? this.advancedTab : this.generalTab;
   }

   set defaultTab(tab: string) {
      this.uiContextService.setDefaultTab("chart-property-dialog", tab);
   }

   get composer(): boolean {
      return !!this.contextProvider.composer || !!this.contextProvider.composerBinding;
   }

   get linePaneVisible() {
      return this.model?.chartLinePaneModel?.gridLineVisible
         || this.model?.chartLinePaneModel?.innerLineVisible
         || this.model?.chartLinePaneModel?.facetGridVisible
         || this.model?.chartLinePaneModel?.trendLineVisible;
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
         chartGeneralPaneModel: this.model.chartGeneralPaneModel,
         chartAdvancedPaneModel: this.model.chartAdvancedPaneModel,
         chartLinePaneModel: this.model.chartLinePaneModel,
         hierarchyPropertyPaneModel: hierarchyPropertyPaneModel,
         vsAssemblyScriptPaneModel: this.model.vsAssemblyScriptPaneModel
      };

      const payload = {collapse: collapse, result: model};
      const trapInfo = new TrapInfo(CHECK_TRAP_URI,
         this.model.chartGeneralPaneModel.generalPropPaneModel.basicGeneralPaneModel.name,
         this.runtimeId, model);

      this.trapService.checkTrap(trapInfo,
         () => isApply ? this.onApply.emit(payload) : this.onCommit.emit(model),
         () => {},
         () => isApply ? this.onApply.emit(payload) : this.onCommit.emit(model)
      );
   }

   isValid() {
      return !this.form.invalid &&
         !(this.chartGeneralPane && this.chartGeneralPane.alphaInvalid);
   }

   verifyLastTab(tab: string): boolean {
      if(!tab) {
         return false;
      }

      if(tab == this.lineTab) {
         return this.linePaneVisible;
      }
      else if(tab == this.generalTab || tab == this.scriptTab || tab == this.hierarchyTab) {
         return this.composer;
      }

      return true;
   }
}
