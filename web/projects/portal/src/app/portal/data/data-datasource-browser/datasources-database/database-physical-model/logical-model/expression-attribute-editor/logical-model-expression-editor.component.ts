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
import { HttpClient } from "@angular/common/http";
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { AbstractControl, UntypedFormGroup } from "@angular/forms";
import { AttributeModel } from "../../../../../model/datasources/database/physical-model/logical-model/attribute-model";
import { Tool } from "../../../../../../../../../../shared/util/tool";
import { TreeNodeModel } from "../../../../../../../widget/tree/tree-node-model";
import { GetModelEvent } from "../../../../../model/datasources/database/events/get-model-event";
import { StringWrapper } from "../../../../../model/datasources/database/string-wrapper";
import { ScriptTreeNodeData } from "../../../../../../../widget/formula-editor/script-tree-node-data";
import { ScriptPane } from "../../../../../../../widget/dialog/script-pane/script-pane.component";
import { EntityModel } from "../../../../../model/datasources/database/physical-model/logical-model/entity-model";

const CHECK_EXPRESSION_URI: string = "../api/data/logicalModel/attribute/expression";
const FIELDS_URI: string = "../api/data/logicalModel/tables/nodes";

@Component({
   selector: "logical-model-expression-editor",
   templateUrl: "logical-model-expression-editor.component.html",
   styleUrls: ["logical-model-expression-editor.component.scss"]
})
export class LogicalModelExpressionEditor implements OnInit {
   @Input() databaseName: string;
   @Input() physicalModelName: string;
   @Input() additional: string;
   @Input() entities: EntityModel;
   @Input() existNames: string[] = [];
   @Input() logicalModelParent: string;
   _attribute: AttributeModel;
   form: UntypedFormGroup = new UntypedFormGroup({});
   invalidSQL: boolean = false;
   columnTreeRoot: TreeNodeModel;
   operatorTreeRoot: TreeNodeModel;
   functionTreeRoot: TreeNodeModel;
   cursor: {line: number, ch: number};
   editable: boolean = true;

   @Input() set attribute(value: AttributeModel) {
      this._attribute = value;

      if(value) {
         this.editable = !value.baseElement;
      }
   }

   /**
    * Get the current attribute being edited.
    * @returns {AttributeModel}
    */
   get attribute(): AttributeModel {
      return this._attribute;
   }

   constructor(private http: HttpClient) {
   }

   ngOnInit(): void {
      this.loadFields();

      this.functionTreeRoot = {
         label: "_#(js:Functions)",
         children: [
            {
               label: "_#(js:Aggregate)",
               expanded: false,
               leaf: false,
               children: [
                  {
                     label: "_#(js:Summarization)",
                     data: "SUM()",
                     leaf: true
                  },
                  {
                     label: "_#(js:Count)",
                     data: "COUNT()",
                     leaf: true
                  },
                  {
                     label: "_#(js:Average)",
                     data: "AVG()",
                     leaf: true
                  },
                  {
                     label: "_#(js:Minimum)",
                     data: "MIN()",
                     leaf: true
                  },
                  {
                     label: "_#(js:Maximum)",
                     data: "MAX()",
                     leaf: true
                  },
               ]
            }
         ]
      };

      this.operatorTreeRoot = {
         label: "_#(js:Operators)",
         children: [
            {
               label: "_#(js:Arithmetic)",
               expanded: false,
               leaf: false,
               children: [
                  {
                     label: "_#(js:data.logicalmodel.expression.addition)",
                     data: "+",
                     leaf: true
                  },
                  {
                     label: "_#(js:data.logicalmodel.expression.subtraction)",
                     data: "-",
                     leaf: true
                  },
                  {
                     label: "_#(js:data.logicalmodel.expression.multiplication)",
                     data: "*",
                     leaf: true
                  },
                  {
                     label: "_#(js:data.logicalmodel.expression.division)",
                     data: "/",
                     leaf: true
                  }
               ]
            },
            {
               label: "_#(js:Relational)",
               expanded: false,
               leaf: false,
               children: [
                  {
                     label: "_#(js:data.logicalmodel.expression.lessThan)",
                     data: "<",
                     leaf: true
                  },
                  {
                     label: "_#(js:data.logicalmodel.expression.lessThanEqualTo)",
                     data: "<=",
                     leaf: true
                  },
                  {
                     label: "_#(js:data.logicalmodel.expression.greaterThan)",
                     data: ">",
                     leaf: true
                  },
                  {
                     label: "_#(js:data.logicalmodel.expression.greaterThanEqualTo)",
                     data: ">=",
                     leaf: true
                  },
                  {
                     label: "_#(js:data.logicalmodel.expression.equalTo)",
                     data: "=",
                     leaf: true
                  },
                  {
                     label: "_#(js:data.logicalmodel.expression.notEqualTo)",
                     data: "<>",
                     leaf: true
                  }
               ]
            }
         ]
      };
   }

   /**
    * Get the name form control
    * @returns {AbstractControl|null} the form control
    */
   get nameControl(): AbstractControl {
      return this.form.get("name");
   }

   /**
    * Apply the form control value to model.
    * @param formControlName
    * @param fieldName
    */
   applyFromValue(formControlName: string, fieldName: string) {
      if(this.form && this.form.get(formControlName) && this.form.get(formControlName).valid &&
         this.attribute)
      {
         this.attribute[fieldName] = this.form.get(formControlName).value;
      }
   }

   /**
    * Apply changes.
    */
   apply(): void {
      this.applyFromValue("name", "name");
      this.applyFromValue("refType", "refType");

      this.http
         .post(CHECK_EXPRESSION_URI, new StringWrapper(this.attribute.expression), { responseType: "text" })
         .subscribe(
            data => {
               if(data) {
                  this.invalidSQL = true;
               }
               else {
                  this.invalidSQL = false;
               }
            },
            err => {
               this.invalidSQL = true;
            }
         );
   }

   updateExpression(obj: any) {
      let fexpress: string = "";
      let target: string = obj.target;
      let node: TreeNodeModel = <TreeNodeModel> obj.node;

      if(!node || !node.leaf || !node.data) {
         this.attribute.expression = obj.expression;
         return;
      }

      if(target == "columnTree") {
         fexpress += "field['" + node.data.qualifiedName + "']";
      }
      else if(!!target) {
         fexpress += node.data;
      }

      if(!!target) {
         let len = fexpress.length;
         fexpress = ScriptPane.insertText(obj.expression ? obj.expression : "",
            fexpress, obj.selection);
         this.cursor = {
            line: obj.selection.from.line,
            ch: obj.selection.from.ch + len
         };
      }

      this.attribute.expression = fexpress;
   }

   /**
    * Load the fields from the server.
    */
   private loadFields(): void {
      let event = new GetModelEvent(this.databaseName, this.physicalModelName,
         null, null, this.additional);

      this.http.post<TreeNodeModel>(FIELDS_URI, event)
         .subscribe(
            data => {
               this.columnTreeRoot = data;
               this.columnTreeRoot.expanded = false;
               this.columnTreeRoot.label = "_#(js:Fields)";
            },
            err => {}
         );
   }
}
