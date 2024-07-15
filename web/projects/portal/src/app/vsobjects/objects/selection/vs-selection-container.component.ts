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
import { HttpClient, HttpParams } from "@angular/common/http";
import {
   Component,
   EventEmitter,
   Input,
   NgZone,
   OnChanges,
   OnDestroy,
   Optional,
   Output,
   SimpleChanges
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subscription } from "rxjs";
import { Tool } from "../../../../../../shared/util/tool";
import { AssemblyActionGroup } from "../../../common/action/assembly-action-group";
import { ComponentTool } from "../../../common/util/component-tool";
import { DataPathConstants } from "../../../common/util/data-path-constants";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { Viewsheet } from "../../../composer/data/vs/viewsheet";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { SelectionContainerActions } from "../../action/selection-container-actions";
import { AddVSObjectCommand } from "../../command/add-vs-object-command";
import { RefreshVSObjectCommand } from "../../command/refresh-vs-object-command";
import { RemoveVSObjectCommand } from "../../command/remove-vs-object-command";
import { RenameVSObjectCommand } from "../../command/rename-vs-object-command";
import { ContextProvider } from "../../context-provider.service";
import { AddFilterDialog } from "../../dialog/add-filter-dialog.component";
import { ChangeVSObjectTextEvent } from "../../event/change-vs-object-text-event";
import { InsertSelectionChildEvent } from "../../event/insert-selection-child-event";
import { VSObjectModel } from "../../model/vs-object-model";
import { VSSelectionContainerModel } from "../../model/vs-selection-container-model";
import { VSUtil } from "../../util/vs-util";
import { AbstractVSObject } from "../abstract-vsobject.component";
import { DataTipService } from "../data-tip/data-tip.service";
import { SelectionContainerChildrenService } from "./services/selection-container-children.service";
import { SelectionMobileService } from "./services/selection-mobile.service";
import { GuiTool } from "../../../common/util/gui-tool";
import { MaxObjectEvent } from "../../event/table/max-object-event";

const Add_FILTER_TREE_URI: string = "../api/selectioncontainer/add-filter/tree";
const INSERT_CHILD_URI = "/events/composer/viewsheet/selectionContainer/insertChild/";
const MAX_MODE_URL: string = "/events/vs/assembly/max-mode/toggle";

