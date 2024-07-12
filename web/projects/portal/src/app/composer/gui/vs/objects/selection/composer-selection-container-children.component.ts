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
   ChangeDetectorRef,
   ElementRef,
   EventEmitter,
   HostListener,
   Input,
   NgZone,
   OnDestroy,
   OnChanges,
   SimpleChanges,
   OnInit,
   Output,
   TemplateRef,
   ViewChild
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subscription } from "rxjs";
import { DragEvent } from "../../../../../common/data/drag-event";
import { AssetEntry } from "../../../../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../../../../shared/data/asset-type";
import { TrapInfo } from "../../../../../common/data/trap-info";
import { GuiTool } from "../../../../../common/util/gui-tool";
import { Tool } from "../../../../../../../../shared/util/tool";
import { ViewsheetClientService } from "../../../../../common/viewsheet-client";
import { AssemblyActionEvent } from "../../../../../common/action/assembly-action-event";
import { AssemblyActionFactory } from "../../../../../vsobjects/action/assembly-action-factory.service";
import { SelectionContainerActions } from "../../../../../vsobjects/action/selection-container-actions";
import { ChangeVSSelectionTitleCommand } from "../../../../../vsobjects/command/change-vs-selection-title-command";
import { ContextProvider } from "../../../../../vsobjects/context-provider.service";
import { InsertSelectionChildEvent } from "../../../../../vsobjects/event/insert-selection-child-event";
import { VSObjectModel } from "../../../../../vsobjects/model/vs-object-model";
import { VSSelectionContainerModel } from "../../../../../vsobjects/model/vs-selection-container-model";
import { SelectionContainerChildDragModel } from "../../../../../vsobjects/objects/selection/selection-container-child-drag-model";
import { SelectionContainerChildrenService } from "../../../../../vsobjects/objects/selection/services/selection-container-children.service";
import { VSSelectionContainerChildren } from "../../../../../vsobjects/objects/selection/vs-selection-container-children.component";
import { DomService } from "../../../../../widget/dom-service/dom.service";
import { PlaceholderDragElementModel } from "../../../../../widget/placeholder-drag-element/placeholder-drag-element-model";
import { VSTrapService } from "../../../../../vsobjects/util/vs-trap.service";
import { LayoutOptionDialogModel } from "../../../../data/vs/layout-option-dialog-model";
import { Viewsheet } from "../../../../data/vs/viewsheet";
import { AssemblyType } from "../../assembly-type";
import { ComposerObjectService } from "../../composer-object.service";
import { SelectionBaseController } from "../../../../../vsobjects/objects/selection/selection-base-controller";
import { VSSelectionListModel } from "../../../../../vsobjects/model/vs-selection-list-model";
import { DragService } from "../../../../../widget/services/drag.service";
import { EditableObjectContainer } from "../../editor/editable-object-container.component";
import { ComponentTool } from "../../../../../common/util/component-tool";
import { ComposerVsSearchService } from "../../composer-vs-search.service";

export enum DragBorderType {
   NONE = 0, // "none"
   ABOVE = 1, // "border-top"
   ALL = 2, // "border"
   BELOW = 3, // "border-bottom"
}

const INSERT_CHILD_URI = "/events/composer/viewsheet/selectionContainer/insertChild/";
const CHECK_TRAP_URI = "../api/composer/viewsheet/objects/checkSelectionTrap";

@Component({
   selector: "composer-selection-container-children",
   templateUrl: "composer-selection-container-children.component.html",
   styleUrls: ["composer-selection-container-children.component.scss"]
})
export class ComposerSelectionContainerChildren extends VSSelectionContainerChildren implements OnInit, OnDestroy, OnChanges {
   @Input() viewsheet: Viewsheet;
   @Input() containerRef: HTMLElement;
   @Input() selectionContainerRef: EditableObjectContainer;
   @Input() placeholderDragElementModel: PlaceholderDragElementModel;
   @Input() touchDevice: boolean;
   @Input() actions: SelectionContainerActions;
   @Input() childObjects: any; // only used for change detection for now
   @Output() objectChanged: EventEmitter<boolean> = new EventEmitter<boolean>();
   @Output() onAssemblyActionEvent: EventEmitter<AssemblyActionEvent<VSObjectModel>> =
      new EventEmitter<AssemblyActionEvent<VSObjectModel>>();
   @Output() onMove = new EventEmitter<{event: any, model: VSObjectModel}>();
   @Output() onResize = new EventEmitter<{event: any, model: VSObjectModel}>();
   @Output() onRefreshFormat = new EventEmitter<{event: MouseEvent, vsobject: VSObjectModel}>();
   @Output() onOpenFormatPane = new EventEmitter<VSObjectModel>();

