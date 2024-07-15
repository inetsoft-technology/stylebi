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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import {
   EntityModel
} from "../../../../../../model/datasources/database/physical-model/logical-model/entity-model";
import { SelectedItem } from "../../logical-model.component";
import { Tool } from "../../../../../../../../../../../shared/util/tool";

@Component({
   selector: "select-attribute-pane",
   templateUrl: "select-attribute-pane.component.html"
})
export class SelectAttributePaneComponent {
   @Input() entities: EntityModel[];
   selectedItem: SelectedItem;
   expanded: EntityModel[] = [];

   @Input() set info(_info: {expanded: EntityModel[], selectedItem: SelectedItem}) {
      this.expanded = _info.expanded;
      this.selectedItem = _info.selectedItem;
   }

   @Output() onSelectItem: EventEmitter<string> = new EventEmitter<string>();

   updateSelectedItem(label: string): void {
      if(!label || label.indexOf(".") == -1) {
         this.selectedItem = {entity: -1, attribute: -1};
         this.expanded = [];

         return;
      }

      let entityName: string = label.substring(0, label.lastIndexOf("."));
      let attribute: string = label.substring(label.lastIndexOf(".") + 1);

      if(!this.entities) {
         this.selectedItem = {entity: -1, attribute: -1};
         this.expanded = [];

         return;
      }

      for(let i = 0; i < this.entities.length; i++) {
         if(this.entities[i].name == entityName) {
            let entity = this.entities[i];
            this.selectedItem.entity = i;
            this.expanded = [entity];

            for(let j = 0; j < entity.attributes.length; j++) {
               if(entity.attributes[j].name == attribute) {
                  this.selectedItem.attribute = j;
                  return;
               }
            }
         }
      }
   }

   selectItem(item: SelectedItem) {
      this.selectedItem = item;

      if(item.entity == -1 || item.attribute == -1) {
         return;
      }

      let entityName: string = this.entities[item.entity].name;
      let attrName: string = this.entities[item.entity].attributes[item.attribute].name;
      this.onSelectItem.emit(entityName + "." + attrName);
   }

   entityToggle(data: {entity: EntityModel, toggle: boolean}, expanded: EntityModel[]): void {
      if(!data || !data.entity) {
         return;
      }

      if(!expanded) {
         expanded = [];
      }

      let findIndex: number = expanded.findIndex(entity => Tool.isEquals(entity, data.entity));

      if(data.toggle && findIndex < 0) {
         expanded.push(data.entity);
      }
      else if(!data.toggle && findIndex >= 0){
         expanded.splice(findIndex, 1);
      }
   }
}