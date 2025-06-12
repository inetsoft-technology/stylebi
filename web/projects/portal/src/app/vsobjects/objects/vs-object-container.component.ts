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
   AfterViewInit,
   ChangeDetectorRef,
   Component,
   ElementRef,
   EventEmitter,
   Input,
   OnChanges,
   OnDestroy,
   Output,
   SimpleChanges,
   ViewChild
} from "@angular/core";
import { Observable, Subject, Subscription } from "rxjs";
import { Tool } from "../../../../../shared/util/tool";
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";
import { Dimension } from "../../common/data/dimension";
import { Rectangular } from "../../common/data/rectangle";
import { GuiTool } from "../../common/util/gui-tool";
import { ViewsheetClientService } from "../../common/viewsheet-client";
import { PlaceholderDragElementModel } from "../../widget/placeholder-drag-element/placeholder-drag-element-model";
import { ScaleService } from "../../widget/services/scale/scale-service";
import { AbstractVSActions } from "../action/abstract-vs-actions";
import { ContextProvider } from "../context-provider.service";
import { ViewsheetInfo } from "../data/viewsheet-info";
import { BaseTableModel } from "../model/base-table-model";
import { FocusObjectEventModel } from "../model/focus-object-event-model";
import { GuideBounds } from "../model/layout/guide-bounds";
import { VSChartModel } from "../model/vs-chart-model";
import { VSObjectModel } from "../model/vs-object-model";
import { VSSelectionBaseModel } from "../model/vs-selection-base-model";
import { VSUtil } from "../util/vs-util";
import { ScrollViewportRect } from "../viewer-app.component";
import { AdhocFilterService } from "./data-tip/adhoc-filter.service";
import { DataTipService } from "./data-tip/data-tip.service";
import { DateTipHelper } from "./data-tip/date-tip-helper";
import { PopComponentService } from "./data-tip/pop-component.service";
import { MiniToolbarService } from "./mini-toolbar/mini-toolbar.service";
import { NavigationKeys } from "./navigation-keys";
import { SelectionBaseController } from "./selection/selection-base-controller";

@Component({
   selector: "vs-object-container",
   templateUrl: "vs-object-container.component.html",
   styleUrls: ["vs-object-container.component.scss"]
})
export class VSObjectContainer implements AfterViewInit, OnChanges, OnDestroy {
   @Input() public vsInfo: ViewsheetInfo;
   @Input() public vsObjectActions: AbstractVSActions<any>[];
   @Input() public activeName: string;
   @Input() public containerRef: HTMLElement;
   @Input() public touchDevice: boolean = false;
   @Input() public selectedAssemblies: number[];
   @Input() embeddedVS: boolean = false;
   @Input() embeddedVSBounds: Rectangular;
   @Input() variableValues = (objName: string) => this.getVariablesValues(objName);
   @Input() scaleToScreen = false;
   @Input() appSize: Dimension;
   @Input() allAssemblyBounds: {top: number, left: number, bottom: number, right: number} = null;
   @Input() focusedObject: VSObjectModel;
   @Input() guideType: GuideBounds = GuideBounds.GUIDES_NONE;
   @Input() submitted: Subject<boolean>;
   @Input() hideMiniToolbar: boolean = false;
   @Input() globalLoadingIndicator: boolean = false;
   @Input() virtualScrolling = false;
   @Output() public openContextMenu = new EventEmitter<{
      actions: AbstractVSActions<any>,
      event: MouseEvent
   }>();
   @Output() onEditTable = new EventEmitter<BaseTableModel>();
   @Output() onEditChart = new EventEmitter<VSChartModel>();
   @Output() onOpenChartFormatPane = new EventEmitter<VSChartModel>();
   @Output() onOpenConditionDialog = new EventEmitter<BaseTableModel>();
   @Output() onOpenHighlightDialog = new EventEmitter<BaseTableModel>();
   @Output() onOpenAnnotationDialog = new EventEmitter<MouseEvent>();
   @Output() onOpenViewsheet = new EventEmitter<string>();
   @Output() onLoadFormatModel = new EventEmitter<VSChartModel>();
   @Output() onSelectedAssemblyChanged =
      new EventEmitter<[number, AbstractVSActions<any>, MouseEvent]>();
   @Output() removeAnnotations = new EventEmitter<void>();
   @Output() maxModeChange = new EventEmitter<{assembly: string, maxMode: boolean}>();
   @Output() onSubmit = new EventEmitter<any>();
   @Output() onToggleDoubleCalendar = new EventEmitter<boolean>();
   @Output() onLoadingStateChanged = new EventEmitter<{ name: string, loading: boolean }>();
   @ViewChild("popUpDim") popUpDim: ElementRef;

