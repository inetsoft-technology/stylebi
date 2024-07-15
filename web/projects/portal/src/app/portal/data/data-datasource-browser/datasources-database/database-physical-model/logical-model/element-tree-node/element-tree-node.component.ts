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
   Input,
   Output,
   EventEmitter,
   HostListener,
   OnChanges,
   SimpleChanges
} from "@angular/core";
import { DragService } from "../../../../../../../widget/services/drag.service";
import { ElementModel } from "../../../../../model/datasources/database/physical-model/logical-model/element-model";
import { EntityModel } from "../../../../../model/datasources/database/physical-model/logical-model/entity-model";
import { Tool } from "../../../../../../../../../../shared/util/tool";
import { SelectedItem } from "../logical-model.component";
import { AttributeModel } from "../../../../../model/datasources/database/physical-model/logical-model/attribute-model";

export const DRAG_SEPARATOR = "^-^";

@Component({
   selector: "element-tree-node",
   templateUrl: "element-tree-node.component.html",
   styleUrls: ["element-tree-node.component.scss"]
})
export class ElementTreeNode implements OnChanges {
   @Input() node: ElementModel;
   @Input() attrIndex: number = -1;
   @Input() entityIndex: number;
   @Input() selected: SelectedItem[] = [];
   @Input() indentLevel: number = 0;
   @Input() lastNode: boolean = false;
   @Input() firstNode: boolean = false;
   @Input() moveEnabled: boolean = true;
   @Input() draggable = false;
   @Input() droppable = false;
   @Input() expandedNodes: ElementModel[] = [];
   @Input() item: any;
   @Input() deleteEnable: (entityIndex: number, attrIndex: number) => boolean = (entityIndex: number, attrIndex: number) => true;
   @Output() onOpenNode: EventEmitter<SelectedItem> = new EventEmitter<SelectedItem>();
   @Output() onMoveDown: EventEmitter<any> = new EventEmitter<any>();
   @Output() onMoveUp: EventEmitter<any> = new EventEmitter<any>();
   @Output() onDeleteEntity: EventEmitter<ElementModel> = new EventEmitter<ElementModel>();
   @Output() onDeleteAttribute: EventEmitter<SelectedItem> = new EventEmitter<SelectedItem>();
   @Output() onShiftSelect: EventEmitter<ElementModel> = new EventEmitter<ElementModel>();
   @Output() onToggleEntity: EventEmitter<{entity: EntityModel, toggle: boolean}> = new EventEmitter<{entity: EntityModel, toggle: boolean}>();
   @Output() nodeDrag = new EventEmitter<any>();
   @Output() onAttributeOrderChanged: EventEmitter<any> = new EventEmitter<any>();
   focusin: boolean = false;
   expanded: boolean;
   readonly INDENT_SIZE: number = 15;

   constructor(private dragService: DragService) {}

   ngOnChanges(changes: SimpleChanges): void {
      this.synchronousExpandNode();
   }

   private synchronousExpandNode(): void {
      this.expanded = this.isEntity() &&
         this.selected.some(ele => ele && ele.entity == this.entityIndex && ele.attribute >= 0);

      if(!this.expanded && this.expandedNodes) {
         this.expanded = this.expandedNodes.some(expandNode => expandNode &&
            expandNode.name == this.node.name && expandNode.type == this.node.type);
      }
   }

   /**
    * Check if current node is entity node and return it as entity node else null.
    * @returns {EntityModel}  the node as an EntityNode else null
    */
   get entityNode(): EntityModel {
      return this.node && (<EntityModel> this.node).attributes ? <EntityModel> this.node : null;
   }

   /**
    * Expand or contract this node.
    */
   toggleNode(): void {
      this.expanded = !this.expanded;

      if(this.isEntity()) {
         this.onToggleEntity.emit({entity: <EntityModel> this.node, toggle: this.expanded});
      }
   }

   getIcon(): string {
      let icon: string = null;

      if(this.node.type == "entity") {
         icon = "data-table-icon";
      }
      else if(this.node.type == "expression") {
         icon = "formula-icon";
      }
      else if(this.node.type == "column") {
         let attr = this.node as AttributeModel;

         if(attr.drillInfo != null && attr.drillInfo.paths.length > 0) {
            return "drill-up-icon";
         }

         icon = "column-icon";
      }

      return icon;
   }

   /**
    * Get the icon class to display as toggle icon
    * @returns {string}
    */
   getToggleIcon(): string {
      if(this.expanded) {
         return "caret-down-icon";
      }
      else {
         return "caret-right-icon";
      }
   }

