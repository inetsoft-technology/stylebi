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
import { Component, EventEmitter, Input, Output, ViewChild } from "@angular/core";
import { IdentityId } from "../../../../../../../em/src/app/settings/security/users/identity-id";
import { IdentityTreeComponent } from "../../../../widget/identity-tree/identity-tree.component";
import { TreeNodeModel } from "../../../../widget/tree/tree-node-model";
import { IdentityType } from "../../../../../../../shared/data/identity-type";
import { Tool } from "../../../../../../../shared/util/tool";
import { ExpandStringDirective } from "../../../../widget/expand-string/expand-string.directive";
import { SearchComparator } from "../../../../widget/tree/search-comparator";

@Component({
   selector: "execute-as-dialog",
   templateUrl: "execute-as-dialog.component.html",
   styleUrls: ["execute-as-dialog.component.scss"]
})
export class ExecuteAsDialog {
   @Input() type: number;
   @Output() onCommit: EventEmitter<{name: string, type: number}> =
      new EventEmitter<{name: string, type: number}>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @ViewChild("searchIdentityTree") searchIdentityTree: IdentityTreeComponent;
   @ViewChild("identityTree") identityTree: IdentityTreeComponent;
   private readonly MAX_NODES = 1000;
   _users: IdentityId[] = [];
   _groups: IdentityId[] = [];
   selectedNode: TreeNodeModel;
   searchMode: boolean = false;
   searchText: string = "";
   usersNode: TreeNodeModel = <TreeNodeModel> {
      label: "_#(js:Users)",
      data: "",
      type: String(IdentityType.USERS),
      leaf: false,
      expanded: false,
      children: []
   };

   groupsNode: TreeNodeModel = <TreeNodeModel> {
      label: "_#(js:Groups)",
      data: "",
      type: String(IdentityType.GROUPS),
      leaf: false,
      expanded: false,
      children: []
   };

   usersSearchTree: TreeNodeModel = <TreeNodeModel> {
      label: "_#(js:Users)",
      data: "",
      type: String(IdentityType.USERS),
      leaf: false,
      expanded: true,
      children: []
   };

   groupsSearchTree: TreeNodeModel = <TreeNodeModel> {
      label: "_#(js:Groups)",
      data: "",
      type: String(IdentityType.GROUPS),
      leaf: false,
      expanded: false,
      children: []
   };

   @Input() set users(_users: IdentityId[]) {
      this._users = _users;
      this.usersNode.children = this._users
         .slice(0, this.MAX_NODES)
         .map((user) => {
            return <TreeNodeModel>{
               label: user?.name,
               data: user,
               type: String(IdentityType.USER),
               leaf: true,
               expanded: false
            };
         });

      if(this._users.length > this.MAX_NODES) {
         this.usersNode.children.push(this.getLimitWarningNode());
      }
   }

   get users() {
      return this._users;
   }

   @Input() set groups(_groups: IdentityId[]) {
      this._groups = _groups;
      this.groupsNode.children = this._groups
         .slice(0, this.MAX_NODES)
         .map((group) => {
            let groupNode = this.getGroupNode(group);

            return <TreeNodeModel>{
               label: groupNode.label,
               data: groupNode.value,
               type: String(IdentityType.GROUP),
               leaf: true,
               expanded: false
            };
         });

      if(this._groups.length > this.MAX_NODES) {
         this.groupsNode.children.push(this.getLimitWarningNode());
      }
   }

   get groups() {
      return this._groups;
   }

   nodeSelected(nodes: TreeNodeModel[]): void {
      this.selectedNode = null;

      if(nodes && (nodes[0].type === String(IdentityType.USER) ||
         nodes[0].type === String(IdentityType.GROUP)))
      {
         this.selectedNode = nodes[0];
      }
   }

   searchUsers(): void {
      if(this.searchText && this.searchText.length > 0) {
         this.initSearchTree();
         this.selectedNode = null;
         this.searchTree.expanded = true;
         this.searchIdentityTree.searchMode = true;
         this.searchText = this.searchText.trim();
         this.searchIdentityTree.nodeExpanded(this.searchTree);
         this.searchMode = true;
      }
      else if(this.searchMode) {
         this.selectedNode = null;
         this.identityTree.tree.selectedNodes = [];
         this.searchIdentityTree.searchMode = false;
         this.searchMode = false;
      }
   }

   initSearchTree() {
      const searchTree = this.searchTree;
      let children;

      if(this.type == IdentityType.USER) {
         children = this.users
            .filter((user) => user.name.indexOf(this.searchText) != -1)
            .map((user) => {
               return <TreeNodeModel>{
                  label: user.name,
                  data: user,
                  type: String(IdentityType.USER),
                  leaf: true,
                  expanded: false
               };
            }).sort((a, b) => new SearchComparator(this.searchText).searchSort(a, b));
      }
      else {
         children = this.groups
            .filter((group) => group.name.indexOf(this.searchText) != -1)
            .map((group) => {
               let groupNode = this.getGroupNode(group);

               return <TreeNodeModel> {
                  label: groupNode.label,
                  data: groupNode.value,
                  type: String(IdentityType.GROUP),
                  leaf: true,
                  expanded: false
               };
            });
      }

      const limitApplied = children.length > this.MAX_NODES;

      if(limitApplied) {
         children = children.slice(0, this.MAX_NODES);
         children.push(this.getLimitWarningNode());
      }

      searchTree.children = children;
   }

   private getGroupNode(group: IdentityId): any {
      return {label: group.name, value: group};
   }

   close(): void {
      this.onCancel.emit("cancel");
   }

   ok(): void {
      if(this.selectedNode && (this.selectedNode.type === IdentityType.USER.toString()) ||
         this.selectedNode.type === IdentityType.GROUP.toString())
      {
         this.onCommit.emit({
            name: (<IdentityId> this.selectedNode.data).name,
            type: parseInt(this.selectedNode.type)
         });
      }
   }

   getRoot(): TreeNodeModel {
      return this.type == IdentityType.USER ? this.usersNode : this.groupsNode;
   }

   get searchTree() {
      return this.type == IdentityType.USER ? this.usersSearchTree : this.groupsSearchTree;
   }

   getLimitWarningNode(): TreeNodeModel {
      const label = ExpandStringDirective.expandString(
         "_#(js:schedule.task.options.userTreeLimited)", [this.MAX_NODES + ""]);
      return <TreeNodeModel>{
         label: label,
         leaf: true,
         expanded: false,
         icon: "alert-circle-icon",
         cssClass: "alert alert-danger disable-actions"
      };
   }
}
