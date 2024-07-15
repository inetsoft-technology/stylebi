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
import { Component, EventEmitter, Input, OnInit, Output, ViewChild } from "@angular/core";
import { AbstractControl, UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { HttpClient } from "@angular/common/http";
import { EntityModel } from "../../../../../model/datasources/database/physical-model/logical-model/entity-model";
import { AttributeModel } from "../../../../../model/datasources/database/physical-model/logical-model/attribute-model";
import { NotificationsComponent } from "../../../../../../../widget/notifications/notifications.component";
import { StringWrapper } from "../../../../../model/datasources/database/string-wrapper";
import { TreeNodeModel } from "../../../../../../../widget/tree/tree-node-model";
import { GetModelEvent } from "../../../../../model/datasources/database/events/get-model-event";
import { ScriptPane } from "../../../../../../../widget/dialog/script-pane/script-pane.component";

const CHECK_EXPRESSION_URI: string = "../api/data/logicalModel/attribute/expression";
const FIELDS_URI: string = "../api/data/logicalModel/tables/nodes";

@Component({
   selector: "logical-model-expression-dialog",
   templateUrl: "logical-model-expression-dialog.component.html",
   styleUrls: ["logical-model-expression-dialog.component.scss"]
})
export class LogicalModelExpressionDialog implements OnInit {
   @Input() entities: EntityModel[];
   @Input() parent: number = 0;
   @Input() databaseName: string;
   @Input() physicalModelName: string;
   @Input() additional: string;
   @Output() onCommit: EventEmitter<any> = new EventEmitter<any>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @ViewChild("notifications") notifications: NotificationsComponent;
   name: string = "";
   expression: string = "";
   form: UntypedFormGroup;
   columnTreeRoot: TreeNodeModel;
   operatorTreeRoot: TreeNodeModel;
   functionTreeRoot: TreeNodeModel;
   cursor: {line: number, ch: number};

   constructor(private http: HttpClient) {
   }

   ngOnInit() {
      this.initFormControl();
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
    * Get the parent form control
    * @returns {AbstractControl|null} the form control
    */
   get parentControl(): AbstractControl {
      return this.form.get("parent");
   }

   /**
    * Initialize the form group.
    */
   private initFormControl() {
      this.form = new UntypedFormGroup({
         name: new UntypedFormControl(this.name, [
            Validators.required
         ]),
         parent: new UntypedFormControl(this.parent, [
            Validators.required
         ])
      });
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

   /**
    * Submit expression modifications.
    */
   ok(): void {
      const name: string = this.nameControl.value;
      const parent: number = this.parentControl.value;
      const parentEntity: EntityModel = this.entities[parent];
      const attribute: AttributeModel = {
         table: null,
         dataType: null,
         qualifiedName: null,
         name: name,
         oldName: name,
         expression: this.expression,
         baseElement: false,
         elementType: "attributeElement",
         visible: true
      };

      const duplicates: boolean =
         !parentEntity.attributes.every((attr: AttributeModel) => name != attr.name);

      if(duplicates) {
         this.notifications.danger("_#(js:data.logicalmodel.attributeNameDuplicate)");
         return;
      }

      this.http.post(CHECK_EXPRESSION_URI, new StringWrapper(this.expression))
         .subscribe((data: any) => {
               if(data && data.body) {
                  this.notifications.danger("_#(js:Error)" + ": " + data.body);
               }
               else {
                  this.notifications.success("_#(js:data.logicalmodel.expression.success)");
                  this.onCommit.emit({entity: parentEntity, attributes: [attribute]});
               }
            },
            err => {
               this.notifications.danger("_#(js:Error)" + ": " + err.message);
            }
         );
   }

   updateExpression(obj: any) {
      let fexpress: string = "";
      let target: string = obj.target;
      let node: TreeNodeModel = <TreeNodeModel>obj.node;

      if(!node || !node.leaf || !node.data) {
         this.expression = obj.expression;
         return;
      }

      if(target == "columnTree") {
         fexpress += "field['" + node.data.qualifiedName + "']";
      }
      else if (!!target) {
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

      this.expression = fexpress;
   }

   /**
    * Cancel changes.
    */
   cancel(): void {
      this.onCancel.emit("cancel");
   }
}
