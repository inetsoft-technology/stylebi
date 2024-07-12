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
import { DOCUMENT } from "@angular/common";
import { HttpClient, HttpParams } from "@angular/common/http";
import { Component, EventEmitter, Inject, Input, OnInit, Output } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Condition } from "../../../common/data/condition/condition";
import { ConditionOperation } from "../../../common/data/condition/condition-operation";
import { ConditionValueType } from "../../../common/data/condition/condition-value-type";
import { JunctionOperator } from "../../../common/data/condition/junction-operator";
import { JunctionOperatorType } from "../../../common/data/condition/junction-operator-type";
import { DataRef } from "../../../common/data/data-ref";
import { XSchema } from "../../../common/data/xschema";
import { isValidConditionList } from "../../../common/util/condition.util";
import { LocalStorage } from "../../../common/util/local-storage.util";
import { Tool } from "../../../../../../shared/util/tool";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { ModelService } from "../../../widget/services/model.service";
import { AssemblyConditionDialogModel } from "../../data/ws/assembly-condition-dialog-model";
import { Worksheet } from "../../data/ws/worksheet";
import { SimpleConditionItemPaneProvider } from "./simple-condition-item-pane-provider";
import { ComponentTool } from "../../../common/util/component-tool";

const CHECK_CONDITION_TRAP_URI = "../api/composer/worksheet/check-condition-trap/";

@Component({
   selector: "assembly-condition-dialog",
   templateUrl: "assembly-condition-dialog.component.html"
})
export class AssemblyConditionDialog implements OnInit {
   @Input() assemblyName: string;
   @Input() worksheet: Worksheet;
   @Output() onCommit = new EventEmitter<AssemblyConditionDialogModel>();
   @Output() onCancel = new EventEmitter<string>();
   public XSchema = XSchema;
   public ConditionOperation = ConditionOperation;
   public ConditionValueType = ConditionValueType;
   model: AssemblyConditionDialogModel;
   REST_CONTROLLER: string = "../api/composer/ws/assembly-condition-dialog-model";
   SOCKET_CONTROLLER: string = "/events/composer/ws/assembly-condition-dialog-model/";
   simpleProvider: SimpleConditionItemPaneProvider;
   simpleConditionList: any[];
   simpleFields: DataRef[] = [];
   grayedOutFields: DataRef[];
   conditionListValid: boolean;
   formValid = () => this.conditionListValid;
   private _conditionListCheckpoint: any[]; // last valid conditionlist
   variableNames: string[] = [];

   private set conditionListCheckpoint(conditionListCheckpoint: any[]) {
      this._conditionListCheckpoint = Tool.clone(conditionListCheckpoint);
   }

   private get conditionListCheckpoint(): any[] {
      return this._conditionListCheckpoint;
   }

   constructor(private modelService: ModelService,
               private worksheetClient: ViewsheetClientService,
               private http: HttpClient,
               private modalService: NgbModal,
               @Inject(DOCUMENT) private document: any)
   {
   }

   ngOnInit(): void {
      this.initVariableNames();
      this.simpleProvider = new SimpleConditionItemPaneProvider(this.http,
         this.worksheet.runtimeId, this.assemblyName);

      const params = new HttpParams()
         .set("runtimeId", this.worksheet.runtimeId)
         .set("assemblyName", this.assemblyName);

      this.modelService.getModel(this.REST_CONTROLLER, params).subscribe(
         (data) => {
            this.model = <AssemblyConditionDialogModel> data;
            this.simpleProvider.showOriginalName = true;
            this.simpleProvider.fields = this.model.expressionFields;
            this.simpleProvider.variableNames = this.model.variableNames;
            this.simpleProvider.scriptDefinitions = this.model.scriptDefinitions;
            this.updateModelFromLocalStorage();

            if(!this.model.advanced) {
               this.updateSimpleFromAdvanced();
               this.checkConditionTrap(this.simpleConditionList, this.simpleConditionList);
               this.conditionListCheckpoint = this.simpleConditionList;
            }
            else {
               this.updateAdvancedConditionValidity();
               this.checkConditionTrap(this.model.preAggregateConditionList,
                  this.model.preAggregateConditionList);
               this.conditionListCheckpoint = this.model.preAggregateConditionList;
            }
         }
      );
   }

   initVariableNames() {
      for(let variableId in this.worksheet.variables) {
         if(!!variableId) {
            let variable = this.worksheet.variables[variableId];

            if(!!variable.info && variable.classType == "VariableAssembly") {
               this.variableNames[this.variableNames.length] = variable.name;
            }
         }
      }
   }

   updateModelFromLocalStorage() {
      let localAdvanced: boolean = LocalStorage.getItem("ws.condition") === "advanced";

      if(localAdvanced) {
         this.model.advanced = true;
      }
   }

