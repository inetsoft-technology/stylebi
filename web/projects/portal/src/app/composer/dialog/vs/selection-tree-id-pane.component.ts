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
   Input,
   ViewChild,
   AfterViewInit,
   ChangeDetectorRef, OnInit
} from "@angular/core";
import { SelectionTreePaneModel } from "../../data/vs/selection-tree-pane-model";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { TreeComponent } from "../../../widget/tree/tree.component";
import { OutputColumnRefModel } from "../../../vsobjects/model/output-column-ref-model";
import { ComboMode } from "../../../widget/dynamic-combo-box/dynamic-combo-box-model";
import { Tool } from "../../../../../../shared/util/tool";
import { DataRefType } from "../../../common/data/data-ref-type";

@Component({
   selector: "selection-tree-id-pane",
   templateUrl: "selection-tree-id-pane.component.html",
})
export class SelectionTreeIdPane implements OnInit, AfterViewInit {
   @Input() model: SelectionTreePaneModel;
   @Input() targetIdTree: TreeNodeModel;
   @Input() localRefs: OutputColumnRefModel[] = [];
   @Input() variableValues: string[];
   @Input() iconFunction: (node: TreeNodeModel) => string;
   @Input() measureTooltips: string[] = [];
   @Input() runtimeId: string;
   @ViewChild("idTree") idTree: TreeComponent;
   _singleSelection: boolean;
   localRefLabels: any[] = [];
   grayedOutRefLabels: any[] = [];
   localTable: string;
   localParentId: string;
   localId: string;
   localLabel: string;

   constructor(private changeDetectorRef: ChangeDetectorRef) {
   }

   ngOnInit(): void {
      this.localParentId = this.model.parentId;
      this.localId = this.model.id;
      this.localLabel = this.model.label;
      this.initGrayedOutLabel();
   }

   ngAfterViewInit(): void {
      if(this.model.mode == 2 && this.model.selectedTable) {
         let node: TreeNodeModel = this.idTree.getNodeByData("label", this.model.selectedTable);
         this.idTree.selectAndExpandToNode(node);
         this.localTable = this.model.selectedTable;
         this.changeDetectorRef.detectChanges();
      }
   }

   @Input()
   set singleSelection(singleSelection: boolean) {
      this._singleSelection = singleSelection;

      if(singleSelection && !!this.model) {
         this.model.selectChildren = false;
      }
   }

   get singleSelection(): boolean {
      return this._singleSelection;
   }

   @Input()
   set idRefs(columns: OutputColumnRefModel[]) {
      if(!this.localTable || Tool.isEquals(columns, this.localRefs)) {
         return;
      }

      this.localRefLabels = [];
      this.localParentId = null;
      this.localId = null;
      this.localLabel = null;

      for(let i = 0; i < columns.length; i++) {
         let column = columns[i];

         if(column.refType == DataRefType.AGG_CALC) {
            continue;
         }

         this.localRefs.push(column);
         this.localRefLabels.push({value: column.view, label: column.view,
                                   tooltip: this.measureTooltips[i]});

         if(this.model.parentId == column.name) {
            this.localParentId = column.view;
         }

         if(this.model.id == column.name) {
            this.localId = column.view;
         }

         if(this.model.label == column.name) {
            this.localLabel = column.view;
         }
      }

      if(this.model.parentId &&
         (this.model.parentId.startsWith("$") || this.model.parentId.startsWith("=")))
      {
         this.localParentId = this.model.parentId;
      }
      else if(!this.localParentId && this.localRefs.length > 0) {
         this.localParentId = this.localRefs[0].view;
         this.model.parentId = this.localRefs[0].name;
         this.model.parentIdRef = this.localRefs[0];
      }

      if(this.model.id &&
         (this.model.id.startsWith("$") || this.model.id.startsWith("=")))
      {
         this.localId = this.model.id;
      }
      else if(!this.localId && this.localRefs.length > 0) {
         this.localId = this.localRefs[0].view;
         this.model.id = this.localRefs[0].name;
         this.model.idRef = this.localRefs[0];
      }

      if(this.model.label &&
         (this.model.label.startsWith("$") || this.model.label.startsWith("=")))
      {
         this.localLabel = this.model.label;
      }
      else if(!this.localLabel && this.localRefs.length > 0) {
         this.localLabel = this.localRefs[0].view;
         this.model.label = this.localRefs[0].name;
         this.model.labelRef = this.localRefs[0];
      }
   }

