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
import { UntypedFormGroup } from "@angular/forms";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Tool } from "../../../../../shared/util/tool";
import { TrapInfo } from "../../common/data/trap-info";
import { UIContextService } from "../../common/services/ui-context.service";
import { ComponentTool } from "../../common/util/component-tool";
import { ScriptPaneTreeModel } from "../../widget/dialog/script-pane/script-pane-tree-model";
import { RangeSliderPropertyDialogModel } from "../model/range-slider-property-dialog-model";
import { VSTrapService } from "../util/vs-trap.service";
import { PropertyDialogService } from "../util/property-dialog.service";
import { PropertyDialog } from "../../composer/dialog/vs/property-dialog.component";

const CHECK_TRAP_URI: string = "../api/composer/vs/range-slider-property-dialog-model/checkTrap/";

@Component({
   selector: "range-slider-property-dialog",
   templateUrl: "range-slider-property-dialog.component.html",
})
export class RangeSliderPropertyDialog extends PropertyDialog implements OnInit {
   @Input() model: RangeSliderPropertyDialogModel;
   @Input() scriptTreeModel: ScriptPaneTreeModel;
   @Input() advancedPaneOnly: boolean = false;
   form: UntypedFormGroup;
   invalidData: boolean;
   generalTab = "range-slider-property-dialog-general-tab";
   scriptTab = "range-slider-property-dialog-script-tab";
   advancedTab = "range-slider-property-dialog-advanced-tab";
   formValid = () => this.form && this.form.valid;

   public constructor(protected uiContextService: UIContextService,
                      protected trapService: VSTrapService,
                      protected propertyDialogService: PropertyDialogService,
                      private modalService: NgbModal)
   {
      super(uiContextService, trapService, propertyDialogService);
   }

   ngOnInit(): void {
      super.ngOnInit();
      this.form = new UntypedFormGroup({
         rangeSliderForm: new UntypedFormGroup({}),
         rangeSliderAdvancedForm: new UntypedFormGroup({})
      });
   }

   get defaultTab(): string {
      return this.openToScript ? this.scriptTab
         : this.uiContextService.getDefaultTab(
            "range-slider-property-dialog",
            this.advancedPaneOnly ? this.advancedTab : this.generalTab);
   }

   set defaultTab(tab: string) {
      this.uiContextService.setDefaultTab("range-slider-property-dialog", tab);
   }

   protected getScripts(): string[] {
      return [this.model.vsAssemblyScriptPaneModel.expression];
   }

   protected closing(isApply: boolean, collapse: boolean = false) {
      if(this.canSubmit()) {
         this.submit(isApply, collapse);
      }
   }

   private submit(isApply: boolean, collapse: boolean = false): void {
      const model2: RangeSliderPropertyDialogModel = Tool.clone(this.model);
      // don't send tree back, could be very large
      model2.rangeSliderDataPaneModel.targetTree = null;
      model2.rangeSliderDataPaneModel.compositeTargetTree = null;
      const trapInfo = new TrapInfo(CHECK_TRAP_URI, this.assemblyName, this.runtimeId, model2);
      const payload = {collapse: collapse, result: model2};

      this.trapService.checkTrap(
         trapInfo,
         () => isApply ? this.onApply.emit(payload) : this.onCommit.emit(model2),
         () => {},
         () => isApply ? this.onApply.emit(payload) : this.onCommit.emit(model2),
      );
   }

   private canSubmit(): boolean {
      if(this.invalidData) {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Warning)",
            "_#(js:viewer.viewsheet.timeSlider.simpleBlankWarning)",
            {"ok": "_#(js:OK)"}, { backdrop: false });
         return false;
      }

      return true;
   }
}
