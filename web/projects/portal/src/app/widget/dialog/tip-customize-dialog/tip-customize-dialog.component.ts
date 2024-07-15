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
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { UIContextService } from "../../../common/services/ui-context.service";
import { ComponentTool } from "../../../common/util/component-tool";
import { TipCustomizeDialogModel } from "./tip-customize-dialog-model";

@Component({
   selector: "tip-customize-dialog",
   templateUrl: "tip-customize-dialog.component.html"
})
export class TipCustomizeDialog implements OnChanges {
   @Input() model: TipCustomizeDialogModel;
   @Input() tooltipOnly: boolean = false;
   @Output() confirm = new EventEmitter<TipCustomizeDialogModel>();
   @Output() cancel = new EventEmitter<string>();
   form: UntypedFormGroup;

   constructor(private modalService: NgbModal,
               private uiContextService: UIContextService)
   {
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes.hasOwnProperty("model")) {
         this.initForm();
      }
   }

   private initForm(): void {
      this.form = new UntypedFormGroup({
         customRB: new UntypedFormControl(this.model.customRB),
         customTip: new UntypedFormControl({value: this.model.customTip, disabled: this.model.customRB != "CUSTOM" },
            [Validators.required]),
         combinedTip: new UntypedFormControl({
            value: this.model.combinedTip && this.model.customRB != "CUSTOM" && this.model.customRB != "NONE",
            disabled: this.model.customRB == "CUSTOM" || this.model.customRB == "NONE" || !this.model.lineChart}),
      });

      this.form.get("customRB").valueChanges.subscribe(custom => {
         if(custom == "CUSTOM") {
            this.form.get("customTip").enable();
            this.form.get("combinedTip").disable();
            this.form.get("combinedTip").setValue(false);
         }
         else if(custom == "NONE") {
            this.form.get("customTip").disable();
            this.form.get("combinedTip").disable();
            this.form.get("combinedTip").setValue(false);
         }
         else {
            this.form.get("customTip").disable();

            if(this.model.lineChart) {
               this.form.get("combinedTip").enable();
            }
         }
      });

      this.form.valueChanges.subscribe(_ => {
         const value = this.form.getRawValue();
         this.model.customRB = value["customRB"];
         this.model.customTip = value["customTip"];
         this.model.combinedTip = value["combinedTip"];
      });
   }

   cancelChanges(): void {
      this.cancel.emit("cancel");
   }

   saveChanges(): void {
      if(this.isValid()) {
         this.confirm.emit(this.model);
      }
   }

   private isValid(): boolean {
      if(this.model.customRB != "CUSTOM") {
         return true;
      }

      let exps: any[] = this.model.customTip.match(/\{[^}.*]*[.*]*[^}.*]*\}/g);

      if(!exps) {
         return true;
      }

      for(let exp of exps) {
         if(exp.length < 2) {
            this.showMessageDialog(this.model.customTip);
            return false;
         }

         exp = exp.substr(1, exp.length - 2);

         if(this.isInTipValues(exp)) {
            return true;
         }

         //remove the exp format.
         let expArr: string[] = exp.split(",");
         exp = expArr[0];

         if(!this.isInTipValues(exp)) {
            let idx: number = parseInt(exp, 10);

            if(isNaN(idx) || (idx + "").length != exp.length ||
               idx > this.model.dataRefList.length - 1 || idx < 0)
            {
               this.showMessageDialog(exp);
               return false;
            }
         }
      }

      return true;
   }

   // check if the exp is an available dataref name.
   private isInTipValues(exp: string): boolean {
      return this.model.dataRefList.indexOf(exp) != -1;
   }

   private showMessageDialog(msg: string): void {
      ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", "_#(js:viewer.viewsheet.chart.tooltip.invalid) " + "_*" + msg);
   }

   get cshid(): string {
      return "CustomTooltip";
   }
}