   dragStarted(event: DragEvent): void {
      if(this.selected == null || !this.isNodeSelected()) {
         this.select(event);
      }

      this.dragService.reset();

      let map: Map<string, any[]> = new Map();

      for(let node of this.selected) {
         let data: any;

         if(node.dragData == null) {
            data = node;
         }
         else {
            data = node.dragData;
         }

         const dragKey = node.entity + DRAG_SEPARATOR + node.attribute;
         map.set(dragKey, data);
      }

      const transferData: any = {
         dragName: [],
      };

      map.forEach((value, key) => {
         transferData.dragName.push(key);
         transferData[key] = value;
         let data = JSON.stringify(value);
         this.dragService.put(key, data);
      });

      Tool.setTransferData(event.dataTransfer, transferData);

      this.nodeDrag.emit(event);
   }

   /**
    * Select this node.
    */
   select(event: MouseEvent): void {
      let selectEle: SelectedItem = {
         entity: -1,
         attribute: -1
      };

      selectEle.entity = this.entityIndex;
      selectEle.attribute = this.attrIndex;

      if(event.ctrlKey) {
         let index = this.getSelectedIndex();

         if(index >= 0) {
            this.selected.splice(index, 1);
         }
         else {
            this.selected.push(selectEle);
         }
      }
      else if(event.shiftKey) {
         this.onShiftSelect.emit(this.node);
      }
      else {
         this.selected.splice(0, this.selected.length);
         this.selected.push(selectEle);
         this.onOpenNode.emit(selectEle);
      }
   }

   // move selection by up/down arrow key
   @HostListener("document:keydown", ["$event"])
   moveSelection(event: KeyboardEvent) {
      const idx = this.selected
         .findIndex(a => a.entity == this.entityIndex && a.attribute == this.attrIndex);

      if(idx >= 0) {
         if(event.keyCode == 40 && !this.lastNode) {
            setTimeout(() => this.selected[idx].attribute++, 0);
         }
         else if(event.keyCode == 38 && !this.firstNode) {
            setTimeout(() => this.selected[idx].attribute--, 0);
         }
      }
   }

   /**
    * Get current element index in selected.
    */
   private getSelectedIndex(): number {
      return this.selected.findIndex(value => value &&
         value.entity == this.entityIndex && value.attribute == this.attrIndex);
   }

   isNodeSelected(): boolean {
      return this.selected.some(value => value.entity == this.entityIndex &&
         value.attribute == this.attrIndex);
   }

   /**
    * Check is this node is an entity type.
    * @returns {boolean}
    */
   private isEntity(): boolean {
      return this.node.type == "entity";
   }

   /**
    * Move entity node down.
    */
   moveNodeDown(): void {
      this.onMoveDown.emit();
   }

   /**
    * Move entity node up.
    */
   moveNodeUp(): void {
      this.onMoveUp.emit();
   }

   getSelectedItem(entityIndex: number): SelectedItem {
      if(!this.selected || this.selected.length == 0) {
         return null;
      }

      return this.selected.find(item => item.entity == entityIndex);
   }

   /**
    * Move attribute node down.
    * @param index
    */
   moveAttributeDown(index: number): void {
      if(index < (<EntityModel>this.node).attributes.length - 1) {
         let selectedItem = this.getSelectedItem(this.attrIndex);

         if(!!selectedItem && selectedItem.entity == this.entityIndex && selectedItem.attribute == index) {
            selectedItem.attribute++;
         }

         const entity: EntityModel = this.node as EntityModel;
         const temp: any = Tool.clone(entity.attributes[index]);
         entity.attributes[index] = entity.attributes[index + 1];
         entity.attributes[index + 1] = temp;
         this.onAttributeOrderChanged.emit();
      }
   }

   /**
    * Move attribute node up.
    * @param index
    */
   moveAttributeUp(index: number): void {
      if(index > 0) {
         let selectedItem = this.getSelectedItem(this.attrIndex);

         if(!!selectedItem && selectedItem.entity == this.entityIndex
            && selectedItem.attribute == index)
         {
            selectedItem.attribute--;
         }

         const entity: EntityModel = this.node as EntityModel;
         const temp: any = Tool.clone(entity.attributes[index]);
         entity.attributes[index] = entity.attributes[index - 1];
         entity.attributes[index - 1] = temp;
         this.onAttributeOrderChanged.emit();
      }
   }

   deleteAttribute(index: number): void {
      this.onDeleteAttribute.emit({entity: this.entityIndex, attribute: index});
   }
}
