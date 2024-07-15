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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { find } from "rxjs/operators";
import { VSObjectModel } from "../../../../vsobjects/model/vs-object-model";
import { Sheet } from "../../../data/sheet";
import { VSObjectTreeNode } from "../../../data/vs-object-tree-node";

const CSS_CLASSES: {[type: string]: string} = {
   "VSChart": "chart",
   "VSCrosstab": "crosstab",
   "VSTable": "table",
   "VSCalcTable": "freehand-table",
   "VSSelectionList": "selection-list",
   "VSSelectionTree": "selection-tree",
   "VSRangeSlider": "range_slider",
   "VSCalendar": "calendar",
   "VSSelectionContainer": "selection_container",
   "VSText": "text",
   "VSImage": "image",
   "VSGauge": "gauge",
   "VSSlider": "slider",
   "VSSpinner": "spinner",
   "VSCheckBox": "checkbox",
   "VSRadioButton": "radiobutton",
   "VSComboBox": "combobox",
   "VSTextInput": "textinput",
   "VSSubmit": "submit",
   "VSUpload": "upload",
   "VSLine": "line",
   "VSRectangle": "rectangle",
   "VSOval": "oval",
   "VSTab": "tab"
};

@Component({
   selector: "component-tree",
   templateUrl: "component-tree.component.html",
   styleUrls: ["component-tree.component.scss"]
})
export class ComponentTree  {
   @Input() children: VSObjectTreeNode[];
   @Input() sheet: Sheet;
   @Output() onCopy: EventEmitter<VSObjectModel> = new EventEmitter<VSObjectModel>();
   @Output() onCut: EventEmitter<VSObjectModel> = new EventEmitter<VSObjectModel>();
   @Output() onRemove: EventEmitter<VSObjectModel> = new EventEmitter<VSObjectModel>();
   @Output() onBringToFront: EventEmitter<VSObjectModel> = new EventEmitter<VSObjectModel>();
   @Output() onSendToBack: EventEmitter<VSObjectModel> = new EventEmitter<VSObjectModel>();

   constructor() {
   }

   getCssClass(node: VSObjectTreeNode): string {
      return CSS_CLASSES[node.model.objectType];
   }

   getToggleIcon(node: VSObjectTreeNode): string {
      if(node.expanded) {
         return "caret-down-icon icon-lg";
      }
      else {
         return "caret-right-icon icon-lg";
      }
   }

   isSelected(node: VSObjectTreeNode): boolean {
      return !!this.sheet && !!this.sheet.focusedAssemblies.pipe(find((v) => v === node.model));
   }

   hasChildren(node: VSObjectTreeNode): boolean {
      return node && node.children && node.children.length > 0;
   }

   selectNode(event: MouseEvent, node: VSObjectTreeNode): void {
      if(event.ctrlKey) {
         this.sheet.selectAssembly(node.model);
      }
      else {
         this.sheet.currentFocusedAssemblies = [node.model];
      }

      event.stopPropagation();
   }

   expand(node: VSObjectTreeNode): void {
      node.expanded = !node.expanded;
   }

   copyAssembly(model: VSObjectModel): void {
      this.onCopy.emit(model);
   }

   cutAssembly(model: VSObjectModel): void {
      this.onCut.emit(model);
   }

   removeAssembly(model: VSObjectModel): void {
      this.onRemove.emit(model);
   }

   bringAssemblyToFront(model: VSObjectModel): void {
      this.onBringToFront.emit(model);
   }

   sendAssemblyToBack(model: VSObjectModel): void {
      this.onSendToBack.emit(model);
   }
}
