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
import { Component, Input, NgZone, OnChanges, ViewChild } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { AssetType } from "../../../../../shared/data/asset-type";
import { GuiTool } from "../../common/util/gui-tool";
import { Tool } from "../../../../../shared/util/tool";
import { TreeComponent } from "../../widget/tree/tree.component";
import { DomService } from "../../widget/dom-service/dom.service";
import { TreeNodeModel } from "../../widget/tree/tree-node-model";
import { GroupCondition } from "../data/named-group-info";
import { NameInputDialog } from "./name-input-dialog.component";
import { ComponentTool } from "../../common/util/component-tool";

@Component({
   selector: "named-group-pane",
   templateUrl: "named-group-pane.component.html",
   styleUrls: ["named-group-pane.component.scss"],
})

export class NamedGroupPane implements OnChanges {
   @Input() oldGroups: GroupCondition[];
   @Input() groups: GroupCondition[] = [];
   @Input() ngValues: any[];
   @ViewChild(TreeComponent) tree: TreeComponent;
   root: TreeNodeModel;
   selectNodes: any[];
   private refreshTree: boolean = false;

   public constructor(private modalService: NgbModal,
                      private zone: NgZone,
                      private domService: DomService)
   {
   }

   ngOnChanges() {
      this.initGroups();
      this.root = this.getRootNode();
   }

   private initGroups() {
      if(this.oldGroups == null || this.oldGroups.length == 0) {
         return;
      }

      for(let i = 0; i < this.oldGroups.length; i++) {
         this.groups.push(<GroupCondition> JSON.parse(JSON.stringify(this.oldGroups[i])));
      }
   }

   private getRootNode(): TreeNodeModel {
      let childs: Array<TreeNodeModel> = [];
      let rnode: TreeNodeModel = <TreeNodeModel> {
         label: "_#(js:root)", data: "root",
         expandedIcon: "folder-expanded-icon", collapsedIcon: "folder-collapsed-icon",
         leaf: false, children: [],
         type: AssetType.FOLDER + ""
      };

      let useValues: Array<any> = [];
      this.populateGroupNodes(rnode, useValues);
      this.populateOtherNodes(rnode, useValues);
      return rnode;
   }

   private findFolderByName(name: string, root: TreeNodeModel): TreeNodeModel {
      let node: TreeNodeModel = null;
      if(!!root && !!root.children && root.children.length > 0) {
         for(let i = 0; i < root.children.length; i++) {
            if(root.children[i].data === name) {
               node = root.children[i];
               break;
            }
         }
      }

      return node;
   }

   private populateGroupNodes(rnode: TreeNodeModel, useVals: Array<any>) {
      if(this.groups == null || this.groups.length == 0) {
         return;
      }

      for(let i = 0; i < this.groups.length; i++) {
         let group = this.groups[i];
         let vals = group.value;
         let oldGroup = this.findFolderByName(group.name, this.root);
         let node = <TreeNodeModel> {
            label: group.name, data: group.name,
            expandedIcon: "folder-expanded-icon", collapsedIcon: "folder-collapsed-icon",
            leaf: false, children: [],
            expanded: !!oldGroup ? oldGroup.expanded : false,
            type: AssetType.FOLDER + ""
         };

         if(vals != null && vals.length > 0) {
            for(let j = 0; j < vals.length; j++) {
               const val: any = vals[j];
               let name: string = val;

               if(typeof val === "object" && val.name !== undefined) {
                  name = val.name;
               }

               useVals.push(val);
               let child = <TreeNodeModel> {
                  label: name,
                  data: val,
                  dragName: "groupTree",
                  dragData: (group.name + "^" + name),
                  leaf: true,
                  icon: "file-inverted-icon",
                  type: AssetType.DATA + ""
               };
               node.children.push(child);
            }
         }

         rnode.children.push(node);
      }
   }

   private populateOtherNodes(rnode: TreeNodeModel, useVals: Array<any>) {
      if(this.ngValues == null || this.ngValues.length == 0) {
         return;
      }

      let oldOther = this.findFolderByName("Others", this.root);
      let otherNode = <TreeNodeModel> {
         label: "_#(js:Others)", data: "Others",
         expandedIcon: "folder-expanded-icon", collapsedIcon: "folder-collapsed-icon",
         leaf: false, children: [],
         expanded: !!oldOther ? oldOther.expanded : false,
         type: AssetType.FOLDER + ""
      };

      for(let i = 0; i < this.ngValues.length; i++) {
         let val = this.ngValues[i];
         let found = false;

         for(let j = 0; j < useVals.length; j++) {
            if(val == useVals[j]) {
               found = true;
               break;
            }
         }

         if(!found) {
            let child = <TreeNodeModel> {
               label: val.toString(), data: val, leaf: true,
               dragName: "groupTree", dragData: ("Others^" + val.toString()),
               icon: "file-inverted-icon",
               type: AssetType.DATA + ""
            };
            otherNode.children.push(child);
         }
      }

      rnode.children.push(otherNode);
   }

