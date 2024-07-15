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
import { Injectable, OnDestroy } from "@angular/core";
import { Subject } from "rxjs";
import { Tool } from "../../../../../../../../../shared/util/tool";
import { ExpressionRef } from "../../../../../../common/data/expression-ref";
import { XSchema } from "../../../../../../common/data/xschema";
import { NotificationData } from "../../../../../../widget/repository-tree/repository-tree.service";
import { AttributeModel } from "../../../../model/datasources/database/physical-model/logical-model/attribute-model";
import { EntityModel } from "../../../../model/datasources/database/physical-model/logical-model/entity-model";
import {
   LMHierarchyConstants
} from "../../../../model/datasources/database/physical-model/logical-model/lm-hierarchy-constants";
import { LogicalModelDefinition } from "../../../../model/datasources/database/physical-model/logical-model/logical-model-definition";
import { LogicalModelSettings } from "../../../../model/datasources/database/physical-model/logical-model/logical-model-settings";
import { SelectedItem } from "./logical-model.component";
import { DataRefType } from "../../../../../../common/data/data-ref-type";
import { ElementModel } from "../../../../model/datasources/database/physical-model/logical-model/element-model";
const QUALIFIED_NAME_CONCAT = ".";

@Injectable()
export class LogicalModelService implements OnDestroy {
   _selectedItems: any[] = [];
   settings: LogicalModelSettings;
   onNotification = new Subject<NotificationData>();
   onDeleteSelectedItems = new Subject<any[]>();

   ngOnDestroy(): void {
      if(!!this.onDeleteSelectedItems) {
         this.onDeleteSelectedItems.unsubscribe();
         this.onDeleteSelectedItems = null;
      }

      if(!!this.onNotification) {
         this.onNotification.unsubscribe();
         this.onNotification = null;
      }
   }

   get selectedItems(): any[] {
      return this._selectedItems;
   }

   set selectedItems(selectedItems: any[]) {
      this._selectedItems = selectedItems;
   }

   unsupportedFullDate(attribute: AttributeModel): boolean {
      return this.isDateType(attribute.dataType) && !this.fullDateSupport;
   }

   get fullDateSupport(): boolean {
      return !!this.settings && this.settings.fullDateSupport;
   }

   entityToggle(data: {entity: EntityModel, toggle: boolean}, expanded: EntityModel[]): void {
      if(!data || !data.entity) {
         return;
      }

      if(!expanded) {
         expanded = [];
      }

      let findIndex: number = expanded.findIndex(entity =>
         Tool.isEquals(entity, data.entity));

      if(data.toggle && findIndex < 0) {
         expanded.push(data.entity);
      }
      else if(!data.toggle && findIndex >= 0){
         expanded.splice(findIndex, 1);
      }
   }


   /**
    * Multi-select by shift.
    * @param element selecected element.
    */
   shiftSelect(element: ElementModel, selectedEles: SelectedItem[],
               logicalModel: LogicalModelDefinition, expanded: EntityModel[]): void
   {
      let shiftObj: any = {start: false, end: false, order: true, shiftNodes: []};

      if(selectedEles == null || selectedEles.length == 0) {
         selectedEles = []; //Todo
         return;
      }

      let lastSelected = selectedEles[selectedEles.length - 1];
      let lastNode: ElementModel = logicalModel.entities[lastSelected.entity];

      if(!this.isEntity(lastSelected)) {
         lastNode = (<EntityModel> lastNode).attributes[lastSelected.attribute];
      }

      if(lastNode == element) {
         return;
      }

      this.findShiftSelectedNodes(shiftObj, lastNode, element, logicalModel, expanded);
      this.addSelectedNodes(shiftObj, selectedEles);
   }

   /**
    * Find the elements should be selected by shift in logical model.
    * @param shiftObj shift selected info.
    * @param lastNode last selected element.
    * @param node current selected element.
    */
   findShiftSelectedNodes(shiftObj: any, lastNode: ElementModel, node: ElementModel,
                          logicalModel: LogicalModelDefinition, expanded: EntityModel[])
   {
      for(let i = 0; i < logicalModel.entities.length; i++) {
         this.findNodes(logicalModel.entities[i], shiftObj, lastNode, node, i,
            -1, expanded);
      }
   }

