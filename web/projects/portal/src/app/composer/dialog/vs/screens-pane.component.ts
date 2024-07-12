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
import {
   Component,
   Input,
   OnChanges,
   OnInit,
   TemplateRef,
   ViewChild,
   ViewEncapsulation,
} from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Tool } from "../../../../../../shared/util/tool";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { ScreensPaneModel } from "../../data/vs/screens-pane-model";
import { Viewsheet } from "../../data/vs/viewsheet";
import { ViewsheetDeviceLayoutDialogModel } from "../../data/vs/viewsheet-device-layout-dialog-model";
import { ViewsheetPrintLayoutDialogModel } from "../../data/vs/viewsheet-print-layout-dialog-model";
import { ComponentTool } from "../../../common/util/component-tool";

@Component({
   encapsulation: ViewEncapsulation.None,
   selector: "screens-pane",
   templateUrl: "screens-pane.component.html",
   styleUrls: ["screens-pane.component.scss"]
})
export class ScreensPane implements OnInit, OnChanges {
   @Input() model: ScreensPaneModel;
   @Input() form: UntypedFormGroup;
   @Input() isPrintLayout: boolean = false;
   @Input() viewsheet: Viewsheet;
   editing: boolean = false;
   selectedLayout: number = -1;
   printLayoutModel: ViewsheetPrintLayoutDialogModel;
   @ViewChild("viewsheetDeviceLayoutDialog") viewsheetDeviceLayoutDialog: TemplateRef<any>;
   @ViewChild("viewsheetPrintLayoutDialog") viewsheetPrintLayoutDialog: TemplateRef<any>;

   constructor(private modalService: NgbModal) {
   }

   ngOnInit(): void {
      this.initForm();

      if(this.model.deviceLayouts.length > 0) {
         this.selectedLayout = 0;
      }
   }

   ngOnChanges(): void {
      if(this.form && this.form.controls["templateWidth"] && this.form.controls["templateHeight"]) {
         // initialized
         this.form.controls["templateWidth"].setValue(this.model.templateWidth, {emitEvent: false});
         this.form.controls["templateHeight"].setValue(this.model.templateHeight, {emitEvent: false});
         this.updateEnabledState();
      }

      if(this.form && this.form.controls["printLayout"]) {
         this.form.controls["printLayout"].setValue(this.getPrintLayoutLabel());
      }
   }

   initForm(): void {
      let control = new UntypedFormControl(this.model.templateHeight, FormValidators.positiveIntegerInRange);
      control.valueChanges.subscribe((value: number) => this.model.templateHeight = value);
      this.form.addControl("templateHeight", control);

      control = new UntypedFormControl(this.model.templateWidth, FormValidators.positiveIntegerInRange);
      control.valueChanges.subscribe((value: number) => this.model.templateWidth = value);
      this.form.addControl("templateWidth", control);

      this.form.addControl("layoutForm", new UntypedFormGroup({}));

      if(this.isPrintLayout) {
         control = new UntypedFormControl(this.model.printLayout, Validators.required);
      }
      else {
         control = new UntypedFormControl();
      }

      this.form.addControl("printLayout", control);
      this.form.controls["printLayout"].setValue(this.getPrintLayoutLabel());
      this.updateEnabledState();
   }

   updateEnabledState(): void {
      if(this.model.targetScreen) {
         this.form.controls["templateWidth"].enable();
         this.form.controls["templateHeight"].enable();
      }
      else {
         this.form.controls["templateWidth"].disable();
         this.form.controls["templateHeight"].disable();
      }
   }

   elementToString(element: any): string {
      return element.name;
   }

   deleteDeviceLayout(): void {
      const message = "_#(js:layout.design.deleteLayout)";

      ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", message,
         {"yes": "_#(js:Yes)", "no": "_#(js:No)"})
         .then((buttonClicked) => {
            if(buttonClicked === "yes") {
               this.model.deviceLayouts.splice(this.selectedLayout, 1);

               if(this.selectedLayout == this.model.deviceLayouts.length) {
                  this.selectedLayout = this.selectedLayout - 1;
               }
            }
         });
   }

   getPrintLayoutLabel(): string {
      return this.model.printLayout ? this.model.printLayout.paperSize
      + (this.model.printLayout.landscape ? " _#(js:Landscape)" : "")
      + " _#(js:Margin): "
      + this.model.printLayout.marginTop + ", "
      + this.model.printLayout.marginLeft + ", "
      + this.model.printLayout.marginBottom + ", "
      + this.model.printLayout.marginRight
         : "_#(js:No Layout)";
   }

   removePrintLayout() {
      this.model.printLayout = null;
      this.form.controls["printLayout"].setValue(this.model.printLayout);
   }

   showViewsheetDeviceLayoutDialogAdd(): void {
      this.showViewsheetDeviceLayoutDialog(false);
   }

   showViewsheetDeviceLayoutDialogEdit(): void {
      this.showViewsheetDeviceLayoutDialog(true);
   }

   private showViewsheetDeviceLayoutDialog(editing: boolean): void {
      if(editing && this.selectedLayout == -1 ||
         this.model.deviceLayouts.length <= this.selectedLayout)
      {
         return;
      }

      this.editing = editing;
      this.modalService.open(this.viewsheetDeviceLayoutDialog, {backdrop: "static"}).result.then(
         (result: ViewsheetDeviceLayoutDialogModel) => {
            if(editing) {
               this.model.deviceLayouts[this.selectedLayout] = result;
            }
            else {
               this.model.deviceLayouts.push(result);
               this.selectedLayout = this.model.deviceLayouts.length - 1;
            }
         },
         () => {
            // cancel
         }
      );
   }

   showViewsheetPrintLayoutDialog(editing: boolean): void {
      this.printLayoutModel = Tool.clone(this.model.printLayout);
      this.modalService.open(this.viewsheetPrintLayoutDialog, {backdrop: "static"}).result.then(
         (result: ViewsheetPrintLayoutDialogModel) => {
            this.model.printLayout = result;
         },
         () => {
            // cancel
         }
      );
   }

   isClearPrintLayoutEnabled(): boolean {
      return this.model.printLayout &&
         (this.viewsheet.currentLayout == null || !this.viewsheet.currentLayout.printLayout);
   }

   isDeleteLayoutEnabled() {
      return this.selectedLayout >= 0 &&
         (this.viewsheet.currentLayout == null ||
          this.model.deviceLayouts[this.selectedLayout].name != this.viewsheet.currentLayout.name);
   }

   onKeyDown(event: KeyboardEvent) {
      if(this.selectedLayout > -1) {
         if(event.keyCode === 38) { //Up
            this.selectedLayout = this.selectedLayout > 0 ?
               this.selectedLayout - 1 : this.selectedLayout;
         }
         else if(event.keyCode === 40) { //Down
            this.selectedLayout = this.selectedLayout < this.model.deviceLayouts.length - 1 ?
               this.selectedLayout + 1 : this.selectedLayout;
         }
      }
   }
}
