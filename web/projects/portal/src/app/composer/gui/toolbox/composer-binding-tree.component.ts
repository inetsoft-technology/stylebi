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
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Viewsheet } from "../../data/vs/viewsheet";
import { ModelService } from "../../../widget/services/model.service";
import { BindingTreeService } from "../../../binding/widget/binding-tree/binding-tree.service";
import { VSBindingTreeActions } from "../../../binding/widget/binding-tree/vs-binding-tree-actions";
import { ActionsContextmenuComponent } from "../../../widget/fixed-dropdown/actions-contextmenu.component";
import { DropdownOptions } from "../../../widget/fixed-dropdown/dropdown-options";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { DragEvent } from "../../../common/data/drag-event";
import { DataRef } from "../../../common/data/data-ref";
import { VSDndEvent } from "../../../common/dnd/vs-dnd-event";
import { DataTransfer } from "../../../common/data/dnd-transfer";
import { ComposerObjectService } from "../vs/composer-object.service";
import { Subscription } from "rxjs";
import { VirtualScrollTreeDatasource } from "../../../widget/tree/virtual-scroll-tree-datasource";

@Component({
   selector: "composer-binding-tree",
   templateUrl: "composer-binding-tree.component.html"
})
export class ComposerBindingTree implements OnInit, OnDestroy {
   @Input() viewsheet: Viewsheet;
   @Input() grayedOutFields: DataRef[];
   @Input() useVirtualScroll: boolean;
   @Input() virtualScrollTreeDatasource: VirtualScrollTreeDatasource;
   @Output() bindingTreeChanged: EventEmitter<TreeNodeModel> = new EventEmitter<TreeNodeModel>();
   @Output() nodeExpanded: EventEmitter<TreeNodeModel> = new EventEmitter<TreeNodeModel>();
   @Output() nodeCollapsed: EventEmitter<TreeNodeModel> = new EventEmitter<TreeNodeModel>();
   private subscription = Subscription.EMPTY;

   constructor(private composerObjectService: ComposerObjectService,
               private dropdownService: FixedDropdownService,
               private dialogService: NgbModal,
               private treeService: BindingTreeService,
               private modelService: ModelService)
   {
   }

   ngOnInit(): void {
      this.subscription = this.treeService.bindingTreeChanged()
         .subscribe((root) => this.bindingTreeChanged.emit(root));
   }

   ngOnDestroy(): void {
      if(this.subscription) {
         this.subscription.unsubscribe();
      }
   }

   hasMenuFunction(): any {
      return (node) => this.hasMenu(node);
   }

   hasMenu(node: TreeNodeModel): boolean {
      const actions = new VSBindingTreeActions(this.viewsheet, node, [node],
         this.dialogService, this.treeService, this.modelService,
         null, this.grayedOutFields, false).actions;
      return actions.some(group => group.visible);
   }

   openBindingTreeContextmenu(event: [MouseEvent, TreeNodeModel, TreeNodeModel[]]): void {
      let options: DropdownOptions = {
         position: {x: event[0].clientX, y: event[0].clientY},
         contextmenu: true,
      };

      let contextmenu: ActionsContextmenuComponent = this.dropdownService
         .open(ActionsContextmenuComponent, options).componentInstance;
      contextmenu.sourceEvent = event[0];
      contextmenu.actions = new VSBindingTreeActions(this.viewsheet, event[1], event[2],
         this.dialogService, this.treeService, this.modelService,
         null, this.grayedOutFields, false).actions;
   }

   sendRemoveColumnEvent(event: DragEvent) {
      const data: any = JSON.parse(event.dataTransfer.getData("text"));
      const transfer: DataTransfer = data.dragSource;

      // transfer would be empty if the entry is dragged from tree itself
      if(transfer) {
         // Binding data dragged from a selection container child component, treat as a
         // remove event for the dragged child component
         if(data.container) {
            this.composerObjectService.removeObjects(this.viewsheet, [transfer.assembly]);
         }
         else {
            let url = "/events/" + transfer.objectType + "/dnd/removeColumns";
            this.viewsheet.socketConnection.sendEvent(url, new VSDndEvent(transfer.assembly,
               transfer, null, null));
         }
      }
   }
}
