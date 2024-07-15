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
import {
   Component,
   EventEmitter,
   Input,
   OnChanges,
   OnInit,
   Output,
   SimpleChange,
   TemplateRef,
   ViewChild
} from "@angular/core";
import { NgbModal, NgbModalOptions } from "@ng-bootstrap/ng-bootstrap";
import { Observable } from "rxjs";
import { BrowseDataModel } from "../../common/data/browse-data-model";
import { Condition } from "../../common/data/condition/condition";
import { ColumnRef } from "../../binding/data/column-ref";
import {
   ConditionItemPaneProvider
} from "../../common/data/condition/condition-item-pane-provider";
import { ConditionOperation } from "../../common/data/condition/condition-operation";
import { ConditionValueType } from "../../common/data/condition/condition-value-type";
import { ExpressionType } from "../../common/data/condition/expression-type";
import { ExpressionValue } from "../../common/data/condition/expression-value";
import { SubqueryTable } from "../../common/data/condition/subquery-table";
import { DataRef } from "../../common/data/data-ref";
import { FormulaField } from "../../common/data/formula-field";
import { FormulaType } from "../../common/data/formula-type";
import { XSchema } from "../../common/data/xschema";
import { UIContextService } from "../../common/services/ui-context.service";
import { Tool } from "../../../../../shared/util/tool";
import { FormulaEditorDialogModel } from "../formula-editor/formula-editor-dialog-model";
import { TreeNodeModel } from "../tree/tree-node-model";
import { ConditionFieldComboModel } from "./condition-field-combo-model";
import { SourceInfo } from "../../binding/data/source-info";
import { BaseField } from "../../binding/data/base-field";
import { ConditionValue } from "../../common/data/condition/condition-value";

@Component({
   selector: "condition-item-pane",
   templateUrl: "condition-item-pane.component.html",
   styleUrls: ["condition-item-pane.component.scss"]
})
export class ConditionItemPane implements OnInit, OnChanges {
   public XSchema = XSchema;
   public ConditionOperation = ConditionOperation;
   public ConditionValueType = ConditionValueType;
   @ViewChild("formulaEditorDialog") formulaEditorDialog: TemplateRef<any>;
   @Input() subqueryTables: SubqueryTable[];
   @Input() condition: Condition;
   @Input() availableFields: DataRef[];
   @Input() provider: ConditionItemPaneProvider;
   @Input() isVSContext = true;
   @Input() variableNames: string[];
   @Input() showExpression: boolean = true;
   @Input() isHighlight: boolean = false;
   @Input() table: string;
   @Input() vsId: string;
   @Input() addNoneItem = true;
   @Input() showOriginalName: boolean = false;
   @Output() conditionChange = new EventEmitter<Condition>();
   @Output() expressionRenamed = new EventEmitter<{oname: string, nname: string}>();
   operations: ConditionOperation[];
   valueTypes: ConditionValueType[];
   expressionTypes: ExpressionType[];
   negationAllowed: boolean;
   dataFunction = () => {
      return this.getData();
   };
   variablesFunction = () => {
      return this.getVariables();
   };
   columnTreeFunction = (value: ExpressionValue) => {
      return this.getColumnTree(value);
   };
   scriptDefinitionsFunction = (value: ExpressionValue) => {
      return this.getScriptDefinitions(value);
   };
   columnTreeModel: TreeNodeModel;
   formula: DataRef;
   fieldsModel: ConditionFieldComboModel;

   get formulaExpression(): string {
      return this.formula ? (<FormulaField> this.formula).exp : null;
   }

   get formulaType(): string {
      if(this.isReportWorksheetSource()) {
         return FormulaType.SCRIPT;
      }

      return this.formula ? (<FormulaField> this.formula).formulaType : null;
   }

   @Input() set fields(fields: DataRef[]) {
      if(fields == null) {
         return;
      }

      let tree: TreeNodeModel = <TreeNodeModel> {
         children: []
      };

      let entityMap: Map<string, DataRef[]> = new Map<string, DataRef[]>();

      for(let field of fields) {
         let entity = !!field.entity ? field.entity : "_#(js:Query Fields)";
         let arr: DataRef[] = entityMap.get(entity);

         if(!arr) {
            entityMap.set(entity, [field]);
         }
         else {
            arr.push(field);
         }
      }

      let entitites: string[] = Array.from(entityMap.keys());

      for(let entitiy of entitites) {
         let entityNode: TreeNodeModel = <TreeNodeModel> {
            label: entitiy,
            leaf: false,
            children: []
         };

         let flds: DataRef[] = entityMap.get(entitiy);

         for(let field of flds) {
            let fieldNode: TreeNodeModel = <TreeNodeModel> {
               label: field.view,
               data: field,
               tooltip: field.description,
               leaf: true
            };

            entityNode.children.push(fieldNode);
         }

         tree.children.push(entityNode);
      }

      this.fieldsModel = <ConditionFieldComboModel> {
         list: fields,
         tree: tree
      };
   }