@Component({
   selector: "vs-selection-container",
   templateUrl: "vs-selection-container.component.html",
   styleUrls: ["vs-selection-container.component.scss"]
})
export class VSSelectionContainer extends AbstractVSObject<VSSelectionContainerModel>
   implements OnChanges, OnDestroy
{
   @Input() selected: boolean = false;
   @Input() container: Element;
   @Output() onTitleResizeMove: EventEmitter<number> = new EventEmitter<number>();
   @Output() onTitleResizeEnd: EventEmitter<any> = new EventEmitter<any>();
   @Output() onUpdateFocus = new EventEmitter<VSObjectModel>();
   @Output() onOpenFormatPane = new EventEmitter<VSSelectionContainerModel>();
   @Output() maxModeChange = new EventEmitter<{assembly: string, maxMode: boolean}>();
   public leftBorderWidth: number = 0;
   public topBorderWidth: number = 0;
   private _actions: SelectionContainerActions;
   private actionSubscription: Subscription;
   private subscriptions: Subscription = new Subscription();

   constructor(private clientService: ViewsheetClientService, private http: HttpClient,
               private selectionContainerChildrenService: SelectionContainerChildrenService,
               zone: NgZone, private modalService: NgbModal,
               protected context: ContextProvider,
               protected dataTipService: DataTipService,
               protected dropdownService: FixedDropdownService,
               @Optional() private selectionMobileService?: SelectionMobileService)
   {
      super(clientService, zone, context, dataTipService);

      if(GuiTool.isMobileDevice() && this.selectionMobileService) {
         this.subscriptions.add(this.selectionMobileService.maxSelectionChanged().subscribe(({obj, max}) => {
            if(obj?.objectType == this.model?.objectType && obj?.absoluteName == this.model.absoluteName) {
               if(this.model.maxMode != max) {
                  this.toggleMaxMode();
               }
            }
         }));
      }
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.model) {
         const border = changes.model.currentValue.objectFormat.border;
         this.leftBorderWidth = Tool.getMarginSize(border.left);
         this.topBorderWidth = Tool.getMarginSize(border.top);
      }
   }

   ngOnDestroy(): void {
      super.ngOnDestroy();

      if(this.actionSubscription) {
         this.actionSubscription.unsubscribe();
         this.actionSubscription = null;
      }

      if(this.subscriptions) {
         this.subscriptions.unsubscribe();
         this.subscriptions = null;
      }
   }

   @Input()
   set actions(value: SelectionContainerActions) {
      if(this.actionSubscription) {
         this.actionSubscription.unsubscribe();
         this.actionSubscription = null;
      }

      this._actions = value;

      if(value) {
         this.actionSubscription = value.onAssemblyActionEvent.subscribe((event) => {
            switch(event.id) {
            case "selection-container unselect-all":
               this.onUnselectAll();
               break;
            case "selection-container addfilter":
               this.addFilter();
               break;
            case "menu actions":
               VSUtil.showDropdownMenus(event.event, this.getMenuActions(), this.dropdownService);
               break;
            case "selection-container show-format-pane":
               this.onOpenFormatPane.emit(this.model);
               break;
            case "selection-container open-max-mode":
            case "selection-container close-max-mode":
               this.toggleMaxMode();
               break;
            case "more actions":
               VSUtil.showDropdownMenus(event.event, this.getMoreActions(), this.dropdownService);
               break;
            }
         });
      }
   }

   onUnselectAll(): void {
      this.clientService.sendEvent(
         "/events/selectionContainer/unselectAll/" + this.model.absoluteName);
   }

   addFilter(): void {
      let params = new HttpParams().set("vsId", this.clientService.runtimeId);

      this.http.get<TreeNodeModel>(Add_FILTER_TREE_URI, { params })
         .subscribe(
            (data: TreeNodeModel) => {
               this.openAddFilterDialog(data);
            }
         );
   }

   openAddFilterDialog(node: TreeNodeModel) {
      const dialog = ComponentTool.showDialog(this.modalService, AddFilterDialog,
         (result: TreeNodeModel[]) => {
            if(result == null) {
               return;
            }

            let columns = [];

            for(let treeNode of result) {
               columns.push(treeNode.data);
            }

            const vsevent = new InsertSelectionChildEvent(this.model.absoluteName,
               this.model.childObjects.length, null, null, columns);
            this.clientService.sendEvent(INSERT_CHILD_URI + this.model.absoluteName, vsevent);
      });

      dialog.model = node;
   }

   getActions(): AssemblyActionGroup[] {
      return this._actions && this.model.enabled && this.model.visible
         ? this._actions.showingActions : [];
   }

   getMenuActions(): AssemblyActionGroup[] {
      return this._actions ? this._actions.menuActions : [];
   }

   getMoreActions(): AssemblyActionGroup[] {
      return this._actions ? this._actions.getMoreActions() : [];
   }

   /**
    * Adds or updates an assembly object
    * @param command the command.
    */
   private processAddVSObjectCommand(command: AddVSObjectCommand): void {
      let updated: boolean = false;

      for(let i = 0; i < this.model.childObjects.length; i++) {
         if(this.model.childObjects[i].absoluteName === command.name) {
            updated = true;
            this.model.childObjects[i] =
               VSUtil.replaceObject(this.model.childObjects[i], command.model);
            break;
         }
      }

      if(!updated) {
         this.model.childObjects.push(command.model);
      }

      // force change detection in composer-selection-container-children
      this.model.childObjects = Tool.clone(this.model.childObjects);

      //make sure action and assembly component has same assembly model.
      this.model?.childObjects?.forEach((value, index) => {
         this.updateFocus(value);
         this.selectionContainerChildrenService.updateChild(index);
      });
   }

   private updateFocus(model: VSObjectModel): void {
      this.onUpdateFocus.emit(model);
   }

   /**
    * Remove an assembly object
    * @param command the command.
    */
   private processRemoveVSObjectCommand(command: RemoveVSObjectCommand): void {
      for(let i in this.model.childObjects) {
         if(this.model.childObjects[i].absoluteName === command.name) {
            this.model.childObjects.splice(parseInt(i, 10), 1);
            break;
         }
      }
   }

   /**
    * Refresh an assembly object
    * @param command the command.
    */
   private processRefreshVSObjectCommand(command: RefreshVSObjectCommand): void {
      for(let i = 0; i < this.model.childObjects.length; i++) {
         if(this.model.childObjects[i].absoluteName === command.info.absoluteName) {
            this.model.childObjects[i] =
               VSUtil.replaceObject(this.model.childObjects[i], command.info);
            this.selectionContainerChildrenService.updateChild(i);
            this.updateFocus(this.model.childObjects[i]);
            break;
         }
      }
   }

   /**
    * Rename an assembly object
    * @param command the command.
    */
   private processRenameVSObjectCommand(command: RenameVSObjectCommand): void {
      for(let i = 0; i < this.model.childObjects.length; i++) {
         if(this.model.childObjects[i].absoluteName === command.oldName) {
            this.model.childObjects[i].absoluteName = command.newName;
            break;
         }
      }
   }

   updateTitle(newTitle: string) {
      if(!this.viewer) {
         let event: ChangeVSObjectTextEvent = new ChangeVSObjectTextEvent(
            this.model.absoluteName, newTitle);

         this.clientService.sendEvent("/events/composer/viewsheet/objects/changeTitle", event);
      }
   }

   selectTitle(): void {
      // select vsobject before select parts
      if(!this.selected && !this.vsInfo.formatPainterMode) {
         return;
      }

      this.model.selectedRegions = [DataPathConstants.TITLE];
   }

   isTitleSelected(): boolean {
      return this.model.selectedRegions
         && this.model.selectedRegions.indexOf(DataPathConstants.TITLE) != -1;
   }

   titleResizeMove(event: any): void {
      this.onTitleResizeMove.emit(event.rect.height);
   }

   titleResizeEnd(): void {
      this.onTitleResizeEnd.emit();
   }

   private toggleMaxMode(): void {
      let event: MaxObjectEvent = new MaxObjectEvent(this.model.absoluteName,
         this.model.maxMode ? null : this.container);

      this.viewsheetClient.sendEvent(MAX_MODE_URL, event);
      this.maxModeChange.emit(
         {assembly: this.model.absoluteName, maxMode: !this.model.maxMode});
   }
}
