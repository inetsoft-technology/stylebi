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
import { HttpClient } from "@angular/common/http";
import {
   AfterViewInit,
   Component,
   ElementRef,
   EventEmitter,
   Input,
   OnInit,
   Output,
   Renderer2,
   ViewChild
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Condition } from "../../common/data/condition/condition";
import { ConditionOperation } from "../../common/data/condition/condition-operation";
import { ConditionValueType } from "../../common/data/condition/condition-value-type";
import { JunctionOperatorType } from "../../common/data/condition/junction-operator-type";
import { VSConditionDialogModel } from "../../common/data/condition/vs-condition-dialog-model";
import { DataRef } from "../../common/data/data-ref";
import { XSchema } from "../../common/data/xschema";
import { isValidConditionList } from "../../common/util/condition.util";
import { Tool } from "../../../../../shared/util/tool";
import { ModelService } from "../../widget/services/model.service";
import { ConditionDialogService } from "../../widget/condition/condition-dialog.service";
import { VSConditionItemPaneProvider } from "./vs-condition-item-pane-provider";
import { ComponentTool } from "../../common/util/component-tool";
import { ConditionPane } from "../../widget/condition/condition-pane.component";
import { BaseResizeableDialogComponent } from "./base-resizeable-dialog.component";

const CHECK_CONDITION_TRAP_URI = "../api/composer/viewsheet/check-condition-trap/";

@Component({
   selector: "vs-condition-dialog",
   templateUrl: "vs-condition-dialog.component.html",
   providers: [
      ModelService,
      ConditionDialogService
   ]
})
export class VSConditionDialog extends BaseResizeableDialogComponent implements OnInit, AfterViewInit {
   @Input() highlightModel: VSConditionDialogModel;
   @Input() model: VSConditionDialogModel;
   @Input() nonSupportBrowseFields: string[];
   @Input() runtimeId: string;
   @Input() assemblyName: string;
   @Input() variableValues: string[];
   @Input() supportApply: boolean = true;
   @Input() isHighlight: boolean = false;
   @Input() checkTrap:
      (callback: () => void, conditionModel: VSConditionDialogModel) => void;
   @Output() onCommit = new EventEmitter<VSConditionDialogModel>();
   @Output() onCancel = new EventEmitter<string>();
   @Output() onApply = new EventEmitter<{collapse: boolean, result: any}>();
   public XSchema = XSchema;
   public ConditionOperation = ConditionOperation;
   public ConditionValueType = ConditionValueType;
   advancedMode: boolean = false;
   controller: string = "../api/composer/vs/vs-condition-dialog-model";
   provider: VSConditionItemPaneProvider;
   conditionListCheckpoint: any[];
   @ViewChild(ConditionPane) conditionPane: ConditionPane;

   constructor(private http: HttpClient,
               private modelService: ModelService,
               private modalService: NgbModal,
               private conditionService: ConditionDialogService,
               protected renderer: Renderer2,
               protected element: ElementRef)
   {
      super(renderer, element);
   }

   ngOnInit(): void {
      if(this.highlightModel) {
         this.model = Tool.clone(this.highlightModel);
      }

      if(this.model) {
         this.model.conditionList = this.model.conditionList ? this.model.conditionList : [];
         this.provider = new VSConditionItemPaneProvider(
            this.http, this.runtimeId, this.assemblyName, this.model.tableName,
            this.variableValues, this.isHighlight, this.nonSupportBrowseFields);

         // the field in condition may not contain complete information (named group).
         // get the field from the field list which is more accurate. (60408)
         this.model.conditionList.forEach(cond => {
            const field = this.model.fields.find(a => a.view == cond.field.view);
            if(field) {
               cond.field = field;
            }
         });
      }

      if(!this.checkTrap) {
         this.conditionListCheckpoint = Tool.clone(this.model.conditionList);
         this.checkConditionTrap(true);
      }
   }

   ngAfterViewInit(): void {
      this.conditionService.dirtyCondition = null;
      this.conditionService.dirtyJunction = null;
      super.ngAfterViewInit();
   }

   ok(): void {
      this.submit(true);
   }

