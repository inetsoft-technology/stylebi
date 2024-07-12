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
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { Router } from "@angular/router";
import { RepositoryTreeService } from "./repository-tree.service";
import { ModelService } from "../services/model.service";
import { TreeNodeModel } from "../tree/tree-node-model";
import { RepositoryBaseComponent } from "./repository-base-component";
import { ActionsContextmenuComponent } from "../fixed-dropdown/actions-contextmenu.component";
import { DropdownOptions } from "../fixed-dropdown/dropdown-options";
import { FixedDropdownService } from "../fixed-dropdown/fixed-dropdown.service";
import { RepositoryClientService } from "../../common/repository-client/repository-client.service";
import { RepositoryEntry } from "../../../../../shared/data/repository-entry";

@Component({
   selector: "repository-list",
   templateUrl: "repository-list.component.html",
   styleUrls: ["repository-list.component.scss"],
   providers: [RepositoryClientService]
})
export class RepositoryListComponent extends RepositoryBaseComponent implements OnInit {
   @Output() nodeSelected = new EventEmitter<TreeNodeModel>();
   openNodes: TreeNodeModel[];
   _root: TreeNodeModel;

   @Input() set root(root: TreeNodeModel) {
      this._root = root;

      if(root) {
         this.openNodes = [root];
      }
   }

   get root(): TreeNodeModel {
      return this._root;
   }

   constructor(protected repositoryTreeService: RepositoryTreeService,
               private repositoryClient: RepositoryClientService,
               protected router: Router,
               protected dropdownService: FixedDropdownService,
               protected modalService: NgbModal,
               protected modelService: ModelService)
   {
      super(repositoryTreeService, modalService, modelService, router);
   }

   ngOnInit() {
      this.repositoryClient.connect();
      this.repositoryClient.repositoryChanged.subscribe(() => {
         if(this.openNodes.length) {
            const node = this.openNodes[this.openNodes.length - 1];
            this.repositoryTreeService.getFolder(node.data.path).subscribe(
               (data) => {
                  node.children = data.children;
               });
         }
      });
   }

   openTreeContextmenu(event: MouseEvent, node: TreeNodeModel) {
      let options: DropdownOptions = {
         position: {x: event.clientX, y: event.clientY},
         contextmenu: true,
      };

      let contextmenu: ActionsContextmenuComponent =
         this.dropdownService.open(ActionsContextmenuComponent, options).componentInstance;
      contextmenu.sourceEvent = event;
      contextmenu.actions = this.createActions0(node, [node]);
   }

   nodeClicked(node: TreeNodeModel): void {
      if(!node.leaf) {
         this.openNodes.push(node);

         this.repositoryTreeService.getFolder(node.data.path).subscribe(
            (data) => {
               node.children = data.children;
            });
      }

      this.nodeSelected.emit(node);
   }

   upClicked(): void {
      this.openNodes.pop();
   }

   /**
    * Method for determining the css class of an entry by its type
    */
   getCSSIcon(node: TreeNodeModel): string {
      const entry: RepositoryEntry = node.data;
      return this.repositoryTreeService.getCSSIcon(entry, node.expanded);
   }
}
