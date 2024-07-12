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
   ViewChild,
   TemplateRef,
   ViewEncapsulation,
   Output,
   EventEmitter,
} from "@angular/core";
import { ConditionModel } from "../../../../model/datasources/database/vpm/condition/condition-model";
import { OperationModel } from "../../../../model/datasources/database/vpm/condition/clause/operation-model";
import { VPMColumnModel } from "../../../../model/datasources/database/vpm/condition/vpm-column-model";
import { VPMConditionDialogModel } from "../../../../model/datasources/database/vpm/condition/vpm-condition-dialog-model";
import { HttpClient, HttpParams } from "@angular/common/http";
import { NgbModal, NgbModalOptions } from "@ng-bootstrap/ng-bootstrap";
import { Tool } from "../../../../../../../../../shared/util/tool";
import { ComponentTool } from "../../../../../../common/util/component-tool";
import { ConditionTypes } from "../../../../model/datasources/database/vpm/condition/condition-types.enum";
import { Observable } from "rxjs";
import { StringWrapper } from "../../../../model/datasources/database/string-wrapper";
import { ClauseModel } from "../../../../model/datasources/database/vpm/condition/clause/clause-model";
import { ClauseValueTypes } from "../../../../model/datasources/database/vpm/condition/clause/clause-value-types";
import { ConditionItemModel } from "../../../../model/datasources/database/vpm/condition/condition-item-model";

const TABLE_COLUMNS_URI: string = "../api/data/vpm/columns/";
const PHYSICAL_MODEL_COLUMNS_URI: string = "../api/data/vpm/physicalModel/tables";

@Component({
   selector: "vpm-conditions",
   templateUrl: "vpm-conditions.component.html",
   styleUrls: ["vpm-conditions.component.scss", "../../database-physical-model/database-model-pane.scss"],
   encapsulation: ViewEncapsulation.None
})
export class VPMConditionsComponent {
   _conditions: ConditionModel[];
   @Input() set conditions(conditions: ConditionModel[]) {
      this._conditions = conditions;

      if(conditions && conditions.length > 0) {
         this.editingCondition = conditions[0];
         this.editCondition(null, this.editingCondition);
      }
      else {
         this.editingCondition = null;
         this.selectedConditions = [];
      }
   }

   get conditions(): ConditionModel[] {
      return this._conditions;
   }
   @Input() databaseName: string;
   @Input() operations: OperationModel[] = [];
   @Input() sessionOperations: OperationModel[] = [null, null];
   @Output() refreshedColumns: EventEmitter<boolean> = new EventEmitter<boolean>();
   @Output() tableChange: EventEmitter<boolean> = new EventEmitter<boolean>();
   @ViewChild("chooseTableDialog") chooseTableDialog: TemplateRef<any>;
   @ViewChild("vpmConditionDialog") vpmConditionDialog: TemplateRef<any>;
   editingCondition: ConditionModel;
   selectedConditions: ConditionModel[];
   existsNames: string[] = [];
   currentColumns: VPMColumnModel[] = [];
   refreshingColumns: boolean = false;
   vpmConditionDialogModel: VPMConditionDialogModel;
   ConditionTypes = ConditionTypes;
   private defaultCondition: ConditionModel = {
      name: "",
      clauses: [],
      type: ConditionTypes.TABLE,
      tableName: "",
      script: ""
   };

   constructor(private httpClient: HttpClient, private modalService: NgbModal) {
   }

   /**
    * Add a new default condition and select it.
    */
   addCondition(): void {
      const newCondition: ConditionModel = Tool.clone(this.defaultCondition);
      newCondition.name = this.getNextConditionName();
      this.conditions.push(newCondition);
      this.editCondition(null, newCondition);
   }

   getNextConditionName(): string {
      const newNamePre = "condition";
      let existIndexs = [];

      for(let i = 0; i < this.conditions.length; i++) {
         if(this.conditions[i] == null || this.conditions[i].name == null) {
            continue;
         }

         let name = this.conditions[i].name;

         if(name === newNamePre) {
            existIndexs[0] = true;
            continue;
         }

         let index = name.indexOf(newNamePre);

         if(index == 0) {
            let number = parseInt(name.substring(newNamePre.length), 10);

            if(!Number.isNaN(number)) {
               existIndexs[number] = true;
            }
         }
      }

      if(this.conditions.length == 0) {
         return newNamePre;
      }

      for(let index = 0; index < existIndexs.length; index++) {
         if(existIndexs[index] !== true) {
            return index == 0 ? newNamePre : newNamePre + index;
         }
      }

      return newNamePre + existIndexs.length;
   }

   deleteSelectedCondition(): void {
      const msg = this.selectedConditions.length > 1
         ? "_#(js:data.vpm.confirmConditions)"
         : "_#(js:data.vpm.confirmSingleCondition)";

      ComponentTool.showConfirmDialog(this.modalService, "_#(js:data.vpm.removeConditionTooltip)",
         msg).then((buttonClicked) => {
            if(buttonClicked === "ok") {
               for(let i: number = 0; i < this.selectedConditions.length; i++) {
                  let cond = this.selectedConditions[i];

                  for(let j: number = 0; j < this.conditions.length; j++) {
                     if(cond == this.conditions[j]) {
                        this.conditions.splice(j, 1);
                        break;
                     }
                  }
               }

               this.editingCondition = null;
               this.selectedConditions = [];
               this.tableChange.emit();
            }
         });
   }

   deleteDisabled(): boolean {
      if(this.conditions.length > 0 && this.selectedConditions.length > 0) {
         return false;
      }

      return true;
   }

