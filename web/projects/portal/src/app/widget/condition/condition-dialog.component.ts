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
   AfterViewInit,
   ViewChild,
   Component,
   EventEmitter,
   Input,
   Output,
   Renderer2,
   ElementRef
} from "@angular/core";
import { ConditionItemPaneProvider } from "../../common/data/condition/condition-item-pane-provider";
import { JunctionOperatorType } from "../../common/data/condition/junction-operator-type";
import { SubqueryTable } from "../../common/data/condition/subquery-table";
import { DataRef } from "../../common/data/data-ref";
import { Tool } from "../../../../../shared/util/tool";
import { isValidConditionList } from "../../common/util/condition.util";
import { Condition } from "../../common/data/condition/condition";
import { ConditionPane } from "../../widget/condition/condition-pane.component";
import { ConditionDialogService } from "./condition-dialog.service";
import { BaseResizeableDialogComponent } from "../../vsobjects/dialog/base-resizeable-dialog.component";

@Component({
   selector: "condition-dialog",
   templateUrl: "condition-dialog.component.html",
})
export class ConditionDialog extends BaseResizeableDialogComponent implements AfterViewInit {
   @Input() simplePane: boolean = false;
   @Input() provider: ConditionItemPaneProvider;
   @Input() subqueryTables: SubqueryTable[];
   @Input() fields: DataRef[];
   @Input() isVSContext = true;
   @Input() showOriginalName: boolean = false;
   @Output() conditionListUpdated: EventEmitter<any[]> = new EventEmitter<any[]>();
   @Output() onCommit: EventEmitter<any[]> = new EventEmitter<any[]>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   conditionListClone: any[];
   conditionListValid: boolean;
   @ViewChild(ConditionPane) conditionPane: ConditionPane;

   @Input()
   set conditionList(conditionList: any[]) {
      this.conditionListClone = Tool.clone(conditionList);
      this.updateConditionListValidity();
   }

   constructor(private conditionService: ConditionDialogService,
               protected renderer: Renderer2, protected element: ElementRef)
   {
      super(renderer, element);
   }

   ngAfterViewInit() {
      super.ngAfterViewInit();
      this.conditionService.dirtyCondition = null;
      this.conditionService.dirtyJunction = null;
   }

   conditionListChanged(conditionList: any[]) {
      this.conditionListClone = conditionList;
      this.updateConditionListValidity();
      this.conditionListUpdated.emit(conditionList);
      this.conditionService.dirtyCondition = null;
      this.conditionService.dirtyJunction = null;
   }

   conditionChanged(value: {selectedIndex: number, condition: Condition}) {
      this.conditionService.dirtyCondition = value;
   }

   junctionChanged(value: {selectedIndex: number, junctionType: JunctionOperatorType}) {
      this.conditionService.dirtyJunction = value;
   }

   ok(): void {
      if(this.simplePane) {
         this.onCommit.emit(this.conditionListClone);
      }
      else {
         const save = () => this.conditionPane.save();
         const isValid = () => this.conditionPane.isConditionValid();
         const saveOption = () => this.conditionPane.saveOption();

         this.conditionService.checkDirtyConditions(save, isValid, saveOption).then((success: boolean) => {
            if(success) {
               this.onCommit.emit(this.conditionListClone);
            }
            else {
               this.cancel();
            }
         });
      }
   }

   cancel(): void {
      this.onCancel.emit("cancel");
   }

   private updateConditionListValidity() {
      this.conditionListValid = isValidConditionList(this.conditionListClone);
   }
}