   @ViewChild("layoutOptionDialog") layoutOptionDialog: TemplateRef<any>;
   @ViewChild("scrollbarContainer") scrollbarContainer: ElementRef;

   scrollbarWidth: number = GuiTool.measureScrollbars();
   private gutterMargin: number = 0.15;

   private model: VSSelectionContainerModel;
   private subscriptions: Subscription = new Subscription();
   private dragPlaceholderElement: boolean = false;
   dragOverBorder: number = DragBorderType.NONE;
   layoutOptionDialogModel: LayoutOptionDialogModel;
   isContainerDragover: boolean = false;
   childrenHeight: number[] = [];

   @Input()
   set vsObject(value: VSSelectionContainerModel) {
      this.model = value;
      this.currentSelectionActions = [];

      if(value) {
         if(value.outerSelections) {
            for(let i = 0; i < value.outerSelections.length; i++) {
               this.currentSelectionActions.push(
                  this.actionFactory.createCurrentSelectionActions(this.model));
            }
         }

         this.setChildrenHeight();
      }
   }

   get vsObject(): VSSelectionContainerModel {
      return this.model;
   }

   constructor(viewsheetClient: ViewsheetClientService,
               actionFactory: AssemblyActionFactory,
               selectionContainerChildrenService: SelectionContainerChildrenService,
               protected changeRef: ChangeDetectorRef,
               zone: NgZone,
               private element: ElementRef,
               modalService: NgbModal,
               private composerObjectService: ComposerObjectService,
               trapService: VSTrapService,
               context: ContextProvider,
               domService: DomService,
               private dragService: DragService,
               private composerVsSearchService: ComposerVsSearchService)
   {
      super(viewsheetClient, actionFactory, selectionContainerChildrenService, context, trapService,
            modalService, changeRef, zone, domService);
   }

   ngOnInit(): void {
      this.subscriptions.add(this.selectionContainerChildrenService.dragModelSubject
         .subscribe((dragModel: SelectionContainerChildDragModel) => {
            if(dragModel && dragModel.container == this.vsObject.absoluteName) {
               this.childDragModel = dragModel;
            }
         }));

      this.selectionContainerChildrenService.onChildUpdate.subscribe((index) => {
         this.setChildrenHeight();
      });

      this.subscriptions.add(this.composerVsSearchService.focusChange().subscribe(obj => {
         if(!this.vsObject?.childrenNames ||  this.vsObject?.childrenNames.indexOf(obj) < 0 ||
            !this.vsObject.childObjects)
         {
            return;
         }

         let scrollTop = 0;

         for(let i = 0; i < this.vsObject.childObjects.length; i++) {
            if(this.vsObject.childObjects[i].absoluteName == obj) {
               break;
            }

            scrollTop += this.childrenHeight[i];
         }

         if(this.scrollbarContainer?.nativeElement) {
            this.scrollbarContainer.nativeElement.scrollTop = scrollTop;
         }
      }));
   }

