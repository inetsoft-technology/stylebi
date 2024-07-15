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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { DateRangeRef } from "../../../common/data/date-range-ref";
import { ExpressionRef } from "../../../common/data/expression-ref";
import { DateRangeRef as DateEnum } from "../../../common/util/date-range-ref";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { ValueRangeDialogModel } from "../../data/ws/value-range-dialog-model";
import { DateLevelExamplesService } from "../../../common/services/date-level-examples.service";

@Component({
   selector: "date-range-option-dialog",
   templateUrl: "date-range-option-dialog.component.html"
})
export class DateRangeOptionDialog implements OnInit {
   @Input() model: ValueRangeDialogModel;
   @Input() submitCallback: (model: ValueRangeDialogModel) => Promise<boolean> =
      () => Promise.resolve(true);
   @Output() onCommit: EventEmitter<ValueRangeDialogModel> = new EventEmitter<ValueRangeDialogModel>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   public DateEnum = DateEnum;
   form: UntypedFormGroup;
   ref: DateRangeRef;
   dateLevels: string[] = [];
   dateLevelExamples: string[] = [];
   formValid = () => this.form && this.form.valid;

   constructor(private examplesService: DateLevelExamplesService){
   }

   ngOnInit() {
      if(this.model && this.isDateRangeRef(this.model.ref)) {
         this.ref = this.model.ref;
         this.initForm();
      }
      else {
         console.error(this.model);
      }

      this.dateLevels = [
         DateEnum.QUARTER_OF_YEAR_PART + "",
         DateEnum.MONTH_OF_YEAR_PART + "",
         DateEnum.WEEK_OF_YEAR_PART + "",
         DateEnum.DAY_OF_MONTH_PART + "",
         DateEnum.DAY_OF_WEEK_PART + "",
         DateEnum.HOUR_OF_DAY_PART + "",
         DateEnum.YEAR_INTERVAL + "",
         DateEnum.QUARTER_INTERVAL + "",
         DateEnum.MONTH_INTERVAL + "",
         DateEnum.WEEK_INTERVAL + "",
         DateEnum.DAY_INTERVAL + "",
         DateEnum.HOUR_INTERVAL + "",
         DateEnum.MINUTE_INTERVAL + "",
         DateEnum.SECOND_INTERVAL + ""
      ];

      this.examplesService.loadDateLevelExamples(this.dateLevels, "timeInstant")
         .subscribe((data: any) => this.dateLevelExamples = data.dateLevelExamples);
   }

   initForm(): void {
      this.form = new UntypedFormGroup({
         option: new UntypedFormControl(this.ref.option, [
            Validators.required,
         ]),
      });

      if(this.model.oldName == null) {
         this.form.addControl("newName", new UntypedFormControl(this.model.oldName, [
            Validators.required,
            FormValidators.notWhiteSpace,
            FormValidators.calcSpecialCharacters
         ]));
      }
   }

   private isDateRangeRef(ref: ExpressionRef): ref is DateRangeRef {
      return (<DateRangeRef> ref).option !== undefined;
   }

   close(): void {
      this.onCancel.emit("cancel");
   }

   ok(): void {
      this.ref.option = this.form.get("option").value;
      const control = this.form.get("newName");

      if(control) {
         this.model.newName = control.value.trim();
      }

      this.submitCallback(this.model).then((validated) => {
         if(validated) {
            this.onCommit.emit(this.model);
         }
      });
   }

   get baseColumn(): string {
      return this.model && this.model.ref && this.model.ref.ref ? this.model.ref.ref.view : "";
   }

   getDateLevelExample(level: number) {
      let findIndex = this.dateLevels.findIndex((dateLevel) => dateLevel == level + "");
      return this.dateLevelExamples[findIndex];
   }
}
