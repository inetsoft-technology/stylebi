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
   AfterViewInit,
   Component,
   ElementRef,
   EventEmitter,
   Input, OnDestroy,
   OnInit,
   Output,
   Renderer2,
   TemplateRef,
   ViewChild
} from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";
import { AggregateRef } from "../../common/data/aggregate-ref";
import { AttributeRef } from "../../common/data/attribute-ref";
import { ExpressionType } from "../../common/data/condition/expression-type";
import { DataRef } from "../../common/data/data-ref";
import { FormulaType } from "../../common/data/formula-type";
import { XSchema } from "../../common/data/xschema";
import { Tool } from "../../../../../shared/util/tool";
import { GuiTool } from "../../common/util/gui-tool";
import { AnalysisResult } from "../dialog/script-pane/analysis-result";
import { NewAggrDialogModel } from "../dialog/new-aggr-dialog/new-aggr-dialog-model";
import { ScriptPane } from "../dialog/script-pane/script-pane.component";
import { FormValidators } from "../../../../../shared/util/form-validators";
import { ActionsContextmenuComponent } from "../fixed-dropdown/actions-contextmenu.component";
import { DropdownOptions } from "../fixed-dropdown/dropdown-options";
import { FixedDropdownService } from "../fixed-dropdown/fixed-dropdown.service";
import { TreeNodeModel } from "../tree/tree-node-model";
import { FormulaEditorDialogModel } from "./formula-editor-dialog-model";
import { FormulaEditorService } from "./formula-editor.service";
import { ScriptTreeNodeData } from "./script-tree-node-data";
import { ComponentTool } from "../../common/util/component-tool";
import { AggregateFormula } from "../../binding/util/aggregate-formula";
import { BaseResizeableDialogComponent } from "../../vsobjects/dialog/base-resizeable-dialog.component";
import { FormulaField } from "../../common/data/formula-field";
import { Subscription } from "rxjs";
import {
   FeatureFlagsService,
   FeatureFlagValue
} from "../../../../../shared/feature-flags/feature-flags.service";
import { DynamicDate } from "../../portal/schedule/schedule-task-editor/dynamic-date";

const DATE_PARTS: any[] = [
   { label: "_#(js:Year)", data: "year" },
   { label: "_#(js:QuarterOfYear)", data: "quarter" },
   { label: "_#(js:MonthOfYear)", data: "month" },
   { label: "_#(js:DayOfMonth)", data: "day" },
   { label: "_#(js:DayOfWeek)", data: "weekday" }];

const TIME_PARTS: any[] = [
   { label: "_#(js:HourOfDay)", data: "hour" },
   { label: "_#(js:MinuteOfHour)", data: "minute" },
   { label: "_#(js:SecondOfMinute)", data: "second" }];

const DATE_TIME_PARTS: any[] = [
   { label: "_#(js:Year)", data: "year" },
   { label: "_#(js:QuarterOfYear)", data: "quarter" },
   { label: "_#(js:MonthOfYear)", data: "month" },
   { label: "_#(js:DayOfMonth)", data: "day" },
   { label: "_#(js:DayOfWeek)", data: "weekday" },
   { label: "_#(js:HourOfDay)", data: "hour" },
   { label: "_#(js:MinuteOfHour)", data: "minute" },
   { label: "_#(js:SecondOfMinute)", data: "second" }];

