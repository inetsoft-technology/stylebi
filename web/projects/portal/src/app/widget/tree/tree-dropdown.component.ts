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
   AfterViewInit,
   Component,
   ElementRef,
   EventEmitter,
   HostListener,
   Input,
   NgZone,
   Output,
   Renderer2,
   SimpleChanges,
   TemplateRef,
   ViewChild
} from "@angular/core";
import { Point } from "../../common/data/point";
import { DropdownOptions } from "../fixed-dropdown/dropdown-options";
import { DropdownRef } from "../fixed-dropdown/fixed-dropdown-ref";
import { FixedDropdownService } from "../fixed-dropdown/fixed-dropdown.service";
import { FixedDropdownDirective } from "../fixed-dropdown/fixed-dropdown.directive";
import { TreeNodeModel } from "./tree-node-model";
import { TreeDataPane } from "../../composer/dialog/vs/tree-data-pane";
import { DataTreeValidatorService } from "../../vsobjects/dialog/data-tree-validator.service";

@Component({
   selector: "tree-dropdown",
   templateUrl: "tree-dropdown.component.html",
   styleUrls: ["tree-dropdown.component.scss"]
})
export class TreeDropdownComponent extends TreeDataPane {
   @Input() root: TreeNodeModel;
   @Input() set selected(value: string) {
      this.currentLabel = value;
   }
   @Input() selectedType: string = null;
   @Input() isDisabled: boolean = false;
   @Input() iconFunction: (node: TreeNodeModel) => string;
   @Input() initSelectedNodes: TreeNodeModel[];
   @Input() currentNodeData: string = "";
   @Input() currentSelectedNode: TreeNodeModel;
   @Input() expandSelectedNodes: boolean = false;
   @Output() nodeExpanded = new EventEmitter<TreeNodeModel>();
   @Output() nodeSelected = new EventEmitter<TreeNodeModel>();
   @ViewChild("dropdownMenu") dropdownMenu: TemplateRef<any>;
   @ViewChild(FixedDropdownDirective) inputDropdown: FixedDropdownDirective;
   currentLabel: string = "";

   constructor(private dropdownService: FixedDropdownService,
               private renderer: Renderer2,
               private zone: NgZone,
               protected treeValidator: DataTreeValidatorService)
   {
      super(treeValidator);
   }

   onTreeNodeSelect(node: TreeNodeModel) {
      this.selectedNode(node);

      if(node && node.leaf) {
         this.selectNode(node);
         this.inputDropdown.close();
      }
   }

   reset() {
      this.currentLabel = "";
      this.currentSelectedNode = null;
   }

   private selectNode(node: TreeNodeModel) {
      this.currentLabel = node.label;
      this.currentSelectedNode = node;
      this.nodeSelected.emit(node);
   }

   get selectedNodes(): TreeNodeModel[] {
      if(this.currentLabel != null) {
         let selectedNode = !!this.currentSelectedNode ? this.currentSelectedNode :
            {
               label: this.currentLabel,
               data: !!this.currentNodeData ? this.currentNodeData : this.currentLabel,
               type: this.selectedType
            };

         return [selectedNode, ...(this.initSelectedNodes ? this.initSelectedNodes : [])];
      }

      return [];
   }
}
