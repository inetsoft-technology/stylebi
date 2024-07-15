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
   ElementRef,
   EventEmitter,
   Input,
   OnChanges,
   OnInit,
   Output,
   SimpleChanges,
   ViewChild,
   Renderer2,
   ChangeDetectorRef
} from "@angular/core";
import { DataRef } from "../../common/data/data-ref";
import { GroupRef } from "../../common/data/group-ref";
import { Tool } from "../../../../../shared/util/tool";
import { TreeNodeModel } from "../tree/tree-node-model";
import { ConditionFieldComboModel } from "./condition-field-combo-model";
import { FixedDropdownDirective } from "../fixed-dropdown/fixed-dropdown.directive";
import { ColumnRef } from "../../binding/data/column-ref";
import { AggregateRef } from "../../common/data/aggregate-ref";
import {VirtualScrollService} from "../tree/virtual-scroll.service";
import { TreeTool } from "../../common/util/tree-tool";
import { VirtualScrollTreeDatasource } from "../tree/virtual-scroll-tree-datasource";

@Component({
   selector: "condition-field-combo",
   templateUrl: "condition-field-combo.component.html",
   styleUrls: ["condition-field-combo.component.scss"]
})
export class ConditionFieldComboComponent implements OnChanges, OnInit {
   @Input() field: DataRef;
   @Input() grayedOutFields: DataRef[];
   @Input() addNoneItem: boolean = true;
   @Input() enabled: boolean = true;
   @Input() showOriginalName: boolean = false;
   @Output() onSelectField: EventEmitter<any> = new EventEmitter<any>();
   @ViewChild(FixedDropdownDirective) fieldsDropdown: FixedDropdownDirective;
   noneItem: DataRef = {name: "None", view: "_#(js:None)", fakeNone: true};
   defaultFocus: boolean = false;
   treeModel: TreeNodeModel = {};
   listModel: DataRef[] = [];
   private static _displayList: boolean = true;
   private _fieldsModel: ConditionFieldComboModel;
   DROPDOWN_HEIGHT: number = 300;
   needUseVirtualScroll: boolean = false;
   showSearch: boolean = false;
   searchStr: string = "";
   virtualScrollTreeDatasource: VirtualScrollTreeDatasource = new VirtualScrollTreeDatasource();
   @ViewChild("dropdownBody") dropdownBody: ElementRef;
   @ViewChild("searchInput") searchInput: ElementRef;

   @Input()
   set fieldsModel(values: ConditionFieldComboModel) {
      this._fieldsModel = values;
   }

   get fieldsModel(): ConditionFieldComboModel {
      return this._fieldsModel;
   }

   constructor(private changeRef: ChangeDetectorRef,
               private renderer: Renderer2) {
   }

   get displayList(): boolean {
      return ConditionFieldComboComponent._displayList;
   }

   set displayList(flag: boolean) {
      ConditionFieldComboComponent._displayList = flag;
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.hasOwnProperty("fieldsModel")) {
         this.listModel = this.createListModel();
         this.treeModel = this.createTreeModel();
         this.needUseVirtualScroll = TreeTool.needUseVirtualScroll(this.treeModel);
      }