   update() {
      this.groups.splice(0, this.groups.length);
   }

   addClick(evt: MouseEvent) {
      evt.stopPropagation();

      let dialog: NameInputDialog = ComponentTool.showDialog(this.modalService, NameInputDialog,
         (result: string) => {
            this.addNamedGroup(result);
            let nNode = this.findFolderByName(result, this.root);
            if(!!nNode) {
               nNode.expanded = true;
            }
         }, {
            backdrop: "static"
         });

      dialog.title = "_#(js:Group Name)";
      dialog.existedNames = this.getGroupNames();
   }

   deleteClick(evt: MouseEvent) {
      let nodes = this.selectNodes;

      if(nodes == null) {
         return;
      }

      let groups = this.groups;

      for(let i = 0; i < nodes.length; i++) {
         let node = nodes[i];

         for(let j = groups.length - 1; j > -1; j--) {
            if(!node.leaf && node.data == groups[j].name) {
               this.moveChildrenToOthers(node);
               this.groups.splice(j, 1);
            }
         }
      }

      this.root = this.getRootNode();
      this.selectNodes = null;
   }

   private moveChildrenToOthers(node: TreeNodeModel) {
      let childs = node.children;

      if(childs == null) {
         return;
      }

      let groups = this.groups;

      for(let i = 0; i < groups.length; i++) {
         let ogroup = groups[i];

         if(ogroup.name == "others") {
            for(let j = 0; j < childs.length; j++) {
               ogroup.value.push(childs[j].data);
            }
         }
      }
   }

   private addNamedGroup(nname: string) {
      let ngroup: GroupCondition = new GroupCondition();
      ngroup.name = nname;
      ngroup.value = [];
      this.groups.push(ngroup);
      this.root = this.getRootNode();
   }

   selectNode(nodes: any[]) {
      this.selectNodes = nodes;
   }

   dropToNode(evt: any) {
      let node: any = evt.node;

      if(node == null || this.selectNodes == null) {
         return;
      }

      if(node.type == AssetType.DATA + "") {
         let parentNode = this.tree.getParentNode(node);

         if(!!parentNode) {
            node = parentNode;
         }
      }

      if(node.type !== AssetType.FOLDER + "") {
         return;
      }

      for(let i = 0; i < this.selectNodes.length; i++) {
         this.dropValue(this.selectNodes[i].dragData, node.data,
            this.selectNodes[i].data);
      }

      this.refreshTree = true;

      if(!!this.tree) {
         this.selectNodes = [];
         this.tree.deselectAllNodes();
      }
   }

   private dropValue(dropValue: string, dropGroup: string, dropData: any) {
      if(dropValue == null) {
         return;
      }

      let idx = dropValue.indexOf("^");

      if(idx < 0) {
         return;
      }

      let groups = this.groups;
      let oldGroup = dropValue.substring(0, idx);
      let oldValue = dropValue.substring(idx + 1);

      if(oldGroup == dropGroup) {
         return;
      }

      for(let i = 0; i < groups.length; i++) {
         let group = groups[i];

         if(oldGroup == group.name) {
            this.removeDropNode(group, oldValue);
         }

         if(dropGroup == group.name) {
            this.addDropNode(group, dropData);
         }
      }
   }

   private removeDropNode(group: GroupCondition, dropValue: string) {
      let vals = group.value;

      if(vals == null) {
         return;
      }

      for(let i = group.value.length - 1; i > -1; i--) {
         if(dropValue == group.value[i].toString()) {
            group.value.splice(i, 1);
         }
      }
   }

   private addDropNode(group: GroupCondition, dropData: any) {
      let vals = group.value;

      if(vals == null) {
         group.value = [];
      }

      group.value.push(dropData);
   }

   deleteEnabled(): boolean {
      return this.selectNodes && !!this.groups &&
         this.selectNodes.every(n => !n.leaf && n.data != "Others");
   }

   private getGroupNames(): string[] {
      let groupNames: string[] = new Array<string>();
      groupNames.push("Others");

      for(let i = 0; this.groups && i < this.groups.length; i++) {
         groupNames.push(this.groups[i].name);
      }

      return groupNames;
   }

   public dragNode(event: any) {
      event.dataTransfer.effectAllowed  = "move";

      const labels: string[] = this.selectNodes.map(n => n.label);
      const image = GuiTool.createDragImage(labels);
      GuiTool.setDragImage(event, image, this.zone, this.domService).then(() => {
         if(this.refreshTree) {
            this.zone.run(() => this.root = this.getRootNode());
            this.refreshTree = false;
         }
      });
   }
}
