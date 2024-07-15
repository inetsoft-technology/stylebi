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
import {
   ChangeDetectorRef,
   Component,
   ElementRef,
   EventEmitter,
   Input,
   NgZone,
   OnInit,
   Output,
   ViewChild
} from "@angular/core";
import { TableTransfer } from "../../../common/data/dnd-transfer";
import { GuiTool } from "../../../common/util/gui-tool";
import { Tool } from "../../../../../../shared/util/tool";
import {
   CommandProcessor,
   ViewsheetClientService
} from "../../../common/viewsheet-client";
import { RemoveVSObjectEvent } from "../../../composer/gui/vs/objects/event/remove-vs-object-event";
import { DomService } from "../../../widget/dom-service/dom.service";
import { PlaceholderDragElementModel } from "../../../widget/placeholder-drag-element/placeholder-drag-element-model";
import { AbstractVSActions } from "../../action/abstract-vs-actions";
import { AssemblyActionFactory } from "../../action/assembly-action-factory.service";
import { CurrentSelectionActions } from "../../action/current-selection-actions";
import { ContextProvider } from "../../context-provider.service";
import { ViewsheetInfo } from "../../data/viewsheet-info";
import { MoveSelectionChildEvent } from "../../event/move-selection-child-event";
import { VSObjectModel } from "../../model/vs-object-model";
import { VSSelectionContainerModel } from "../../model/vs-selection-container-model";
import { SelectionContainerChildDragModel } from "./selection-container-child-drag-model";
import { SelectionContainerChildrenService } from "./services/selection-container-children.service";
import { InsertSelectionChildEvent } from "../../event/insert-selection-child-event";
import { TrapInfo } from "../../../common/data/trap-info";
import { VSTrapService } from "../../util/vs-trap.service";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { NavigationKeys } from "../navigation-keys";
import { FocusObjectEventModel } from "../../model/focus-object-event-model";
import { Observable, Subject, Subscription } from "rxjs";

const INSERT_CHILD_URI = "/events/viewsheet/selectionContainer/insertChild/";
const CHECK_TRAP_URI = "../api/viewsheet/objects/checkSelectionTrap";

@Component({
   selector: "vs-selection-container-children",
   templateUrl: "vs-selection-container-children.component.html",
   styleUrls: ["vs-selection-container-children.component.scss"]
})
export class VSSelectionContainerChildren extends CommandProcessor implements OnInit {
   @Input() vsInfo: ViewsheetInfo;
   @Input() runtimeId: string;
   @Input() containerRef: HTMLElement;
   @Input() placeholderDragElementModel: PlaceholderDragElementModel;
   @Input() set keyNavigation(obs: Observable<FocusObjectEventModel>) {
      this._keyNavigation = obs;

      if(obs) {
         this.focusSub = obs.subscribe((data: FocusObjectEventModel) => {
            if(data && data.focused && this.vsObject.absoluteName == data.focused.absoluteName
               && (data.key == NavigationKeys.TAB || data.key == NavigationKeys.SHIFT_TAB))
            {
               let index: number = data.index;

               if(index >= 0 && this.containerBody &&
                  this.containerBody.nativeElement.children &&
                  this.containerBody.nativeElement.children.length > index)
               {
                  this.containerBody.nativeElement.children[index].focus();
               }
            }

            let evt: FocusObjectEventModel = {
               focused: null,
               key: data.key
            };

            if(data && data.focused &&
               this.vsObject.absoluteName == data.focused.absoluteName &&
               data.index >= 0 && data.index < this.vsObject.childObjects.length)
            {
               evt.focused = Tool.clone(this.vsObject.childObjects[data.index]);
            }
            else {
               evt.focused = Tool.clone(data.focused);
            }

            this.innerKeyNavigation.next(evt);
         });
      }
   }

   @ViewChild("containerBody") containerBody: ElementRef;
   _keyNavigation: Observable<FocusObjectEventModel>;
   private focusSub: Subscription;
   innerKeyNavigation = new Subject<FocusObjectEventModel>();

   @Output() public containerChildContextMenu = new EventEmitter<{
      actions: AbstractVSActions<any>,
      event: MouseEvent
   }>();

   childDragModel: SelectionContainerChildDragModel =
      <SelectionContainerChildDragModel> { dragging: false };
   scrollbarWidth: number = GuiTool.measureScrollbars();
   childActions: AbstractVSActions<any>[] = [];
   currentSelectionActions: CurrentSelectionActions[] = [];
   mobileDevice: boolean = GuiTool.isMobileDevice();
   private _vsObject: VSSelectionContainerModel;

   get viewer(): boolean {
      return this.context.viewer || this.context.preview;
   }

