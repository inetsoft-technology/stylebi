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
import { HttpParams } from "@angular/common/http";
import {
   Component,
   EventEmitter,
   Input,
   OnInit,
   Output,
   TemplateRef,
   ViewChild
} from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { XSchema } from "../../../common/data/xschema";
import { Tool } from "../../../../../../shared/util/tool";
import { VariableListDialogModel } from "../../../widget/dialog/variable-list-dialog/variable-list-dialog-model";
import { VariableValueEditor } from "../../../widget/dialog/variable-list-dialog/variable-value-editor/variable-value-editor.component";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { ModelService } from "../../../widget/services/model.service";
import { AbstractTableAssembly } from "../../data/ws/abstract-table-assembly";
import { VariableAssemblyDialogModel } from "../../data/ws/variable-assembly-dialog-model";
import { VariableTableListDialogModel } from "../../data/ws/variable-table-list-dialog-model";
import { Worksheet } from "../../data/ws/worksheet";
import { ComponentTool } from "../../../common/util/component-tool";
import { ConditionValueType } from "../../../common/data/condition/condition-value-type";
import { ExpressionType } from "../../../common/data/condition/expression-type";
import { ExpressionValue } from "../../../common/data/condition/expression-value";
import { Observable, of } from "rxjs";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";

enum UserVariable {
   NONE = 0,
      /**
       * Display as a combobox.
       */
   COMBOBOX = 1,
      /**
       * Display as a list.
       */
   LIST = 2,
      /**
       * Display as radio buttons.
       */
   RADIO_BUTTONS = 3,
      /**
       * Display as checkboxes.
       */
   CHECKBOXES = 4
}

@Component({
   selector: "variable-assembly-dialog",
   templateUrl: "variable-assembly-dialog.component.html",
   styleUrls: ["variable-assembly-dialog.component.scss"],
})
export class VariableAssemblyDialog implements OnInit {
   @Input() worksheet: Worksheet;
   @Input() tables: AbstractTableAssembly[];
   @Input() variableName: string;
   @Output() onCommit: EventEmitter<any> = new EventEmitter<any>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @ViewChild("variableListDialog") variableListDialog: TemplateRef<any>;
   @ViewChild("variableTableListDialog") variableTableListDialog: TemplateRef<any>;
   @ViewChild(VariableValueEditor) defaultValueEditor: VariableValueEditor;
   model: VariableAssemblyDialogModel;
   private readonly RESTController: string = "../api/composer/ws/variable-assembly-dialog-model/";
   private readonly socketController: string = "/events/ws/dialog/variable-assembly-dialog-model";
   readonly timeInstantFormat = "YYYY-MM-DD HH:mm:ss";
   public XSchema = XSchema;
   public UserVariable = UserVariable;
   public ConditionValueType = ConditionValueType;
   dataTypeList = XSchema.standardDataTypeList;
   form: UntypedFormGroup;
   outerMirror: boolean;
   formValid = () => this.model && this.form && this.valid();
   defaultValueTypes: ConditionValueType[] = [
      ConditionValueType.VALUE,
      ConditionValueType.EXPRESSION
   ];
   expressionTypes: ExpressionType[] = [ExpressionType.JS];
   expressionColumnFunc = (value: ExpressionValue) => {
      return this.getVariableTree(value);
   };
   defaultValueType: ConditionValueType = ConditionValueType.VALUE;

   constructor(private modelService: ModelService,
               private modalService: NgbModal)
   {
   }

   ngOnInit(): void {
      if(!this.model) {
         let params = new HttpParams();

         if(this.variableName) {
            params = params.set("variable", this.variableName);
         }

         this.modelService.getModel(this.RESTController + Tool.byteEncode(this.worksheet.runtimeId), params)
            .subscribe(
               (data) => {
                  this.model = <VariableAssemblyDialogModel>data;
                  this.init();
               },
               () => {
                  console.warn("Could not fetch variable assembly model.");
               }
            );
      }
      else {
         this.init();
      }
   }

   private init(): void {
      this.initForm();
      this.checkOuterMirror();
      this.initDefaultValueType();
   }