   /**
    * Confirm then delete the given condition.
    * @param condition  the condition to delete
    * @param index   the index of the condition in the conditions array
    */
   deleteCondition(condition: ConditionModel, index: number): void {
      ComponentTool.showConfirmDialog(this.modalService, "_#(js:data.vpm.removeConditionTooltip)",
         "_#(js:data.vpm.confirmSingleCondition)")
         .then((buttonClicked) => {
            if(buttonClicked === "ok") {
              if(this.editingCondition == condition) {
                 this.editingCondition = null;
              }

               for(let i: number = 0; i < this.selectedConditions.length; i++) {
                  if(condition == this.selectedConditions[i]) {
                     this.selectedConditions.splice(i, 1);
                  }
               }

              this.conditions.splice(index, 1);
              this.tableChange.emit();
            }
         });
   }

   isSelected(condition: ConditionModel): boolean {
      for(let i: number = 0; i < this.selectedConditions.length; i++) {
         if(condition == this.selectedConditions[i]) {
            return true;
         }
      }

      return false;
   }

   /**
    * Select a condition for editing.
    * @param condition  the condition to edit
    */
   editCondition(evt: MouseEvent, condition: ConditionModel): void {
      condition.script = condition.script == null ? "" : condition.script;

      if(evt && (evt.ctrlKey || evt.shiftKey)) {
         this.editingCondition = condition;
         this.selectedConditions.push(condition);
      }
      else {
         this.editingCondition = condition;
         this.selectedConditions = [condition];
      }

      this.existsNames = this.conditions
         .filter(cond => cond != condition)
         .map(cond => cond.name);
      this.refreshedColumns.emit(false);
      this.refreshColumns(true);
   }

   /**
    * Open the choose table dialog. Reset selected condition type if this is called from changing
    * type and user cancels.
    * @param typeChange if opening dialog because of type change
    */
   chooseTable(typeChange: boolean = false): void {
      const oldTableName: string = this.editingCondition.tableName;

      if(typeChange) {
         this.editingCondition.tableName = null;
      }

      this.modalService.open(this.chooseTableDialog, { backdrop: "static" }).result.then(
         (result: string) => {
            this.editingCondition.tableName = result;
            this.tableChange.emit();
            this.refreshColumns();
            this.clearClauses();
         },
         (reject) => {
            // change table name back to original
            this.editingCondition.tableName = oldTableName;

            if(typeChange) {
               // change type back to original
               this.editingCondition.type = this.editingCondition.type == ConditionTypes.TABLE ?
                  ConditionTypes.PHYSICAL_MODEL : ConditionTypes.TABLE;
            }
         }
      );
   }

   /**
    * Check if selected condition's table name is null.
    * @returns {boolean}   true if null
    */
   get tableNameNull(): boolean {
      return this.editingCondition.tableName == null || this.editingCondition.tableName.length == 0;
   }

   /**
    * Get the columns of the selected table/physicalModel and update any clause of type field to
    * have the correct field.
    * @param updateFieldTypes if should update field type clauses to point to the new column
    */
   refreshColumns(updateFieldTypes: boolean = false): void {
      if(this.tableNameNull) {
         this.currentColumns = [];
         return;
      }

      this.refreshingColumns = true;
      let request: Observable<VPMColumnModel[]>;

      if(this.editingCondition.type == ConditionTypes.TABLE) {
         request = this.httpClient
            .post<VPMColumnModel[]>(TABLE_COLUMNS_URI + this.databaseName,
                                    new StringWrapper(this.editingCondition.tableName));
      }
      else {
         let params: HttpParams = new HttpParams()
            .set("database", this.databaseName)
            .set("tableName", this.editingCondition.tableName);
         request = this.httpClient.get<VPMColumnModel[]>(PHYSICAL_MODEL_COLUMNS_URI,
            { params: params});
      }

      request
         .subscribe(
            data => {
               this.currentColumns = data;

               // update field types for selected condition clauses
               if(updateFieldTypes) {
                  this.editingCondition.clauses.filter(clause => !clause.junc)
                     .forEach(clause => {
                        const clauseModel: ClauseModel = <ClauseModel> clause;

                        [clauseModel.value1, clauseModel.value2, clauseModel.value3]
                           .forEach(clauseValue => {
                              // if clause type is field, find field in returned columns and update type
                              if(clauseValue.type == ClauseValueTypes.FIELD) {
                                 clauseValue.field =
                                    data.find(col => col.name == clauseValue.expression);
                              }
                           });
                     });
               }
            },
            err => {
               this.currentColumns = [];
            },
            () => {
               this.refreshingColumns = false;

               if(updateFieldTypes) {
                  this.refreshedColumns.emit(true);
               }
            }
         );
   }

   /**
    * Open the edit vpm condition dialog to edit the selected conditions clauses.
    */
   editClauses(): void {
      this.vpmConditionDialogModel = new VPMConditionDialogModel(this.databaseName,
         this.editingCondition.tableName,
         this.editingCondition.type == ConditionTypes.PHYSICAL_MODEL,
         this.currentColumns, Tool.clone(this.editingCondition.clauses),
         this.operations, this.sessionOperations);

      let options: NgbModalOptions = {
         size: "lg",
         windowClass: "condition-dialog",
         backdrop: "static"
      };

      this.modalService.open(this.vpmConditionDialog, options).result.then(
         (result: ConditionItemModel[]) => {
            this.editingCondition.clauses = result;
         },
         (reject) => {}
      );
   }

   /**
    * Called when user selects a new table/physicalModel. Clear the old clauses.
    */
   clearClauses(): void {
      this.editingCondition.clauses = [];
   }

   expressionChanged(value: string): void {
      this.editingCondition.script = value;
   }
}
