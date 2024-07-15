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
   EventEmitter,
   Input,
   OnDestroy,
   OnInit,
   Output
} from "@angular/core";
import { UntypedFormGroup, AbstractControl, UntypedFormControl, Validators } from "@angular/forms";
import { AttributeModel } from "../../../../../model/datasources/database/physical-model/logical-model/attribute-model";
import { Tool } from "../../../../../../../../../../shared/util/tool";
import { FormulaEditorService } from "../../../../../../../widget/formula-editor/formula-editor.service";
import { NgbDropdown, NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { HttpClient } from "@angular/common/http";
import { TreeNodeModel } from "../../../../../../../widget/tree/tree-node-model";
import { XSchema } from "../../../../../../../common/data/xschema";
import { AutoDrillPathModel } from "../../../../../model/datasources/database/physical-model/logical-model/auto-drill-path-model";
import { AutoDrillInfoModel } from "../../../../../model/datasources/database/physical-model/logical-model/auto-drill-info-model";
import { AutoDrillDialog } from "./auto-drill-dialog/data-auto-drill-dialog.component";
import { ComponentTool } from "../../../../../../../common/util/component-tool";
import { GetModelEvent } from "../../../../../model/datasources/database/events/get-model-event";
import { FormValidators } from "../../../../../../../../../../shared/util/form-validators";
import { Subscription } from "rxjs";
import { EntityModel } from "../../../../../model/datasources/database/physical-model/logical-model/entity-model";

const COLUMNS_URI: string = "../api/data/logicalModel/tables/nodes";
const FORMAT_STRING_URI: string = "../api/data/logicalModel/attribute/format";

@Component({
   selector: "logical-model-attribute-editor",
   templateUrl: "logical-model-attribute-editor.component.html",
   styleUrls: ["logical-model-attribute-editor.component.scss",
      "../../../../../../../widget/tree/tree-dropdown.component.scss"]
})
export class LogicalModelAttributeEditor implements OnInit, OnDestroy {
   @Input() databaseName: string;
   @Input() physicalModelName: string;
   @Input() additional: string;
   @Input() logicalModelName: string;
   @Input() logicalModelParent: string;
   @Input() form: UntypedFormGroup;
   @Input() isExpression: boolean = false;
   @Input() editable: boolean = true;
   @Input() entities: EntityModel[];
   @Output() onAttributeChanged: EventEmitter<any> = new EventEmitter<any>();

   _attribute: AttributeModel;
   returnTypes: {label: string, data: string}[] = FormulaEditorService.returnTypes;
   otherDataTypes: {label: string, data: string}[] = [
      { label: "_#(js:Enum)",  data: "enum" },
      { label: "_#(js:UserDefined)", data: "userDefined" }
   ];

   dataTypes = this.returnTypes.concat(this.otherDataTypes);

   defaultRefTypes: {label: string, data: any}[] = [
      { label: "_#(js:None)", data: { formula: "None", type: 0}},
      { label: "_#(js:Dimension)", data: { formula: "Dimension", type: 1}}
   ];

   dateFormulas: {label: string, data: any}[] = [
      { label: "_#(js:None)", data: { formula: "None", type: 2}},
      { label: "_#(js:Max)", data: { formula: "Max", type: 2}},
      { label: "_#(js:Min)", data: { formula: "Min", type: 2}},
      { label: "_#(js:Count)", data: { formula: "Count", type: 2}},
      { label: "_#(js:DistinctCount)", data: { formula: "DistinctCount", type: 2}}
   ];

   stringFormulas: {label: string, data: any}[] = [
      { label: "_#(js:None)", data: { formula: "None", type: 2}},
      { label: "_#(js:Max)", data: { formula: "Max", type: 2}},
      { label: "_#(js:Min)", data: { formula: "Min", type: 2}},
      { label: "_#(js:Count)", data: { formula: "Count", type: 2}},
      { label: "_#(js:DistinctCount)", data: { formula: "DistinctCount", type: 2}},
      { label: "_#(js:Product)", data: { formula: "Product", type: 2}},
      { label: "_#(js:Concat)", data: { formula: "Concat", type: 2}},
      { label: "_#(js:Median)", data: { formula: "Median", type: 2}},
      { label: "_#(js:Mode)", data: { formula: "Mode", type: 2}}
   ];

   boolFormulas: {label: string, data: any}[] = [
      { label: "_#(js:None)", data: { formula: "None", type: 2}},
      { label: "_#(js:Count)", data: { formula: "Count", type: 2}},
      { label: "_#(js:DistinctCount)", data: { formula: "DistinctCount", type: 2}}
   ];

   numberFormulas: {label: string, data: any}[] = [
      { label: "_#(js:None)", data: { formula: "None", type: 2}},
      { label: "_#(js:Sum)", data: { formula: "Sum", type: 2}},
      { label: "_#(js:Average)", data: { formula: "Average", type: 2}},
      { label: "_#(js:Max)", data: { formula: "Max", type: 2}},
      { label: "_#(js:Min)", data: { formula: "Min", type: 2}},
      { label: "_#(js:Count)", data: { formula: "Count", type: 2}},
      { label: "_#(js:DistinctCount)", data: { formula: "DistinctCount", type: 2}},
      { label: "_#(js:Product)", data: { formula: "Product", type: 2}},
      { label: "_#(js:Concat)", data: { formula: "Concat", type: 2}},
      { label: "_#(js:StandardDeviation)", data: { formula: "StandardDeviation", type: 2}},
      { label: "_#(js:Variance)", data: { formula: "Variance", type: 2}},
      { label: "_#(js:PopulationStandardDeviation)", data: { formula: "PopulationStandardDeviation", type: 2}},
      { label: "_#(js:PopulationVariance)", data: { formula: "PopulationVariance", type: 2}},
      { label: "_#(js:Median)", data: { formula: "Median", type: 2}},
      { label: "_#(js:Mode)", data: { formula: "Mode", type: 2}}
   ];

   columnsTree: TreeNodeModel;
   selectedNode: TreeNodeModel;
   currentFormulas: {label: string, data: any}[] = [];
   selectedFormula: any;
   selectedFormulaLabel;
   formatString: string = "";
   _existNames: string[];
   private inited: boolean = false;
   private subscription: Subscription;

   @Input() set existNames(existNames: string[]) {
      this._existNames = existNames;

      if(this.inited) {
         this.resetFormControl();
      }
   }

   get existNames() {
      return this._existNames;
   }

   @Input() set attribute(value: AttributeModel) {
      const columnChanged: boolean = !this.attribute || value.qualifiedName != this.attribute.qualifiedName;
      const tableChanged: boolean = !this.attribute || value.table != this.attribute.table;
      const formatChanged: boolean = !this.attribute || !Tool.isEquals(value.format, this.attribute.format);
      this._attribute = value;
      this.editable = !!value && !value.baseElement;
      this.resetFormControl();

      if(tableChanged || columnChanged) {
         this.selectNode();
      }

      if(formatChanged) {
         this.getFormatString();
      }

      this.updateFormulas();
      this.selectFormula(this.findSelectFormula(value.refType));
   }

   get attribute(): AttributeModel {
      return this._attribute;
   }

   constructor(private http: HttpClient,
               private modalService: NgbModal) {}

   ngOnInit(): void {
      this.getColumns();
      this.resetFormControl();
      this.inited = true;
   }

   ngOnDestroy(): void {
      this.unsubscribeForm();
   }

   /**
    * Get the name form control
    * @returns {AbstractControl|null} the form control
    */
   get nameControl(): AbstractControl {
      return this.form.get("name");
   }

   /**
    * Get the data type form control
    * @returns {AbstractControl|null} the form control
    */
   get dataTypeControl(): AbstractControl {
      return this.form.get("dataType");
   }

   /**
    * Get the ref type form control
    * @returns {AbstractControl|null} the form control
    */
   get refTypeControl(): AbstractControl {
      return this.form.get("refType");
   }

   /**
    * Initialize the form controls.
    */
   private resetFormControl() {
      if(!this.form) {
         return;
      }

      this.unsubscribeForm();
      this.form.removeControl("name");
      this.form.removeControl("dataType");
      this.form.removeControl("refType");
      this.form.addControl("name",
         new UntypedFormControl(this.attribute.name, [
            Validators.required, FormValidators.exists(this.existNames)
         ]));

      this.form.addControl("dataType",
         new UntypedFormControl(this.attribute.dataType, [
            Validators.required
         ]));

      this.form.addControl("refType",
         new UntypedFormControl(this.attribute.refType, [
            Validators.required
         ]));

      if(!this.editable) {
         this.form.disable();
      }
      else {
         this.form.enable();
      }

      this.subscription = this.form.valueChanges
         .subscribe(() => this.onAttributeChanged.emit());
   }

   /**
    * Get the columns to choose from.
    */
   private getColumns(): void {
      let getModelEvent: GetModelEvent = new GetModelEvent(this.databaseName,
         this.physicalModelName, this.logicalModelName, this.logicalModelParent, this.additional);

      this.http
         .post<TreeNodeModel>(COLUMNS_URI, getModelEvent)
         .subscribe(
            data => {
               this.columnsTree = data;
               this.selectNode();
            },
            err => {
               this.columnsTree = null;
            }
         );
   }

   /**
    * Select a column node on init.
    */
   private selectNode(): void {
      if(this.columnsTree && this.columnsTree.children) {
         const table: string = this.attribute.table;
         const column: string = this.attribute.qualifiedName;
         const tableNode: TreeNodeModel =
            this.columnsTree.children.find((child: TreeNodeModel) => child.data == table);

         if(tableNode) {
            const columnNode: TreeNodeModel =
               tableNode.children
                  .find((child: TreeNodeModel) => child.data.qualifiedName == column);
            this.selectedNode = columnNode;
         }
         else {
            this.selectedNode = null;
         }
      }
   }

   /**
    * Select column node by click.
    * @param targetNode
    */
   selectPhysicalColumn(targetNode: TreeNodeModel): void {
      this.attribute.qualifiedName = targetNode.data.qualifiedName;
      this.attribute.table = targetNode.data.table;
      this.attribute.column = targetNode.data.name;
      this.selectedNode = targetNode;

      if(this.dataTypes) {
         let findType = this.dataTypes.find((type) => type && targetNode && targetNode.data &&
            type.data === targetNode.data.dataType);

         if(findType) {
            this.updateDataType(findType.data);
         }
      }
   }

   /**
    * Update the current list of formulas being used.
    */
   private updateFormulas(): void {
      const type: string = this.attribute.dataType;

      if(XSchema.DATE == type || XSchema.TIME == type || XSchema.TIME_INSTANT == type)
      {
         this.currentFormulas = [...this.dateFormulas];
      }
      else if(XSchema.STRING == type || XSchema.CHAR == type) {
         this.currentFormulas = [...this.stringFormulas];
      }
      else if(XSchema.BOOLEAN == type) {
         this.currentFormulas = [...this.boolFormulas];
      }
      else {
         this.currentFormulas = [...this.numberFormulas];
      }
   }

   /**
    * The drill description shown in the input.
    * @returns {any}
    */
   get drillString(): string {
      if(this.attribute.drillInfo) {
         if(!this.attribute.drillInfo.paths || this.attribute.drillInfo.paths.length == 0) {
            return "None";
         }

         const paths: string[] = this.attribute.drillInfo.paths
            .map((path: AutoDrillPathModel) => path.name);

         return paths.join(", ");
      }

      return "None";
   }

   selectFormula(ref: {label: string, data: any}, dropdown?: NgbDropdown) {
      this.selectedFormula = ref.data;
      let measure = !this.defaultRefTypes.find((refType) => Tool.isEquals(refType.data, ref.data));
      this.selectedFormulaLabel = measure ? "_#(js:Measure):" + ref.label : ref.label;
      this.attribute.refType = ref.data;

      if(!!dropdown) {
         dropdown.close();
      }
   }

   isSelected(ref: {label: string, data: any}) {
      return this.selectedFormula == ref.data;
   }

   findSelectFormula(data: any) {
      let formula = this.currentFormulas.find((ref) => Tool.isEquals(data, ref.data));
      formula = !!formula ?
         formula : this.defaultRefTypes.find((ref) => Tool.isEquals(data, ref.data));

      return !!formula ? formula : this.defaultRefTypes[0];
   }

   /**
    * Get sample format string from the server.
    */
   getFormatString(): void {
      this.http
         .post(FORMAT_STRING_URI, this.attribute.format, { responseType: "json" })
         .subscribe(
            (data: string) => {
               this.formatString = data;
            },
            err => {}
         );
   }

   /**
    * Open auto drill dialog.
    */
   openAutoDrillDialog(): void {
      let dialog = ComponentTool.showDialog(this.modalService, AutoDrillDialog,
         (data: AutoDrillInfoModel) => {
            this.attribute.drillInfo = data;
         }, {size: "lg", windowClass: "data-auto-drill-dialog"});

      dialog.autoDrillModel = this.attribute.drillInfo;
      dialog.entities = this.entities;
   }

   /**
    * update the dataType of attribute, and update formulas.
    * @param {string} dataType: The latest dataType
    */
   updateDataType(dataType: string): void {
      this.attribute.dataType = dataType;
      this.updateFormulas();
   }

   /**
    * get icon for column tree.
    * @param node
    */
   iconFunction(node: TreeNodeModel): string {
      if(!node || !node.data || !node.data || !node.data.dataType) {
         return "data-table-icon";
      }

      let dataType = node.data.dataType;

      if(dataType === XSchema.DATE) {
         return "date-field-icon";
      }
      else if(dataType === XSchema.TIME_INSTANT ||
         dataType === XSchema.TIME)
      {
         return "datetime-field-icon";
      }
      else if(dataType === XSchema.STRING ||
         dataType === XSchema.CHARACTER ||
         dataType === XSchema.CHAR ||
         dataType === XSchema.BYTE)
      {
         return " text-field-icon";
      }
      else if(dataType === XSchema.DOUBLE ||
         dataType === XSchema.INTEGER ||
         dataType === XSchema.FLOAT ||
         dataType === XSchema.SHORT ||
         dataType === XSchema.LONG)
      {
         return " number-field-icon";
      }
      else if(dataType === XSchema.BOOLEAN) {
         return " boolean-field-icon";
      }
      else {
         return " column-icon";
      }
   }

   /**
    * unsubscribe form change.
    */
   private unsubscribeForm() {
      if(this.subscription) {
         this.subscription.unsubscribe();
         this.subscription = null;
      }
   }
}