   private initGrayedOutLabel(): void {
      this.grayedOutRefLabels = [];

      if(!this.model.grayedOutFields) {
         return;
      }

      for(let i = 0; i < this.model.grayedOutFields.length; i++) {
         let column = this.model.grayedOutFields[i];

         this.grayedOutRefLabels.push(column.view);
      }
   }

   selectIdTable(node: TreeNodeModel): void {
      let table: string = "";

      if(node.type == "table") {
         // Children of logic models should send their assembly name so the right columns
         // can be retrieved from the server by selection-measures-pane, otherwise use
         // the table name (node.label)
         table = node.data.properties.assembly ?
            node.data.properties.assembly : node.label;
      }
      else if(node.type == "folder") { // Logic Model
         table = node.label;
      }

      this.model.selectedTable = table;
      this.localTable = table;
   }

   selectParentId(parentId: string): void {
      if(!!parentId && (parentId.startsWith("$") || parentId.startsWith("="))) {
         this.model.parentId = parentId;
      }
      else {
         let index = this.getIndex(parentId);

         if(index != -1) {
            this.model.parentId = this.localRefs[index].name;
            this.model.parentIdRef = this.localRefs[index];
         }
      }
   }

   selectParentIdType(type: ComboMode): void {
      if(type == ComboMode.VALUE) {
         if(this.localRefLabels.length > 0) {
            this.localParentId = this.localRefLabels[0].label;
            this.model.parentId = this.localRefs[0].name;
            this.model.parentIdRef = this.localRefs[0];
         }
         else {
            this.localParentId = "(none)";
            this.model.parentId = this.model.parentIdRef = null;
         }
      }
   }

   selectId(id: string): void {
      if(!!id && (id.startsWith("$") || id.startsWith("="))) {
         this.model.id = id;
      }
      else {
         let index = this.getIndex(id);

         if(index != -1) {
            this.model.id = this.localRefs[index].name;
            this.model.idRef = this.localRefs[index];
         }
      }
   }

   selectIdType(type: ComboMode): void {
      if(type == ComboMode.VALUE) {
         if(this.localRefLabels.length > 0) {
            this.localId = this.localRefLabels[0].label;
            this.model.id = this.localRefs[0].name;
            this.model.idRef = this.localRefs[0];
         }
         else {
            this.localId = "(none)";
            this.model.id = this.model.idRef = null;
         }
      }
   }

   selectLabel(label: string): void {
      if(!!label && (label.startsWith("$") || label.startsWith("="))) {
         this.model.label = label;
      }
      else {
         let index = this.getIndex(label);

         if(index != -1) {
            this.model.label = label != "None" ? this.localRefs[index].name : null;
            this.model.labelRef = label != "None" ? this.localRefs[index] : null;
         }
      }
   }

   selectLabelType(type: ComboMode): void {
      if(type == ComboMode.VALUE) {
         if(this.localRefLabels.length > 0) {
            this.localLabel = this.localRefLabels[0].label;
            this.model.label = this.localRefs[0].name;
            this.model.labelRef = this.localRefs[0];
         }
         else {
            this.localLabel = "(none)";
            this.model.label = this.model.labelRef = null;
         }
      }
   }

   getIndex(value: string): number {
      for(let i = 0; i < this.localRefLabels.length; i++) {
         if(this.localRefLabels[i].label == value) {
            return i;
         }
      }
      return -1;
   }
}