   ngOnDestroy(): void {
      this.subscriptions.unsubscribe();
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes["childObjects"]) {
         this.setChildrenHeight();
      }
   }

   getObjectTop(): number {
      return this.vsObject.objectFormat.top + this.vsObject.titleFormat.height;
   }

   setChildrenHeight() {
      this.model.childObjects.forEach((child, index) => {
         if(child.objectType === "VSSelectionList" &&
            child.containerType === "VSSelectionContainer")
         {
            const selectionObj = <VSSelectionListModel> child;
            const height = selectionObj.dropdown && SelectionBaseController.isHidden(selectionObj)
               ? selectionObj.titleFormat.height : selectionObj.objectFormat.height;

            if(this.childrenHeight[index]) {
               this.childrenHeight[index] = height;
            }
            else {
               this.childrenHeight.push(height);
            }
         }
         else {
            this.childrenHeight[index] = child.objectFormat.height;
         }
      });
   }

   onEnter(event: any): void {
      event.preventDefault();
      this.model.isDropTarget = true;

      if(!this.isDragAcceptable(this.dragService.getDragData()) &&
         this.selectionContainerRef.isEnterEnabled())
      {
         this.selectionContainerRef.dropTarget++;
         this.selectionContainerRef.updateDropTarget();
      }
   }

   onLeave(event: any): void {
      this.dragOverBorder = DragBorderType.NONE;
      this.model.isDropTarget = false;
      this.isContainerDragover = false;

      if(!this.isDragAcceptable(this.dragService.getDragData()) &&
         this.selectionContainerRef.isEnterEnabled())
      {
         this.selectionContainerRef.dropTarget--;
         this.selectionContainerRef.updateDropTarget();
      }
   }

   onContainerDragOver(event: any): void {
      event.preventDefault();
      const dragData = this.dragService.getDragData();

      if(this.isDragAcceptable(dragData)) {
         this.isContainerDragover = true;
      }
   }

   isDragAcceptable(dragData: any) {
      for(let dragName of Object.keys(dragData)) {
         const type: number = this.composerObjectService.getObjectType(dragName);

         if(type == AssemblyType.TIME_SLIDER_ASSET || type == AssemblyType.SELECTION_LIST_ASSET) {
            return true;
         }

         let data: any = null;

         try {
            data = JSON.parse(dragData[dragName]);
         }
         catch(e) {
            console.warn("Invalid drag event on " + this.vsObject.objectType + ": ", e);
            return false;
         }

         if(data.length > 0 && (data[0].type == AssetType.COLUMN ||
                data[0].type == AssetType.PHYSICAL_COLUMN))
         {
            if(!this.selectionContainerRef.isCalcDroppable(data, this.vsObject.objectType)) {
               return false;
            }

            return true;
         }
      }

      return false;
   }

   onDragOver(event: any, index: number, isCurrentSelection: boolean): void {
      event.preventDefault();

      // Pass dragging selection container children to switch
      // places with one another to vs-selection-container-children
      if(this.childDragModel.isContainerChild) {
         super.onDragOver(event, index, isCurrentSelection);
      }
      // Handle dragOver events for editable-object-container children of this container
      else {
         // Get editable-object-container for the child component
         const containerRef = event.path.find(p => p.localName == "editable-object-container");

         if(!containerRef || !containerRef.getBoundingClientRect()) {
            return;
         }

         const boundingRect = containerRef.getBoundingClientRect();
         const rectTop = boundingRect.top + (boundingRect.height * this.gutterMargin);
         const rectBottom = boundingRect.bottom - (boundingRect.height * this.gutterMargin);
         const dragModel = this.childDragModel;
         const dragAcceptable = this.isDragAcceptable(this.dragService.getDragData());

         if(!dragAcceptable) {
            return;
         }

         this.selectionContainerChildrenService.childWithBorder = index;
         dragModel.fromIndex = this.vsObject.childObjects.length;

         if(event.clientY < rectTop) { // Insert above
            dragModel.toIndex = index;
            dragModel.insert = true;
            this.dragOverBorder = DragBorderType.ABOVE;
         }
         else if(event.clientY < rectBottom) { // Replace
            dragModel.toIndex = index;
            dragModel.insert = false;
            this.dragOverBorder = DragBorderType.ALL;
         }
         else { // Insert below
            dragModel.toIndex = index + 1;
            dragModel.insert = true;
            this.dragOverBorder = DragBorderType.BELOW;
         }

         this.selectionContainerChildrenService.pushModel(dragModel);
         this.changeRef.detectChanges();
      }
   }

   @HostListener("drop", ["$event"])
   drop(event: any): void {
      event.preventDefault();
      this.model.isDropTarget = false;
      this.isContainerDragover = false;
      this.selectionContainerRef.dropTarget = 0;
      this.selectionContainerRef.updateDropTarget();

      let data: any = null;

      try {
         data = JSON.parse(event.dataTransfer.getData("text"));
      }
      catch(e) {
         console.warn("Invalid drop event on " + this.vsObject.objectType + ": ", e);
         return;
      }

      const dragName: string = data.dragName[0];
      const type: number = this.composerObjectService.getObjectType(dragName);
      const dragModel = this.selectionContainerChildrenService.childDragModel;

      if(type || data.viewsheet) {
         let vsEntry: AssetEntry = data.viewsheet && data.viewsheet.length > 0 ?
            data.viewsheet[0] : null;

         // Shapes cannot be put into tabbed interfaces
         if(type === AssemblyType.LINE_ASSET || type === AssemblyType.RECTANGLE_ASSET
            || type === AssemblyType.OVAL_ASSET || this.isShape())
         {
            return;
         }

         let selectionObject: boolean = dragName === "VSSelectionList"
            || dragName === "VSRangeSlider"
            || dragName === "VSSelectionContainer"
            || type === AssemblyType.SELECTION_LIST_ASSET
            || type === AssemblyType.TIME_SLIDER_ASSET;

         this.layoutOptionDialogModel = {
            selectedValue: selectionObject ? 1 : 0,
            object: "",
            target: this.vsObject.absoluteName,
            showSelectionContainerOption: false,
            newObjectType: type,
            vsEntry: vsEntry
         };

         if(this.vsObject.objectType === "VSSelectionContainer" && selectionObject) {
            this.layoutOptionDialogModel.showSelectionContainerOption = true;
         }

         this.openLayoutOptionDialog().then(
            (result: LayoutOptionDialogModel) => {
               if(result.selectedValue == 0) {
                  let box: ClientRect =
                     this.element.nativeElement.parentNode.getBoundingClientRect();

                  let left: number = event.pageX - box.left;
                  let top: number = event.pageY - box.top;

                  this.composerObjectService.addNewObject(
                     this.viewsheet, dragName, left, top, vsEntry);
               }
            },
            () => {}
         );
      }
      // Binding data dragged from a table component
      else if(dragName === "tableBinding") {
         if(this.vsObject.objectType === "VSRangeSlider") {
            this.composerObjectService.applyChangeBinding(this.viewsheetClient,
               this.vsObject.absoluteName, null, null, data.dragSource);
         }
         // SelectionList
         else if(this.vsObject.objectType === "VSSelectionList") {
            this.composerObjectService.checkTableTransferDataType(
               this.viewsheetClient, data.dragSource).subscribe((res) => {
               const datatype: string = res.body;

               if(datatype == "timeinstant" || datatype == "date") {
                  ComponentTool.showConfirmDateTypeBindingDialog(this.modalService).then((applyChange) => {
                     if(applyChange) {
                        this.composerObjectService.applyChangeBinding(this.viewsheetClient,
                           this.vsObject.absoluteName, null, null, data.dragSource);
                     }
                  });
               }
               else {
                  this.composerObjectService.applyChangeBinding(this.viewsheetClient,
                     this.vsObject.absoluteName, null, null, data.dragSource);
               }
            });
         }
         else if(dragModel.insert) {
            if(dragModel.fromIndex == this.vsObject.childObjects.length ||
               dragModel.fromIndex != dragModel.toIndex)
            {
               const vsevent = new InsertSelectionChildEvent(this.vsObject.absoluteName,
                  dragModel.toIndex, null, data.dragSource);
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
         else {
            const vsevent = new InsertSelectionChildEvent(this.vsObject.absoluteName,
               this.model.childObjects.length, null, data.dragSource);
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
      else if(dragName === "column" && data.column.length > 1) {
         const binding = this.composerObjectService.getDataSource(data);

         this.layoutOptionDialogModel = {
            selectedValue: 0,
            object: "column",
            target: this.vsObject.absoluteName,
            showSelectionContainerOption: false,
            newObjectType: AssemblyType.SELECTION_TREE_ASSET,
            vsEntry: null,
            columns: data.column,
         };

         this.openLayoutOptionDialog().then(
            (result: LayoutOptionDialogModel) => {
               if(result.selectedValue === 0) {
                  const vsevent = {
                     x: this.vsObject.objectFormat.left,
                     y: this.vsObject.objectFormat.top
                  };

                  this.composerObjectService.applyChangeBinding(
                     this.viewsheetClient, null, binding, vsevent);
               }
            },
            () => {}
         );
      }
      else {
         // Data Source
         let binding = this.composerObjectService.getDataSource(data);

         if(binding != null) {

            if(binding.length > 1 || binding.length > 0 &&
               binding[0].type == AssetType.PHYSICAL_TABLE)
            {
               this.layoutOptionDialogModel = {
                  selectedValue: 0,
                  object: "",
                  target: this.vsObject.absoluteName,
                  showSelectionContainerOption: false,
                  newObjectType: 0,
                  vsEntry: null
               };

               this.openLayoutOptionDialog().then(
                  (result: LayoutOptionDialogModel) => {
                     const vsevent = {
                        tab: result.selectedValue == 2,
                        x: this.vsObject.objectFormat.left,
                        y: this.vsObject.objectFormat.top
                     };

                     this.composerObjectService.applyChangeBinding(
                        this.viewsheetClient, null, binding, vsevent);
                  },
                  () => {}
               );
            }
            else if(binding.length == 1 && binding[0].type == AssetType.TABLE) {
               // ignore table, which is not well defined
            }
            else if(dragModel.insert && binding.length == 1) {
               if(dragModel.fromIndex == this.vsObject.childObjects.length ||
                  dragModel.fromIndex != dragModel.toIndex)
               {
                  const vsevent = new InsertSelectionChildEvent(this.vsObject.absoluteName,
                     dragModel.toIndex, binding);
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
            else {
               if(!this.selectionContainerRef.isCalcDroppable(binding, this.vsObject.objectType))
               {
                  return;
               }

               const vsevent = new InsertSelectionChildEvent(this.vsObject.absoluteName,
                  this.model.childObjects.length, binding);
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

      event.stopPropagation();
      this.dragOverBorder = DragBorderType.NONE;
      this.selectionContainerChildrenService.pushModel({dragging: false});
      this.selectionContainerChildrenService.childWithBorder = -1;
   }

   isShape(): boolean {
      return this.vsObject.objectType === "VSOval"
         || this.vsObject.objectType === "VSRectangle"
         || this.vsObject.objectType === "VSLine";
   }

   private openLayoutOptionDialog(): Promise<LayoutOptionDialogModel> {
      return this.modalService.open(this.layoutOptionDialog, {backdrop: "static"}).result;
   }

   select(event: MouseEvent): void {
      if(!event.ctrlKey && !event.metaKey) {
         this.viewsheet.clearFocusedAssemblies();
      }

      this.viewsheet.selectAssembly(this.vsObject);

      this.onRefreshFormat.emit({
         event: event,
         vsobject: this.vsObject
      });
   }

   childChanged(callChangeDetector: boolean) {
      this.objectChanged.emit(callChangeDetector);
   }

   moveAssembly(event: {event: any, model: VSObjectModel}): void {
      this.onMove.emit(event);
   }

   resizeAssembly(event: {event: any, model: VSObjectModel}): void {
      this.onResize.emit(event);
   }

   get childWithBorder(): number {
      return this.selectionContainerChildrenService.childWithBorder;
   }

   private processChangeVSSelectionTitleCommand(command: ChangeVSSelectionTitleCommand): void {
      for(let i = 0; i < this.model.outerSelections.length; i++) {
         if(this.model.outerSelections[i].title === command.oldTitle) {
            this.model.outerSelections[i].title = command.newTitle;
            break;
         }
      }
   }

   getBodyWidth(): number {
      return this.vsObject.objectFormat.width;
   }

   getPaddingHeight(): number {
      let height = this.childrenHeight.reduce((bodyHeight, childHeight) => {
         return bodyHeight - childHeight;
      }, this.getBodyHeight());

      height -= this.vsObject.outerSelections.length * (this.vsObject.dataRowHeight + 1);

      height -= Tool.getMarginSize(this.vsObject.objectFormat.border.top) +
         Tool.getMarginSize(this.vsObject.objectFormat.border.bottom);
      return Math.max(0, height);
   }

   getInnerWidth() {
      return this.getBodyWidth() - Tool.getMarginSize(this.vsObject.objectFormat.border.left) -
         Tool.getMarginSize(this.vsObject.objectFormat.border.right);
   }

   droppedOnChild(event: DragEvent) {
      this.isContainerDragover = false;

      if(this.dragOverBorder == DragBorderType.ALL) {
         event.stopPropagation();
      }
   }

   isSelected(): boolean {
      return this.viewsheet.isAssemblyFocused(this.vsObject);
   }

   get zIndex(): number {
      return EditableObjectContainer.calculateZIndex(this.vsObject, this.viewsheet);
   }
}