      if(changes.hasOwnProperty("field") || changes.hasOwnProperty("fieldsModel")) {
         if(this.field && this.fieldsModel) {
            const exact = this.fieldsModel.list.find((ref) => {
               return ref.view == this.field.view;
            });

            if(!exact) {
               const matchingRef = this.fieldsModel.list.find((ref) => {
                  return ref.attribute == this.field.attribute && ref.entity == this.field.entity &&
                     ref.classType == this.field.classType;
               });

               if(matchingRef) {
                  const changed = this.field && this.field.view != matchingRef.view;
                  this.field = matchingRef;

                  // if changed to a different aggregate, fire so the condition can be
                  // updated when the formula on an aggregate has been updated.
                  if(changed) {
                     this.onSelectField.emit(matchingRef);
                     this.changeRef.detectChanges();
                  }
               }
            }
         }
      }
   }

   ngOnInit() {
      this.initExpanded();
      this.listModel = this.createListModel();
      this.treeModel = this.createTreeModel();
      this.needUseVirtualScroll = TreeTool.needUseVirtualScroll(this.treeModel);
   }

   nodeStateChanged(node: TreeNodeModel, expand: boolean): void {
      if(expand) {
         this.virtualScrollTreeDatasource.nodeExpanded(this.treeModel, node);
      }
      else {
         this.virtualScrollTreeDatasource.nodeCollapsed(this.treeModel, node);
      }
   }

   initExpanded(): void {
      if(!this.field || !this.fieldsModel || !this.fieldsModel.tree) {
         return;
      }

      let children: TreeNodeModel[] = this.fieldsModel.tree.children;

      for(let node of children) {
         if(this.getSelectedNode(node)) {
            node.expanded = true;
            return;
         }
      }
   }

   createTreeModel(): TreeNodeModel {
      if(!this.fieldsModel) {
         return null;
      }

      if(this.addNoneItem) {
         let childrenNodes: TreeNodeModel[] = [];
         childrenNodes.push({label: "_#(js:None)", data: null, leaf: true});
         childrenNodes = childrenNodes.concat(this.fieldsModel.tree.children);
         return {label: "_#(js:root)", children: childrenNodes};
      }

      return this.fieldsModel.tree;
   }

   createListModel(): DataRef[] {
      if(!this.fieldsModel) {
         return null;
      }

      if(this.addNoneItem) {
         let nlist: DataRef[] = [];
         nlist.push(this.noneItem);
         return nlist.concat(this.fieldsModel.list);
      }

      return this.fieldsModel.list;
   }

   getDefaultValue(): string {
      return this.addNoneItem ? "_#(js:None)" : "";
   }

   get selectedNodes(): TreeNodeModel[] {
      let nodes: TreeNodeModel[] = [];
      let node: TreeNodeModel = this.getSelectedNode(this.treeModel);

      if(node) {
         nodes.push(node);
      }

      return nodes;
   }

   getSelectedNode(treeNode: TreeNodeModel): TreeNodeModel {
      if(!treeNode) {
         return null;
      }

      let children: TreeNodeModel[] = treeNode.children;

      if(!children) {
         return null;
      }

      for(let node of children) {
         let nodeFld: DataRef = node.data ? node.data : null;

         if(nodeFld && this.field && nodeFld.view == this.field.view) {
            return node;
         }

         let findNode: TreeNodeModel = this.getSelectedNode(node);

         if(findNode) {
            return findNode;
         }
      }

      return null;
   }

   selectNode(treeNode: TreeNodeModel): void {
      if(treeNode.data || !treeNode.data && treeNode.label == "_#(js:None)") {
         this.onSelectField.emit(treeNode.data);
         this.fieldsDropdown.close();
         this.showSearch = false;
      }
   }

   selectField(fld: DataRef) {
      let nfld: DataRef = Tool.isEquals(fld, this.noneItem) ? null : fld;
      this.onSelectField.emit(nfld);
      this.fieldsDropdown.close();
      this.showSearch = false;
   }

   isSelectedField(fld: DataRef): boolean {
      return !this.field && this.addNoneItem ?
         Tool.isEquals(this.noneItem, fld) : Tool.isEquals(this.field, fld);
   }

   getCSSIcon(node: TreeNodeModel): string {
      return node.data || !node.data && (!node.children || node.children.length == 0) ?
         "column-icon" : null;
   }

   convertDropDownStyle(): void {
      this.displayList = !this.displayList;
   }

   convertSwitchBtnTitle(): string {
      return this.displayList ? "_#(js:Switch to Tree)" : "_#(js:Switch to List)";
   }

   getTooltip(fld: DataRef): string {
      if(fld == null) {
         return "";
      }

      let tooltip: string = "";

      if(fld.classType == "GroupRef") {
         let columnRef: DataRef = (<GroupRef> fld).ref;
         tooltip = columnRef == null ? "" : this.showOriginalName ?
            ColumnRef.getTooltip(<GroupRef> fld) : columnRef.description;
      }
      else if(fld.classType == "AggregateRef") {
         let columnRef: DataRef = (<AggregateRef> fld).ref;
         tooltip = columnRef == null ? "" : this.showOriginalName ?
            ColumnRef.getTooltip(<AggregateRef> fld) : columnRef.description;
      }
      else if(fld.classType == "ColumnRef") {
         let columnDesc: string = (<ColumnRef> fld).description;
         tooltip = columnDesc == null ? "" : this.showOriginalName ?
            ColumnRef.getTooltip(<ColumnRef> fld) : columnDesc;
      }
      else {
         tooltip = fld.description;
      }

      return !!tooltip ? tooltip : "";
   }

   get dropdownMinWidth(): number {
      return this.dropdownBody && this.dropdownBody.nativeElement
         ? this.dropdownBody.nativeElement.offsetWidth : null;
   }

   get dropdownHeight(): number {
      return this.treeModel != null ? this.DROPDOWN_HEIGHT : null;
   }

   noOp(): void {
      // no-op, just a blank method to bind to while triggering angular change detection.
   }

   startSearch(event: MouseEvent) {
      this.showSearch = true;
      this.fieldsDropdown.open(event);
      setTimeout(() => this.renderer.selectRootElement(this.searchInput.nativeElement).focus(),
                 200);
   }

   closeSearch(event: MouseEvent) {
      this.showSearch = false;
      this.searchStr = "";
   }

   dropDownOpenChange(open: boolean) {
      if(!open) {
         this.showSearch = false;
      }
   }
}
