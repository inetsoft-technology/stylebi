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
import { Component, Input } from "@angular/core";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { LocalizationComponent } from "../../data/vs/localization-component";
import { LocalizationPaneModel } from "../../data/vs/localization-pane-model";
import { ElidedCellComponent } from "../../../widget/elided-cell/elided-cell.component";
import { ScrollableTableDirective } from "../../../widget/scrollable-table/scrollable-table.directive";
import { TreeComponent } from "../../../widget/tree/tree.component";
import { ShuffleListComponent } from "../../../widget/shuffle-list/shuffle-list.component";
import { FormsModule } from "@angular/forms";


@Component({
    selector: "localization-pane",
    templateUrl: "localization-pane.component.html",
    styleUrls: ["localization-pane.component.scss"],
    imports: [FormsModule, ShuffleListComponent, TreeComponent, ScrollableTableDirective, ElidedCellComponent]
})

export class LocalizationPane {
   @Input() model: LocalizationPaneModel;
   selectedComponents: TreeNodeModel[] = [];
   selectedLocalized: LocalizationComponent[] = [];

   selectComponent(event: TreeNodeModel[]): void {
      this.selectedComponents = [];

      for(let node of event) {
         if(node.leaf) {
            this.selectedComponents.push(node);
         }
      }
   }

   selectLocalized(event: MouseEvent, component: LocalizationComponent): void {
      if(event.ctrlKey || event.metaKey) {
         const placement: number = this.selectedLocalized.indexOf(component);

         if(placement > -1) {
            this.selectedLocalized.splice(placement, 1);
         }
         else {
            this.selectedLocalized.push(component);
         }
      }
      else if(event.shiftKey) {
         if(this.selectedLocalized.length > 0) {
            let lastSelected: LocalizationComponent =
               this.selectedLocalized[this.selectedLocalized.length - 1];
            let indexLast: number = this.model.localized.indexOf(lastSelected);
            let indexCurrent: number = this.model.localized.indexOf(component);

            if(indexLast < 0 || indexCurrent < 0) {
               if(this.selectedLocalized.indexOf(component) == -1) {
                  this.selectedLocalized.push(component);
               }

               return;
            }

            let smaller: number = indexLast < indexCurrent ? indexLast : indexCurrent;
            let larger: number = indexLast < indexCurrent ? indexCurrent : indexLast;

            // never select a row twice
            for(let i = smaller; i <= larger; i++) {
               const item = this.model.localized[i];

               if(this.selectedLocalized.indexOf(item) == -1) {
                  this.selectedLocalized.push(item);
               }
            }
         }
         else {
            this.selectedLocalized = [component];
         }
      }
      else {
         this.selectedLocalized = [component];
      }
   }

   isLocalizedSelected(component: LocalizationComponent): boolean {
      return this.selectedLocalized.indexOf(component) != -1;
   }

   add(): void {
      for(let node of this.selectedComponents) {
         let locale: LocalizationComponent = {
            name: node.data ? node.data : node.label,
            textId: node.label
         };

         if(!this.find(locale)) {
            this.model.localized.push(locale);
         }
      }
   }

   find(node: LocalizationComponent): boolean {
      for(let item of this.model.localized) {
         if(item.name == node.name) {
            return true;
         }
      }

      return false;
   }

   isAddEnabled(): boolean {
      return this.selectedComponents && this.selectedComponents.length > 0;
   }

   remove(): void {
      // remove by membership; a selected row that is not in the model must never remove another
      for(let i = this.model.localized.length - 1; i >= 0; i--) {
         if(this.selectedLocalized.indexOf(this.model.localized[i]) != -1) {
            this.model.localized.splice(i, 1);
         }
      }

      this.selectedLocalized = [];
   }

   isRemoveEnabled(): boolean {
      return this.selectedLocalized && this.selectedLocalized.length > 0;
   }

   getDisplayName(nodeName: string): string {
      return nodeName.replace("^_^", ".");
   }
}