   @Input() set keyNavigation(obs: Observable<FocusObjectEventModel>) {
      this._keyNavigation = obs;

      if(obs) {
         this.focusSub = obs.subscribe((data: FocusObjectEventModel) => {
            if(data && data.focused && data.index < 0 && (data.key == NavigationKeys.TAB ||
               data.key == NavigationKeys.SHIFT_TAB))
            {
               const assembly: any = this.element.nativeElement
                  .querySelector("." + this.getAssemblyAsClass(data.focused));

               if(!!assembly) {
                  assembly.focus();
               }
            }
         });
      }
   }

   get keyNavigation(): Observable<FocusObjectEventModel> {
      return this._keyNavigation;
   }

   @Input()
   get scrollViewport(): ScrollViewportRect {
      return this._scrollViewport;
   }

   set scrollViewport(value: ScrollViewportRect) {
      if(!value && !!this._scrollViewport || !!value && !this._scrollViewport ||
         !!value && !!this._scrollViewport && (value.top !== this._scrollViewport.top ||
         value.left !== this._scrollViewport.left || value.width !== this._scrollViewport.width ||
         value.height !== this._scrollViewport.height))
      {
         this._scrollViewport = value;
         this.updateRendered();
      }
   }

   public menuActions: AssemblyActionGroup[];
   public placeholderDragElementModel: PlaceholderDragElementModel = <PlaceholderDragElementModel> {
      top: 0,
      left: 0,
      width: 0,
      height: 0,
      text: "",
      visible: false
   };
   public containerBounds: DOMRectInit;
   public containerScrollLeft = 0;
   public containerHasVerticalScrollbar = true;
   private subscriptions = new Subscription();
   public forceShowMiniToolbar: boolean = false;
   protected maxZIndex: number;
   private _keyNavigation: Observable<FocusObjectEventModel>;
   private focusSub: Subscription;
   mobile: boolean = GuiTool.isMobileDevice();
   private scale: number;
   private popDimDrew: boolean;
   private renderedObjects = new Map<string, boolean>();
   private _scrollViewport: ScrollViewportRect;

   constructor(public miniToolbarService: MiniToolbarService,
               protected dataTipService: DataTipService,
               protected context: ContextProvider,
               protected adhocFilterService: AdhocFilterService,
               protected popService: PopComponentService,
               private changeDetectorRef: ChangeDetectorRef,
               private scaleService: ScaleService,
               private element: ElementRef,
               private viewsheetClient: ViewsheetClientService)
   {
      this.subscriptions.add(this.scaleService.getScale()
         .subscribe((scale) => this.scale = scale));

      this.subscriptions.add(this.popService.componentPop.subscribe((name) => {
         this.resetAssemblyAction(name);
      }));
   }

   ngAfterViewInit(): void {
      this.updateRendered();
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.containerRef) {
         this.subscriptions.add(this.miniToolbarService.addContainerEvents(
            this.containerRef,
            (e) => {
               this.containerScrollLeft = e.target.scrollLeft;
               this.containerHasVerticalScrollbar = this.checkContainerHasVerticalScrollbar();
               this.changeDetectorRef.detectChanges();
            },
            () => {
               this.containerBounds = this.containerRef.getBoundingClientRect();
               this.containerHasVerticalScrollbar = this.checkContainerHasVerticalScrollbar();
            }
         ));

         this.containerBounds = this.containerRef.getBoundingClientRect();
      }