   constructor(private dialogService: NgbModal,
               private uiContextService: UIContextService)
   {
   }

   ngOnInit(): void {
      this.updateCondition();
      this.formula = this.condition.field;
   }

   getSource(): SourceInfo {
      return null;
   }

   get showUseList(): boolean {
      // doesn't make sense for date range
      if(this.condition.operation == ConditionOperation.DATE_IN) {
         return false;
      }

      if(this.condition.field != null && this.condition.field.classType == "ColumnRef") {
         if((this.condition.field as ColumnRef).dataRefModel.classType == "DateRangeRef") {
            return false;
         }
      }
      else if(this.isHighlight && (this.isDateRange(this.condition.field) || !this.isBrowseDataEnabled())) {
         return false;
      }

      return this.isVSContext;
   }

   // check if name is range(str)
   private isGroupRef(name: string, range: string) {
      return name.startsWith(range + "(") && name.endsWith(")");
   }

   // check if BaseField is a DateRangeRef (for reports)
   private isDateRange(fld: DataRef): boolean {
      if(fld == null || fld.classType != "BaseField") {
         return false;
      }

      const dateType = XSchema.isDateType(fld.dataType);
      const grouped = (fld as BaseField).gfld;
      const name = fld.attribute;

      if(dateType && (grouped || this.isGroupRef(name, "None"))) {
         return true;
      }

      // date-part has a type of integer, so has to check name
      return this.isGroupRef(name, "QuarterOfYear") ||
         this.isGroupRef(name, "MonthOfYear") ||
         this.isGroupRef(name, "WeekOfYear") ||
         this.isGroupRef(name, "DayOfMonth") ||
         this.isGroupRef(name, "DayOfWeek") ||
         this.isGroupRef(name, "HourOfDay");
   }

   ngOnChanges(changes: { [propertyName: string]: SimpleChange }): void {
      this.updateCondition();
   }

   conditionChanged(): void {
      this.updateCondition();
      this.conditionChange.emit(this.condition);
   }

   operationChanged(): void {
      this.condition.values = this.getDefaultConditionValues();
      this.conditionChanged();
   }

   private getDefaultConditionValues(): ConditionValue[] {
      const value = this.getDefaultConditionValue();
      let values: ConditionValue[] = [];

      if(value != null) {
         const conditionValueTypes = this.provider.getConditionValueTypes(this.condition);
         let type: ConditionValueType;

         if(conditionValueTypes != null && conditionValueTypes.length > 0) {
            type = conditionValueTypes[0];
         }
         else {
            type = ConditionValueType.VALUE;
         }

         switch(this.condition.operation) {
            case ConditionOperation.EQUAL_TO:
            case ConditionOperation.LESS_THAN:
            case ConditionOperation.GREATER_THAN:
            case ConditionOperation.STARTING_WITH:
            case ConditionOperation.CONTAINS:
            case ConditionOperation.LIKE:
               values.push({value, type});
               break;
            case ConditionOperation.BETWEEN:
               values.push({value, type}, {value, type});
               break;
            default:
               // no-op
         }
      }

      let dtype = this.condition?.field?.dataType;

      if(!XSchema.isDateType(dtype) &&(this.condition.operation == ConditionOperation.TOP_N ||
         this.condition.operation == ConditionOperation.BOTTOM_N))
      {
         values.push({value:{n: 3}, type: ConditionValueType.VALUE});
      }

      return values;
   }

   private getDefaultConditionValue(): any {
      const type = this.condition?.field?.dataType || XSchema.STRING;
      let val: any;

      if(XSchema.isStringType(type)) {
         val = "";
      }
      else {
         val = null;
      }

      return val;
   }

