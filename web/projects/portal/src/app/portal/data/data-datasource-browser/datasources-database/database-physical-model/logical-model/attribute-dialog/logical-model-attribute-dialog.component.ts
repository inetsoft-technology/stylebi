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
import { HttpClient } from "@angular/common/http";
import { Component, EventEmitter, Input, OnInit, Output, ViewChild, ElementRef, AfterViewInit } from "@angular/core";
import { AbstractControl, UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { PhysicalTableTreeComponent } from "./physical-table-tree/physical-table-tree.component";
import { EntityModel } from "../../../../../model/datasources/database/physical-model/logical-model/entity-model";
import { TreeNodeModel } from "../../../../../../../widget/tree/tree-node-model";
import { AttributeModel } from "../../../../../model/datasources/database/physical-model/logical-model/attribute-model";
import { GetModelEvent } from "../../../../../model/datasources/database/events/get-model-event";

const TABLES_URI: string = "../api/data/logicalModel/tables/nodes";

@Component({
   selector: "logical-model-attribute-dialog",
   templateUrl: "logical-model-attribute-dialog.component.html",
   styleUrls: ["logical-model-attribute-dialog.component.scss"]
})
export class LogicalModelAttributeDialog implements OnInit, AfterViewInit {
   @ViewChild("physicalTree") tree: PhysicalTableTreeComponent;
   @ViewChild("selectFocus") selectFocus: ElementRef;
   @Input() entities: EntityModel[] = [];
   @Input() parent: number = -1;
   @Input() databaseName: string;
   @Input() physicalModelName: string;
   @Input() additional: string;
   @Input() logicalModelName: string;
   @Input() parentName: string;
   @Output() onCommit: EventEmitter<any> = new EventEmitter<any>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   form: UntypedFormGroup;
   tablesRoot: TreeNodeModel;
   selectedColumns: TreeNodeModel[] = [];
   private _newParent = false;

   @Input()
   set newParent(value: boolean) {
      this._newParent = value;

      if(this.form) {
         if(value) {
            this.parentControl.enable();
         }
         else {
            this.parentControl.disable();
         }
      }
   }

   get newParent(): boolean {
      return this._newParent;
   }

   constructor(private http: HttpClient) {}

   ngOnInit(): void {
      this.loadTable();
      this.initFormControl();
   }

   ngAfterViewInit(): void {
      this.selectFocus.nativeElement.focus();
   }

   /**
    * Load tables and columns tree.
    */
   private loadTable(): void {
      let event = new GetModelEvent(this.databaseName, this.physicalModelName,
         this.logicalModelName, this.parentName, this.additional);

      this.http.post<TreeNodeModel>(TABLES_URI, event).subscribe(
            data => {
               this.tablesRoot = data;
               setTimeout(() => this.selectColumns());
            },
            err => {}
         );
   }

   /**
    * If entity is selected, select the proper columns if possible.
    */
   selectColumns(): void {
      if(this.parent >= 0 && this.tree) {
         this.tree.removeLockedNodes();
         const parentEntity: EntityModel = this.entities[this.parent];
         parentEntity.attributes.forEach((attr: AttributeModel) => {
            const node: TreeNodeModel = {
               label: attr.name,
               data: attr,
               leaf: true,
               disabled: true
            };

            this.tree.selectAndExpandToNode(node);
         });
      }
   }

   /**
    * When entity is changed, update selected columns.
    * @param event
    */
   entityChange(value: number): void {
      this.parent = value;
      this.selectColumns();
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
      if(this.parent == -1 && this.entities.length > 0) {
         this.parent = 0;
      }

      this.form = new UntypedFormGroup({
         parent: new UntypedFormControl(this.parent, [
            Validators.required
         ])
      });

      if(this.newParent) {
         this.parentControl.disable();
      }
   }

   /**
    * Stores selected nodes from the tree.
    * @param nodes
    */
   select(nodes: TreeNodeModel[]): void {
      this.selectedColumns = nodes;
   }

   /**
    * Submit attribute modifications.
    */
   ok(): void {
      const parentIndex: number = this.parentControl.value;
      const parent: EntityModel = this.entities[parentIndex];
      const attributes: AttributeModel[] = this.selectedColumns
         .filter((node: TreeNodeModel) => node.leaf && !node.disabled)
         .map((node: TreeNodeModel) => node.data as AttributeModel);
      this.onCommit.emit({entity: parent, attributes: attributes});
   }

   /**
    * Cancel changes.
    */
   cancel(): void {
      this.onCancel.emit("cancel");
   }
}