   private initDefaultValueType(): void {
      if(this.model?.defaultValue?.jsonType === "expression") {
         this.defaultValueType = ConditionValueType.EXPRESSION;
      }
      else {
         this.defaultValueType = ConditionValueType.VALUE;
      }
   }

   initForm(): void {
      let displayStyle = this.model.displayStyle ? this.model.displayStyle : UserVariable.COMBOBOX;

      // we need to eliminate the two char types
      if(this.model.type == XSchema.CHAR) {
         this.model.type = XSchema.CHARACTER;
      }

      this.form = new UntypedFormGroup({
         newName: new UntypedFormControl(this.model.oldName, [
            Validators.required,
            FormValidators.variableSpecialCharacters,
            FormValidators.doesNotStartWithNumber,
            FormValidators.exists(this.worksheet.assemblyNames(this.model.oldName),
               {
                  trimSurroundingWhitespace: true,
                  ignoreCase: true,
                  originalValue: this.model.oldName
               })
         ]),
         label: new UntypedFormControl(this.model.label),
         type: new UntypedFormControl(this.model.type, [
            Validators.required,
         ]),
         defaultValue: new UntypedFormControl(this.getDefaultStrValue(), [
            Validators.required
         ]),
         selectionList: new UntypedFormControl(this.model.selectionList, [
            Validators.required,
         ]),
         none: new UntypedFormControl(this.model.none),
         displayStyle: new UntypedFormControl(displayStyle, [
           FormValidators.positiveNonZeroIntegerInRange
         ]),
      });

      Tool.setFormControlDisabled(this.form.get("defaultValue"), this.form.get("none").value);
      Tool.setFormControlDisabled(this.form.get("displayStyle"), this.form.get("selectionList").value === "none");

      this.form.get("none").valueChanges.subscribe((val) => {
         Tool.setFormControlDisabled(this.form.get("defaultValue"), val);

         if(this.form.value.type == "boolean" && !val) {
            this.form.get("defaultValue").patchValue(false);
         }
         else if(val) {
            this.form.get("defaultValue").patchValue(null);
         }

         if(this.isExpressionDefaultValue()) {
            this.model.defaultValue.expression = null;
         }
      });

      this.form.get("type").valueChanges.subscribe((val) => {
         this.model.variableListDialogModel.dataType = val;
         this.model.variableListDialogModel.labels = [];
         this.model.variableListDialogModel.values = [];

         if(this.isExpressionDefaultValue()) {
            this.form.get("defaultValue").patchValue(this.getDefaultStrValue());
         }
         else if(val == "boolean" && !this.form.get("none").value) {
            this.form.get("defaultValue").patchValue(false);
         }
         else {
            this.form.get("defaultValue").patchValue(null);
         }
      });

      this.form.get("selectionList").valueChanges
         .subscribe((val) => Tool.setFormControlDisabled(this.form.get("displayStyle"), val === "none"));
   }

   private checkOuterMirror(): void {
      if(this.model.oldName) {
         const variable = this.worksheet.variables.find((v) => v.name === this.model.oldName);

         if(variable != undefined && variable.info.mirrorInfo &&
            variable.info.mirrorInfo.outerMirror)
         {
            this.outerMirror = true;
            const message = "_#(js:common.outerMirror)";

            // Schedule microtask to avoid creating a new view in a lifecycle hook.
            Promise.resolve(null).then(() => {
               ComponentTool.showMessageDialog(this.modalService, "Information",
                  message, {"ok": "OK"}, {backdrop: false })
                  .then(() => {}, () => {});
            });
         }
      }
   }

   private getDefaultStrValue(): any {
      return this.isExpressionDefaultValue() ? this.model.defaultValue.expression :
         this.model.defaultValue;
   }

   showVariableListDialog(): void {
      this.modalService.open(this.variableListDialog, {backdrop: false})
         .result.then(
         (list: VariableListDialogModel) => {
            this.form.get("selectionList").patchValue("embedded");
            this.model.variableListDialogModel = list;
            this.validateVariableList();
         },
         (reject) => {}
      );
   }

