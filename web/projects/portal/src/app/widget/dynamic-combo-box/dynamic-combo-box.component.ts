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
import { Component, ElementRef, EventEmitter, Input, OnChanges, OnInit, Output,
         ViewChild, ViewChildren, QueryList, SimpleChanges } from "@angular/core";
import { NgbModal, NgbModalOptions } from "@ng-bootstrap/ng-bootstrap";
import { Tool } from "../../../../../shared/util/tool";
import { FixedDropdownDirective } from "../fixed-dropdown/fixed-dropdown.directive";
import { ComboMode, ValueMode } from "./dynamic-combo-box-model";
import { FormulaEditorDialog } from "../formula-editor/formula-editor-dialog.component";
import { TreeNodeModel } from "../tree/tree-node-model";
import { FormulaType } from "../../common/data/formula-type";
import { ComponentTool } from "../../common/util/component-tool";
import {
   FeatureFlagsService,
   FeatureFlagValue
} from "../../../../../shared/feature-flags/feature-flags.service";
import { FormulaEditorDialogModel } from "../formula-editor/formula-editor-dialog-model";

@Component({
   selector: "dynamic-combo-box",
   templateUrl: "dynamic-combo-box.component.html",
   styleUrls: ["./dynamic-combo-box.component.scss"],
})
export class DynamicComboBox implements OnInit, OnChanges {
   public ComboMode = ComboMode;
   public ValueMode = ValueMode;
   @Input() type: ComboMode = ComboMode.VALUE;
   @Input() mode: ValueMode = ValueMode.TEXT;
   @Input() normalColumn: boolean = false;
   private _normalColumn: boolean = false;
   _value: any;
   @Input() origValue: string = null; // original value if switch to var/expr
   @Input() values: any[];
   @Input() examples: any[];
   @Input() tooltip: string;
   @Input() variables: any[];
   @Input() editable: boolean = false;
   @Input() valueOnly: boolean = false;
   @Input() vsId: string;
   @Input() assemblyName: string;
   @Input() promptString: string = "";
   @Input() grayedOutValues: string[] = [];
   @Input() disable: boolean = false;
   @Input() label: string;
   @Input() isCondition: boolean = false;
   @Input() enableFormulaLabel: boolean = false;
   @Input() asTree: boolean = false;
   @Input() showTooltip: boolean = false;
   @Input() dynamicDates: TreeNodeModel = null;
   @Input() columnTreeRoot: TreeNodeModel = null;
   @Input() functionTreeRoot: TreeNodeModel = null;
   @Input() operatorTreeRoot: TreeNodeModel = null;
   @Input() scriptDefinitions: any = null;
   @Input() selectOnClick: boolean = false;
   @Input() initSelectedNodesExpanded: boolean = false;
   @Input() invalid: boolean;
   @Input() supportVariable: boolean = true;
   @Input() task: boolean = false;
   @Input() expressionSubmitCallback: (_?: FormulaEditorDialogModel) => Promise<boolean> =
      () => Promise.resolve(true);
   @Output() typeChange: EventEmitter<ComboMode> = new EventEmitter<ComboMode>(true);
   @Output() valueChange: EventEmitter<any> = new EventEmitter<any>();
   @Output() onValueTyping: EventEmitter<any> = new EventEmitter<any>();

   valueTree: TreeNodeModel = null;
   @ViewChild("dropdownBody") dropdownBody: ElementRef;
   @ViewChild("textInput") textInput: ElementRef;
   @ViewChild("numberInput") numberInput: ElementRef;
   @ViewChildren(FixedDropdownDirective) dropdowns: QueryList<FixedDropdownDirective>;
   readonly FeatureFlagValue = FeatureFlagValue;

   constructor(private dialogService: NgbModal) {
   }

   ngOnInit() {
      this.updateType(this.value);
      this._normalColumn = this.normalColumn;

      if(this.type == ComboMode.VALUE) {
         this.origValue = this._value;
      }
      else {
         // type set by value should be reflected in caller which may rely on its accuracy
         this.typeChange.emit(this.type);

         if(this.isValuesDefinedAndNotEmpty()) {
            this.origValue = this.values[0];
         }
      }

      if(this.asTree) {
         this.valueTree = this.createValueTree();
      }
   }

