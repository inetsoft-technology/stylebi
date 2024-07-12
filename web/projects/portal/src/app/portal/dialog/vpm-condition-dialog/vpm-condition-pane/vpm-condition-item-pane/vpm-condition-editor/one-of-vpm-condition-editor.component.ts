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
   EventEmitter,
   Input,
   OnChanges,
   OnInit,
   Output,
   SimpleChanges
} from "@angular/core";
import { ClauseValueModel } from "../../../../../data/model/datasources/database/vpm/condition/clause/clause-value-model";
import { OperationModel } from "../../../../../data/model/datasources/database/vpm/condition/clause/operation-model";
import { VPMColumnModel } from "../../../../../data/model/datasources/database/vpm/condition/vpm-column-model";
import { XSchema } from "../../../../../../common/data/xschema";
import { Observable } from "rxjs";
import { ClauseValueTypes } from "../../../../../data/model/datasources/database/vpm/condition/clause/clause-value-types";
import { Tool } from "../../../../../../../../../shared/util/tool";

@Component({
   selector: "one-of-vpm-condition-editor",
   templateUrl: "one-of-vpm-condition-editor.component.html",
   styleUrls: ["one-of-vpm-condition-editor.component.scss"]
})
export class OneOfVpmConditionEditor implements OnChanges {
   _valueModel: ClauseValueModel;

   @Input() set valueModel(model: ClauseValueModel) {
      this._valueModel = model;
      this.updateValues();
      this.updateEditingModel();
   }

   get valueModel(): ClauseValueModel {
      return this._valueModel;
   }

   @Input() operation: OperationModel;
   @Input() fields: VPMColumnModel[] = [];
   @Input() valueTypes: string[] = [];
   @Input() enableBrowseData: boolean = true;
   @Input() valueType: string = XSchema.STRING;
   @Input() dataFunction: () => Observable<string[]>;
   @Input() primaryValue: boolean = false;
   @Input() varShowDate: boolean = false;
   @Input() datasource: string;
   @Input() isWSQuery: boolean;
   @Output() valueChange: EventEmitter<ClauseValueModel> = new EventEmitter<ClauseValueModel>();
   selectedIndex = -1;
   values: string[] = [];
   editingModel: ClauseValueModel;

   ngOnChanges(changes: SimpleChanges) {
      this.updateValues();
      this.updateEditingModel();
   }

   get editorType() {
      return this._valueModel ? this._valueModel.type : null;
   }

   private updateEditingModel() {
      if(this.editorType != ClauseValueTypes.VALUE) {
         this.editingModel = this._valueModel;
      }

      let model = Tool.clone(this._valueModel);
      this.editingModel = model;
   }

   private updateValues(): void {
      this.values = [];

      if(this.editorType != ClauseValueTypes.VALUE) {
         return;
      }

      let expr = this._valueModel.expression;

      if(expr == null) {
         return;
      }

      expr = expr + "";

      if(expr && expr.indexOf("(") == 0 && expr.lastIndexOf(")") == expr.length - 1) {
         expr = expr.substring(1, expr.length - 1);
      }

      this.values = !!expr ? expr.split(",") : [];
   }

   isSelected(val: string): boolean {
      return this.selectedIndex >= 0 && this.selectedIndex < this.values.length ?
         this.values[this.selectedIndex] == val : false;
   }

   selectValue(idx: number, value: string): void {
      this.setSelectedIndex(idx);

      if(this.editingModel) {
         this.editingModel.expression = value;
      }
   }

   selectValues(vals: string[]) {
      this.values = vals;
      this.updateValueModel();
   }

   updateValue(val: ClauseValueModel): void {
      if(this.editorType != val.type || val.type != ClauseValueTypes.VALUE ||
         !Tool.isEquals(val.field, this._valueModel.field))
      {
         this._valueModel.type = val.type;
         this._valueModel.expression = val.expression;
         this._valueModel.field = val.field;
         this._valueModel.query = val.query;
         this.values = [];
         this.valueChange.emit(val);
      }
   }

   add(): void {
      if(!!this.editingModel && !this.isEmpty()) {
         let newValue = this.editingModel.expression;
         this.editingModel.expression = null;

         if(newValue != null && this.values.includes(newValue + "")) {
            return;
         }

         this.values[this.values.length] = newValue;
         this.setSelectedIndex(this.values.length - 1);
         this.updateValueModel();
      }
   }

   isEmpty(): boolean {
      if(!this.editingModel || this.editingModel.expression == null) {
         return true;
      }

      let exp = this.editingModel.expression + "";
      return exp.trim().length == 0;
   }

   private setSelectedIndex(idx: number) {
      this.selectedIndex = idx;
   }

   remove(): void {
      if(this.selectedIndex >= 0 && this.selectedIndex < this.values.length) {
         this.values.splice(this.selectedIndex, 1);
         this.updateValueModel();
      }

      if(this.values.length == 0) {
         this.editingModel.expression = "";
      }
   }

   modify(): void {
      if(this.selectedIndex >= 0 && this.selectedIndex < this.values.length &&
         !!this.editingModel && !!this.editingModel.expression)
      {
         this.values[this.selectedIndex] = this.editingModel.expression;
         this.updateValueModel();
      }
   }

   updateValueModel(): void {
      if(this.editorType !== ClauseValueTypes.VALUE) {
         return;
      }

      this._valueModel.expression = !!this.values ? "(" + this.values.join(",") + ")" : "()";
   }

   isValueListVisible(): boolean {
      return this.editorType === ClauseValueTypes.VALUE;
   }
}