      this.updateRendered();
   }

   ngOnDestroy() {
      this.subscriptions.unsubscribe();

      if(!!this.focusSub) {
         this.focusSub.unsubscribe();
      }
   }

   isAssemblyVisible(model: VSObjectModel): boolean {
      return !(this.context.viewer || this.context.preview) && !this.embeddedVS ||
         model.visible && (!!model.container && model.active || !model.container) ||
         this.dataTipService.isDataTipVisible(model.absoluteName) ||
         (!!model.container && this.dataTipService.isDataTipVisible(model.container));
   }

   isMiniToolbarVisible(model: VSObjectModel): boolean {
      if(model.containerType === "VSSelectionContainer" ||
         (<any> model).dropdown || (<any> model).dropdownCalendar)
      {
         return false;
      }

      if(model.objectType == "VSChart" && (<any> model).showPlotResizers &&
         (<any> model).horizontallyResizable)
      {
         return false;
      }

      if(this.embeddedVS && !this.viewer) {
         return false;
      }

      return (model.objectType === "VSSelectionContainer" ||
              model.objectType === "VSCalcTable" ||
              model.objectType === "VSCalendar" ||
              model.objectType === "VSChart" ||
              model.objectType === "VSCrosstab" ||
              model.objectType === "VSSelectionList" ||
              model.objectType === "VSSelectionTree" ||
              model.objectType === "VSTable" ||
              model.objectType === "VSRangeSlider");
   }

   toolbarForceHidden(model: VSObjectModel): boolean {
      return this.miniToolbarService.isMiniToolbarHidden(model?.absoluteName);
   }

   /**
    * Track by function that returns the absolute name of the provided object model.
    *
    * @param index the index of the item.
    * @param item  the item.
    *
    * @returns the absolute name of the assembly.
    */
   public trackByName(index: number, item: VSObjectModel): any {
      return item.absoluteName;
   }

   public showContextMenu(event: MouseEvent, actions: AbstractVSActions<any>): void {
      if(!this.touchDevice) {
         event.stopPropagation();
         this.openContextMenu.emit({
            actions,
            event
         });
      }
   }

   public getVariablesValues(name: string): string[] {
      return VSUtil.getVariableList(this.vsInfo.vsObjects, name);
   }

   get viewer(): boolean {
      return this.context.viewer || this.context.preview;
   }

   /**
    * Select an assembly by index in the vsobject array
    */
   public select(index: number, event: MouseEvent): void {
      // selecting embedded viewsheet causes children in the embedded vs not selected
      if(this.viewer && this.vsInfo.vsObjects[index].objectType == "VSViewsheet") {
         return;
      }

      if(this.dataTipService.isDataTip(this.vsInfo.vsObjects[index].absoluteName) &&
         this.dataTipService.dataTipName && !this.mobile)
      {
         return;
      }

      this.onSelectedAssemblyChanged.emit([index, this.vsObjectActions[index], event]);
   }

   /**
    * Check if a vsobject is selected
    */
   public isSelected(index: number): boolean {
      return this.selectedAssemblies && this.selectedAssemblies.indexOf(index) > -1;
   }

   isAtBottom(index: number, scaleToScreenOnly: boolean) {
      if(!this.scaleToScreen && scaleToScreenOnly || !this.allAssemblyBounds) {
         return false;
      }

      const {top, height} = this.vsInfo.vsObjects[index].objectFormat;
      return top + height === this.allAssemblyBounds.bottom;
   }

   submitClicked(name: string) {
      this.onSubmit.emit();
   }

   onMaxModeChange($event: {assembly: string, maxMode: boolean}): void {
      this.maxModeChange.emit($event);
   }

   isMaxModeHidden(model: VSObjectModel): boolean {
      if(!this.vsInfo || !model || !model.sheetMaxMode) {
         return false;
      }

      let maxObj = this.vsInfo.vsObjects.find(v => v["maxMode"]);

      if(maxObj == null) {
         return false;
      }

      const maxObjName = maxObj.absoluteName;

      if(model.objectType === "VSViewsheet" && !!maxObjName &&
         maxObjName.startsWith(model.absoluteName + "."))
      {
         return false;
      }

      if((model.objectType === "VSChart" || model.objectType === "VSTable" ||
          model.objectType == "VSCalcTable" || model.objectType == "VSCrosstab" ||
          model.objectType == "VSSelectionList" || model.objectType == "VSSelectionTree" ||
          model.objectType == "VSRangeSlider" || model.objectType == "VSSelectionContainer" ||
          model.objectType === "VSViewsheet") && model.absoluteName === maxObjName)
      {
         return false;
      }

      let containerName = model.container;

      while(!!containerName) {
         let container: VSObjectModel = this.vsInfo.vsObjects.find(
               (vso) => vso.absoluteName === containerName);

         if(!!container && containerName == maxObj.dataTip &&
            !(<any> container).adhocFilter)
         {
            return false;
         }

         containerName = container != null ? container.container : null;
      }

      return model.absoluteName !== maxObj.dataTip && !(<any> model).adhocFilter;
   }

   get popupShowing(): boolean {
      return this.dataTipService.isDataTipVisible(this.dataTipService.dataTipName) &&
         !!this.dataTipService.dataTipName || this.adhocFilterService.adhocFilterShowing ||
         !!this.popService.getPopComponent();
   }

   isPopupShowing(vsobj: VSObjectModel): boolean {
      return this.dataTipService.isDataTipVisible(this.dataTipService.dataTipName) &&
         this.dataTipService.dataTipName == vsobj.dataTip && !!vsobj.dataTip ||
         this.popService.getPopComponent() == vsobj.popComponent && !!vsobj.popComponent;
   }

   /**
    * Return whether the given object is being focused on by keyboard nav.
    * @param {VSObjectModel} object
    * @returns {boolean}
    */
   isFocused(object: VSObjectModel): boolean {
      return !!this.focusedObject && this.focusedObject.absoluteName == object.absoluteName;
   }

   /**
    * Setter for whether to force show the mini toolbar or not.
    * @param {boolean} show
    */
   showToolbar(show: boolean): void {
      this.forceShowMiniToolbar = show;
   }

   getAssemblyAsClass(object: VSObjectModel): string {
      return object.absoluteName.replace(/ /g, "-");
   }

   getAssemblyDivId(object: VSObjectModel): string {
      return this.dataTipService.getVSObjectId(object.absoluteName);
   }

   public getToolbarTop(object: VSObjectModel, i: number): number {
      let actionHeight = 28;
      let top: number = this.embeddedVS ? this.embeddedVSBounds.y : object.objectFormat.top;

      if(Tool.equalsIgnoreCase(object.objectType, "VSRangeSlider") && top < actionHeight) {
         top = top + object.objectFormat.height + actionHeight;
      }
      else {
         top = object.objectFormat.top;
      }

      return top;
   }

   public getActionsWidth(object: VSObjectModel, i: number): number {
      return this.miniToolbarService.getActionsWidth(this.vsObjectActions[i].toolbarActions);
   }

   public getToolbarLeft(object: VSObjectModel, i: number): number {
      let left: number;

      // 1. the left property of max mode has setting to 0 in server
      // 2. some max mode assembly has not start from 0. e.g. selection list.
      left = object.objectFormat.left;

      if(i >= this.vsObjectActions.length) {
         return left;
      }

      return this.miniToolbarService.getToolbarLeft(left, this.containerBounds,
         this.scaleService.getCurrentScale(),
         this.containerScrollLeft, this.checkContainerHasVerticalScrollbar(),
         this.vsObjectActions[i].showingActions, this.embeddedVSBounds, (<any> object).maxMode);
   }

   public getToolbarWidth(object: VSObjectModel): number {
      return this.miniToolbarService.getToolbarWidth(object, this.containerBounds,
                                                     this.scaleService.getCurrentScale(),
                                                     this.containerScrollLeft,
                                                     this.checkContainerHasVerticalScrollbar(),
                                                     this.embeddedVSBounds);
   }

   private checkContainerHasVerticalScrollbar(): boolean {
      return this.containerRef.scrollHeight > this.containerRef.clientHeight;
   }

   openWizardPane(evt: any) {
      if(!!evt) {
         this.onEditChart.emit(evt.objectModel);
      }
   }

   zIndex(vsObject: VSObjectModel): number {
      if(this.popService.isPopSource(vsObject.absoluteName) ||
         this.dataTipService.isDataTipSource(vsObject.absoluteName))
      {
         return DateTipHelper.getPopUpSourceZIndex();
      }

      let zIndex = vsObject.objectFormat.zIndex;

      for(let container = vsObject.container; container; ) {
         let containerObj = this.vsInfo.vsObjects.find(v => v.absoluteName == container);

         if(containerObj) {
            zIndex += containerObj.objectFormat.zIndex;
            container = containerObj.container;
         }
         else {
            break;
         }
      }

      return zIndex;
   }

   getVsObjectPosition(obj: VSObjectModel, i, top: boolean): number {
      if(obj.objectType === "VSCalcTable" || obj.objectType === "VSTable" ||
         obj.objectType === "VSCrosstab")
      {
         return (this.viewer || this.embeddedVS) && !(<any> obj).maxMode ?
            top ? obj?.objectFormat?.top : obj?.objectFormat?.left : null;
      }
      else if(obj.objectType === "VSChart") {
         (<any> obj).maxMode ? 0 : (this.viewer || obj.inEmbeddedViewsheet && !this.context.binding
            ? top ? obj?.objectFormat?.top : obj?.objectFormat?.left : 0);
      }
      else if(obj.objectType === "VSRangeSlider") {
         (this.viewer || this.embeddedVS) && obj.containerType !== "VSSelectionContainer" ?
            top ? obj?.objectFormat?.top : obj?.objectFormat?.left : null;
      }
      else if(obj.objectType === "VSSelectionTree" || obj.objectType === "VSSelectionList") {
         if(top) {
            if((this.viewer || this.embeddedVS) && !(<VSSelectionBaseModel> obj).maxMode
               && obj.containerType !== "VSSelectionContainer")
            {
               if(this.isAtBottom(i, true) && (<VSSelectionBaseModel> obj).dropdown &&
                  !SelectionBaseController.isHidden(<VSSelectionBaseModel> obj))
               {
                  let bodyHeight = this.getSelectionBodyHeight(<VSSelectionBaseModel> obj);
                  let popDown = this.containerBounds?.height - obj?.objectFormat?.top -
                     (<VSSelectionBaseModel> obj)?.titleFormat?.height - bodyHeight > 0;

                  return popDown ? obj?.objectFormat?.top : obj?.objectFormat?.top - bodyHeight;
               }
               else {
                  return obj?.objectFormat?.top;
               }
            }

            return null;
         }
         else {
            if((this.viewer || this.embeddedVS) && obj.containerType !== "VSSelectionContainer"
               && !(<any> obj).maxMode)
            {
               if(!(<any> obj)?.maxMode) {
                  return obj?.objectFormat?.left;
               }

               return obj?.objectFormat?.left / (this.scale ?? 1);
            }
            else {
               return null;
            }
         }
      }

      return top ? obj?.objectFormat?.top : obj?.objectFormat?.left;
   }

   private getSelectionBodyHeight(obj: VSSelectionBaseModel): number {
      const titleBorders = obj.titleFormat.border;
      const objectBorders = obj.objectFormat.border;
      let topMarginTitle = 0;

      if(obj.containerType === "VSSelectionContainer") {
         if(obj.dropdown) {
            objectBorders.bottom = null;
         }

         topMarginTitle = -(Tool.getMarginSize(titleBorders.top) +
            Tool.getMarginSize(objectBorders.top));
      }
      else {
         topMarginTitle = -Tool.getMarginSize(titleBorders.top);
      }

      const bottomMargin: number = Tool.getMarginSize(obj.objectFormat.border.bottom);
      const topMargin: number = Tool.getMarginSize(obj.objectFormat.border.top);
      const offset = Math.max(0, bottomMargin + topMargin + topMarginTitle);
      return obj.containerType === "VSSelectionContainer" ?
         obj.objectFormat.height - obj.titleFormat.height :
         obj.dropdown && !obj.maxMode ? this.getSelectionCellHeight(obj) * obj.listHeight
            : obj.objectFormat.height -
            (!this.viewer || obj.titleVisible ? obj.titleFormat.height : 0) - offset;
   }

   private getSelectionCellHeight(obj: VSSelectionBaseModel): number {
      return this.mobile ? Math.max(40, obj.cellHeight) : obj.cellHeight;
   }

   private resetAssemblyAction(name: string) {
      if(!name || !this.vsInfo?.vsObjects) {
         return;
      }

      for(let i = 0; i < this.vsInfo.vsObjects.length; i++) {
         if(this.vsInfo.vsObjects[i].absoluteName === name) {
            this.vsObjectActions[i].resetAssemblyMenuActions();
         }
      }
   }

   showingPopUpOrDataTip(): boolean {
      let showingPop = this.popService.hasPopUpComponentShowing() || this.dataTipService.hasDataTipShowing();

      if(showingPop && !this.popDimDrew) {
         setTimeout(() => this.drawPopDim());
         this.popDimDrew = true;
      }

      if(!showingPop) {
         this.popDimDrew = false;
      }

      return showingPop;
   }

   getPopDimZIndex(): number {
      return DateTipHelper.getPopUpBackgroundZIndex();
   }

   private drawPopDim(): void {
      if(this.popUpDim?.nativeElement && this.vsInfo?.vsObjects) {
         let context = this.popUpDim.nativeElement.getContext("2d");
         context.clearRect(0, 0, this.popUpDim.nativeElement.width, this.popUpDim.nativeElement.height);
         context.fillStyle = DateTipHelper.popDimColor;
         context.fillRect(0, 0, this.getPopDimWidth(), this.getPopDimHeight());

         for(let vsObject of this.vsInfo.vsObjects) {
            if(vsObject.objectType == "VSViewsheet") {
               context.clearRect(vsObject.objectFormat.left, vsObject.objectFormat.top,
                  vsObject.objectFormat.width, vsObject.objectFormat.height);
            }
         }
      }
   }

   getPopDimWidth(): number {
      return this.embeddedVS ? this.embeddedVSBounds?.width : this.containerRef.scrollWidth;
   }

   getPopDimHeight(): number {
      return this.embeddedVS ? this.embeddedVSBounds?.height : this.containerRef.scrollHeight;
   }

   getActualWidth(vsObject: VSObjectModel): number {
      return this.dataTipService.isDataTip(vsObject.absoluteName) && !!vsObject.realWidth ?
         vsObject.realWidth : vsObject.objectFormat.width;
   }

   onMouseEnter(vsObject: VSObjectModel, event: any): void {
      this.miniToolbarService.handleMouseEnter(vsObject?.absoluteName, event);
   }
   isObjectRendered(vsObject: VSObjectModel) {
      return !this.virtualScrolling || this.renderedObjects.get(vsObject.absoluteName) === true;
   }

   private updateRendered(): void {
      if(this.virtualScrolling && !!this.scrollViewport &&
         this.isRectInitialized(this.scrollViewport))
      {
         this.vsInfo.vsObjects.forEach((vsObject, index) => {
            const rendered = this.renderedObjects.get(vsObject.absoluteName);

            if(!rendered) {
               const height = ((vsObject as any).objectHeight as number) || vsObject.objectFormat.height;
               const rect = {
                  top: this.getVsObjectPosition(vsObject, index, true),
                  left: this.getVsObjectPosition(vsObject, index, false),
                  width: this.getActualWidth(vsObject),
                  height
               };

               if(this.isRectInitialized(rect)) {
                  const newRendered = this.isInScrollViewport(rect);

                  if(newRendered && rendered != undefined &&
                     (vsObject.objectType === "VSCalcTable" ||
                        vsObject.objectType === "VSCrosstab"  ||
                        vsObject.objectType === "VSTable" ||
                        vsObject.objectType === "VSViewsheet"))
                  {
                     const event = {
                        vsRuntimeId: this.vsInfo.runtimeId,
                        assemblyName: vsObject.absoluteName
                     };
                     this.viewsheetClient.sendEvent("/events/vs/refresh/assembly", event);
                  }

                  this.renderedObjects.set(vsObject.absoluteName, newRendered);
               }
            }
         });
      }
   }

   private isRectInitialized(rect: ScrollViewportRect): boolean {
      return rect.top > 0 || rect.left > 0 || rect.width > 0 || rect.height > 0;
   }

   private isInScrollViewport(bounds: ScrollViewportRect): boolean {
      if(bounds.left + bounds.width < this.scrollViewport.left ||
         this.scrollViewport.left + this.scrollViewport.width < bounds.left)
      {
         return false;
      }

      if(bounds.top + bounds.height < this.scrollViewport.top ||
         this.scrollViewport.top + this.scrollViewport.height < bounds.top)
      {
         return false;
      }

      return true;
   }
}
