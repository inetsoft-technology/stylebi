/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Component, EventEmitter, HostBinding, Input, Output } from "@angular/core";
import { TreeNodeModel } from "../../../../widget/tree/tree-node-model";
import { HttpClient } from "@angular/common/http";
import { AssemblyActionGroup } from "../../../../common/action/assembly-action-group";
import { AssemblyAction } from "../../../../common/action/assembly-action";
import { DropdownOptions } from "../../../../widget/fixed-dropdown/dropdown-options";
import { ActionsContextmenuComponent } from "../../../../widget/fixed-dropdown/actions-contextmenu.component";
import { FixedDropdownService } from "../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { SpecificationModel } from "../../../data/tablestyle/specification-model";
import { TableStyleModel } from "../../../data/tablestyle/table-style-model";
import { TableStyleUtil } from "../../../../common/util/table-style-util";

@Component({
   selector: "style-tree-pane",
   templateUrl: "style-tree-pane.component.html"
})
export class StyleTreePane {
   @HostBinding("hidden")
   @Input() inactive: boolean;
   @Input() tableStyle: TableStyleModel;
   @Output() onOpenCustomEdit = new EventEmitter<{new: boolean, model: SpecificationModel}>();
   @Output() onRemoveCustom = new EventEmitter();

   constructor(private http: HttpClient, private dropdownService: FixedDropdownService) {
   }

   selectNode(node: TreeNodeModel) {
      if(node.type && node.type != TableStyleUtil.CUSTOM_FOLDER) {
         this.tableStyle.selectedRegion = node.data + "";
         this.tableStyle.selectedRegionLabel = node.label;
      }
   }

   hasMenuFunction(): any {
      return (node) => this.hasMenu(node);
   }

   hasMenu(node: TreeNodeModel): boolean {
      let actions = this.createActions([null, node, [node]]);
      return actions.some(group => group.visible);
   }

   openAssetTreeContextmenu(event: [MouseEvent, TreeNodeModel, TreeNodeModel[]]) {
      let options: DropdownOptions = {
         position: {x: event[0].clientX, y: event[0].clientY},
         contextmenu: true,
      };

      let contextmenu: ActionsContextmenuComponent =
         this.dropdownService.open(ActionsContextmenuComponent, options).componentInstance;
      contextmenu.sourceEvent = event[0];
      contextmenu.actions = this.createActions(event);
   }

   private createActions(event: [MouseEvent, TreeNodeModel, TreeNodeModel[]]): AssemblyActionGroup[] {
      let group = new AssemblyActionGroup([]);
      let groups = [group];
      let node = event[1];
      let specId: number = node.data;

      if(node.type == TableStyleUtil.CUSTOM) {
         group.actions.push(this.createOpenEditPaneAction(specId));
         group.actions.push(this.createDeleteAction(specId));
      }

      if(node.type == TableStyleUtil.CUSTOM_FOLDER) {
         group.actions.push(this.createNewEditPaneAction());
      }

      return groups;
   }

   private createOpenEditPaneAction(specId: number): AssemblyAction {
      return {
         id: () => "table-style open-edit",
         label: () => "_#(js:Edit)",
         icon: () => "",
         enabled: () => true,
         visible: () => true,
         action: () => this.openEdit(specId)
      };
   }

   private createDeleteAction(specId: number): AssemblyAction {
      return {
         id: () => "table-style delete-custom",
         label: () => "_#(js:Remove)",
         icon: () => "",
         enabled: () => true,
         visible: () => true,
         action: () => this.deleteCustom(specId)
      };
   }

   private createNewEditPaneAction(): AssemblyAction {
      return {
         id: () => "table-style new-custom",
         label: () => "_#(js:New Pattern)",
         icon: () => "",
         enabled: () => true,
         visible: () => true,
         action: () => this.newCustom()
      };
   }

   openEdit(specId: number) {
      this.onOpenCustomEdit.emit({new: false, model: this.tableStyle.styleFormat.specList[specId]});
   }

   newCustom() {
      this.tableStyle.isModified = true;
      let len =  this.tableStyle.styleFormat.specList == null ? 0 : this.tableStyle.styleFormat.specList.length;
      let specModel = <SpecificationModel> { id: len, customType: TableStyleUtil.ROW,
         start: 0, repeat: true, all: true, level: 1};
      this.onOpenCustomEdit.emit({new: true, model: specModel});
   }

   deleteCustom(specId: number) {
      TableStyleUtil.deleteCustom(this.tableStyle, specId);
      TableStyleUtil.initRegionsTree(this.tableStyle);
      TableStyleUtil.addUndoList(this.tableStyle);
      this.onRemoveCustom.emit();
   }
}