   advancedChange(advanced: boolean): void {
      if(!this.model.advanced) {
         this.updateAdvancedFromSimple();
      }
      else {
         if(this.isConditionListSimple(this.model.preAggregateConditionList) &&
            this.isConditionListSimple(this.model.postAggregateConditionList))
         {
            this.updateSimpleFromAdvanced();
         }
         else {
            event.preventDefault();
            ComponentTool.showMessageDialog(this.modalService, "_#(js:Warning)",
               "_#(js:common.condition.unsupportedCondition)",
               {"ok": "_#(js:OK)", "cancel": "_#(js:Cancel)"}, {
                  backdrop: false,
               })
               .then((buttonClicked) => {
                  switch(buttonClicked) {
                     case "ok":
                        this.model.advanced = false;
                        this.simpleConditionList = [];
                        break;
                     default:
                        break;
                  }
               }, () => {
               });
         }
      }

      this.model.advanced = advanced;
   }

   /**
    * Check if a condition list is simple. A simple condition list is one that does not
    * contain OR junctions and any indentation.
    */
   isConditionListSimple(conditionList: any[]): boolean {
      if(conditionList == null) {
         return true;
      }

      for(let i = 0; i < conditionList.length; i++) {
         if(conditionList[i].level != 0) {
            return false;
         }

         if(i % 2 != 0 && conditionList[i].type != JunctionOperatorType.AND) {
            return false;
         }
      }

      return true;
   }

   /**
    * Updates the simple condition list from the advanced condition lists
    * (pre, post, ranking).
    */
   updateSimpleFromAdvanced(): void {
      this.simpleConditionList = [];

      if(this.model.preAggregateConditionList &&
         this.model.preAggregateConditionList.length > 0)
      {
         this.simpleConditionList = this.simpleConditionList.concat(
            this.model.preAggregateConditionList);
      }

      if(this.model.postAggregateConditionList &&
         this.model.postAggregateConditionList.length > 0)
      {
         if(this.simpleConditionList.length > 0) {
            this.simpleConditionList.push(<JunctionOperator> {
               jsonType: "junction",
               type: JunctionOperatorType.AND,
               level: 0
            });
         }

         this.simpleConditionList = this.simpleConditionList.concat(
            this.model.postAggregateConditionList);
      }

      if(this.model.rankingConditionList &&
         this.model.rankingConditionList.length > 0)
      {
         if(this.simpleConditionList.length > 0) {
            this.simpleConditionList.push(<JunctionOperator> {
               jsonType: "junction",
               type: JunctionOperatorType.AND,
               level: 0
            });
         }

         this.simpleConditionList = this.simpleConditionList.concat(
            this.model.rankingConditionList);
      }

      this.updateSimpleFields();
      this.simpleConditionListChange(this.simpleConditionList);
   }

   updateSimpleFields(): void {
      this.simpleFields = [];
      let fields = this.model.postAggregateFields.concat(this.model.preAggregateFields);
      let names = [];

      fields.forEach(fld => {
         if(!names.includes(fld.view)) {
            this.simpleFields.push(fld);
            names.push(fld.view);
         }
      });
   }

   /**
    * Updates the advanced condition lists (pre, post, ranking) from the simple
    * condition list.
    */
   updateAdvancedFromSimple(): void {
      this.model.preAggregateConditionList = [];
      this.model.postAggregateConditionList = [];
      this.model.rankingConditionList = [];

      for(let i = 0; i < this.simpleConditionList.length; i++) {
         if(i % 2 == 0) {
            let condition: Condition = this.simpleConditionList[i];

            // is it a ranking condition
            if(condition.operation == ConditionOperation.TOP_N ||
               condition.operation == ConditionOperation.BOTTOM_N)
            {
               this.addCondition(condition, this.model.rankingConditionList);
            }
            // check if the condition belongs to the post-aggregate conditions
            else if(condition.field.classType === "AggregateRef"
               || condition.field.formulaName)
            {
               this.addCondition(condition, this.model.postAggregateConditionList);
            }
            // else just add to the pre-aggregate conditions
            else {
               this.addCondition(condition, this.model.preAggregateConditionList);
            }
         }
      }
   }

   /**
    * Helper function for adding a condition into a condition list. Adds an AND junction
    * operator whenever appropriate.
    */
   addCondition(condition: Condition, conditionList: any[]) {
      if(conditionList.length > 0) {
         conditionList.push(<JunctionOperator> {
            jsonType: "junction",
            type: JunctionOperatorType.AND,
            level: 0
         });
      }

      conditionList.push(condition);
   }

   ok(): void {
      // If using simple conditions (one list) then we need to turn them back into
      // advanced conditions (three distinct lists) before sending the conditions
      // back to the server. Could potentially make this more seamless by updating
      // the advanced conditions every time the simple conditions change.
      if(!this.model.advanced) {
         this.updateAdvancedFromSimple();
         LocalStorage.setItem("ws.condition", "basic");
      }
      else {
         LocalStorage.setItem("ws.condition", "advanced");
      }

      this.trimModel();
      this.worksheetClient.sendEvent(
         this.SOCKET_CONTROLLER + Tool.byteEncode(this.assemblyName), this.model);
      this.onCommit.emit(this.model);
   }