   private submit(commit: boolean, collapse: boolean = false) {
      const save = () => this.conditionPane.save();
      const isValid = () => this.conditionPane.isConditionValid();
      const saveOption = () => this.conditionPane.saveOption();
      this.conditionService.checkDirtyConditions(save, isValid, saveOption).then((success: boolean) => {
         if(success) {
            if(!!this.checkTrap) {
               this.checkTrap(() => {
                  if(commit) {
                     this.onCommit.emit(this.getServerAppliedModel());
                  }
                  else {
                     this.onApply.emit({collapse: collapse, result: this.getServerAppliedModel()});
                  }
               }, this.model);
            }
            else if(commit) {
               this.onCommit.emit(this.getServerAppliedModel());
            }
            else {
               this.onApply.emit({collapse: collapse, result: this.getServerAppliedModel()});
            }
         }
      });
   }

   apply(event: boolean): void {
      this.submit(false, event);
   }

   cancel(): void {
      this.onCancel.emit("cancel");
   }

   conditionListChanged(value: any[]) {
      this.model.conditionList = value;
      this.conditionService.dirtyCondition = null;
      this.conditionService.dirtyJunction = null;

      if(!this.checkTrap) {
         const conditionListValid: boolean = isValidConditionList(value);

         if(conditionListValid && value.length > 0 && !!this.conditionListCheckpoint) {
            this.checkConditionTrap();
         }
      }
   }

   conditionChanged(value: {selectedIndex: number, condition: Condition}) {
      this.conditionService.dirtyCondition = value;
   }

   junctionChanged(value: {selectedIndex: number, junctionType: JunctionOperatorType}) {
      this.conditionService.dirtyJunction = value;
   }

   /**
    * Check for a trap change between the new and old condition lists.
    * Updates the trap fields.
    */
   private checkConditionTrap(check?: boolean) {
      if(!check && !this.shouldCheckTrap()) {
         this.conditionListCheckpoint = Tool.clone(this.model.conditionList);
         return;
      }

      const model = {
         newConditionList: this.model.conditionList,
         oldConditionList: this.conditionListCheckpoint,
         tableName: this.assemblyName
      };
      const uri = CHECK_CONDITION_TRAP_URI + Tool.byteEncode(this.runtimeId);

      this.modelService.sendModel<any>(uri, model)
         .subscribe((res) => {
            if(!!res.body) {
               const trapValidator: {showTrap: boolean, trapFields: DataRef[]} = res.body;

               if(trapValidator.showTrap) {
                  ComponentTool.showTrapAlert(this.modalService, true, null, {backdrop: false})
                     .then((buttonClicked) => {
                        if(buttonClicked === "undo") {
                           this.model.conditionList =
                              Tool.clone(this.conditionListCheckpoint);
                        }
                        else {
                           this.conditionListCheckpoint = Tool.clone(this.model.conditionList);
                           this.provider.grayedOutFields = trapValidator.trapFields;
                        }
                     });
               }
               else {
                  this.conditionListCheckpoint = Tool.clone(this.model.conditionList);
                  this.provider.grayedOutFields = trapValidator.trapFields;
               }
            }
         });
   }

   /**
    * Check whether or not checking trap is required.
    *
    * @returns  true if the trap should be checked, false otherwise
    */
   private shouldCheckTrap(): boolean {
      const newFields = this.getUniqueFields(this.model.conditionList);
      const oldFields = this.getUniqueFields(this.conditionListCheckpoint);

      if(newFields.length !== oldFields.length) {
         return true;
      }

      for(const newField of newFields) {
         if(oldFields.indexOf(newField) === -1) {
            return true;
         }
      }

      return false;
   }

   /**
    * Return the unique field names of the given condition list.
    *
    * @param conditionList the condition list to find the unique fields of
    *
    * @returns the unique fields of the condition list
    */
   private getUniqueFields(conditionList: any[]): string[] {
      const fieldSet = new Set<string>();

      for(let i = 0; i < conditionList.length; i += 2) {
         const condition = conditionList[i] as Condition;

         if(condition.field != null) {
            fieldSet.add(condition.field.name);
         }

         for(const value of condition.values) {
            if(value.type === ConditionValueType.FIELD && value.value != null) {
               fieldSet.add(value.value.name);
            }
         }
      }

      return Array.from(fieldSet);
   }

   // remove fields that are not used on the server side to reduce the transmission size
   getServerAppliedModel(): VSConditionDialogModel {
      return {
         tableName: this.model.tableName,
         fields: [],
         conditionList: this.model.conditionList
      };
   }
}