   showVariableTableListDialog(): void {
      this.modalService.open(this.variableTableListDialog, {backdrop: false}).result.then(
         (result: VariableTableListDialogModel) => {
            this.form.get("selectionList").patchValue("query");
            this.model.variableTableListDialogModel = result;
         },
         (reject) => {}
      );
   }

   public valid(): boolean {
      if(!this.form || !this.model) {
         return false;
      }

      if(this.outerMirror) {
         return false;
      }

      if(!this.embeddedValid()) {
         return false;
      }

      if(!this.queryValid()) {
         return false;
      }

      if(!this.form.get("none").value && this.defaultValueEditor &&
         !this.defaultValueEditor.isValid())
      {
         return false;
      }

      return this.form.valid;
   }

   private embeddedValid(): boolean {
      if(!this.form || !this.model) {
         return false;
      }

      return this.form.get("selectionList").value !== "embedded" || (
         !!this.model.variableListDialogModel.labels.find((label) => label != null) ||
         !!this.model.variableListDialogModel.values.find((value) => value != null));
   }

   private queryValid(): boolean {
      if(!this.form || !this.model) {
         return false;
      }

      return this.form.get("selectionList").value !== "query" || (
         !!this.model.variableTableListDialogModel &&
         !!this.model.variableTableListDialogModel.value &&
         !!this.model.variableTableListDialogModel.label &&
         !!this.model.variableTableListDialogModel.tableName);
   }

   selectDefaultValueType(type:  ConditionValueType): void {
      this.defaultValueType = type;

      if(this.isExpressionDefaultValue()) {
         this.model.defaultValue = <ExpressionValue> {
            type: ExpressionType.JS,
            expression: null,
            jsonType: "expression"
         };
      }
      else {
         this.model.defaultValue = null;
      }

      this.form.get("defaultValue").patchValue(this.getDefaultStrValue());
   }

   private isExpressionDefaultValue(): boolean {
      return this.defaultValueType == ConditionValueType.EXPRESSION;
   }

   cancelChanges(): void {
      this.onCancel.emit("cancel");
   }

   okDisabled() {
      return !this.model || !this.form || !this.valid();
   }

   saveChanges(): void {
      let currentValue = this.model.defaultValue;
      Object.assign(this.model, this.form.value);

      if(this.isExpressionDefaultValue()) {
         this.model.defaultValue = currentValue;
      }

      this.validateVariableList();
      this.model.newName = this.model.newName.trim();
      this.onCommit.emit({model: this.model, controller: this.socketController});
   }

   private validateVariableList(): void {
      const list = this.model.variableListDialogModel;
      const labels: string[] = [];
      const values: string[] = [];

      for(let i = 0; i < list.labels.length; i++) {
         let label = list.labels[i];
         let value = list.values[i];

         if(value && value.trim() === "") {
            value = value.trim();
         }

         if(label != null || value != null) {
            labels.push(label);
            values.push(value);
         }
      }

      this.model.variableListDialogModel.labels = labels;
      this.model.variableListDialogModel.values = values;
   }

   defaultExpressionValueChange(value: ExpressionValue): void {
      if(!this.model.defaultValue) {
         this.model.defaultValue = <ExpressionValue> {
            type: value.type,
            expression: value.expression,
            jsonType: "expression"
         };
      }
      else {
         this.model.defaultValue.expression = value.expression;
      }

      this.form.get("defaultValue").patchValue(this.getDefaultStrValue());
   }

   getVariableTree(value: ExpressionValue): Observable<TreeNodeModel> {
      let root: TreeNodeModel = {children: []};

      let variableTreeModel = this.getVariableTreeModel();

      if(!!variableTreeModel) {
         root.children.push(variableTreeModel);
      }

      if(root.children.length >= 1) {
         root.children[0].expanded = true;
      }

      return of(root);
   }

   getVariableTreeModel(): TreeNodeModel {
      const variableTreeNodes: TreeNodeModel[] = [];

      for(let variable of this.model.otherVariables) {
         variableTreeNodes.push(<TreeNodeModel> {
            label: variable,
            data: "parameter." + variable,
            icon: "variable-icon",
            leaf: true,
         });
      }

      return <TreeNodeModel> {
         label: "_#(js:Variables)",
         children: variableTreeNodes,
         leaf: false
      };
   }
}