   constructor(protected viewsheetClient: ViewsheetClientService,
               protected actionFactory: AssemblyActionFactory,
               protected selectionContainerChildrenService: SelectionContainerChildrenService,
               private context: ContextProvider,
               protected trapService: VSTrapService,
               protected modalService: NgbModal,
               protected changeRef: ChangeDetectorRef,
               private zone: NgZone,
               private domService: DomService)
   {
      super(viewsheetClient, zone, true);
   }

   ngOnInit(): void {
      this.selectionContainerChildrenService.onChildUpdate.subscribe((index) => {
         this.childActions[index] =
            this.actionFactory.createActions(this.vsObject.childObjects[index]);
      });

      if(this.vsObject.childObjects) {
         this.childActions = [];

         this.vsObject.childObjects.forEach((model) => {
            this.childActions.push(this.actionFactory.createActions(model));
         });
      }
   }

   @Input() set vsObject(model: VSSelectionContainerModel) {
      this._vsObject = model;
      this._vsObject.childObjects.forEach((childModel, index) => {
         this.childActions[index] = this.actionFactory.createActions(childModel);
      });

      this.currentSelectionActions = [];

      if(model.outerSelections) {
         for(let i = 0; i < model.outerSelections.length; i++) {
            this.currentSelectionActions.push(
               this.actionFactory.createCurrentSelectionActions(model));
         }
      }
   }

   get vsObject(): VSSelectionContainerModel {
      return this._vsObject;
   }

   trackByName(index: number, object: VSObjectModel): string {
      return object.absoluteName;
   }

   showContextMenu(event: MouseEvent, actions: AbstractVSActions<any>): void {
      event.preventDefault();
      event.stopPropagation();

      this.containerChildContextMenu.emit({
         actions,
         event
      });
   }

   onRemoveChild(childIndex: number): void {
      const event = new RemoveVSObjectEvent(this.vsObject.childObjects[childIndex].absoluteName);
      this.viewsheetClient.sendEvent("/events/composer/viewsheet/objects/remove", event);
      this.removeChild(childIndex);
   }

   private removeChild(childIndex: number): void {
      if(this.vsObject.childObjects.length > 1) {
         this.vsObject.childObjects.splice(childIndex, 1);
         this.childActions.splice(childIndex, 1);
      }
      else {
         this.vsObject.childObjects = [];
         this.childActions = [];
      }
   }

   onDragStart(event: any, index: number, isCurrentSelection: boolean): void {
      let dragObject: any;

      if(isCurrentSelection) {
         dragObject = this.vsObject.outerSelections[index];
         this.placeholderDragElementModel.height = this.vsObject.dataRowHeight;
         this.placeholderDragElementModel.text = dragObject.title;
      }
      else {
         dragObject = this.vsObject.childObjects[index];
         this.placeholderDragElementModel.height = dragObject.titleFormat.height;
         this.placeholderDragElementModel.text = dragObject.absoluteName;

         Tool.setTransferData(event.dataTransfer,
            {
               dragName: ["tableBinding"],
               objectName: dragObject.absoluteName,
               container: this.vsObject.absoluteName,
               dragSource: new TableTransfer("details", null, dragObject.absoluteName)
            });
      }

      const paneRect: ClientRect = this.containerRef.getBoundingClientRect();

      this.placeholderDragElementModel.top = event.pageY + 1 - paneRect.top;
      this.placeholderDragElementModel.left = event.pageX + 1 - paneRect.left;
      this.placeholderDragElementModel.width =
         GuiTool.measureText(this.placeholderDragElementModel.text, this.vsObject.objectFormat.font);
      this.placeholderDragElementModel.font = this.vsObject.objectFormat.font;
      this.placeholderDragElementModel.visible = true;

      // remove the default 'ghost' image when dragging
      const elem = new Image();
      document.body.appendChild(elem);
      GuiTool.setDragImage(event, elem, this.zone, this.domService);

      this.childDragModel = <SelectionContainerChildDragModel> {
         dragging: true,
         fromIndex: index,
         toIndex: -1,
         eventX: event.pageX,
         eventY: event.pageY,
         originalX: this.placeholderDragElementModel.left,
         originalY: this.placeholderDragElementModel.top,
         isCurrentSelection: isCurrentSelection,
         isContainerChild: true
      };
   }

   onDrag(event: any): void {
      if(this.childDragModel.dragging) {
         this.placeholderDragElementModel.top = this.childDragModel.originalY +
            (event.pageY - this.childDragModel.eventY);
         this.placeholderDragElementModel.left = this.childDragModel.originalX +
            (event.pageX - this.childDragModel.eventX);
      }
   }

   onDragEnd(event: any): void {
      this.childDragModel.dragging = false;
      this.childDragModel.isContainerChild = false;
      this.placeholderDragElementModel.visible = false;
   }