   /**
    * Find the elements should be selected by shift in entity and attributes.
    * @param findNode the element to match.
    * @param shiftObj shift selected info.
    * @param lastNode last selected element
    * @param node current selected element
    * @param entityIndex entity index in logical model.
    * @param attributeIndex attribute index in entity.
    */
   findNodes(findNode: ElementModel, shiftObj: any, lastNode: ElementModel, node: ElementModel,
             entityIndex: number, attributeIndex: number, expanded: EntityModel[]): void
   {
      let currentSelection: SelectedItem = {
         entity: entityIndex,
         attribute: attributeIndex
      };

      if(shiftObj.start && !shiftObj.end) {
         this.addToArray(shiftObj.shiftNodes, currentSelection);
      }

      let nodes: ElementModel[];

      if(this.isEntityElement(findNode) && this.isExpandedEntity(<EntityModel> findNode, expanded)) {
         nodes = (<EntityModel> findNode).attributes;
      }

      // Find first node, start is true, find the second node, end is true.
      // add node when start is true and end is false.
      if(findNode == lastNode || findNode == node) {
         // If select node from top to bottom, order is true, else false.
         if(!shiftObj.start) {
            shiftObj.order = findNode == lastNode;
         }

         shiftObj.end = shiftObj.start;
         shiftObj.start = true;
         this.addToArray(shiftObj.shiftNodes, currentSelection);
      }

      if(nodes == null) {
         return;
      }

      for(let i = 0; i < nodes.length; i++) {
         this.findNodes(nodes[i], shiftObj, lastNode, node, entityIndex, i, expanded);
      }
   }

   addSelectedNodes(shiftObj: any, selectedEles: SelectedItem[]) {
      let nodes = shiftObj.shiftNodes;

      if(!shiftObj.order) {
         for(let i = (nodes.length - 1); i >= 0; i--) {
            this.addToArray(selectedEles, nodes[i]);
         }
      }
      else {
         for(let j = 0; j < nodes.length; j++) {
            this.addToArray(selectedEles, nodes[j]);
         }
      }
   }

   /**
    * Check is this node is an entity type.
    * @returns {boolean}
    */
   private isEntity(selected: SelectedItem): boolean {
      return selected && selected.entity >= 0 && selected.attribute == -1;
   }

   /**
    * Check is this element is an entity type.
    * @returns {boolean}
    */
   private isEntityElement(ele: ElementModel): boolean {
      return ele.type == "entity";
   }

   private addToArray(arr: SelectedItem[], ele: SelectedItem) {
      let find = arr.some(element => element && element.entity === ele.entity &&
         element.attribute === ele.attribute);

      if(!find) {
         arr.push(ele);
      }
   }

   /**
    * Whether the entity is expanded.
    * @param entity
    */
   private isExpandedEntity(entity: EntityModel, expanded: EntityModel[]): boolean {
      return expanded && expanded.some(expandedNode => Tool.isEquals(expandedNode, entity));
   }

   isDateType(type: string): boolean {
      return XSchema.DATE == type || XSchema.TIME_INSTANT == type;
   }

   isDateItem(item: any): boolean {
      if(item.columnType == LMHierarchyConstants.DIMENSION) {
         return item.type === DataRefType.CUBE_MODEL_TIME_DIMENSION;
      }
      else {
         return this.isDateType(item.originalType);
      }
   }

   private doChangeItemName(item: any, newName: string) {
      item.name = newName;

      if(item.columnType == LMHierarchyConstants.DIMENSION) {
         item.qualifiedName = newName;

         for (let member of item.members) {
            member.qualifiedName = newName + QUALIFIED_NAME_CONCAT + member.name;
         }
      }
      else if(item.columnType == LMHierarchyConstants.MEMBER) {
         item.qualifiedName = item.dimName + QUALIFIED_NAME_CONCAT + newName;
      }
      else {
         item.qualifiedName = newName;

         if(!!item.dataRef.exp) {
            item.dataRef.name = newName;
         }
      }
   }
}