   fieldChanged(field: DataRef): void {
      if(!Tool.isEquals(this.condition.field, field)) {
         // refChanged is false if same ref but different aggregate formula
         const refChanged = !this.condition.field || !field ||
            this.condition.field.view != field.view;

         this.condition.field = field;

         if(refChanged) {
            this.condition.operation = this.operations[0];
            this.condition.values = this.getDefaultConditionValues();
         }

         this.conditionChanged();
      }
   }

   updateCondition(): void {
      // if column type is changed, the field in condition may still contain the old
      // data type. copy from the field list to update. (60640)
      if(this.condition?.field && this.fieldsModel) {
         for(let i = 0; i < this.fieldsModel.list.length; i++) {
            if(this.fieldsModel.list[i].view == this.condition.field.view) {
               this.condition.field = this.fieldsModel.list[i];
            }
         }
      }

      this.operations = this.provider.getConditionOperations(this.condition);

      if(this.condition != null &&
         this.operations.indexOf(this.condition.operation) == -1 &&
         this.operations.length > 0)
      {
         this.condition.operation = this.operations[0];
      }

      this.valueTypes = this.provider.getConditionValueTypes(this.condition);
      this.expressionTypes = this.provider.getExpressionTypes(this.condition);
      this.negationAllowed = this.provider.isNegationAllowed(this.condition);

      if(this.condition != null && !this.negationAllowed) {
         this.condition.negated = false;
      }

      // if use-list is disabled, it should not be set to use list
      if(!this.showUseList) {
         this.condition.values.forEach(c => c.choiceQuery = null);
      }
   }

   getData(): Observable<BrowseDataModel> {
      return this.provider.getData(this.condition);
   }

   getVariables(): Observable<any[]> {
      return this.provider.getVariables(this.condition);
   }

   getColumnTree(value: ExpressionValue, oldFormulaName?: string): Observable<TreeNodeModel> {
      return this.provider.getColumnTree(value, this.variableNames, oldFormulaName);
   }

   getScriptDefinitions(value: ExpressionValue): Observable<any> {
      return this.provider.getScriptDefinitions(value);
   }

   isBrowseDataEnabled(): boolean {
      return this.provider.isBrowseDataEnabled(this.condition);
   }

   getGrayedOutFields(): DataRef[] {
      return this.provider.getGrayedOutFields();
   }

   isFormulaField(): boolean {
      return this.condition.field != null &&
         this.condition.field.classType == "FormulaField";
   }

   isReportWorksheetSource(): boolean {
      const source = this.getSource();
      return source != null ? source.type == SourceInfo.ASSET : false;
   }

   openFormulaEdit(isNew: boolean): void {
      const formulaField: FormulaField = <FormulaField> {
         classType: "FormulaField",
         formulaType: FormulaType.SCRIPT,
         exp: undefined,
         visible: true,
         order: 0,
         dataType: "string"
      };
      this.formula = isNew ? formulaField : this.condition.field;

      if(this.provider.isSqlMergeable()) {
         formulaField.formulaType = FormulaType.SQL;
      }

      let oldFormulaName = isNew || this.formula.classType != "FormulaField" ? null : this.formula.name;

      this.getColumnTree(null, oldFormulaName).subscribe(
         (data: TreeNodeModel) => {
            this.columnTreeModel = data;
            const options: NgbModalOptions = {windowClass: "formula-dialog"};

            this.dialogService.open(this.formulaEditorDialog, options).result.then(
               (result: FormulaEditorDialogModel) => {
                  if(!isNew) {
                     if(this.condition.field.name != result.formulaName) {
                        this.expressionRenamed.emit({oname: this.condition.field.name,
                                                     nname: result.formulaName});
                     }

                     this.condition.field = <FormulaField> {
                        ...this.condition.field,
                        name: result.formulaName,
                        attribute: result.formulaName,
                        view: result.formulaName,
                        formulaType: result.formulaType,
                        dataType: result.dataType,
                        exp: result.expression};
                  }

                  this.provider.setFormula(result);
               },
               () => {
               }
            );
         });
   }

   private createFormulaByResult(result: FormulaEditorDialogModel) {
      let formulaField: FormulaField = <FormulaField> {
         classType: "FormulaField",
         formulaType: undefined,
         exp: undefined,
         visible: true,
         order: 0
      };
      formulaField.name = result.formulaName;
      formulaField.formulaType = result.formulaType;
      formulaField.dataType = result.dataType;
      formulaField.exp = result.expression;
      return formulaField;
   }
}
