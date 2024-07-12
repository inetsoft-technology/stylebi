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
   AfterViewInit,
   ChangeDetectorRef,
   Component,
   ElementRef,
   EventEmitter,
   HostListener,
   Input,
   OnChanges,
   OnDestroy,
   OnInit,
   Output,
   Renderer2,
   SimpleChanges,
   TemplateRef,
   ViewChild
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subscription } from "rxjs";
import { AssetEntry } from "../../../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../../../shared/data/asset-type";
import { Tool } from "../../../../../../../shared/util/tool";
import { SourceInfoType } from "../../../../binding/data/source-info-type";
import { AssemblyActionEvent } from "../../../../common/action/assembly-action-event";
import { DragEvent } from "../../../../common/data/drag-event";
import { Line } from "../../../../common/data/line";
import { Point } from "../../../../common/data/point";
import { Rectangle } from "../../../../common/data/rectangle";
import { XSchema } from "../../../../common/data/xschema";
import { ComponentTool } from "../../../../common/util/component-tool";
import { GuiTool } from "../../../../common/util/gui-tool";
import { ViewsheetClientService } from "../../../../common/viewsheet-client";
import { VsWizardEditModes } from "../../../../vs-wizard/model/vs-wizard-edit-modes";
import { VsWizardModel } from "../../../../vs-wizard/model/vs-wizard-model";
import { AssemblyActionFactory } from "../../../../vsobjects/action/assembly-action-factory.service";
import { MoveSelectionChildEvent } from "../../../../vsobjects/event/move-selection-child-event";
import { VSImageModel } from "../../../../vsobjects/model/output/vs-image-model";
import { VSTextModel } from "../../../../vsobjects/model/output/vs-text-model";
import { SelectionChildModel } from "../../../../vsobjects/model/selection-container-child-model";
import { VSChartModel } from "../../../../vsobjects/model/vs-chart-model";
import { VSGroupContainerModel } from "../../../../vsobjects/model/vs-group-container-model";
import { VSLineModel } from "../../../../vsobjects/model/vs-line-model";
import { VSObjectModel } from "../../../../vsobjects/model/vs-object-model";
import { VSRangeSliderModel } from "../../../../vsobjects/model/vs-range-slider-model";
import { VSSelectionContainerModel } from "../../../../vsobjects/model/vs-selection-container-model";
import { VSSelectionListModel } from "../../../../vsobjects/model/vs-selection-list-model";
import { VSShapeModel } from "../../../../vsobjects/model/vs-shape-model";
import { VSViewsheetModel } from "../../../../vsobjects/model/vs-viewsheet-model";
import { AbstractVSObject } from "../../../../vsobjects/objects/abstract-vsobject.component";
import { AdhocFilterService } from "../../../../vsobjects/objects/data-tip/adhoc-filter.service";
import { DataTipService } from "../../../../vsobjects/objects/data-tip/data-tip.service";
import { MiniToolbarService } from "../../../../vsobjects/objects/mini-toolbar/mini-toolbar.service";
import { SelectableObject } from "../../../../vsobjects/objects/selectable-object";
import { SelectionBaseController } from "../../../../vsobjects/objects/selection/selection-base-controller";
import { SelectionContainerChildrenService } from "../../../../vsobjects/objects/selection/services/selection-container-children.service";
import { VSSelectionContainer } from "../../../../vsobjects/objects/selection/vs-selection-container.component";
import { VSSelection } from "../../../../vsobjects/objects/selection/vs-selection.component";
import { VSObjectMoveHandle } from "../../../../vsobjects/objects/vsobject-move-handle";
import { VSUtil } from "../../../../vsobjects/util/vs-util";
import { AssetTreeService } from "../../../../widget/asset-tree/asset-tree.service";
import { PlaceholderDragElementModel } from "../../../../widget/placeholder-drag-element/placeholder-drag-element-model";
import { DragService } from "../../../../widget/services/drag.service";
import { DialogService } from "../../../../widget/slide-out/dialog-service.service";
import { LayoutOptionDialogModel } from "../../../data/vs/layout-option-dialog-model";
import { Viewsheet } from "../../../data/vs/viewsheet";
import { LineAnchorService } from "../../../services/line-anchor.service";
import { AssemblyType } from "../assembly-type";
import { ComposerObjectService, KeyEventAdapter } from "../composer-object.service";
import { DragBorderType } from "../objects/selection/composer-selection-container-children.component";
import { AbstractActionComponent } from "./abstract-action-component";
import { BaseTableModel } from "../../../../vsobjects/model/base-table-model";
import { ScaleService } from "../../../../widget/services/scale/scale-service";
import { ComposerVsSearchService } from "../composer-vs-search.service";
import { VSTabModel } from "../../../../vsobjects/model/vs-tab-model";
import { AssemblyAction } from "../../../../common/action/assembly-action";