   onDrop(event: any): void {
      event.preventDefault();

      if(this.childDragModel.isContainerChild) {
         event.stopPropagation();

         if(this.childDragModel.toIndex != -1 &&
            this.childDragModel.fromIndex != this.childDragModel.toIndex)
         {
            // move fromIndex to toIndex in array
            this.viewsheetClient.sendEvent(
               "/events/selectionContainer/moveChild/" + this.vsObject.absoluteName,
               new MoveSelectionChildEvent(this.childDragModel.fromIndex,
                  this.childDragModel.toIndex,
                  this.childDragModel.isCurrentSelection));
         }
      }
      else if(this.viewer && this.vsObject.supportRemoveChild) {
         event.stopPropagation();
         let data: any = null;

         try {
            data = JSON.parse(event.dataTransfer.getData("text"));
         }
         catch(e) {
            console.warn("Invalid drop event on " + this.vsObject.objectType + ": ", e);
            return;
         }

         const dragName: string = data.dragName[0];

         if(dragName === "tableBinding") {
            const vsevent = new InsertSelectionChildEvent(this.vsObject.absoluteName,
               this.vsObject.childObjects.length, null, data.dragSource);
            const trapInfo = new TrapInfo(CHECK_TRAP_URI, "", this.viewsheetClient.runtimeId,
               vsevent);

            this.trapService.checkTrap(trapInfo,
               () => this.viewsheetClient.sendEvent(
                  INSERT_CHILD_URI + this.vsObject.absoluteName, vsevent),
               () => {},
               () => this.viewsheetClient.sendEvent(
                  INSERT_CHILD_URI + this.vsObject.absoluteName, vsevent)
            );
         }
      }
   }

   onDragOver(event: any, index: number, isCurrentSelection: boolean): void {
      // allow drop if dragging child
      if(this.childDragModel.dragging) {
         // stop propagation to dragOverContainer
         event.preventDefault();
         event.stopPropagation();

         const oindex = this.childDragModel.toIndex;

         if(this.childDragModel.isCurrentSelection == isCurrentSelection) {
            // if same type then set new index to drag over index
            this.childDragModel.toIndex = index;
         }
         else {
            if(isCurrentSelection) {
               // set to index to top of child objects
               this.childDragModel.toIndex = 0;
            }
            else {
               // set to index to bottom of outer selections
               this.childDragModel.toIndex = this.vsObject.outerSelections.length - 1;
            }
         }

         if(oindex != this.childDragModel.toIndex) {
            this.changeRef.detectChanges();
         }
      }
   }

   onDragOverContainer(event: any): void {
      if(this.childDragModel.dragging) {
         event.preventDefault();
         let toIndex = this.childDragModel.toIndex;

         if(this.childDragModel.isCurrentSelection) {
            // set to index to top of child objects
            toIndex = this.vsObject.outerSelections.length - 1;
         }
         else {
            // set to index to bottom of outer selections
            toIndex = this.vsObject.childObjects.length - 1;
         }

         if(toIndex != this.childDragModel.toIndex) {
            this.childDragModel.toIndex = toIndex;
            this.changeRef.detectChanges();
         }
      }
      else if(this.vsObject.supportRemoveChild) {
         event.preventDefault();
      }
   }

   isDropChildTop(index: number, isCurrentSelection: boolean): boolean {
      return this.childDragModel.dragging &&
         this.childDragModel.isCurrentSelection == isCurrentSelection &&
         index == this.childDragModel.toIndex && index < this.childDragModel.fromIndex;
   }

   isDropChildBottom(index: number, isCurrentSelection: boolean): boolean {
      return this.childDragModel.dragging &&
         this.childDragModel.isCurrentSelection == isCurrentSelection &&
         index == this.childDragModel.toIndex && index > this.childDragModel.fromIndex;
   }

   getAssemblyName(): string {
      return this._vsObject ? this._vsObject.absoluteName : null;
   }

   getBodyHeight(): number {
      return this.vsObject.titleVisible ?
          this.vsObject.objectFormat.height - this.vsObject.titleFormat.height : this.vsObject.objectFormat.height;
   }

   getBodyWidth(): number {
      return this.vsObject.objectFormat.width;
   }

   getBodyTop(): number {
      return this.vsObject.objectFormat.top +
         (this.vsObject.titleVisible || !this.viewer ? this.vsObject.titleFormat.height : 0);
   }

   getBodyLeft(): number {
      return this.vsObject.objectFormat.left;
   }

   getInnerWidth() {
      return this.getBodyWidth() - Tool.getMarginSize(this.vsObject.objectFormat.border.left) -
         Tool.getMarginSize(this.vsObject.objectFormat.border.right);
   }
}