@Component({
   selector: "formula-editor-dialog",
   templateUrl: "formula-editor-dialog.component.html",
   styleUrls: ["formula-editor-dialog.component.scss"]
})
export class FormulaEditorDialog extends BaseResizeableDialogComponent implements
   OnInit, OnDestroy, AfterViewInit
{
   @Input() expression: string;
   @Input() formulaType: any;
   @Input() formulaName: string;
   @Input() dataType: string;
   @Input() dynamicDates: TreeNodeModel = null;

   @Input() set columnTreeRoot(columnRoot: TreeNodeModel) {
      this._columnTreeRoot = columnRoot;
      this.originalColumnTreeRoot = columnRoot;
   }

   get columnTreeRoot(): TreeNodeModel {
      return this._columnTreeRoot;
   }

   @Input() vsId: string;
   @Input() isCube: boolean = false;
   @Input() assemblyName: string;
   @Input() isVSContext: boolean = true;
   @Input() isHighlight: boolean = false;
   @Input() isCalcTable: boolean = false;

   @Input() availableFields: DataRef[];
   @Input() availableCells: string[];
   @Input() columns: DataRef[] = [];
   @Input() sqlMergeable: boolean = true;

   @Input() submitCallback: (_?: FormulaEditorDialogModel) => Promise<boolean> =
      () => Promise.resolve(true);

   @Input() nameVisible: boolean = true;
   @Input() returnTypeVisible: boolean = true;
   @Input() columnTreeEnabled: boolean = true;
   @Input() functionTreeEnabled: boolean = true;
   @Input() isCondition: boolean = false;

   @Input() grayedOutFields: DataRef[];
   @Input() selfVisible: boolean = true;
   @Input() checkDuplicatesInColumnTree: boolean = true;

   // input for create and edit calc field
   @Input() isCalc: boolean = false;
   @Input() createCalcField: boolean = false;
   @Input() calcType: string;
   @Input() calcFieldsGroup: string[] = [];

   @Input() isHyperLink: boolean = false;
   @Input() showFunctionTree: boolean = true;
   @Input() isLMHierarchy: boolean = false;
   @Input() reportEleId: string;
   @Input() formulaFields: FormulaField[];
   @Input() showOriginalName: boolean = false;
   @Input() reportWorksheetSource: boolean = false;
   @Input() task: boolean = false;

   _columnTreeRoot: TreeNodeModel;

   oname: string = null;
   aggrModel: NewAggrDialogModel;
   functionTreeRoot: TreeNodeModel;
   operatorTreeRoot: TreeNodeModel;

   originalColumnTreeRoot: TreeNodeModel;
   analysisResults: AnalysisResult[] = [];
   form: UntypedFormGroup;
   NEW_AGGREGATE: string = "New Aggregate";
   cursor: {line: number, ch: number};
   returnTypes: {label: string, data: string}[];
   @Output() onCommit: EventEmitter<FormulaEditorDialogModel> =
      new EventEmitter<FormulaEditorDialogModel>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @Output() aggregateModify: EventEmitter<any> = new EventEmitter<any>();
   @Output() aggregateDelete: EventEmitter<any> = new EventEmitter<any>();
   @ViewChild("newAggrDialog") newAggrDialog: TemplateRef<any>;
   private _scriptDefinitions: any = null;
   _aggregates: DataRef[] = [];
   public static DATE_PART_COLUMN: string = "date_part_column";
   subscriptions: Subscription = new Subscription();
   private init: boolean = false;

   get title(): string {
      return this.isCube ? "_#(js:Create Measure)" : this.isCalc ? "_#(js:Edit Calculated Field)" :
         "_#(js:Formula Editor)";
   }

   get aggregateOnly(): boolean {
      return this.calcType == "aggregate";
   }

   @Input()
   set aggregates(value: DataRef[]) {
      this._aggregates = value;

      if(this.init) {
         this.populateColumnTree();
      }
   }

   get aggregates() {
      return this._aggregates;
   }

   @Input()
   set scriptDefinitions(value: any) {
      this._scriptDefinitions = value;
   }

   get scriptDefinitions(): any {
      return this.isSqlType() ? null : this._scriptDefinitions;
   }

   constructor(private editorService: FormulaEditorService,
               private modalService: NgbModal,
               protected renderer: Renderer2,
               protected element: ElementRef,
               private featureFlagsService: FeatureFlagsService,
               private dropdownService: FixedDropdownService)
   {
      super(renderer, element);
   }

   ngOnInit(): void {
      this.initForm();
      this.populateTrees();
      this.oname = this.formulaName;
      this.returnTypes = FormulaEditorService.returnTypes;
      this.init = true;
   }

   ngOnDestroy(): void {
      if(!!this.subscriptions) {
         this.subscriptions.unsubscribe();
         this.subscriptions = null;
      }
   }

   ngAfterViewInit() {
      // TODO remove timeout if https://github.com/angular/angular/issues/15634 is implemented
      Promise.resolve(null).then(() => {
         if(this.form.contains("formulaType")) {
            this.checkValid();
         }
      });

      super.ngAfterViewInit();
   }

   initForm() {
      this.form = new UntypedFormGroup({});

      if(this.nameVisible) {
         this.form.addControl("formulaName", new UntypedFormControl(this.formulaName, [
            Validators.required,
            FormValidators.calcSpecialCharacters]));
      }

      if(this.returnTypeVisible) {
         this.form.addControl("formulaType", new UntypedFormControl(this.formulaType));
         this.form.addControl("dataType", new UntypedFormControl(this.dataType));
         this.form.get("formulaType").valueChanges.forEach((value) => {
            this.populateTrees();
            this.checkValid();
            // Need to do this because message dialog causes blur and modifies formControl.touched
            this.form.get("formulaType").markAsTouched();
         });
      }

      if(this.isCalc) {
         this.form.addControl("calcType", new UntypedFormControl(this.calcType, [
            Validators.required
         ]));

         this.subscriptions.add(this.form.get("calcType").valueChanges.subscribe((value) => {
            this.calcType = value;
            this.form.get("dataType").setValue(this.calcType == "aggregate" ? "double" : "string");
            this.form.get("formulaType").setValue(FormulaType.SCRIPT);
         }));
      }
   }

   isDuplicateFormulaName(node: TreeNodeModel, fname: string): boolean {
      if(node == null || node.children == null || node.children.length == 0) {
         return false;
      }

      for(let child of node.children) {
         if(child != null && child.label != this.formulaName &&
            child.label?.toLowerCase() == fname?.toLowerCase() ||
            this.isDuplicateFormulaName(child, fname))
         {
            return true;
         }
      }

      if(this.availableFields && this.availableFields.length > 0) {
         let flds = this.availableFields;

         for(let i = 0; i < flds.length; i++) {
            if(flds[i].attribute != this.formulaName && flds[i].attribute == fname) {
               return true;
            }
         }
      }

      return false;
   }

   ok(): void {
      let model: FormulaEditorDialogModel = {
         expression: this.expression,
         oname: this.oname
      };

      model.formulaName = this.form.contains("formulaName") ?
         this.form.get("formulaName").value : this.formulaName;

      if(this.isCalc && this.calcFieldsGroup &&
         this.calcFieldsGroup.indexOf(model.formulaName) > -1)
      {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", "_#(js:Duplicate Name)!");
         return;
      }

      if(this.checkDuplicatesInColumnTree &&
         this.isDuplicateFormulaName(this.columnTreeRoot, model.formulaName))
      {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
            "_#(js:viewer.formulaNameInUseError)!");
         return;
      }

      if(this.isCycle()) {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
            "_#(js:viewer.formulaUseSelf)!");
         return;
      }

      model.formulaType = this.form.contains("formulaType") ?
         this.form.get("formulaType").value : this.formulaType;
      model.dataType = this.form.contains("dataType") ?
         this.form.get("dataType").value : this.dataType;
      model.calcType = this.form.contains("calcType") ?
         this.form.get("calcType").value : this.calcType;

      this.submitCallback(model).then((valid) => {
         if(valid) {
            this.onCommit.emit(model);
         }
      });
   }

   isCycle(): boolean {
      if(this.formulaFields == null || this.formulaFields.length == 0) {
         return false;
      }

      return this.checkExpression(this.formulaName, this.expression);
   }

   checkExpression(fname: string, exp: string): boolean {
      let script = "field['" + fname + "']";

      if(exp.indexOf(script) != -1) {
         return true;
      }

      for(let formula of this.formulaFields) {
         if(formula != null && formula.exp != null) {
            let cscript = "field['" + formula.name + "']";

            if(exp.indexOf(cscript) != -1) {
               if(this.checkExpression(fname, formula.exp)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   isDuplicateName(): boolean {
      if(!this.formulaFields) {
         return false;
      }

      return this.formulaFields
         .filter(field => field.name != this.formulaName)
         .some(field => field.name == this.form.get("formulaName").value);
   }

   cancel(): void {
      this.onCancel.emit("cancel");
   }

   populateTrees(): void {
      this.populateColumnTree();
      this.populateFunctionTree();
      this.populateOperatorTree();
      this.populateScriptDefinitions();
   }

   analysisResultsChange(analysisResults: AnalysisResult[]): void {
      this.analysisResults = analysisResults;
   }

   expressionChange(obj: any): void {
      let fexpress: string = obj.expression;
      let target: string = obj.target;
      let node: TreeNodeModel = <TreeNodeModel> obj.node;
      let scriptData: ScriptTreeNodeData = <ScriptTreeNodeData> obj.data || obj.node;

      if(node) {
         if(!node.leaf) {
            return;
         }

         if(scriptData.data.data == this.NEW_AGGREGATE) {
            this.showAggregateDialog();

            return;
         }

         fexpress = scriptData.expression;
         const dot = scriptData.data.dot;
         let suffix: string = scriptData.data.suffix || "";
         let isDateField: boolean = node.type === FormulaEditorDialog.DATE_PART_COLUMN;
         let datePart: string = null;

         if(fexpress == null || fexpress.length == 0) {
            if(typeof scriptData.data == "object") {
               fexpress = this.isCube ? scriptData.data.properties.attribute
                  : <string> scriptData.data.data;
            }
            else {
               fexpress = <string> scriptData.data;
            }

            if(isDateField && fexpress.indexOf("(") > 0) {
               let idx = fexpress.indexOf("(");
               datePart = fexpress.substring(0, idx);
               fexpress = fexpress.substring(idx + 1, fexpress.length - 1);
            }
         }

         if(target) {
            if(fexpress != null && fexpress.length > 0 && fexpress !== "[]" &&
               fexpress.charAt(0) == "[" && fexpress.charAt(fexpress.length - 1) == "]")
            {
               fexpress = fexpress.substring(1, fexpress.length - 1);
            }

            const quote: string = (fexpress.indexOf("'") >= 0) ? '"' : "'";

            if(target == "columnTree") {
               if((!this.isSqlType() || this.isHighlight) && this.isVSContext) {
                  if(scriptData.data.parentData && scriptData.data.parentName == "component") {
                     if(fexpress != null && fexpress.indexOf(".") != -1) {
                        fexpress = scriptData.data.parentLabel +
                           "[" + quote + fexpress + quote + "]" + suffix;
                     }
                     else {
                        fexpress = scriptData.data.parentLabel + "." + fexpress + suffix;
                     }
                  }
                  else if(scriptData.data.name == "bindingInfo") {
                     fexpress = scriptData.data.parentLabel + ".bindingInfo." + fexpress + suffix;
                  }
                  else if(scriptData.data.name === "layoutInfo") {
                     fexpress = scriptData.data.parentLabel + ".layoutInfo." + fexpress + suffix;
                  }
                  else if(scriptData.data.name === "yAxis") {
                     fexpress = scriptData.data.parentLabel + ".yAxis." + fexpress + suffix;
                  }
                  else if(scriptData.data.name === "yA2xis") {
                     fexpress = scriptData.data.parentLabel + ".yA2xis." + fexpress + suffix;
                  }
                  else if(scriptData.data.name === "xAxis") {
                     fexpress = scriptData.data.parentLabel + ".xAxis." + fexpress + suffix;
                  }
                  else if(scriptData.data.name === "axis") {
                     fexpress = scriptData.data.parentLabel + ".axis[]." + fexpress + suffix;
                  }
                  else if(scriptData.data.name === "valueFormats") {
                     fexpress = scriptData.data.parentLabel + ".valueFormats[]." + fexpress + suffix;
                  }
                  else if(scriptData.data.name === "colorLegends") {
                     fexpress = scriptData.data.parentLabel + ".colorLegends[]." + fexpress + suffix;
                  }
                  else if(scriptData.data.name === "shapeLegends") {
                     fexpress = scriptData.data.parentLabel + ".shapeLegends[]." + fexpress + suffix;
                  }
                  else if(scriptData.data.name === "sizeLegends") {
                     fexpress = scriptData.data.parentLabel + ".sizeLegends[]." + fexpress + suffix;
                  }
                  else if(scriptData.data.name === "colorLegend") {
                     fexpress = scriptData.data.parentLabel + ".colorLegend." + fexpress + suffix;
                  }
                  else if(scriptData.data.name === "shapeLegend") {
                     fexpress = scriptData.data.parentLabel + ".shapeLegend." + fexpress + suffix;
                  }
                  else if(scriptData.data.name === "sizeLegend") {
                     fexpress = scriptData.data.parentLabel + ".sizeLegend." + fexpress + suffix;
                  }
                  else if(scriptData.data.name === "xTitle") {
                     fexpress = scriptData.data.parentLabel + ".xTitle." + fexpress + suffix;
                  }
                  else if(scriptData.data.name === "x2Title") {
                     fexpress = scriptData.data.parentLabel + ".x2Title." + fexpress + suffix;
                  }
                  else if(scriptData.data.name === "yTitle") {
                     fexpress = scriptData.data.parentLabel + ".yTitle." + fexpress + suffix;
                  }
                  else if(scriptData.data.name === "y2Title") {
                     fexpress = scriptData.data.parentLabel + ".y2Title." + fexpress + suffix;
                  }
                  else if(scriptData.data.name === "highlighted") {
                     fexpress = scriptData.data.parentLabel + ".highlighted." + fexpress + suffix;
                  }
                  else if(scriptData.data.name === "graph") {
                     fexpress = scriptData.data.parentLabel + ".graph." + fexpress + suffix;
                  }
                  else if(scriptData.data.parentData && scriptData.data.parentName == "parameter") {
                     fexpress = !Tool.isIdentifier(fexpress) ?
                        `${scriptData.data.parentData}[${quote}${fexpress}${quote}]` :
                        `${scriptData.data.parentData}.${fexpress}`;
                  }
                  else if(scriptData.data.name === "COLUMN" &&
                     (scriptData.data.parentName === "TABLE" ||
                     scriptData.data.parentName === "PHYSICAL_TABLE"))
                  {
                     const pname = scriptData.data.parentData;

                     if(!Tool.isIdentifier(pname)) {
                        fexpress = `viewsheet[${quote}${pname}${quote}][${quote}${fexpress}${quote}]`;
                     }
                     else {
                        fexpress = `${pname}[${quote}${fexpress}${quote}]`;
                     }
                  }
                  else if(scriptData.data.parentName == "cell") {
                     fexpress = "$" + fexpress;
                  }
                  else if(this.isCube) {
                     fexpress = "[Measures].[" + fexpress + "]";
                  }
                  else if(this.isHyperLink) {
                     fexpress = "field[" + quote + fexpress + quote + "]";
                  }
                  else if((scriptData.data.parentData || !this.isCalc) &&
                          scriptData.data.parentName == "field")
                  {
                     fexpress = "data[" + quote + fexpress + quote + "]";
                  }
                  else if(scriptData.data.name == "field") {
                     fexpress = "data[" + quote + fexpress + quote + "]";
                  }
                  else if(scriptData.data.name === "axis"
                     || scriptData.data.name === "valueFormats"
                     || scriptData.data.name === "colorLegends"
                     || scriptData.data.name === "shapeLegends"
                     || scriptData.data.name === "sizeLegends"
                  ) {
                     fexpress = `${scriptData.data.parentLabel}.${scriptData.data.name}[].${fexpress}`;
                  }
                  else if(!fexpress.startsWith("field[") && scriptData.data.parentName != DynamicDate.DYNAMIC_DATES) {
                     fexpress = "field[" + quote + fexpress + quote + "]";

                     if(isDateField) {
                        fexpress = datePart + "(" + fexpress + ")";
                     }
                  }
               }
               else {
                  const parameterIndex = fexpress.indexOf("parameter.");

                  if(parameterIndex === 0 && this.isSqlType()) {
                     const variableName = fexpress.substring("parameter.".length);
                     fexpress = `$(${variableName})`;
                  }
                  else if(parameterIndex === 0 && !this.isSqlType()) {
                     const variableName = fexpress.substring("parameter.".length);

                     if(!/^[a-zA-Z0-9]+$/.test(variableName)) {
                        fexpress = "parameter['" + variableName + "']";
                     }
                  }
                  else if(parameterIndex !== 0 && fexpress.indexOf("MV.") !== 0 &&
                     !fexpress.startsWith("field["))
                  {
                     fexpress = "field[" + quote + fexpress + quote + "]";

                     if(isDateField) {
                        fexpress = datePart + "(" + fexpress + ")";
                     }
                  }
               }
            }

            let len = fexpress.length;
            fexpress = ScriptPane.insertText(this.expression || "", fexpress, obj.selection);

            this.cursor = {
               line: obj.selection.from.line,
               ch: obj.selection.from.ch + len
            };
         }

         this.expression = fexpress;
      }
      else {
         this.expression = fexpress;
      }
   }

   get validExpression(): boolean {
      if(!this.form.valid || !!this.expression === false) {
         return false;
      }

      return (!this.showFunctionTree || Tool.isEmpty(this.analysisResults)) && !this.isDuplicateName();
   }

   get validFunctionRoot(): TreeNodeModel {
      return this.showFunctionTree ? this.functionTreeRoot : null;
   }

   getGrayedOutValues(): string[] {
      if(this.columns == null || this.grayedOutFields == null) {
         return [];
      }

      let grayedOutFlds = this.grayedOutFields;
      let values: string[] = [];

      if(grayedOutFlds == null) {
         return values;
      }

      let isModel = this.columns != null && this.columns.length > 1 &&
         this.columns[1].name.indexOf(":") > 0;

      for(let i = 0; i < grayedOutFlds.length; i++) {
         let modelColName: string = grayedOutFlds[i].name.replace(".", ":");
         values.push(isModel ? modelColName : grayedOutFlds[i].attribute);
      }

      return values;
   }

   showAggregateDialog(): void {
      let fields: string[] = [];
      let fieldsType: string[] = [];

      for(let column of this.columns) {
         fields.push(column.name);
         fieldsType.push(column.dataType);
      }

      this.aggrModel = <NewAggrDialogModel> {
         id: "1",
         aggregate: fields.length > 0 && XSchema.isNumericType(fieldsType[0]) ? "Sum" : "Count",
         fields: fields,
         fieldsType: fieldsType,
         field: (fields.length > 0 ? fields[0] : null),
         grayedOutValues: this.getGrayedOutValues()
      };

      this.modalService.open(this.newAggrDialog).result.then(
         (result: any) => {
            let attributeRef: AttributeRef = new AttributeRef();
            attributeRef.classType = "AttributeRef";
            attributeRef.attribute = result.field;
            attributeRef.name = result.field;

            let aggregateRef: AggregateRef = <AggregateRef> {
               name: result.field,
               classType: "AggregateRef",
               ref: attributeRef,
               formulaName: result.aggregate
            };

            if(result.with) {
               aggregateRef.ref2 = this.getDataRef(result.with);
            }

            aggregateRef.n = parseInt(result.numValue, 10);
            let columnNode: TreeNodeModel =
               this.columnTreeRoot.children[this.columnTreeRoot.children.length - 1];
            const properties = {
               parent: "fields",
               parentTarget: "fields",
               currentTarget: "field_" + columnNode.children.length,
               useragg: "true",
               data: this.getAggrExpression(aggregateRef)
            };
            let anode: TreeNodeModel = this.createTreeNode(
               this.getFullName(aggregateRef), properties, true, null);

            const isExist: boolean = columnNode.children.some(n => n.label == anode.label);

            if(!isExist) {
               let newAggrNode = columnNode.children[columnNode.children.length - 1];
               columnNode.children[columnNode.children.length - 1] = anode;
               columnNode.children.push(newAggrNode);
               // force virtual scroll to update, or the new node will not be visible. (49972)
               this._columnTreeRoot = Tool.clone(this.columnTreeRoot);

               this.aggregateModify.emit({nref: aggregateRef, oref: null});
            }

            this.checkValid();
         },
         (reject) => {});
   }

   private createTreeNode(label: string, data: any, leaf: boolean, children: any): TreeNodeModel {
      return <TreeNodeModel> {
         label: label,
         data: data,
         leaf: leaf,
         children: children
      };
   }


   private getDataRef(name: string): DataRef {
      for(let column of this.columns) {
         if(column.name === name) {
            return (<any> column).dataRefModel;
         }
      }

      return null;
   }

   private populateColumnTree(): void {
      let oldRoot = Tool.clone(this._columnTreeRoot);

      if(!this.isCube && this.vsId) {
         this.editorService.getColumnTreeNode(this.vsId, this.assemblyName,
            this.isCondition).subscribe((data: TreeNodeModel) => {
               this._columnTreeRoot = this.getColumnTree(data);
               this.keepNodeExpands(oldRoot, this._columnTreeRoot);
            });
      }
      else {
         if(this.columnTreeRoot != null && this.columnTreeRoot.children.length == 3 &&
            this.columnTreeRoot.children[2].data.data == "table")
         {
            this._columnTreeRoot.children.splice(2);
         }
         else {
            this._columnTreeRoot = Tool.clone(this.originalColumnTreeRoot);

            if(this.isSqlType() && this._columnTreeRoot != null) {
               this.removeDateParts(this._columnTreeRoot);
            }
         }

         if(this._columnTreeRoot != null && this._columnTreeRoot.children.length == 1 &&
            this.isCube && !this.selfVisible)
         {
            this._columnTreeRoot.children[0].children = this._columnTreeRoot.children[0].children
               .filter((node) => node.label != this.formulaName);
         }

         this.keepNodeExpands(oldRoot, this._columnTreeRoot);
      }
   }

   private keepNodeExpands(sourceNode: TreeNodeModel, targetNode: TreeNodeModel) {
      if(!sourceNode || !targetNode) {
         return;
      }

      let expandNodes: TreeNodeModel[] = [];
      this.getExpandNodes(sourceNode, expandNodes);

      expandNodes.forEach(expandNode => {
         let findNode =
            GuiTool.findNode(targetNode, n => Tool.isEquals(expandNode.data, n.data));

         if(findNode) {
            findNode.expanded = true;
         }
      });
   }

   private getExpandNodes(node: TreeNodeModel, expandNodes: TreeNodeModel[]) {
      if(!node?.expanded) {
         return;
      }

      expandNodes.push(node);

      if(node.children) {
         node.children.forEach(n => {
            this.getExpandNodes(n, expandNodes);
         });
      }
   }

   private removeDateParts(root: TreeNodeModel) {
      let nodes: TreeNodeModel[] = root.children;

      if(nodes == null) {
         return;
      }

      for(let i = 0; i < nodes.length; i++) {
         let node = nodes[i];

         if(node.leaf && node.icon == "column-icon" && node.children != null &&
            node.children.length > 0)
         {
            node.children = [];
            continue;
         }

         this.removeDateParts(node);
      }
   }

   private getTreeNodeChildren(nodes: any[]): TreeNodeModel[] {
      let nnodes: TreeNodeModel[] = [];

      for(let i = 0; i < nodes.length; i++) {
         let node: any = nodes[i];
         let children: TreeNodeModel[] = this.getTreeNodeChildren(node.nodes);
         let nnode: TreeNodeModel;

         if(children.length > 0) {
            nnode = <TreeNodeModel> {
               label: node.title,
               children: children,
               leaf: false
            };
         }
         else {
            nnode = <TreeNodeModel> {
               label: node.title,
               data: node.title,
               leaf: true
            };
         }

         nnodes.push(nnode);
      }

      return nnodes;
   }

   private getColumnTree(data: TreeNodeModel): TreeNodeModel {
      if(this.isCalcTable) {
         if(data != null && data.children.length > 2 && data.children[2].data.data == "table") {
            data.children.splice(2);
         }
      }

      if(this.availableCells && this.availableCells.length > 0) {
         let cellNode: TreeNodeModel = this.createCellNode();
         data.children.push(cellNode);
      }

      if(this.createCalcField || this.isHyperLink) {
         let columnNode: TreeNodeModel = this.createColumnNode();

         if(this.isSqlType()) {
            let children: TreeNodeModel[] = [];
            children.push(columnNode);
            data.children = children;
         }
         else {
            data.children.push(columnNode);
         }
      }
      else if(this.availableFields && this.availableFields.length > 0) {
         let columnNode: TreeNodeModel = this.createColumnNode();
         data.children.push(columnNode);
      }

      return data;
   }

   private createColumnNode(): TreeNodeModel {
      let column: TreeNodeModel = {
         label: "_#(js:Fields)",
         leaf: false,
         data: {
            name: "field",
            parentName: "data",
            parentLabel: "Data",
            isTable: "true"
         }
      };

      let children: TreeNodeModel[] = [];
      let aflds: DataRef[] = this.aggregateOnly ? this.aggregates : this.columns;

      for(let i = 0; i < aflds.length; i++) {
         let field: DataRef = aflds[i];

         if(!this.selfVisible && this.getFullName(field) == this.formulaName) {
            continue;
         }

         if(!this.isSqlType() && XSchema.isDateType(field.dataType)) {
            children.push(this.getDateNode(field, column, i));
            continue;
         }

         let child: TreeNodeModel = {
            label: this.getFullName(field),
            leaf: true,
            tooltip: this.getColumnNodeTooltip(field),
            data: {
               data: this.getFullName(field),
               name: "folder_0_field_" + i,
               parentName: column.data.name,
               parentLabel: column.label,
               parentData: column.data.data,
               isField: "true"
            }
         };

         let aggreagteChild: TreeNodeModel = {
            label: this.getFullName(field),
            leaf: true,
            tooltip: this.getColumnNodeTooltip(field),
            data: {
               data: this.getAggrExpression(field),
               name: "folder_0_field_" + i,
               parentName: column.data.name,
               parentLabel: column.label,
               parentData: column.data.data,
               useragg: "true"
            }
         };

         children.push(this.aggregateOnly ? aggreagteChild : child);
      }

      if(this.aggregateOnly) {
         let aggr: TreeNodeModel = {
            label: "_#(js:New Aggregate)",
            leaf: true,
            data: {
               data: this.NEW_AGGREGATE,
               name: "folder_0_field_" + children.length,
               expression: this.NEW_AGGREGATE,
               parentName: column.data.name,
               parentLabel: column.label,
               parentData: column.data.data
            }
         };

         children.push(aggr);
      }

      column.children = children;

      return column;
   }

   private getDateNode(field: DataRef, column: TreeNodeModel, index: number): TreeNodeModel {
      let children: TreeNodeModel[] = [];
      let dataType = field.dataType;
      let dateFolder: TreeNodeModel = {
         label: this.getFullName(field),
         leaf: true,
         tooltip: this.getColumnNodeTooltip(field),

         data: {
            data: this.getFullName(field),
            name: this.getFullName(field),
            parentName: column.data.name,
            parentLabel: column.label,
            parentData: column.data.data,
            isField: "true",
            useragg: this.aggregateOnly ? "true" : null
         }
      };

      this.addDateNodes(field, children, dateFolder);
      dateFolder.children = children;

      return dateFolder;
   }

   private addDateNodes(fld: DataRef, children: TreeNodeModel[], parent: TreeNodeModel) {
      let dataType = fld.dataType;

      if(dataType == XSchema.DATE) {
         this.addDateTimeNodes(fld, children, parent, DATE_PARTS);
      }
      if(dataType == XSchema.TIME) {
         this.addDateTimeNodes(fld, children, parent, TIME_PARTS);
      }
      else if(dataType == XSchema.TIME_INSTANT) {
         this.addDateTimeNodes(fld, children, parent, DATE_TIME_PARTS);
      }
   }

   private addDateTimeNodes(fld: DataRef, children: TreeNodeModel[], parent: TreeNodeModel, dateLevels) {
      for(let i: number = 0; i < dateLevels.length; i++) {
         let childName = this.getFullName(fld);
         let label = dateLevels[i].label + "(" + childName + ")";
         let data = dateLevels[i].data + "(" + childName + ")";

         let child: TreeNodeModel = {
            label: label,
            leaf: true,
            tooltip: childName,
            type: FormulaEditorDialog.DATE_PART_COLUMN,

            data: {
               data: data,
               name: childName,
               parentName: parent.data.name,
               parentLabel: parent.label,
               parentData: parent.data.data,
               isField: "true",
            }
         };

         children.push(child);
      }
   }

   private getColumnNodeTooltip(fld: any): string {
      return !fld ? null : fld.description;
   }

   private createCellNode(): TreeNodeModel {
      let column: TreeNodeModel = {
         label: "_#(js:Cell)",
         leaf: false,
         data: {
            name: "cell",
            parentName: "data",
            parentLabel: "Data",
            isTable: "true"
         }
      };

      let children: TreeNodeModel[] = [];

      for(let i = 0; i < this.availableCells.length; i++) {
         let cell: string = this.availableCells[i];

         let child: TreeNodeModel = {
            label: cell,
            leaf: true,
            data: {
               data: cell,
               name: "cell" + i,
               parentName: column.data.name,
               parentLabel: column.label,
               parentData: column.data.data,
               isField: "true"
            }
         };

         children.push(child);
      }

      if(this.aggregateOnly) {
         let aggr: TreeNodeModel = {
            label: "_#(js:New Aggregate)",
            leaf: true,
            data: {
               data: this.NEW_AGGREGATE,
               name: "folder_0_field_" + children.length,
               expression: this.NEW_AGGREGATE,
               parentName: column.data.name,
               parentLabel: column.label,
               parentData: column.data.data
            }
         };

         children.push(aggr);
      }

      column.children = children;

      return column;
   }

   private populateFunctionTree(): void {
      let functionTreeNodes: TreeNodeModel[] = [];

      if(this.isCube) {
         return;
      }

      if(this.isSqlType()) {
         for(let i = 0; i < FormulaEditorService.sqlFunctions.length; i++) {
            let fnode: TreeNodeModel = <TreeNodeModel> {
               label: FormulaEditorService.sqlFunctions[i].label,
               data: FormulaEditorService.sqlFunctions[i].data,
               leaf: true
            };

            functionTreeNodes.push(fnode);
         }

         this.functionTreeRoot = <TreeNodeModel> {
            label: "_#(js:Functions)",
            children: functionTreeNodes,
            leaf: false
         };
      }
      else {
         this.editorService.getFunctionTreeNode(this.vsId, this.task).subscribe((data: TreeNodeModel) => {
            this.functionTreeRoot = data;
         });
      }
   }

   private populateOperatorTree(): void {
      let operatorTreeNodes: TreeNodeModel[] = [];

      if(this.isSqlType() || this.isCube) {
         for(let i = 0; i < FormulaEditorService.sqlOperators.length; i++) {
            let onode: TreeNodeModel = <TreeNodeModel> {
               label: FormulaEditorService.sqlOperators[i].label,
               data: FormulaEditorService.sqlOperators[i].data,
               leaf: true
            };

            operatorTreeNodes.push(onode);
         }

         this.operatorTreeRoot = <TreeNodeModel> {
            label: "_#(js:Operators)",
            children: operatorTreeNodes,
            leaf: false,
            expanded: this.isCube
         };
      }
      else {
         this.editorService.getOperationTreeNode(this.vsId, this.task).subscribe((data: TreeNodeModel) => {
            this.operatorTreeRoot = data;
         });
      }
   }

   private populateScriptDefinitions(): void {
      if(this.task) {
         this.editorService.getTaskScriptDefinitions().subscribe(defs => this.scriptDefinitions = defs);
      }
      if(!this.isSqlType()) {
         if(this.vsId) {
            this.editorService.getScriptDefinitions(this.vsId, this.assemblyName, this.isCondition)
               .subscribe((defs) => {
                  this.scriptDefinitions = defs;
               });
         }
      }
   }

   isSqlType(): boolean {
      const formulaType = this.form.contains("formulaType") ?
         this.form.get("formulaType").value : this.formulaType;

      return formulaType === FormulaType.SQL || formulaType === ExpressionType.SQL;
   }

   private getFullName(ref: any): string {
      let name: string = ref.name;

      if(ref.ref) {
         name = ref.ref.name;
      }

      let formulaName: string = ref.formulaName;

      if(formulaName != null) {
         const formula: AggregateFormula = AggregateFormula.getFormula(formulaName);

         if(formula && formula.twoColumns && ref.ref2) {
            name = `${formulaName}(${name}, ${ref.ref2.name})`;
         }
         else if(formula && formula.hasN && ref.n > 0) {
            name = `${formulaName}([${name}], ${ref.n})`;
         }
         else {
            name = `${formulaName}(${name})`;
         }
      }

      return name;
   }

   private getAggrExpression(ref: any): string {
      let name: string = ref.ref ? ref.ref.name : ref.name;
      let formulaName: string = ref.formulaName;

      if(ref.formulaName != null) {
         const formula: AggregateFormula = AggregateFormula.getFormula(ref.formulaName);

         if(formula && formula.twoColumns && ref.ref2) {
            name = `${formulaName}([${name}], [${ref.ref2.name}])`;
         }
         else if(formula && formula.hasN && ref.n > 0) {
            name = `${formulaName}([${name}], ${ref.n})`;
         }
         else {
            name = `${formulaName}([${name}])`;
         }
      }

      return name;
   }

   private checkValid() {
      // Aggregate calc field don't support sql expression, don't disable but
      // pop up a warning
      if(this.aggregateOnly && this.form.get("formulaType").value === "SQL") {
         let message = "_#(js:common.calcfieldAggrSqlUnsupport)";
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Warning)", message,
                                         {"ok": "_#(js:OK)"}, {
            backdrop: false,
         }).then(() => false);
      }
      // pop up a warning if "SQL" is selected when invalid.
      else if(this.sqlMergeable === false && this.form.get("formulaType").value === "SQL") {
         let message = "_#(js:common.formulaDataUnmergeable)";
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Warning)", message,
                                         {"ok": "_#(js:OK)"}, {
            backdrop: false,
         }).then(() => false);
      }
   }

   hasMenu(): any {
      return (node) => {
         return "true" == node?.data?.useragg;
      };
   }

   showContextMenu(event: [MouseEvent, TreeNodeModel, TreeNodeModel[]]) {
      let options: DropdownOptions = {
         position: {x: event[0].clientX + 2, y: event[0].clientY + 2},
         contextmenu: true,
      };

      let contextmenu: ActionsContextmenuComponent = this.dropdownService
         .open(ActionsContextmenuComponent, options).componentInstance;
      contextmenu.sourceEvent = event[0];
      contextmenu.actions = this.createActions(event[1])
   }

   private createActions(node: TreeNodeModel): AssemblyActionGroup[] {
      return [
         new AssemblyActionGroup([
            {
               id: () => "delete user aggregate",
               label: () => "_#(js:Delete)",
               icon: () => null,
               enabled: () => true,
               visible: () => node?.data?.useragg,
               action: () => this.deleteAggregate(node)
            }
         ])
      ];
   }

   private deleteAggregate(node: TreeNodeModel) {
      if(!this.aggregates) {
         return;
      }

      let index = this.aggregates.findIndex((agg) => {
         return this.getFullName(agg) == node.label;
      });

      let currentAgg = this.aggregates[index];

      if(currentAgg) {
         if(!currentAgg.classType) {
            currentAgg.classType = "AggregateRef";
         }

         this.aggregateDelete.emit({nref: null, oref: currentAgg});
      }
   }
}