@Component({
   selector: "editable-object-container",
   templateUrl: "editable-object-container.component.html",
   styleUrls: ["editable-object-container.component.scss"]
   //changeDetection: ChangeDetectionStrategy.OnPush
})
export class EditableObjectContainer extends AbstractActionComponent
   implements OnChanges, OnInit, OnDestroy, AfterViewInit
{
   vsObject: VSObjectModel;

   @Input() viewsheet: Viewsheet;
   @Input() touchDevice: boolean;
   @Input() deployed: boolean;
   @Input() vsPaneRef: HTMLElement;
   @Input() placeholderDragElementModel: PlaceholderDragElementModel;
   @Input() dragOverBorder: number = DragBorderType.NONE;
   @Input() selectionBorderOffset: number;
   @Input() containerBounds: any;

   @Output() onAssemblyActionEvent = new EventEmitter<AssemblyActionEvent<VSObjectModel>>();
   @Output() objectChanged = new EventEmitter<boolean>();
   @Output() public onOpenEmbeddedViewsheet = new EventEmitter<string>();

   @Output() onMove = new EventEmitter<{event: any, model: VSObjectModel}>();
   @Output() onResize = new EventEmitter<{event: any, model: VSObjectModel}>();
   @Output() onOpenEditPane: EventEmitter<VSObjectModel> = new EventEmitter<VSObjectModel>();
   @Output() onOpenWizardPane = new EventEmitter<VsWizardModel>();
   @Output() onRefreshFormat = new EventEmitter<any>();
   @Output() onPopupNotifications = new EventEmitter<any>();
   @Output() maxModeChange = new EventEmitter<{assembly: string, maxMode: boolean}>();
   @Output() onOpenFormatPane = new EventEmitter<VSObjectModel>();

   @ViewChild("layoutOptionDialog") layoutOptionDialog: TemplateRef<any>;
   @ViewChild("objectComponent") objectComponent: AbstractVSObject<any>;
   @ViewChild("objectEditor") objectEditor: ElementRef;

   selectionChildModel: SelectionChildModel;
   layoutOptionDialogModel: LayoutOptionDialogModel;
   selected: boolean = false;
   contextMenuVisible = false;
   dropTarget: number = 0;
   isDropTarget = false;

   dragInteractionEnabled: boolean = false;
   dropInteractionEnabled: boolean = false;
   moveHandle: string | null = null;

   resizeEnabled: boolean = false;
   resizeTopEdge = true;
   resizeBottomEdge = true;

   multiSelection: boolean = false;
   compositeResizeable: boolean = false;

   activeHandlesLineDrag: Element[] = [];
   activeLineHandles: Element[] = [];
   objectEditorElements: Element[] = [];
   closeObjectEditorElements: Element[] = [];
   nonActiveHandles: Element[] = [];
   containerScrollLeft = 0;
   readonly dropzoneAccept: string = ".interact-group-element";
   private subscriptions = new Subscription();
   private keyEventAdapter: KeyEventAdapter;
   dragTop: number = 0;
   dragLeft: number = 0;
   private dragPlaceholderElement: boolean = false;
   private cubeString: string = "__inetsoft_cube_";
   private isCtrl: boolean = false;
   private dragObj: VSObjectModel = null;
   private missingHeight: number = 0;
   private missingWidth: number  = 0;
   private isShift: boolean = false;
   private shadowObj: any;
   private assemblySelectedTimer: any;
   private olineInfo: {startTop: number, startLeft: number, endTop: number, endLeft: number};

   variableValuesFunction: (objName: string) => string[] =
      (objName: string) =>  this.getVariableValues(objName);

   constructor(private miniToolbarService: MiniToolbarService,
               private element: ElementRef,
               private composerObjectService: ComposerObjectService,
               private viewsheetClient: ViewsheetClientService,
               private selectionContainerChildrenService: SelectionContainerChildrenService,
               private modalService: NgbModal,
               private changeDetectorRef: ChangeDetectorRef,
               private renderer: Renderer2,
               private dragService: DragService,
               private dataTipService: DataTipService,
               private adhocFilterService: AdhocFilterService,
               private dialogService: DialogService,
               private lineAnchorService: LineAnchorService,
               actionFactory: AssemblyActionFactory,
               public scaleService: ScaleService,
               private composerVsSearchService: ComposerVsSearchService)
   {
      super(actionFactory);
      this.keyEventAdapter = {
         getElement: () => this.element,
         getViewsheet: () => this.viewsheet,
         getVsObject: () => this.vsObject,
         isSelected: () => this.selected,
      };
      this.composerObjectService.addKeyEventAdapter(this.keyEventAdapter);
   }

   @Input()
   set vsObjectModel(_vsObject: VSObjectModel) {
      if(this.vsObject && _vsObject) {
         _vsObject.interactionDisabled = this.vsObject.interactionDisabled;
      }

      this.vsObject = _vsObject;

      if(_vsObject) {
         let isInTab = false;

         if(_vsObject.container) {
            const container = this.viewsheet.getAssembly(_vsObject.container);
            isInTab = container && container.objectType === "VSTab";
         }

         // resizing bottom of a dropdown is meaningless. it should resize the title
         const dropdown = (<any> _vsObject).dropdownCalendar || (<any> _vsObject).dropdown;
         this.resizeTopEdge = !isInTab && !dropdown;
         this.resizeBottomEdge = !dropdown;
      }

      // if group removed (ungroup), children shouldn't be forced to interactable
      if(!this.vsObject.container) {
         if(!this.selected) {
            this.disableInteraction();
         }
         else if(this.selected && !this.isLocked()) {
            this.enableInteraction();
         }
      }

      this.updateActions(this.vsObject, this.viewsheet);
      this.moveHandle = this.vsObject != null ?
         VSObjectMoveHandle.getMoveHandle(_vsObject.objectType) : null;
   }

   get zIndex(): number {
      return EditableObjectContainer.calculateZIndex(this.vsObject, this.viewsheet);
   }

   // if an object has a container, its zIndex is that of its container
   public static calculateZIndex(vsObject: VSObjectModel, viewsheet: Viewsheet): number {
      if((vsObject.objectType === "VSTable" || vsObject.objectType === "VSCrosstab" ||
         vsObject.objectType === "VSCalcTable") && (<BaseTableModel> vsObject).resizingCell)
      {
         return 9999;
      }

      let zIndex = vsObject.dragZIndex || vsObject.objectFormat.zIndex;

      for(let container = vsObject.container; container; ) {
         let containerObj = viewsheet.getAssembly(container);

         if(containerObj) {
            zIndex += containerObj.objectFormat.zIndex;
            container = containerObj.container;
         }
         else {
            break;
         }
      }

      if((<any> vsObject).dropdown && !SelectionBaseController.isHidden(<any> vsObject) ||
         (<any> vsObject).dropdownCalendar && (<any> vsObject).calendarsShown)
      {
         zIndex += 9999;
      }

      return zIndex;
   }

   @Input()
   set selectionChild(index: number) {
      this.selectionChildModel = <SelectionChildModel> {
         index: index,
         container: this.vsObject.container
      };
   }

   get selectionChildModelJson(): string {
      return this.selectionChildModel ? JSON.stringify(this.selectionChildModel) : null;
   }

   get viewsheetModel(): VSViewsheetModel {
      return this.vsObject as VSViewsheetModel;
   }

   get lineModel(): VSLineModel {
      return this.vsObject as VSLineModel;
   }

   get searchDisplayed(): boolean {
      const obj = this.vsObject as any;
      return !!obj && !!obj.searchDisplayed;
   }

   get visible(): boolean {
      let show = this.vsObject.active || this.selectionChildModel ? true : false;

      if(this.composerVsSearchService.isSearchMode() && this.composerVsSearchService.searchString) {
         show  = show || this.composerVsSearchService.assemblyVisible(this.vsObject);
      }

      return show;
   }

   get isSearchResults(): boolean {
      if(this.searchMode) {
         return this.composerVsSearchService.matchName(this.vsObject?.absoluteName);
      }

      return false;
   }

   get searchMode(): boolean {
      return this.composerVsSearchService.isSearchMode() && !!this.composerVsSearchService.searchString;
   }

   get isSearchFocus(): boolean {
      return this.composerVsSearchService.isFocusAssembly(this.vsObject?.absoluteName);
   }

   getVariableValues(objName: string): string[] {
      return VSUtil.getVariableList(this.viewsheet.vsObjects, objName);
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes.vsPaneRef) {
         this.subscriptions.add(this.miniToolbarService.addContainerEvents(
            this.vsPaneRef,
            (e) => this.containerScrollLeft = e.target.scrollLeft,
            () => {
               if(this.selectionChildModel || this.hasMiniToolbar()) {
                  this.containerBounds = this.vsPaneRef.getBoundingClientRect();
               }
            }
         ));
      }

      this.updateDropTarget();
   }

   updateDropTarget(): void {
      this.isDropTarget = !this.isShape() && (!!this.dropTarget && !this.vsObject.container ||
         (this.vsObject.objectType === "VSSelectionContainer" &&
            (<VSSelectionContainerModel>this.vsObject).isDropTarget)) &&
         this.dragService.currentlyDragging;
   }

   ngOnInit(): void {
      this.subscriptions.add(this.viewsheet.focusedAssemblies.subscribe((focusedAssemblies) => {
         this.assemblySelectedTimer = setTimeout(() => {
            this.assemblySelected(focusedAssemblies);
            this.changeDetectorRef.detectChanges();
         }, 0);
      }));

      if(!!this.vsPaneRef) {
         this.containerScrollLeft = this.vsPaneRef.scrollLeft;
      }
   }

   ngAfterViewInit(): void {
      if(this.objectEditor) {
         this.lineAnchorService.addEditorName(this.objectEditor, this.vsObject.absoluteName);
      }
   }

   private assemblySelected(focusedAssemblies: Array<any>): void {
      this.multiSelection = focusedAssemblies.length > 1;

      if(this.viewsheet.isFocused) {
         this.dropInteractionEnabled = true;

         if(this.viewsheet.isAssemblyFocused(this.vsObject.absoluteName)) {
            this.selected = true;

            if(this.vsObject.objectType == "VSImage") {
               let object: VSImageModel = <VSImageModel> this.vsObject;

               if(!object.locked) {
                  this.enableInteraction();
               }
               else {
                  this.disableInteraction();
               }
            }
            else if(this.isShape()) {
               let object: VSShapeModel = <VSShapeModel> this.vsObject;

               if(!object.locked) {
                  this.enableInteraction();
               }
               else {
                  this.disableInteraction();
               }
            }
            else if(this.vsObject.container &&
                    (this.vsObject.containerType !== "VSTab" &&
                     this.vsObject.containerType !== "VSGroupContainer") &&
                    this.viewsheet.isAssemblyFocused(this.vsObject.container))
            {
               this.disableInteraction();
            }
            else {
               this.enableInteraction();
            }

            const parentFocused: boolean =
               this.viewsheet.isParentAssemblyFocused(this.vsObject);

            if(this.vsObject.grouped && !parentFocused) {
               const group: VSGroupContainerModel = <VSGroupContainerModel>
                  this.viewsheet.getAssembly(this.vsObject.container);

               if(group) {
                  // if creating a new group, select the group assembly and clear selection
                  // of children.
                  if(this.viewsheet.newGroup) {
                     this.viewsheet.newGroup = false;
                     this.viewsheet.selectOnlyAssembly(group);
                     group.interactionDisabled = false;
                  }
                  else {
                     this.viewsheet.selectAssembly(group);
                     group.interactionDisabled = true;
                     // trigger listener in ViewsheetPane.ngOnInit to check for
                     // unfocused group container. (59681)
                     this.viewsheet.focusedAssembliesChanged();
                  }
               }
            }
            else if(this.vsObject.grouped && parentFocused) {
               this.multiSelection = focusedAssemblies.length > 2;

               if(!this.multiSelection) {
                  this.resizeEnabled = true;
               }
            }

            if(this.vsObject.objectType === "VSTab") {
               let isSingleTab = true;

               focusedAssemblies.forEach((assembly) => {
                  if(assembly !== this.vsObject) {
                     if(assembly.objectType === "VSTab") {
                        isSingleTab = false;
                     }
                     else if(!assembly.container ||
                             assembly.container !== this.vsObject.absoluteName) {
                        isSingleTab = false;
                     }
                  }
               });

               this.compositeResizeable = isSingleTab;
            }
         }
         else if(!this.selectionChildModel &&
                 this.viewsheet.isParentAssemblyFocused(this.vsObject))
         {
            this.selected = false;

            if(this.viewsheet.currentFocusedAssemblies.length === 1) {
               const container = this.viewsheet.getAssembly(this.vsObject.container);

               if(!this.dragInteractionEnabled && !container.interactionDisabled) {
                  this.enableInteraction();
               }
               else if(this.dragInteractionEnabled && container.interactionDisabled) {
                  this.disableInteraction();
               }
            }
            // if container and another child is selected, make this not interactable. (58837)
            else if(this.viewsheet.currentFocusedAssemblies.length > 1) {
               this.disableInteraction();
            }
         }
         else {
            if(this.selected) {
               /*
                * This is a hack since we get changed after checked errors if the model
                * is updated in the selected setter input. This subscription is run,
                * change detection happens in the calc table component and the abstract
                * context menu component, selected is passed as an input and the model
                * is modified, then dev mode check no changes happens and throws
                */
               if(this.vsObject.objectType === "VSTable" ||
                  this.vsObject.objectType === "VSCrosstab" ||
                  this.vsObject.objectType === "VSCalcTable" ||
                  this.vsObject.objectType === "VSChart")
               {
                  (this.objectComponent as unknown as SelectableObject).clearSelection();
               }

               this.vsObject.selectedRegions = [];
            }

            this.selected = false;
            this.disableInteraction();
         }
      }

      this.changeDetectorRef.detectChanges();
   }

   ngOnDestroy() {
      this.subscriptions.unsubscribe();
      this.unsubscribeAll();
      this.composerObjectService.removeKeyEventAdapter(this.keyEventAdapter);
      this.lineAnchorService.removeEditorName(this.objectEditor);
      clearTimeout(this.assemblySelectedTimer);
   }

   onDragStart(event: any): void {
      this.isCtrl = event.ctrlKey;
      this.isShift = event.shiftKey;
      this.setMovingResizing(true);
      const highestZIndex = this.viewsheet.vsObjects
         .map(o => o.objectFormat.zIndex)
         .reduce((prev, curr) => Math.max(prev, curr));
      this.vsObject.dragZIndex = this.vsObject.objectFormat.zIndex + highestZIndex;

      if(event.ctrlKey) {
         this.dragObj = Tool.clone(this.vsObject);
         this.viewsheet.vsObjects.push(this.dragObj);
      }
      else if(event.shiftKey) {
         this.shadowObj = Tool.clone(this.vsObject);
         this.viewsheet.vsObjects.push(this.shadowObj);
         this.dragObj = this.vsObject;
      }
      else {
         this.dragObj = this.vsObject;
      }

      this.vsObject.dragObj = this.dragObj;

      if(!!this.selectionChildModel) {
         this.dragPlaceholderElement = true;
         this.viewsheet.currentFocusedAssemblies = [ this.vsObject ];

         // set place holder drag element top and left
         const box: ClientRect = this.containerBounds;
         this.placeholderDragElementModel.top = event.pageY - box.top + this.vsPaneRef.scrollTop;
         this.placeholderDragElementModel.left = event.pageX - box.left + this.vsPaneRef.scrollLeft;
         this.placeholderDragElementModel.width =
            GuiTool.measureText(this.vsObject.absoluteName, this.vsObject.objectFormat.font);
         this.placeholderDragElementModel.height = this.vsObject.objectFormat.height;
         this.placeholderDragElementModel.text = this.vsObject.absoluteName;
         this.placeholderDragElementModel.font = this.vsObject.objectFormat.font;
         this.placeholderDragElementModel.visible = true;

         // set child drag model
         this.selectionContainerChildrenService.pushModel({
            dragging: true,
            fromIndex: this.selectionChildModel.index,
            toIndex: -1,
            isCurrentSelection: false,
            container: this.selectionChildModel.container
         });
      }

      this.dragTop = 0;
      this.dragLeft = 0;
   }

   getMovedPosition(x: number, y: number): Point {
      let objectPosition: any = this.dragPlaceholderElement ?
         this.placeholderDragElementModel :
         this.isShift ? this.shadowObj.objectFormat : this.dragObj.objectFormat;
      let opoint: Point = new Point(objectPosition.left, objectPosition.top);
      let pt: Point = new Point(x, y);

      if(this.isShift) {
         if(Math.abs(x - opoint.x) > Math.abs(y - opoint.y)) {
            pt.x = x;
            pt.y = opoint.y;
         }
         else {
            pt.y = y;
            pt.x = opoint.x;
         }
      }

      return pt;
   }

   onDragMove(event: any): void {
      // when vs is zoomed in, need to scale the movement or it is out of sync with cursor
      const scale: number = this.viewsheet.scale ? 1 / this.viewsheet.scale : 1;
      let dy: number = event.dy * scale;
      let dx: number = event.dx * scale;
      let objectPosition: any = this.dragPlaceholderElement ?
         this.placeholderDragElementModel : this.dragObj.objectFormat;
      let currentPosition: Point = this.getMovedPosition(objectPosition.left + dx,
         objectPosition.top + dy);

      this.dragTop = currentPosition.y;
      this.dragLeft = currentPosition.x;
      objectPosition.left = currentPosition.x;
      objectPosition.top = currentPosition.y;

      if(this.dragPlaceholderElement || this.vsObject.objectType === "VSSelectionContainer") {
         if(!this.dragPlaceholderElement) {
            this.changeDetectorRef.detectChanges();
         }

         this.objectChanged.emit(true);
      }

      this.onMove.emit({event: event, model: this.dragObj});
   }

   onDragEnd(): void {
      let isCopy: boolean = this.isCtrl;
      let isShift: boolean = this.isShift;
      this.isCtrl = false;
      this.isShift = false;
      this.setMovingResizing(false);
      this.vsObject.dragZIndex = null;

      if(this.dragPlaceholderElement) {
         const dragModel = this.selectionContainerChildrenService.childDragModel;
         dragModel.dragging = false;
         this.selectionContainerChildrenService.pushModel(dragModel);

         if(Math.abs(this.dragTop) > 10 || Math.abs(this.dragLeft) > 10) {
            this.vsObject.objectFormat.top = this.placeholderDragElementModel.top;
            this.vsObject.objectFormat.left = this.placeholderDragElementModel.left;
            this.composerObjectService.moveFromContainer(this.viewsheet, this.vsObject);
         }

         this.placeholderDragElementModel.visible = false;
         this.dragPlaceholderElement = false;

         if(this.vsObject.container) {
            this.viewsheet.currentFocusedAssemblies = [ this.vsObject.container ];
         }

         return;
      }

      if(this.vsObject.container) {
         const container = this.viewsheet.getAssembly(this.vsObject.container);

         //if moving container, it will handle moving child objects
         if(this.viewsheet.isParentAssemblyFocused(this.vsObject) &&
            !container.interactionDisabled)
         {
            return;
         }
         else if(this.isOutside(this.vsObject, container) && !isCopy) {
            this.composerObjectService.moveFromContainer(this.viewsheet, this.vsObject);
            this.dragObj = this.vsObject.dragObj = null;
            this.onMove.emit(null);
            return;
         }
      }

      if(isCopy) {
         this.composerObjectService.copyObject(this.viewsheet, this.dragObj);
         this.removeCopies();
      }
      else if(isShift) {
         this.removeCopies();
         this.shadowObj = null;
         this.composerObjectService.moveObject(this.viewsheet, this.dragObj);
      }
      else {
         this.composerObjectService.moveObject(this.viewsheet, this.vsObject);
      }

      this.dragObj = this.vsObject.dragObj = null;
      this.onMove.emit(null);

      if(this.vsObject.objectType == "VSLine") {
         this.objectEditorElements.forEach((elem: Element) => {
            this.lineAnchorService.unregisterLineAnchor(elem);
         });
      }
   }

   // remove the copies of assemblies added for ctrl-drag. (61069)
   private removeCopies() {
      const existing = [];

      for(let i = 0; i < this.viewsheet.vsObjects.length; i++) {
         if(existing.includes(this.viewsheet.vsObjects[i].absoluteName)) {
            this.viewsheet.vsObjects.splice(i, 1);
            i--;
         }

         existing.push(this.viewsheet.vsObjects[i].absoluteName);
      }
   }

   // check if object is completely outside of container
   private isOutside(vsObject: VSObjectModel, container: VSObjectModel): boolean {
      const r1 = new Rectangle(vsObject.objectFormat.left,
                               vsObject.objectFormat.top,
                               vsObject.objectFormat.width,
                               vsObject.objectFormat.height);
      const r2 = new Rectangle(container.objectFormat.left,
                               container.objectFormat.top,
                               container.objectFormat.width,
                               container.objectFormat.height);

      return !r1.intersects(r2);
   }

   onResizeStart(event: any): void {
      // When resizing, deselect all other items.
      this.viewsheet.currentFocusedAssemblies = [this.vsObject];
      this.setMovingResizing(true);

      if(event) {
         event.stopPropagation();
      }

      // In the case of auto sizing for VSText, we need to make sure to size from
      // the real size target size, rather than the object size.
      if(this.vsObject.objectType == "VSText" && (<VSTextModel> this.vsObject).autoSize) {
         this.vsObject.objectFormat.height = event.target.clientHeight;
      }
   }

   onResizeMove(event: any): void {
      if((this.vsObject.objectType == "VSRangeSlider" ||
          this.vsObject.objectType == "VSSelectionList") &&
         this.vsObject.containerType == "VSSelectionContainer" &&
         (<VSSelectionListModel | VSRangeSliderModel> this.vsObject).hidden)
      {
         this.onTitleResizeMove(event.rect.height);
         return;
      }

      let applyX = true;
      let applyY = true;

      if(event.interaction && event.interaction.pointers && event.interaction.pointers.length) {
         const mouseEvent = event.interaction.pointers[0];

         const bodyRect = document.body.getBoundingClientRect();
         const paneRect = this.vsPaneRef.getBoundingClientRect();
         const paneScreenRect = {
            top: paneRect.top - bodyRect.top,
            bottom: paneRect.top - bodyRect.top + this.vsPaneRef.clientHeight,
            left: paneRect.left - bodyRect.left,
            right: paneRect.left - bodyRect.left + this.vsPaneRef.clientWidth
         };

         // if(mouseEvent.offsetX > this.vsPaneRef.clientWidth) {
         if(mouseEvent.pageX > paneScreenRect.right) {
            applyX = event.deltaRect.left >= 0 && event.deltaRect.right >= 0;
         }
         // else if(mouseEvent.offsetX < this.vsPaneRef.offsetLeft) {
         else if(mouseEvent.pageX < paneScreenRect.left) {
            applyX = event.deltaRect.left <= 0 && event.deltaRect.right <= 0;
         }

         // if(mouseEvent.offsetY > this.vsPaneRef.clientHeight) {
         if(mouseEvent.pageY > paneScreenRect.bottom) {
            applyY = event.deltaRect.top >= 0 && event.deltaRect.bottom >= 0;
         }
         // else if(mouseEvent.offsetY < this.vsPaneRef.offsetTop) {
         else if(mouseEvent.pageY < paneScreenRect.top) {
            applyY = event.deltaRect.top <= 0 && event.deltaRect.bottom <= 0;
         }
      }

      // when vs is zoomed in, need to scale the movement or it is out of sync with cursor
      const scale: number = this.viewsheet.scale ? 1 / this.viewsheet.scale : 1;
      let dx: number = event.dx;
      let dy: number = event.dy;

      if(event.shiftKey && (this.vsObject.objectType === "VSOval" ||
                            this.vsObject.objectType === "VSRectangle"))
      {
         dx = dy = Math.max(dx, dy);
      }

      if(event.deltaRect.width != 0 && applyX) {
         // in some cases, delta rect only provides the direction of the change, i.e. -1, 0, 1
         const dir = event.deltaRect.width / Math.abs(event.deltaRect.width);
         this.vsObject.objectFormat.width += Math.abs(dx) * scale * dir;
      }

      if(event.deltaRect.height != 0 && applyY) {
         // in some cases, delta rect only provides the direction of the change, i.e. -1, 0, 1
         const dir = event.deltaRect.height / Math.abs(event.deltaRect.height);
         this.vsObject.objectFormat.height += Math.abs(dy) * scale * dir;
      }

      if(this.vsObject.objectFormat.width < 1) {
         this.vsObject.objectFormat.width = 1;
      }

      if(this.vsObject.objectFormat.height < 1) {
         this.vsObject.objectFormat.height = 1;
      }

      // shift while resizing oval causes it to become a circle
      if(event.shiftKey && (this.vsObject.objectType === "VSOval" ||
                            this.vsObject.objectType === "VSRectangle"))
      {
         let diff = this.vsObject.objectFormat.height - this.vsObject.objectFormat.width;
         let min = Math.min(this.vsObject.objectFormat.height, this.vsObject.objectFormat.width);
         this.vsObject.objectFormat.height = min;
         this.vsObject.objectFormat.width = min;

         //keep track of the offset due to using shift so that the oval will snap back
         //when you let go of the shift key
         if(diff > 0) {
            this.missingHeight += diff;
         }
         else {
            this.missingWidth -= diff;
         }
      }
      else {
         this.vsObject.objectFormat.height += this.missingHeight;
         this.vsObject.objectFormat.width += this.missingWidth;
         this.missingHeight = 0;
         this.missingWidth = 0;
      }

      // Move when resizing from top or left edges
      if(event.deltaRect.left != 0 && applyX) {
         // in some cases, delta rect only provides the direction of the change, i.e. -1, 0, 1
         const dir = event.deltaRect.left / Math.abs(event.deltaRect.left);
         this.vsObject.objectFormat.left += Math.abs(event.dx) * scale * dir;
      }

      if(event.deltaRect.top != 0 && applyY) {
         // in some cases, delta rect only provides the direction of the change, i.e. -1, 0, 1
         const dir = event.deltaRect.top / Math.abs(event.deltaRect.top);
         this.vsObject.objectFormat.top += Math.abs(event.dy) * scale * dir;
      }

      this.changeDetectorRef.detectChanges();
      // force change detection for OnPush comp
      this.objectComponent.resized();

      if(this.vsObject.objectType === "VSSelectionContainer") {
         this.objectChanged.emit(true);
      }

      this.onResize.emit({event: event, model: this.vsObject});
   }

   onResizeEnd(): void {
      if((this.vsObject.objectType === "VSRangeSlider" ||
          this.vsObject.objectType === "VSSelectionList") &&
          this.vsObject.containerType === "VSSelectionContainer" &&
          (<VSSelectionListModel | VSRangeSliderModel> this.vsObject).hidden)
      {
         this.onTitleResizeEnd();
         return;
      }

      this.setMovingResizing(false);
      this.composerObjectService.resizeObject(this.viewsheet, this.vsObject);

      if(this.vsObject.objectType === "VSSelectionList") {
         const selection = this.objectComponent as unknown as VSSelection;
         const cols = Math.floor(this.vsObject.objectFormat.width / selection.cellWidth);
         selection.updateColumns(cols);
      }

      this.onResize.emit(null);
   }

   onDropzoneEnter(event: any): void {
      let dropzoneElement = event.target;

      // shapes and selection container children are not valid drop targets
      // children and containers of selected objects are not valid drop zones
      if(dropzoneElement.classList.contains("interact-group-element") &&
         dropzoneElement.classList.contains("interact-drop-zone"))
      {
         if(!dropzoneElement.classList.contains("container-child")) {
            let targetObject = this.vsObject.grouped ?
               this.viewsheet.getAssembly(this.vsObject.container) : this.vsObject;
            targetObject.dropZone = true;
         }
         else {
            let selectionChildModel: SelectionChildModel =
               JSON.parse(dropzoneElement.getAttribute("data-selection-child-model"));

            // if child of same container, set up selectionChildModel
            if(selectionChildModel && selectionChildModel.container == this.vsObject.container) {
               let dragModel = this.selectionContainerChildrenService.childDragModel;
               dragModel.toIndex = selectionChildModel.index;
               this.selectionContainerChildrenService.pushModel(dragModel);
            }
         }
      }
      //if dragging selection container child over other children
      else if(this.dragPlaceholderElement &&
         dropzoneElement.classList.contains("current-selection") &&
         dropzoneElement.getAttribute("data-container") == this.vsObject.container)
      {
         // move to top, set toIndex to 0
         let dragModel = this.selectionContainerChildrenService.childDragModel;
         dragModel.toIndex = 0;
         this.selectionContainerChildrenService.pushModel(dragModel);
      }
   }

   onDropzoneLeave(event: any): void {
      let targetObject = this.vsObject.grouped ?
         this.viewsheet.getAssembly(this.vsObject.container) : this.vsObject;
      targetObject.dropZone = false;

      if(this.dragPlaceholderElement) {
         let dragModel = this.selectionContainerChildrenService.childDragModel;
         dragModel.toIndex = -1;
         this.selectionContainerChildrenService.pushModel(dragModel);
      }
   }

   onDropzoneDrop(event: any): void {
      const dragSource = event.relatedTarget;
      const dropTarget = event.target;

      // if drop on container child of same container
      if(dragSource.classList.contains("container-child")) {
         let selectionChildModel: SelectionChildModel =
            JSON.parse(dragSource.getAttribute("data-selection-child-model"));
         let dropChildModel: SelectionChildModel =
            JSON.parse(dropTarget.getAttribute("data-selection-child-model"));

         // if child of same container, drop
         if(selectionChildModel &&
            selectionChildModel.container == this.vsObject.container)
         {
            let dragModel = this.selectionContainerChildrenService.childDragModel;
            this.viewsheet.socketConnection.sendEvent(
               "/events/selectionContainer/moveChild/" + selectionChildModel.container,
               new MoveSelectionChildEvent(dragModel.fromIndex, dropChildModel.index,
               false));
         }

         return;
      }
      // drop on current selection of same container
      else if(dragSource.classList.contains("current-selection") &&
         dragSource.getAttribute("data-container") == this.vsObject.container)
      {
         let dragModel = this.selectionContainerChildrenService.childDragModel;
         this.viewsheet.socketConnection.sendEvent(
            "/events/selectionContainer/moveChild/" + this.vsObject.container,
            new MoveSelectionChildEvent(dragModel.fromIndex, 0, false));
         return;
      }
      // shapes and parent objects are not valid drop zones
      else if(!dropTarget.classList.contains("interact-group-element") ||
         !dropTarget.classList.contains("interact-drop-zone"))
      {
         return;
      }

      let targetObject = this.vsObject.grouped ?
         this.viewsheet.getAssembly(this.vsObject.container) : this.vsObject;
      targetObject.dropZone = false;
      const name: string = dragSource.getAttribute("data-name");

      const targetRect = dropTarget.getBoundingClientRect();
      const sourceRect = dragSource.getBoundingClientRect();
      const dragEvent = event.dragEvent;
      const sourceRect0 = {
         left: sourceRect.left - dragEvent.dx,
         right: sourceRect.right - dragEvent.dx,
         top: sourceRect.top - dragEvent.dy,
         bottom: sourceRect.bottom - dragEvent.dy
      };

      let object: VSObjectModel = this.vsObject;
      let targetType: string = object.objectType;
      let objectType: string = dragSource.getAttribute("data-type");
      let selectionObject: boolean = objectType === "VSSelectionList"
         || objectType === "VSRangeSlider"
         || objectType === "VSSelectionContainer";

      // Bug #20613 If the drag source is already overlapping target before this move,
      // don't show layout option dialog, only show dialog at the initial overlapping move.
      if(!(targetRect.right < sourceRect0.left || targetRect.left > sourceRect0.right ||
           targetRect.bottom < sourceRect0.top || targetRect.top > sourceRect0.bottom) &&
         !(targetType === "VSSelectionContainer" && selectionObject))
      {
         return;
      }

      // Shapes cannot be in tabs or selection containers
      if(this.vsObject.objectType != "VSOval"
         && this.vsObject.objectType != "VSRectangle"
         && this.vsObject.objectType != "VSLine")
      {
         if(this.vsObject.container) {
            object = this.viewsheet.getAssembly(this.vsObject.container) || this.vsObject;
         }

         this.layoutOptionDialogModel = {
            selectedValue: 0,
            object: name,
            target: object.absoluteName,
            showSelectionContainerOption: false,
            vsEntry: null
         };

         if(targetType === "VSSelectionContainer" && selectionObject) {
            this.layoutOptionDialogModel.showSelectionContainerOption = true;
            this.layoutOptionDialogModel.selectedValue = 1;
            this.layoutOptionDialogModel.target = this.vsObject.absoluteName;
         }

         this.openLayoutOptionDialog().then(
            () => {
               //Do nothing
            },
            () => {
               this.viewsheet.socketConnection.sendEvent("/events/undo");
            }
         );
      }
   }

   //if the line handle is being dragged close enough to the object, its handles should appear
   draggingCloseEnough(lineHandle: Element, elem: Element, isHandle: boolean = false): boolean {
      let withInPixel: number = isHandle ? 1 : 25;
      let lineRect = lineHandle.getBoundingClientRect();
      let elemRect = elem.getBoundingClientRect();

      let lineRectX = lineRect.left;
      let lineRectY = lineRect.top;

      //not within 25 px of the element in the x direction(when is not hanle element)
      if(!(lineRectX >= elemRect.left - withInPixel &&
           lineRectX <= elemRect.left + elemRect.width + withInPixel))
      {
         return false;
      }

      //not within 25 px of the element in the y direction(when is not hanle element)
      if(!(lineRectY >= elemRect.top - withInPixel &&
           lineRectY <= elemRect.top + elemRect.height + withInPixel))
      {
         return false;
      }

      return true;
   }

   onLineDragMove(): void {
      // adds highlight to handles between line handles and selected objects
      for(let i = 0; i < this.activeHandlesLineDrag.length; i++) {
         for(let j = 0; j < this.activeLineHandles.length; j++) {
            let activeHandle = this.activeHandlesLineDrag[i];
            let activeHandleRect = activeHandle.getBoundingClientRect();
            let lineHandle = this.activeLineHandles[j];
            let lineHandleRect = lineHandle.getBoundingClientRect();
            let diffX = Math.abs(lineHandleRect.left - 4 - activeHandleRect.left);
            let diffY = Math.abs(lineHandleRect.top - 4 - activeHandleRect.top);

            if(diffX < 1 && diffY < 1) {
               activeHandle.classList.add("bd-highlight");
               lineHandle.classList.add("bd-highlight");
            }

            if(diffX >= 1 && diffY >= 1 && activeHandle.classList.contains("bd-highlight")
               && lineHandle.classList.contains("bd-highlight"))
            {
               activeHandle.classList.remove("bd-highlight");
               lineHandle.classList.remove("bd-highlight");
            }
         }
      }

      // adds highlight to handles between line handles and non-selected objects
      for(let i = 0; i < this.nonActiveHandles.length; i++) {
         for(let j = 0; j < this.activeLineHandles.length; j++) {

            let nonActiveHandle = this.nonActiveHandles[i];
            let nonActiveHandleRect = nonActiveHandle.getBoundingClientRect();
            let lineHandle = this.activeLineHandles[j];
            let lineHandleRect = lineHandle.getBoundingClientRect();
            let diffX = Math.abs(lineHandleRect.left - 4 - nonActiveHandleRect.left);
            let diffY = Math.abs(lineHandleRect.top - 4 - nonActiveHandleRect.top);

            if(diffX < 1 && diffY < 1) {
               nonActiveHandle.classList.add("bd-highlight");
               lineHandle.classList.add("bd-highlight");
            }

            if(diffX >= 1 && diffY >= 1 && nonActiveHandle.classList.contains("bd-highlight")
               && lineHandle.classList.contains("bd-highlight"))
            {
               nonActiveHandle.classList.remove("bd-highlight");
               lineHandle.classList.remove("bd-highlight");
            }
         }
      }

      let closeEnoughEditorElements = this.objectEditorElements.filter((objEditor) => {
         return this.activeLineHandles
            .map((lineHandle) => this.draggingCloseEnough(lineHandle, objEditor))
            .some(val => val);
      });

      let notCloseEnoughAnymoreElements = this.objectEditorElements.filter((objEditor) => {
         return this.activeLineHandles
            .map((lineHandle) => this.draggingCloseEnough(lineHandle, objEditor))
            .every( val => !val);
      });

      notCloseEnoughAnymoreElements.forEach((elem) => {
         //add inactive tags from non-selected handles that exist
         elem.classList.remove("active");
         elem.classList.remove("close-enough");
      });

      this.closeObjectEditorElements = [];
      this.nonActiveHandles = [];

      closeEnoughEditorElements.forEach( (elem) => {
         //remove inactive tags from non-selected handles that exist
         const handles = elem.querySelectorAll(".not-selected");

         for(let i = 0; i < handles.length; i++) {
            let handle = handles.item(i);
            handle.classList.remove("dragover-handle-border");
            this.nonActiveHandles.push(handle);
         }

         let hasOne = false;

         for(let i = 0; i < handles.length; i++) {
            let handle = handles.item(i);
            const displayHandleBorder = this.activeLineHandles.filter((lineHandle) =>
               this.draggingCloseEnough(lineHandle, handle, true));

            if(displayHandleBorder.length > 0) {
               const end = displayHandleBorder[0].classList.contains("right");
               this.lineAnchorService.registerLineAnchor(this.vsObject.absoluteName, elem,
                                                         end, handle);
               handle.classList.add("dragover-handle-border");
               hasOne = true;
            }
         }

         if(!hasOne) {
            this.lineAnchorService.unregisterLineAnchor(elem);
         }

         elem.classList.add("active");
         elem.classList.add("close-enough");
         this.closeObjectEditorElements.push(elem);
      });
   }

   onLineStartDragMove(event: any): void {
      let vsLine: VSLineModel = <VSLineModel> this.vsObject;
      let dx: number = event.dx * (1 / this.viewsheet.scale);
      let dy: number = event.dy * (1 / this.viewsheet.scale);

      this.olineInfo.startLeft += dx;
      this.olineInfo.startTop += dy;
      vsLine.startLeft = this.olineInfo.startLeft;
      vsLine.startTop = this.olineInfo.startTop;

      if(event.shiftKey) {
         this.snapLine(true);
      }

      this.onLineDragMove();
   }

   onLineDragEnd(): void {
      this.activeHandlesLineDrag.forEach( (element) => element.classList.remove("bd-highlight"));
      this.activeHandlesLineDrag = [];
      this.activeLineHandles.forEach((element) => element.classList.remove("bd-highlight"));
      this.activeLineHandles = [];

      //remove active status from all handles that are not selected
      let handles = document.querySelectorAll(".not-selected");

      for(let i = 0; i < handles.length; i++) {
         let handle = handles.item(i);
         handle.parentElement.classList.remove("active");
         handle.parentElement.classList.remove("close-enough");
         handle.classList.remove("bd-highlight");
         handle.classList.remove("dragover-handle-border");
      }
   }

   onLineStartDragEnd(): void {
      this.endAnchorDrag();
      this.composerObjectService.updateLine(this.viewsheet, this.vsObject);
      this.onLineDragEnd();
   }

   onLineEndDragMove(event: any): void {
      let vsLine: VSLineModel = <VSLineModel> this.vsObject;
      let dx: number = event.dx * (1 / this.viewsheet.scale);
      let dy: number = event.dy * (1 / this.viewsheet.scale);

      this.olineInfo.endLeft += dx;
      this.olineInfo.endTop += dy;
      vsLine.endLeft = this.olineInfo.endLeft;
      vsLine.endTop = this.olineInfo.endTop;

      if(event.shiftKey) {
         this.snapLine(false);
      }

      this.onLineDragMove();
   }

   private snapLine(start: boolean) {
      let vsLine: VSLineModel = <VSLineModel> this.vsObject;
      // diff between width and height
      const w = this.olineInfo.startLeft - this.olineInfo.endLeft;
      const h = this.olineInfo.startTop - this.olineInfo.endTop;
      const diff = Math.abs(Math.abs(w) - Math.abs(h));
      const min = Math.min(Math.abs(w), Math.abs(h));

      // 45 digrees
      if(diff < min / 2 && Math.abs(w) > 3 && Math.abs(h) > 3) {
         if(start) {
            vsLine.startLeft = vsLine.endLeft + min * (w / Math.abs(w));
            vsLine.startTop = vsLine.endTop + min * (h / Math.abs(h));
         }
         else {
            vsLine.endLeft = vsLine.startLeft + min * (-w / Math.abs(w));
            vsLine.endTop = vsLine.startTop + min * (-h / Math.abs(h));
         }
      }
      // vertical
      else if(Math.abs(w) < Math.abs(h)) {
         if(start) {
            vsLine.startLeft = vsLine.endLeft;
         }
         else {
            vsLine.endLeft = vsLine.startLeft;
         }
      }
      // horizontal
      else {
         if(start) {
            vsLine.startTop = vsLine.endTop;
         }
         else {
            vsLine.endTop = vsLine.startTop;
         }
      }
   }

   onLineEndDragEnd(): void {
      this.endAnchorDrag();
      this.composerObjectService.updateLine(this.viewsheet, this.vsObject);
      this.onLineDragEnd();
   }

   onLineDragBegin(event: any): void {
      let activeElements = document.querySelectorAll(".active");

      for(let i = 0; i < activeElements.length; i++) {
         let activeElement = activeElements.item(i);
         let activeElementHandles = activeElement.querySelectorAll(".handle");

         for(let j = 0; j < activeElementHandles.length; j++) {
            let activeElementHandle = activeElementHandles.item(j);
            this.activeHandlesLineDrag.push(activeElementHandle);
         }
      }

      let activeLineElements = document.querySelectorAll(".line.handle");

      for(let i = 0; i < activeLineElements.length; i++) {
         this.activeLineHandles.push(activeLineElements.item(i));
      }

      this.objectEditorElements = [];
      let editorElements = document.querySelectorAll(".object-editor");

      for(let i = 0; i < editorElements.length; i++) {
         if(!editorElements.item(i).classList.contains("active")) {
            this.objectEditorElements.push(editorElements.item(i));
         }
      }

      const vsLine = this.vsObject as VSLineModel;
      this.olineInfo = {
         startTop: vsLine.startTop,
         startLeft: vsLine.startLeft,
         endTop: vsLine.endTop,
         endLeft: vsLine.endLeft
      };
   }

   preventMouseEvents(event: any): void {
      // NO-OP, just capture events so that they don't propagate to the assembly component
      event.preventDefault();
   }

   isShape(): boolean {
      return this.vsObject.objectType == "VSOval"
         || this.vsObject.objectType == "VSRectangle"
         || this.vsObject.objectType == "VSLine";
   }

   isFormAssembly(): boolean {
      return this.vsObject.objectType == "VSSlider"
         || this.vsObject.objectType == "VSSpinner"
         || this.vsObject.objectType == "VSCheckBox"
         || this.vsObject.objectType == "VSRadioButton"
         || this.vsObject.objectType == "VSComboBox"
         || this.vsObject.objectType == "VSTextInput"
         || this.vsObject.objectType == "VSSubmit"
         || this.vsObject.objectType == "VSUpload";
   }

   isPreventResize(): boolean {
      return this.vsObject.interactionDisabled ||
         this.isViewsheet() || this.vsObject.objectType == "VSGroupContainer" ||
         (<any> this.vsObject).adhocFilter || (<any> this.vsObject).editing ||
         (this.vsObject.objectType == "VSChart" && this.vsObject.sheetMaxMode);
   }

   isPreventDrag(): boolean {
      return this.vsObject.interactionDisabled || (<any> this.vsObject).adhocFilter ||
         (<any> this.vsObject).editing  ||
         (this.vsObject.objectType == "VSChart" && this.vsObject.sheetMaxMode);
   }

   isViewsheet(): boolean {
      return this.vsObject.objectType == "VSViewsheet";
   }

   isLocked(): boolean {
      return (this.isShape() && (<VSShapeModel> this.vsObject).locked) ||
         (this.vsObject.objectType == "VSImage" && (<VSImageModel> this.vsObject).locked);
   }

   hasScript(): boolean {
      return this.actions && this.actions.scriptAction && this.actions.scriptAction.visible();
   }

   conditionAction(): AssemblyAction {
      let conditionAction = null;

      this.actions.menuActions.forEach((group) => {
         group.actions.forEach((action) => {
            if(action.id() === "chart conditions" || action.id() === "table conditions" ||
               action.id() === "calc-table conditions" || action.id() === "crosstab conditions" ||
               action.id() === "text conditions" || action.id() === "gauge conditions" ||
               action.id() === "image conditions")
            {
               conditionAction = action;
            }
         });
      });

      return conditionAction;
   }

   hasSlideout(): boolean {
      return this.dialogService.hasSlideout(this.vsObject.absoluteName);
   }

   // check if any slideout pane currently open (visible or collapsed) for this assembly
   showSlideout() {
      this.dialogService.showSlideoutFor(this.vsObject.absoluteName);
   }

   isDragBorderTop(): boolean {
      return this.dragOverBorder === DragBorderType.ABOVE;
   }

   isDragBorderBottom(): boolean {
      return this.dragOverBorder === DragBorderType.BELOW;
   }

   isDragBorderAll(): boolean {
      return this.dragOverBorder === DragBorderType.ALL;
   }

   isDragBorder(): boolean {
      return this.isDragBorderTop() || this.isDragBorderBottom() || this.isDragBorderAll();
   }

   isInteractDropZone(): boolean {
      // self, children and containers of selected objects are not valid drop zones
      return !this.viewsheet.isAssemblyFocused(this.vsObject) &&
         !this.viewsheet.isParentAssemblyFocused(this.vsObject) &&
         !this.viewsheet.isChildAssemblyFocused(this.vsObject);
   }

   contextMenuOpen(event: MouseEvent) {
      this.contextMenuVisible = true;
      this.select(event, false);
      this.miniToolbarService.hiddenFreeze(this.vsObject?.absoluteName);
   }

   select(event: MouseEvent, selectAll: boolean): void {
      if(this.vsObject.container || selectAll) {
         // Don't select container when clicking on container child
         event.stopPropagation();
      }

      if(event && (<any> event).ignoreClick) {
         return;
      }

      const oselected = this.selected;

      if(!this.selected || this.vsObject.objectType == "VSGroupContainer" &&
         !event.ctrlKey && !event.shiftKey && !selectAll)
      {
         if(!event.ctrlKey && !event.metaKey && !event.shiftKey) {
            this.viewsheet.selectOnlyAssembly(this.vsObject);
         }
         else {
            this.viewsheet.selectAssembly(this.vsObject);
         }
      }
      // Do not ctrl deselect object with inner selectable regions,
      // because actions could conflict
      else if(this.selected && !event.shiftKey &&
              event.ctrlKey && (!this.hasMultipleSelectRegions() || selectAll))
      {
         this.viewsheet.deselectAssembly(this.vsObject);
      }

      // if clicking on a child of a group container, switch interaction between this and parent
      if(this.vsObject.grouped && this.vsObject.container) {
         const container = this.viewsheet.getAssembly(this.vsObject.container);

         if(container) {
            if(!oselected) {
               container.interactionDisabled = true;
               this.viewsheet.focusedAssembliesChanged();
            }
            else {
               container.interactionDisabled = false;

               /* @by ashleystankovits 1/23/2023
               Exclude selection objects from being deselected, as regions of selection
               object should be hover-able while in a groupcontainer
                */
               if(this.vsObject.objectType !== "VSSelectionContainer" &&
                  this.vsObject.objectType !== "VSSelectionTree" &&
                  this.vsObject.objectType !== "VSSelectionList" &&
                  (this.vsObject.selectedRegions == null ||
                     this.vsObject.selectedRegions.length == 0))
               {
                  this.viewsheet.deselectAssembly(this.vsObject);
               }
            }
         }
      }

      // still need to refresh formats and status bar if select table cell etc.
      if(this.selected) {
         this.onRefreshFormat.emit(event);
      }
   }

   onEnter(event: DragEvent): void {
      event.preventDefault();

      if(this.isEnterEnabled()) {
         this.dropTarget++;
         this.updateDropTarget();
      }
   }

   onLeave(event: DragEvent): void {
      if(this.isEnterEnabled()) {
         this.dropTarget--;
         this.updateDropTarget();
      }
   }

   private enableInteraction(): void {
      this.dragInteractionEnabled = true;
      this.resizeEnabled = true;
   }

   private disableInteraction(): void {
      this.dragInteractionEnabled = false;
      this.resizeEnabled = false;
   }

   @HostListener("drop", ["$event"])
   drop(event: any): void {
      // allow dropping through on shape
      if(this.vsObject.objectType == "VSRectangle" ||
         this.vsObject.objectType == "VSLine" ||
         this.vsObject.objectType == "VSOval")
      {
         return;
      }

      this.dropTarget = 0;
      this.updateDropTarget();
      let data: any = null;

      try {
         data = JSON.parse(event.dataTransfer.getData("text"));
      }
      catch(e) {
         console.warn("Invalid drop event on " + this.vsObject.objectType + ": ", e);
         return;
      }

      const dragName: string = data.dragName[0];

      if(this.isDragBorderAll() ||
         (!this.vsObject.container ||
          (this.vsObject.containerType == "VSTab" ||
           this.vsObject.containerType == "VSGroupContainer") && dragName == "column") &&
         !this.vsObject.inEmbeddedViewsheet && this.dragOverBorder === DragBorderType.NONE)
      {
         event.preventDefault();

         let type: number = this.composerObjectService.getObjectType(dragName);

         // Object dragged over.
         if(type || data.viewsheet) {
            let vsEntry: AssetEntry = data.viewsheet && data.viewsheet.length > 0 ?
               data.viewsheet[0] : null;

            // Shapes cannot be put into tabbed interfaces
            if(type == AssemblyType.LINE_ASSET || type == AssemblyType.RECTANGLE_ASSET
               || type == AssemblyType.OVAL_ASSET || this.isShape())
            {
               return;
            }

            this.layoutOptionDialogModel = {
               selectedValue: 0,
               object: "",
               target: this.vsObject.absoluteName,
               showSelectionContainerOption: false,
               newObjectType: type,
               vsEntry: vsEntry
            };

            let selectionObject: boolean = dragName === "VSSelectionList"
               || dragName === "VSRangeSlider"
               || dragName === "VSSelectionContainer"
               || type === AssemblyType.SELECTION_LIST_ASSET
               || type === AssemblyType.TIME_SLIDER_ASSET;

            if(this.vsObject.objectType === "VSSelectionContainer" && selectionObject) {
               this.layoutOptionDialogModel.showSelectionContainerOption = true;
            }

            if(vsEntry && vsEntry.identifier === this.viewsheet.id) {
               // if trying to add self into vs, show error dialog
               ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
                  "_#(js:common.selfUseForbidden)");
               return;
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
            if(this.selectionContainerChildrenService.childDragModel.insert) {
               // Pass responsibility to composer-selection-container-children
               return;
            }

            if(this.vsObject.objectType === "VSSelectionList" ||
               this.vsObject.objectType == "VSSelectionTree")
            {
               this.composerObjectService.checkTableTransferDataType(
                  this.viewsheetClient, data.dragSource).subscribe((res) => {
                  const datatype: string = res.body;

                  if(datatype == "timeInstant" || datatype == "date") {
                     ComponentTool.showConfirmDateTypeBindingDialog(this.modalService)
                        .then((applyChange) => {
                           if(applyChange) {
                              this.composerObjectService.applyChangeBinding(
                                 this.viewsheetClient, this.vsObject.absoluteName, null,
                                 null, data.dragSource);
                           }
                        });
                  }
                  else {
                     this.composerObjectService.applyChangeBinding(this.viewsheetClient,
                        this.vsObject.absoluteName, null, null, data.dragSource);
                  }
               });
            }
            else {
               this.composerObjectService.applyChangeBinding(this.viewsheetClient,
                  this.vsObject.absoluteName, null, null, data.dragSource);
            }
         }
         // Datasource dragged over
         // chart and table edit binding by dnd is done by dndservice.
         // continue if dropping a column from a cube onto a table
         else if(!(this.vsObject.objectType === "VSTable" &&
                   data.source && data.source.indexOf(this.cubeString) == -1
                   || this.vsObject.objectType == "VSChart" && dragName == "column")
                 && !this.isShape())
         {
            if(this.selectionContainerChildrenService.childDragModel.insert) {
               // Pass responsibility to composer-selection-container-children
               return;
            }

            let binding = this.composerObjectService.getDataSource(data);

            if(!binding || binding.length < 1 ||
               !this.isCalcDroppable(binding, this.vsObject.objectType) ||
               !this.isDataRefAccepted(binding))
            {
               return;
            }

            let first: AssetEntry = binding[0];

            let dateTypeData = binding.map(dataBinding => dataBinding.properties)
               .find(property => property.dtype == "timeInstant" || property.dtype == "date");

            if(dateTypeData && (this.vsObject.objectType === "VSSelectionList"
                                || this.vsObject.objectType === "VSSelectionTree"))
            {
               ComponentTool.showConfirmDateTypeBindingDialog(this.modalService).then((applyChange) => {
                  if(applyChange) {
                     this.composerObjectService.applyChangeBinding(this.viewsheetClient,
                        this.vsObject.absoluteName, binding);
                  }
               });
            }
            else if(this.showLayoutOptionDialog(first, data)) {
               let newType: number = 0;
               let objType: string = "column";
               let columns: any[] = data.column;

               if(first.type == AssetType.TABLE || first.type == AssetType.PHYSICAL_TABLE) {
                  newType = AssemblyType.TABLE_VIEW_ASSET;
                  objType = "table";
                  columns = [first];
               }
               else if(first.properties["cube.column.type"]) {
                  newType = binding.length > 1 ? AssemblyType.SELECTION_TREE_ASSET
                     : AssemblyType.SELECTION_LIST_ASSET;
               }
               else if(columns && columns.length > 0) {
                  let numTypeData = columns.some(c => XSchema.isNumericType(c.properties.dtype));
                  newType = columns.length > 1 ? AssemblyType.SELECTION_TREE_ASSET
                     : (numTypeData ? AssemblyType.TIME_SLIDER_ASSET
                        : AssemblyType.SELECTION_LIST_ASSET);
               }

               this.layoutOptionDialogModel = {
                  selectedValue: 0,
                  object: objType,
                  target: this.vsObject.absoluteName,
                  showSelectionContainerOption: false,
                  newObjectType: newType,
                  vsEntry: null,
                  columns: newType != 0 ? columns : null
               };

               this.openLayoutOptionDialog().then(
                  (result: LayoutOptionDialogModel) => {
                     if(!!first.properties["cube.column.type"] || result.selectedValue == 2) {
                        return;
                     }

                     const here: boolean = result.selectedValue == 0;
                     let box: ClientRect =
                        this.element.nativeElement.parentNode.getBoundingClientRect();
                     const left: number = event.pageX - box.left;
                     const top: number = event.pageY - box.top;

                     const vsevent = {
                        tab: result.selectedValue == 2,
                        x: here ? left : this.vsObject.objectFormat.left,
                        y: here ? top : this.vsObject.objectFormat.top
                     };

                     const name = this.vsObject.objectType === "VSTable" ||
                        this.vsObject.objectType === "VSChart" || here
                        ? null : this.vsObject.absoluteName;

                     this.composerObjectService.applyChangeBinding(
                        this.viewsheetClient, name, binding, vsevent);
                  },
                  () => {}
               );
            }
            else {
               this.composerObjectService.applyChangeBinding(
                  this.viewsheetClient, this.vsObject.absoluteName, binding);
            }
         }

         event.stopPropagation();
         this.dragOverBorder = DragBorderType.NONE;
         this.selectionContainerChildrenService.pushModel({dragging: false});
         this.selectionContainerChildrenService.childWithBorder = -1;
      }
   }

   @HostListener("dblclick", ["$event"])
   turnOnEditMode(event: MouseEvent): void {
      if(this.selected) {
         this.disableInteraction();
      }
   }

   onKeyDown(event: KeyboardEvent): void {
      // ctrl-g
      if(event.keyCode === 0x47 && this.selected && this.viewsheet.currentFocusedAssemblies.length > 1
         && this.viewsheet.currentFocusedAssemblies[0] == this.vsObject)
      {
         let groupMapping: Map<string, VSObjectModel[]> = new Map<string, VSObjectModel[]>();


         this.viewsheet.currentFocusedAssemblies.forEach( (assembly) => {
            let container = assembly.container;
            if(!!container) {
               if(!groupMapping.get(container)) {
                  groupMapping.set(container, []);
               }
               groupMapping.get(container).push(assembly);
            }
         });

         let count = 0;

         groupMapping.forEach( (val, key) => {
            count += val.length;
         });

         //if there are n - 1 edges in n nodes with no cycles, then they are all connected
         let allInTheSameGroup: boolean = count == this.viewsheet.currentFocusedAssemblies.length - 1;

         if(!allInTheSameGroup) {
            this.actions.onAssemblyActionEvent.emit(<AssemblyActionEvent<VSObjectModel>> {id: "vs-object group", model: this.vsObject});
            event.stopPropagation();
         }
      }
   }

   public isCalcDroppable(assetEntryArray: AssetEntry[], type: string): boolean {
      return !assetEntryArray.some((assetEntry) =>
         (assetEntry.properties["isCalc"] == "true" &&
          assetEntry.properties["basedOnDetail"] == "false" &&
          !this.isCalcDroppableType(type)));
   }

   public isMiniToolbarVisible(): boolean {
      return this.miniToolbarService.isMiniToolbarVisible(this.vsObject);
   }

   get miniToolbarWidth() {
      return this.miniToolbarService.getToolbarWidth(this.vsObject, this.containerBounds,
                                                     this.scaleService.getCurrentScale(),
                                                     this.containerScrollLeft, true);
   }

   public hasMiniToolbar(): boolean {
      return this.miniToolbarService.hasMiniToolbar(this.vsObject);
   }

   private isCalcDroppableType(type: string): boolean {
      return ["VSChart", "VSText", "VSGauge", "VSImage"]
         .indexOf(type) !== -1;
   }

   public isEnterEnabled(): boolean {
      let entriesString = this.dragService.get(AssetTreeService.getDragName(AssetType.COLUMN));
      const data = this.dragService.getDragData();
      let entries: AssetEntry[];

      if(entriesString) {
         entries = JSON.parse(entriesString);
      }

      return !(entries && entries.length > 0 &&
               !this.isCalcDroppable(entries, this.vsObject.objectType)) &&
         this.isDataRefAccepted(entries) &&
         !(this.vsObject.objectType === "VSSelectionContainer" && !!data.table);
   }

   private isDataRefAccepted(entries: AssetEntry[]): boolean {
      // most vsobject(s) not accept cube measure.
      if(entries && entries.length > 0 && entries[0].type == AssetType.COLUMN &&
         parseInt(entries[0].properties["cube.column.type"], 10) === 1)
      {
         return ["VSChart", "VSCrosstab", "VSCheckBox", "VSRadioButton", "VSComboBox", "VSText",
            "VSGauge", "VSImage"]
            .indexOf(this.vsObject.objectType) !== -1;
      }

      return true;
   }

   openEmbeddedViewsheet(assetId: string): void {
      this.onOpenEmbeddedViewsheet.emit(assetId);
   }

   changeMinHeightFromAutoText(event: number) {
      this.vsObject.objectFormat.height = event;
   }

   private openLayoutOptionDialog(): Promise<LayoutOptionDialogModel> {
      return this.modalService.open(this.layoutOptionDialog,
                                    {backdrop: "static"}).result;
   }

   private endAnchorDrag(): void {
      this.changeDetectorRef.detectChanges();

      if(this.vsObject.objectType === "VSSelectionContainer") {
         this.objectChanged.emit(true);
      }

      let line: VSLineModel = <VSLineModel> this.vsObject;

      let startCurrentX: number = line.objectFormat.left + line.startLeft;
      let startCurrentY: number = line.objectFormat.top + line.startTop;

      let endCurrentX: number = line.objectFormat.left + line.endLeft;
      let endCurrentY: number = line.objectFormat.top + line.endTop;

      line.objectFormat.left = Math.min(startCurrentX, endCurrentX);
      line.objectFormat.top = Math.min(startCurrentY, endCurrentY);

      line.startLeft = startCurrentX - line.objectFormat.left;
      line.startTop = startCurrentY - line.objectFormat.top;

      line.endLeft = endCurrentX - line.objectFormat.left;
      line.endTop = endCurrentY - line.objectFormat.top;

      line.objectFormat.width = Math.max(8, Math.abs(
         line.startLeft - line.endLeft));
      line.objectFormat.height = Math.max(8, Math.abs(
         line.startTop - line.endTop));
   }

   openEditPane(): void {
      this.onOpenEditPane.emit(this.vsObject);
   }

   openWizardPane() {
      this.onOpenWizardPane.emit({
         runtimeId: this.viewsheet.runtimeId,
         linkUri: this.viewsheet.linkUri,
         objectModel: this.vsObject,
         oinfo: {
            runtimeId: this.viewsheet.runtimeId,
            editMode: VsWizardEditModes.VIEWSHEET_PANE,
            objectType: this.vsObject.objectType,
            absoluteName: this.vsObject.absoluteName
         }
      });
   }

   private hasMultipleSelectRegions(): boolean {
      switch(this.vsObject.objectType) {
      case "VSChart":
      case "VSCrosstab":
      case "VSTable":
      case "VSCalcTable":
      case "VSSelectionList":
      case "VSSelectionTree":
      case "VSSelectionContainer":
      case "VSCheckBox":
      case "VSRadioButton":
         return true;
      default:
         return false;
      }
   }

   private showLayoutOptionDialog(entry: AssetEntry, data: any): boolean {
      return this.vsObject.objectType === "VSTab"
         || entry.properties.DIMENSION_FOLDER
            && this.vsObject.objectType !== "VSSelectionContainer"
            && this.vsObject.objectType !== "VSSelectionTree"
         || (data.table || data.physical_table) && this.vsObject.objectType !== "VSTable"
         || (this.vsObject.objectType === "VSCalendar"
            && entry.properties.dtype !== "timeInstant"
            && entry.properties.dtype !== "date")
         //|| entry.properties.isCalc === "true" && this.isFormAssembly()
         || !entry.properties["cube.column.type"]
            && (this.vsObject.objectType === "VSTable"
               || this.vsObject.objectType === "VSChart")
         || !!data.column && (this.vsObject.objectType === "VSCrosstab"
            || this.vsObject.objectType === "VSCalcTable" );
   }

   getLineRotationAngle(): number {
      const vsLine = this.vsObject as VSLineModel;
      const start = new Point(vsLine.startLeft, vsLine.startTop);
      const end = new Point(vsLine.endLeft, vsLine.endTop);
      const line = new Line(start, end);
      return line.getAngle();
   }

   getLineLength(): number {
      const vsLine: VSLineModel = this.vsObject as VSLineModel;
      const start: Point = new Point(vsLine.startLeft, vsLine.startTop);
      const end: Point = new Point(vsLine.endLeft, vsLine.endTop);
      const line: Line = new Line(start, end);
      return line.getLength();
   }

   updateFocus(model: VSObjectModel): void {
      if(this.viewsheet.updateSelectedAssembly(model)) {
         this.viewsheet.focusedAssembliesChanged();
      }
   }

   onTitleResizeMove(newTitleHeight: number): void {
      this.composerObjectService.adjustTitleHeight(this.vsObject, newTitleHeight);
   }

   onTitleResizeEnd(): void {
      this.composerObjectService.resizeObjectTitle(this.viewsheet, this.vsObject);
   }

   resizeObject(width: number): void {
      this.composerObjectService.resizeObject(this.viewsheet, this.vsObject);
   }

   get popupShowing(): boolean {
      return !!this.dataTipService.dataTipName || this.adhocFilterService.adhocFilterShowing;
   }

   get showEdit(): boolean {
      return this.vsObject && (<any> this.vsObject).empty && !this.dragService.currentlyDragging &&
         !(<any> this.vsObject).changedByScript &&
         (this.vsObject.objectType !== "VSChart" || (<any> this.objectComponent)?.emptyChart);
   }

   setMovingResizing(flag: boolean) {
      if(flag) {
         this.objectEditor.nativeElement.classList.add("moving-resizing");
      }
      else {
         this.objectEditor.nativeElement.classList.remove("moving-resizing");
      }
   }

   get goToWizardVisible(): boolean {
      return !!this.viewsheet.baseEntry; // cube source
   }

   clickEditButton(): void {
      if(this.vsObject.objectType != "VSChart" || !this.goToWizardVisible) {
         this.openEditPane();
         return;
      }

      let chartModel = <VSChartModel> this.vsObject;

      if(!this.viewsheet.metadata &&
         (chartModel.editedByWizard || chartModel.sourceType == SourceInfoType.NONE))
      {
         this.openWizardPane();
      }
      else {
         this.openEditPane();
      }
   }

   onMaxModeChange(event: {assembly: string, maxMode: boolean}): void {
      this.maxModeChange.emit(event);
   }

   onDetectViewChange(): void {
      this.changeDetectorRef.detectChanges();
   }

   onMouseEnter(event: any) {
      this.miniToolbarService.handleMouseEnter(this.vsObject?.absoluteName, event);
   }

   contextMenuClose() {
      this.contextMenuVisible = false;
      this.miniToolbarService.hiddenUnfreeze(this.vsObject?.absoluteName);
   }

   toolbarForceHidden() {
      return this.miniToolbarService.isMiniToolbarHidden(this.vsObject?.absoluteName);
   }
}
