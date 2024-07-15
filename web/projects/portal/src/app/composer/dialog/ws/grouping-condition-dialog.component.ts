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
import {
   Component,
   ElementRef,
   EventEmitter,
   Input,
   OnInit,
   Output,
   Renderer2, ViewChild
} from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { ConditionExpression } from "../../../common/data/condition/condition-expression";
import { DataRef } from "../../../common/data/data-ref";
import { ConditionList } from "../../../common/util/condition-list";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { ModelService } from "../../../widget/services/model.service";
import { GroupingDialogModel } from "../../data/ws/grouping-dialog-model";
import { GroupingConditionItemPaneProvider } from "./grouping-condition-item-pane-provider";
import { BaseResizeableDialogComponent } from "../../../vsobjects/dialog/base-resizeable-dialog.component";
import { ConditionPane } from "../../../widget/condition/condition-pane.component";
import { ConditionDialogService } from "../../../widget/condition/condition-dialog.service";
import { Condition } from "../../../common/data/condition/condition";

@Component({
   selector: "grouping-condition-dialog",
   templateUrl: "grouping-condition-dialog.component.html",
})
export class GroupingConditionDialog extends BaseResizeableDialogComponent implements OnInit {
   @Input() model: GroupingDialogModel;
   @Input() runtimeId: string;
   @Input() conditionName: string;
   @Input() conditionList: ConditionList;
   @Input() conditionExpressions: ConditionExpression[];
   @Output() onCommit: EventEmitter<any> = new EventEmitter<any>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @ViewChild(ConditionPane) conditionPane: ConditionPane;
   form: UntypedFormGroup;
   provider: GroupingConditionItemPaneProvider;
   fields: DataRef[];
   formValid = () => this.form && this.form.valid;

   constructor(private modelService: ModelService,
               protected renderer: Renderer2,
               protected element: ElementRef,
               private conditionService: ConditionDialogService)
   {
      super(renderer, element);
   }

   ngOnInit() {
      this.provider = new GroupingConditionItemPaneProvider(this.modelService, this.runtimeId);
      let names: string[] = this.conditionExpressions.map((e) => e.name);

      if(!!this.conditionName) {
         names = names.filter((name) => name !== this.conditionName);
      }

      this.form = new UntypedFormGroup({
         name: new UntypedFormControl(this.conditionName, [
            Validators.required,
            FormValidators.exists(names)
         ]),
         conditionList: new UntypedFormControl(this.conditionList, [
            Validators.required
         ])
      });

      if(this.model.onlyFor === undefined) {
         this.fields = <DataRef[]> [{
            attribute: "this",
            classType: "BaseField",
            dataType: this.model.type,
            view: "this"
         }];
      }
      else {
         this.fields = [this.model.attribute];
      }

      this.provider.asset = this.model.onlyFor;
      this.provider.variableNames = this.model.variableNames;
   }

   conditionChange(value: {selectedIndex: number, condition: Condition}) {
      this.conditionService.dirtyCondition = value;
   }

   conditionListChange(conditionList: ConditionList) {
      const control = this.form.get("conditionList");
      control.setValue(conditionList);
      control.markAsDirty();
   }

   ok() {
      const save = () => this.conditionPane.save();
      const isValid = () => this.conditionPane.isConditionValid();
      const saveOption = () => this.conditionPane.saveOption();

      this.conditionService.checkDirtyConditions(save, isValid, saveOption).then((success: boolean) => {
         if(success) {
            this.onCommit.emit({
               name: this.form.get("name").value,
               list: this.form.get("conditionList").value
            });
         }
      });
   }

   cancel() {
      this.onCancel.emit();
   }
}
