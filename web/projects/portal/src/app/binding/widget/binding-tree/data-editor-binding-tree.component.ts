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
import {
   Component,
   Input,
   ChangeDetectorRef,
   EventEmitter,
   SimpleChanges,
   Output,
   OnChanges,
   HostBinding
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { BindingService } from "../../services/binding.service";
import { ContextMenuActions } from "../../../widget/context-menu/context-menu-actions";
import { BindingTreeService } from "./binding-tree.service";
import { ModelService } from "../../../widget/services/model.service";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { DropdownOptions } from "../../../widget/fixed-dropdown/dropdown-options";
import { DndService } from "../../../common/dnd/dnd.service";
import { ActionsContextmenuComponent } from "../../../widget/fixed-dropdown/actions-contextmenu.component";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DataRef } from "../../../common/data/data-ref";
import { UIContextService } from "../../../common/services/ui-context.service";
import { ViewsheetClientService } from "../../../common/viewsheet-client/viewsheet-client.service";

@Component({
   selector: "data-editor-binding-tree",
   templateUrl: "data-editor-binding-tree.component.html"
})
export class DataEditorBindingTree implements OnChanges {
   @HostBinding("hidden")
   @Input() inactive: boolean;
   @Input() grayedOutFields: DataRef[];
   @Input() selectedNodes: TreeNodeModel[] = [];
   @Output() onPopUpWarning: EventEmitter<any> = new EventEmitter<any>();

   constructor(private changeDetectorRef: ChangeDetectorRef,
               private dropdownService: FixedDropdownService,
               private dialogService: NgbModal,
               private bindingService: BindingService,
               private treeService: BindingTreeService,
               private uiContextService: UIContextService,
               private modelService: ModelService,
               private dndService: DndService,
               private clientService: ViewsheetClientService)
   {
   }

   ngOnChanges(changes: SimpleChanges) {
      if(this.inactive) {
         this.changeDetectorRef.detach();
      }
      else {
         this.changeDetectorRef.reattach();
      }
   }

   get runtimeId(): string {
      return this.bindingService ? this.bindingService.runtimeId : "";
   }

   hasMenuFunction(): any {
      return (node) => this.hasMenu(node);
   }

   hasMenu(node: TreeNodeModel): boolean {
      // same as openBindingTreeContextmenu
      if(node.data && node.data.path && node.data.path.startsWith("/components/")) {
         return false;
      }

      let actions: ContextMenuActions = this.createActions([null, node, []]);
      return actions != null && actions.actions.some(group => group.visible);
   }

   openBindingTreeContextmenu(event: [MouseEvent, TreeNodeModel, TreeNodeModel[]]): void {
      if(event[1].data && event[1].data.path && event[1].data.path.startsWith("/components/")) {
         // component field, no right-click operations available
         return;
      }

      let options: DropdownOptions = {
         position: {x: event[0].clientX + 2, y: event[0].clientY + 2},
         contextmenu: true,
      };

      let contextmenu: ActionsContextmenuComponent = this.dropdownService
         .open(ActionsContextmenuComponent, options).componentInstance;
      contextmenu.sourceEvent = event[0];
      let actions: ContextMenuActions = this.createActions(event);

      if(actions != null) {
         contextmenu.actions = actions.actions;
      }
   }

   createActions(event: [MouseEvent, TreeNodeModel, TreeNodeModel[]]): ContextMenuActions {
      const selectedNode: TreeNodeModel = event[1];
      let selectedNodes: TreeNodeModel[] = event[2];

      if(selectedNodes.length == 0 && selectedNode) {
         selectedNodes = [selectedNode];
      }

      const bindingInfo: any = {
         "bindingModel": this.bindingService.getBindingModel(),
         "assemblyName": this.bindingService.assemblyName,
         "urlParams": this.bindingService.getURLParams(),
         "objectType": this.bindingService.objectType,
         "grayedOutFields": this.grayedOutFields
      };

      return this.treeService.getBindingTreeActions(selectedNode, selectedNodes,
         this.dialogService, this.modelService, this.clientService, bindingInfo);
   }

   sendRemoveColumnEvent(event: any) {
      let warningInfo: any = this.dndService.processOnDrop(event, null);

      if(!!warningInfo) {
         this.onPopUpWarning.emit(warningInfo);
      }
   }
}