   /** Trim model that is sent to the server so that the message size is not prohibitively large. */
   private trimModel() {
      if(this.model) {
         delete this.model.expressionFields;
         delete this.model.preAggregateFields;
         delete this.model.postAggregateFields;
         delete this.model.subqueryTables;
         delete this.model.variableNames;
         delete this.model.expressionFields;
         delete this.model.scriptDefinitions;

         if(this.model.mvConditionPaneModel) {
            let mvModel = this.model.mvConditionPaneModel;
            delete mvModel.preAggregateFields;
            delete mvModel.postAggregateFields;
         }
      }
   }

   cancel(): void {
      this.onCancel.emit("cancel");
   }

   simpleConditionListChange(simpleConditionList: any[]) {
      this.simpleConditionList = simpleConditionList;
      this.conditionListValid = isValidConditionList(simpleConditionList);

      if(this.conditionListValid && simpleConditionList.length > 0
         && !!this.conditionListCheckpoint)
      {
         if(this.shouldCheckTrap(simpleConditionList, this.conditionListCheckpoint)) {
            this.checkConditionTrap(simpleConditionList, this.conditionListCheckpoint);
         }

         this.conditionListCheckpoint = simpleConditionList;
      }
   }

   preAggregateConditionListChange(preAggregateConditionList: any[]) {
      this.model.preAggregateConditionList = preAggregateConditionList;
      this.updateAdvancedConditionValidity();
      const preAggregateConditionListValid = isValidConditionList(preAggregateConditionList);

      if(preAggregateConditionListValid && preAggregateConditionList.length > 0 &&
         !!this.conditionListCheckpoint)
      {
         if(this.shouldCheckTrap(preAggregateConditionList, this.conditionListCheckpoint)) {
            this.checkConditionTrap(preAggregateConditionList, this.conditionListCheckpoint);
         }

         this.conditionListCheckpoint = preAggregateConditionList;
      }
   }

   postAggregateConditionListChange(postAggregateConditionList: any[]) {
      this.model.postAggregateConditionList = postAggregateConditionList;
      this.updateAdvancedConditionValidity();
   }

   rankingConditionListChange(rankingConditionList: any[]) {
      this.model.rankingConditionList = rankingConditionList;
      this.updateAdvancedConditionValidity();
   }

   /**
    * Checks the validity of the advanced condition model.
    */
   private updateAdvancedConditionValidity() {
      this.conditionListValid = isValidConditionList(this.model.preAggregateConditionList) &&
         isValidConditionList(this.model.postAggregateConditionList) &&
         isValidConditionList(this.model.rankingConditionList);
   }

   /**
    * Check for a trap change between the new and old condition lists.
    * Updates the trap fields.
    *
    * @param newConditionList the new condition list
    * @param oldConditionList the old condition list
    */
   private checkConditionTrap(newConditionList: any[], oldConditionList: any[]) {
      const model = {newConditionList, oldConditionList, tableName: this.assemblyName};
      const uri = CHECK_CONDITION_TRAP_URI + Tool.byteEncode(this.worksheet.runtimeId);

      this.modelService.sendModel<any>(uri, model)
         .subscribe((res) => {
            if(res.body) {
               const trapValidator: {showTrap: boolean, trapFields: DataRef[]} = res.body;

               if(trapValidator.showTrap) {
                  /** Blur active elements to avoid this angular issue:
                   * {@link https://github.com/angular/angular/issues/16820} */
                  if(this.document.activeElement) {
                     this.document.activeElement.blur();
                  }

                  ComponentTool.showTrapAlert(this.modalService, true, null, {backdrop: false})
                     .then((buttonClicked) => {
                        if(buttonClicked === "undo") {
                           if(this.model.advanced) {
                              this.model.preAggregateConditionList = oldConditionList;
                           }
                           else {
                              this.simpleConditionList = oldConditionList;
                           }

                           this.conditionListCheckpoint = oldConditionList;
                        }
                        else {
                           this.grayedOutFields = trapValidator.trapFields;
                           this.simpleProvider.grayedOutFields = this.grayedOutFields;
                        }
                     });
               }
               else {
                  this.grayedOutFields = trapValidator.trapFields;
                  this.simpleProvider.grayedOutFields = this.grayedOutFields;
               }
            }
         });
   }

   /**
    * Check whether or not checking trap is required.
    *
    * @param newConditionList the new condition list
    * @param oldConditionList the old condition list
    *
    * @returns  true if the trap should be checked, false otherwise
    */
   private shouldCheckTrap(newConditionList: any[], oldConditionList: any[]): boolean {
      const newFields = this.getUniqueFields(newConditionList);
      const oldFields = this.getUniqueFields(oldConditionList);

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
}