   ngOnChanges(changes: SimpleChanges) {
      if(this.asTree) {
         this.valueTree = this.createValueTree();
      }

      if(changes["normalColumn"]) {
         this._normalColumn = this.normalColumn;
         this.updateType(this.value);
      }
   }

   @Input() set value(val: any) {
      this.updateType(val);
      this.updateValue(val);
   }

   get value(): any {
      return this._value;
   }

   private updateValue(val: any) {
      if(this.type == ComboMode.VALUE) {
         // use val as is
      }
      else if(this.type == ComboMode.VARIABLE) {
         val = val ? val :
            (this.variables && this.variables.length > 0 ? this.variables[0] : val);
      }
      else if(this.type == ComboMode.EXPRESSION) {
         val = val ? (val.charAt(0) != "=" ? ("=" + val) : val) : "=";
      }

      this._value = val;
   }

   get displayValue(): string {
      return this.value?.charAt(0) == "=" ? this.value.substring(1) : this.value;
   }

   onChanged(event: any): void {
      const val2 = this.values ? this.values.find(v => v.label == event.target.value) : null;

      if(val2 && val2.value) {
         this.valueChange.emit(val2.value);
      }
      else if(val2) {
         this.valueChange.emit(val2);
      }
      else {
         this.valueChange.emit(event.target.value);
      }
   }

   getInputClass(val: any): string {
      if(val != null && typeof val == "string") {
         val = Tool.replaceStr(val, ":", ".");
      }

      if(this.grayedOutValues.indexOf(val) >= 0) {
         return "grayed-out-field";
      }

      return "";
   }

   getDisplayClass(val: any): string {
      const str = (val.value != null) ? val.value : (val.label != null ? val.label : val);
      const cls = this.getInputClass(str);

      if(cls !== "") {
         return cls;
      }

      return (str == this._value || JSON.stringify(val) == JSON.stringify(this.value))
         ? "selected" : "";
   }

   private updateType(val: any) {
      let _type = ComboMode.VALUE;

      if(!val || !(typeof val === "string") || this._normalColumn) {
         this.type = _type;
         return;
      }
      else if(val.startsWith("$(") && val.endsWith(")")) {
         _type = ComboMode.VARIABLE;
      }
      else if(val.charAt(0) == "=") {
         _type = ComboMode.EXPRESSION;
      }

      if(this.type != _type) {
         this.type = _type;
      }
   }

   getCurrentValue(): string {
      if(this.editable) {
         return this.value;
      }
      else if(this.label) {
         return this.label;
      }
      else if(!this.values) {
         return this.value;
      }

      for(let choice of this.values) {
         if(choice && choice.value == this.value) {
            return choice.label ? choice.label : this.value;
         }
      }
      return this.value && this.value.label ? this.value.label : this.value;
   }

   // Update value and emit change when value chosen
   selectValue(choice: any): void {
      if(this.label) {
         this.label = choice && choice.label ? choice.label : choice;
      }

      this.updateValue(choice && (choice.value != null) ? choice.value : choice);
      this.valueChange.emit(this.value);
   }

   closeDropdowns() {
      this.dropdowns.forEach(d => d.close());
   }

   // Update type and emit change when type chosen
   selectType(event: MouseEvent, type: ComboMode): void {
      event.stopPropagation();
      this.closeDropdowns();

      if(this.type != type) {
         if(this.type == ComboMode.VALUE) {
            this.origValue = this._value;
         }

         if(type == ComboMode.VARIABLE && !this.isVariableEnabled()) {
            return;
         }

         this.type = type;
         this.typeChange.emit(type);

         if(type == ComboMode.VALUE) {
            this._normalColumn = true;
            this.selectValue(this.origValue);
         }
         else {
            this._normalColumn = false;
            this.selectValue(null);
         }

         if(type == ComboMode.EXPRESSION) {
            setTimeout(() => this.showFormulaEditor(), 0);
         }
      }
   }

   showFormulaEditor(): void {
      this.closeDropdowns();
      const options: NgbModalOptions = {windowClass: "formula-dialog", backdrop: "static"};
      let dialog: FormulaEditorDialog = ComponentTool.showDialog(
         this.dialogService, FormulaEditorDialog, (result: any) => {
            this.selectValue(result.expression);
         }, options);
      dialog.resizeable = true;
      dialog.vsId = this.vsId;
      dialog.assemblyName = this.assemblyName;
      dialog.expression = this.displayValue;
      dialog.nameVisible = false;
      dialog.returnTypeVisible = false;
      dialog.isCondition = this.isCondition;
      dialog.dynamicDates = this.dynamicDates;
      dialog.columnTreeRoot = this.columnTreeRoot;
      dialog.functionTreeRoot = this.functionTreeRoot;
      dialog.operatorTreeRoot = this.operatorTreeRoot;
      dialog.scriptDefinitions = this.scriptDefinitions;
      dialog.formulaType = FormulaType.SCRIPT;
      dialog.submitCallback = this.expressionSubmitCallback;
      dialog.task = this.task;
   }

   isValueEnabled(choice: string) {
      return choice != "(Target Formula)" || this.enableFormulaLabel ||
         this.label == "(Target Formula)";
   }

   isExampleEnable(index: number) {
      return this.examples != null && this.examples.length != 0
         && this.examples.length > index && !!this.examples[index];
   }

   createValueTree(): TreeNodeModel {
      const root: TreeNodeModel = {
         expanded: true,
         children: [],
      };

      if(this.values) {
         let branch: TreeNodeModel = null;

         this.values.forEach(v => {
            const str: string = (v.label != null) ? v.label
               : (v.value != null ? v.value : <string> v);
            const pair: Array<string> = str.split(":");

            if(pair.length == 1) {
               root.children.push({
                  label: pair[0],
                  data: pair[0],
                  tooltip: this.getTreeColTooltip(pair[0]),
                  type: v.type
               });
            }
            else {
               if(!branch || branch.label != pair[0]) {
                  branch = {
                     label: pair[0],
                     children: [],
                     type: v.type ? "table" : null
                  };

                  root.children.push(branch);
               }

               branch.children.push({
                  label: pair[1],
                  data: str,
                  tooltip: this.getTreeColTooltip(str),
                  type: v.type
               });
            }
         });
      }

      return root;
   }

   getTreeColTooltip(label: string) {
      for(let i: number = 0; i < this.values.length; i++) {
         if(this.values[i].label == label) {
            return this.values[i].tooltip;
         }
      }

      return "";
   }

   get selectedNodes(): TreeNodeModel[] {
      if(this._value != null) {
         const value = this._value.label ? this._value.label :
            this._value.value != null ? this._value.value : <string> this._value;
         const pair: Array<string> = value.split(":");

         if(pair.length == 1) {
            return [{
               label: pair[0],
               data: pair[0],
               tooltip: this.getTreeColTooltip(pair[0]),
            }];
         }
         else {
            return [{
               label: pair[1],
               data: value,
               tooltip: this.getTreeColTooltip(value),
            }];
         }
      }

      return [];
   }

   nodesSelected(nodes: TreeNodeModel[]) {
      if(nodes && nodes.length > 0 && !nodes[0].children) {
         this.selectValue(nodes[0].data);
         this.closeDropdowns();
      }
   }

   isVariableEnabled() {
      return !!this.variables && !!this.variables.length || this.type == ComboMode.VARIABLE;
   }

   isValuesDefinedAndNotEmpty(): boolean {
      return !!this.values && this.values.length > 0;
   }

   get dropdownMinWidth(): number {
      return this.dropdownBody && this.dropdownBody.nativeElement
         ? this.dropdownBody.nativeElement.offsetWidth : null;
   }
}
