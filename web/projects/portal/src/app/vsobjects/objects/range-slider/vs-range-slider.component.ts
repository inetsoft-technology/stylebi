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
   Component,
   ElementRef,
   EventEmitter,
   Input,
   NgZone,
   OnChanges,
   OnInit,
   OnDestroy,
   Output,
   Renderer2,
   SimpleChanges,
   ViewChild
} from "@angular/core";
import { Subscription } from "rxjs";
import { Tool } from "../../../../../../shared/util/tool";
import { AssemblyActionGroup } from "../../../common/action/assembly-action-group";
import { DragEvent } from "../../../common/data/drag-event";
import { ComponentTool } from "../../../common/util/component-tool";
import { DataPathConstants } from "../../../common/util/data-path-constants";
import { GuiTool } from "../../../common/util/gui-tool";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { GetVSObjectModelEvent } from "../../../vsview/event/get-vs-object-model-event";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DebounceService } from "../../../widget/services/debounce.service";
import { ModelService } from "../../../widget/services/model.service";
import { DialogService } from "../../../widget/slide-out/dialog-service.service";
import { RangeSliderActions } from "../../action/range-slider-actions";
import { ContextProvider } from "../../context-provider.service";
import { RangeSliderEditDialog } from "../../dialog/range-slider-edit-dialog.component";
import { RangeSliderPropertyDialog } from "../../dialog/range-slider-property-dialog.component";
import { ApplySelectionListEvent } from "../../event/apply-selection-list-event";
import { ChangeVSObjectTextEvent } from "../../event/change-vs-object-text-event";
import { HideTimeSliderEvent } from "../../event/hide-time-slider-event";
import { MaxObjectEvent } from "../../event/table/max-object-event";
import { VSObjectEvent } from "../../event/vs-object-event";
import { RangeSliderPropertyDialogModel } from "../../model/range-slider-property-dialog-model";
import { VSRangeSliderModel } from "../../model/vs-range-slider-model";
import { CheckFormDataService } from "../../util/check-form-data.service";
import { VSUtil } from "../../util/vs-util";
import { NavigationComponent } from "../abstract-nav-component";
import { AdhocFilterService } from "../data-tip/adhoc-filter.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { MiniMenu } from "../mini-toolbar/mini-menu.component";
import { NavigationKeys } from "../navigation-keys";
import { FocusRegions } from "../selection/vs-selection.component";
import { SlideOutOptions } from "../../../widget/slide-out/slide-out-options";
import { GlobalSubmitService } from "../../util/global-submit.service";

const RANGESLIDER_PROPERTY_URI: string = "composer/vs/range-slider-property-dialog-model/";
const URI_UPDATE_TITLE_RATIO: string = "/events/composer/viewsheet/currentSelection/titleRatio/";
const RANGE_SLIDER_MAX_MODE_URL: string = "/events/vs/assembly/max-mode/toggle";
enum Handle { Left, Middle, Right, None }

@Component({
   selector: "vs-range-slider",
   templateUrl: "vs-range-slider.component.html",
   styleUrls: ["vs-range-slider.component.scss"]
})
export class VSRangeSlider extends NavigationComponent<VSRangeSliderModel>
   implements OnInit, OnDestroy, OnChanges
{
   @Input() selected: boolean = false;
   @Input() viewsheetScale: number = 1;
   @Output() sliderChanged = new EventEmitter();
   @Output() removeChild = new EventEmitter();
   @Input() container: Element;
   @Output() public maxModeChange = new EventEmitter<{assembly: string, maxMode: boolean}>();
   @ViewChild("leftHandle") leftHandle: ElementRef;
   @ViewChild("rightHandle") rightHandle: ElementRef;
   @ViewChild("middleHandle") middleHandle: ElementRef;
   @ViewChild("rangeSliderContainer") rangeSliderContainer: ElementRef;
   @ViewChild("menu", { read: ElementRef }) miniMenu: ElementRef;
   @ViewChild("menu") miniMenuComponent: MiniMenu;
   @ViewChild("collapseButton", { read: ElementRef }) collapseButtonRef: ElementRef;
   handleType = Handle; // for template
   ticks: number[] = [];
   sliderTop: number;
   headerBorderBottomColor: string;
   headerSeparatorBorderColor: string;
   readonly rangeValueOffset: number = 10;
   readonly tickOffset: number = 5;
   readonly isMobile: boolean = GuiTool.isMobileDevice();

   mouseHandle: Handle = Handle.None;
   private startingXPosition: number;
   private _isMouseDown: boolean = false;
   private _actions: RangeSliderActions;
   private actionSubscription: Subscription;
   private adhocFilterListener: () => any;
   private textSize: number = 0;
   private startingLeftSliderPosition: number = 0;
   private startingRightSliderPosition: number = 0;
   public editingTitle: boolean = false;

   // Positions on handles from the left
   private _leftHandlePosition: number;
   private _rightHandlePosition: number;

   get mobilePadding(): number {
      return this.hasMobilePadding ? 10 : 0;
   }

   get leftHandlePosition(): number {
      return this._leftHandlePosition - this.mobilePadding;
   }

   get rightHandlePosition(): number {
      return this._rightHandlePosition - this.mobilePadding;
   }

   get hasMobilePadding(): boolean {
      return this.context.viewer && this.isMobile;
   }

   // Offset cause by the handle image (should usually be half the the image)
   pointerOffset: number = 4;

   // Width of the selected portion of the range
   rangeLineWidth: number;

   // Width between each label/tick with respect to current width
   widthBetweenTicks: number;

   // To reduce number of visible ticks. Value came from vs html may change later.
   minWidthBetweenTicks: number;

   // show toolbar if in a selection container or used as an adhoc filter
   showToolbar: boolean = false;

   // For focusing on mini menu
   menuFocus: number = FocusRegions.NONE;
   miniMenuDropdownOpen: boolean = false;
   keyNav: boolean = false;
   FocusRegions = FocusRegions;
   _unappliedSelections: {start: number, end: number};
   private submitSubscription: Subscription;

   constructor(protected viewsheetClient: ViewsheetClientService,
               private formDataService: CheckFormDataService,
               private renderer: Renderer2,
               private elementRef: ElementRef,
               private modelService: ModelService,
               private modalService: DialogService,
               private adhocFilterService: AdhocFilterService,
               zone: NgZone,
               protected context: ContextProvider,
               protected dataTipService: DataTipService,
               private debounceService: DebounceService,
               private dropdownService: FixedDropdownService,
               private globalSubmitService: GlobalSubmitService)
   {
      super(viewsheetClient, zone, context, dataTipService);
   }

   ngOnInit() {
      if(!!this.globalSubmitService) {
         this.submitSubscription = this.globalSubmitService.globalSubmit()
            .subscribe(eventSource => {
               if(!this.model.submitOnChange) {
                  if(!!this._unappliedSelections) {
                     this.updateSelections0(this._unappliedSelections.start,
                        this._unappliedSelections.end, eventSource);
                  }
               }
            });
      }
   }

   @Input()
   set model(model: VSRangeSliderModel) {
      this._model = model;
      this.setTopPositions();

      if(!this.adhocFilterListener && this._model.adhocFilter && !this._model.container) {
         this.adhocFilterListener = () => {};
         this.adhocFilterListener = this.adhocFilterService.showFilter(
            this.elementRef, this._model.absoluteName, () => this.adhocFilterListener = null,
            () => !this.model.maxMode);
      }

      this.showToolbar = model.adhocFilter ||
         (model.container && model.containerType === "VSSelectionContainer");

      this.headerBorderBottomColor = this.model?.titleFormat?.border?.bottom ?
         this.model.titleFormat.border.bottom.split(" ")[2] : "#c0c0c0";
      this.headerSeparatorBorderColor = "#c0c0c0";

      // there is no setting for the border color of the separator so just use the border color
      // of the first border that's not null
      const borders = [this.model?.titleFormat?.border?.left,
         this.model?.titleFormat?.border?.right,
         this.model?.titleFormat?.border?.top,
         this.model?.titleFormat?.border?.bottom];

      for(let border of borders) {
         if(!border) {
            continue;
         }

         this.headerSeparatorBorderColor = border.split(" ")[2];
         break;
      }
   }

   get model(): VSRangeSliderModel {
      return this._model;
   }

   @Input()
   set actions(value: RangeSliderActions) {
      if(this.actionSubscription) {
         this.actionSubscription.unsubscribe();
         this.actionSubscription = null;
      }

      this._actions = value;

      if(value) {
         this.actionSubscription = value.onAssemblyActionEvent.subscribe((event) => {
            switch(event.id) {
            case "range-slider open-max-mode":
            case "range-slider close-max-mode":
               this.toggleMaxMode();
               break;
            case "range-slider unselect":
               this.updateSelections();
               break;
            case "range-slider viewer-remove-from-container":
               this.removeChild.emit();
               break;
            case "range-slider viewer-advanced-pane":
               this.showAdvancedPaneDialog();
               break;
            case "range-slider edit-range":
               this.showRangeSliderEditDialog();
               break;
            case "menu actions":
               VSUtil.showDropdownMenus(event.event, this._actions ? this._actions.menuActions : [],
                  this.dropdownService);
               break;
            case "more actions":
               VSUtil.showDropdownMenus(event.event, this._actions ? this._actions.getMoreActions() : [],
                  this.dropdownService);
               break;
            }
         });
      }
   }

   get actions(): RangeSliderActions {
      if(this.mobileDevice) {
         return null;
      }

      return this._actions;
   }

   set isMouseDown(value: boolean) {
      if(value) {
         this.startingLeftSliderPosition = this.model.selectStart;
         this.startingRightSliderPosition = this.model.selectEnd;
      }
      else {
         this.startingLeftSliderPosition = 0;
         this.startingRightSliderPosition = 0;
      }

      this._isMouseDown = value;
   }

   get isMouseDown(): boolean {
      return this._isMouseDown;
   }

   ngOnDestroy(): void {
      super.ngOnDestroy();

      if(this.actionSubscription) {
         this.actionSubscription.unsubscribe();
         this.actionSubscription = null;
      }

      if(this.adhocFilterListener) {
         this.adhocFilterListener();
         this.adhocFilterListener = null;
      }
   }

   ngOnChanges(changes: SimpleChanges) {
      this.calculatePositions();

      if(changes["selected"] && !this.selected) {
         this.editingTitle = false;
      }
   }

   calculatePositions(): void {
      this.rangeLineWidth = this.model.maxRangeBarWidth - (2 * this.pointerOffset) - 1;
      this.widthBetweenTicks = this.getWidthBetweenTicks();
      this.minWidthBetweenTicks = this.rangeLineWidth / 10;
      this._rightHandlePosition = this.model.labels.length > 1 ? this.getRightHandlePosition()
         : this.rangeLineWidth;
      this._leftHandlePosition = this.model.labels.length > 1 ? this.getLeftHandlePosition() : 0;
      this.ticks = this.getTicks();
      this.textSize = GuiTool.measureText(this.getCurrentLabel(), this.model.objectFormat.font);
   }

   private getWidthBetweenTicks(): number {
      return this.rangeLineWidth / (this.model.labels.length - 1);
   }

   private getLeftHandlePosition(): number {
      return this.model.selectStart * this.widthBetweenTicks;
   }

   private getRightHandlePosition(): number {
      return this.model.selectEnd * this.widthBetweenTicks;
   }

   getLabelPosition(): number {
      const objectWidth = this.model.objectFormat.width;
      const centerPosition = (this._rightHandlePosition - this._leftHandlePosition) / 2
         + this._leftHandlePosition - objectWidth / 2 + 4;
      const leftOverflow = this._rightHandlePosition - this.textSize;
      const rightOverflow = objectWidth - this._leftHandlePosition - this.textSize;

      if(leftOverflow < 0) {
         return centerPosition - leftOverflow / 2;
      }
      else if(rightOverflow < 0) {
         return centerPosition + rightOverflow / 2;
      }

      return centerPosition;
   }

   // get the label for the current range
   getCurrentLabel(): string {
      if(!this.model.labels || this.model.labels.length == 0) {
         return "";
      }
      else if(this.model.labels.length == 1) {
         return this.model.labels[0];
      }
      else {
         return this.model.labels[this.model.selectStart] +
                (this.model.upperInclusive ? ".." : "->") +
                this.model.labels[this.model.selectEnd];
      }
   }

   // get the label for the selection being applied when in a selection container
   getContainerLabel(): string {
      return (this.model.container && this.model.selectStart == 0
              && this.model.selectEnd == this.model.labels.length - 1) ?
                 "(none)" : this.getCurrentLabel();
   }

   // get the tick positions (css left)
   private getTicks(): number[] {
      let ticks: number[] = [];
      let i: number;

      for(i = 0; i < this.model.labels.length; i++) {
         let position: number = this.pointerOffset + (i * this.widthBetweenTicks);

         if(i == 0 || (position - ticks[ticks.length - 1] > this.minWidthBetweenTicks)) {
            ticks.push(position);
         }
      }

      return ticks;
   }

   getMinLabel(): string {
      return this.model.labels[0];
   }

   getMaxLabel(): string {
      return this.model.labels[this.model.labels.length - 1];
   }

   getBodyHeight(): number {
      return this.model.containerType === "VSSelectionContainer" ?
         this.model.objectFormat.height - this.model.titleFormat.height :
         this.model.objectFormat.height;
   }

   onClick(event: MouseEvent) {
      if(this.vsWizard) {
         return;
      }

      this.sliderChanged.emit(this.model.absoluteName);
   }

   setMaxRange() {
      this.updateSelections(0, this.model.labels.length - 1);
   }

   mouseDown(event: MouseEvent|TouchEvent, handle: Handle) {
      if(this.model.selectEnd == this.model.selectStart || this.vsWizard) {
         return;
      }

      if(GuiTool.isButton1(event)) {
         this.mouseHandle = handle;
         this.startingXPosition = GuiTool.pageX(event) * (1 / this.viewsheetScale);
         this.isMouseDown = true;

         if(!this.mobileDevice) {
            event.preventDefault();
         }

         if(this.mouseHandle != Handle.None && !GuiTool.isTouch(event)) {
            const mouseMoveListener = this.renderer.listen(
               "document", "mousemove", (evt: MouseEvent) => {
                  this.mouseMove(evt);
               });

            const mouseUpListener = this.renderer.listen(
               "document", "mouseup", (evt: MouseEvent) => {
                  this.mouseUp(evt);
                  mouseMoveListener();
                  mouseUpListener();
               });
         }
      }
   }

   mouseMove(event: MouseEvent|TouchEvent) {
      let movement: number = GuiTool.pageX(event) * (1 / this.viewsheetScale)
         - this.startingXPosition;

      switch(this.mouseHandle) {
      case Handle.Left:
         let oldLeftPos = this._leftHandlePosition;
         this._leftHandlePosition = this.handleMoved(movement, this._leftHandlePosition);
         this.model.selectStart = this.getIndex(this._leftHandlePosition);

         if(this.model.selectStart >= this.model.selectEnd) {
            this.model.selectStart -= 1;
            this._leftHandlePosition = oldLeftPos;
         }

         break;
      case Handle.Right:
         let oldRightPos = this._rightHandlePosition;
         this._rightHandlePosition = this.handleMoved(movement, this._rightHandlePosition);
         this.model.selectEnd = this.getIndex(this._rightHandlePosition);

         if(this.model.selectStart >= this.model.selectEnd) {
            this.model.selectEnd += 1;
            this._rightHandlePosition = oldRightPos;
         }

         break;
      case Handle.Middle:
         this.moveMiddle(movement);
         break;
      default:
      }

      if(this.mouseHandle != Handle.None) {
         event.preventDefault();
         event.stopPropagation();
      }

      this.startingXPosition = GuiTool.pageX(event) * (1 / this.viewsheetScale);
   }

   mouseUp(event: MouseEvent|TouchEvent): void {
      let moved: boolean = this.mouseHandle != Handle.None;

      if(this.isMouseDown) {
         this.mouseHandle = Handle.None;

         if(moved) {
            this.updateSelections(this.model.selectStart, this.model.selectEnd);
         }
      }

      this.isMouseDown = false;
   }

   handleMoved(movement: number, handlePosition: number): number {
      handlePosition += movement;

      if(handlePosition < 0) {
         handlePosition = 0;
      }
      else if(handlePosition > this.rangeLineWidth) {
         handlePosition = this.rangeLineWidth;
      }

      return handlePosition;
   }

   moveMiddle(movement: number): void {
      this._leftHandlePosition += movement;
      this._rightHandlePosition += movement;

      // If either handle hits the edge, backtrack to last viable position
      if(this._leftHandlePosition < 0) {
         this._rightHandlePosition -= this._leftHandlePosition;
         this._leftHandlePosition -= this._leftHandlePosition;
      }
      else if(this._rightHandlePosition > this.rangeLineWidth) {
         this._leftHandlePosition -= this._rightHandlePosition - this.rangeLineWidth;
         this._rightHandlePosition -= this._rightHandlePosition - this.rangeLineWidth;
      }

      this.model.selectStart = this.getIndex(this._leftHandlePosition);
      this.model.selectEnd = this.getIndex(this._rightHandlePosition);
   }

   // get the index (in selection list) of the mouse position
   private getIndex(handlePosition: number): number {
      return Math.round(handlePosition / this.widthBetweenTicks);
   }

   snapToSide(event: MouseEvent): void {
      if(this.vsWizard) {
         return;
      }

      const range: number = this._rightHandlePosition - this._leftHandlePosition;

      if(event.offsetX < this._leftHandlePosition) {
         this.moveMiddle(-range);
      }
      else if(event.offsetX > this._rightHandlePosition) {
         this.moveMiddle(range);
      }

      this.updateSelections(this.model.selectStart, this.model.selectEnd);
   }

   updateTitle(newTitle: string): void {
      if(!this.viewer) {
         this.model.title = newTitle;
         let event: ChangeVSObjectTextEvent = new ChangeVSObjectTextEvent(
            this.model.absoluteName, this.model.title);

         this.viewsheetClient.sendEvent("/events/composer/viewsheet/objects/changeTitle", event);
      }
   }

   private toggleMaxMode(): void {
      let event: MaxObjectEvent = new MaxObjectEvent(this.model.absoluteName,
         this.model.maxMode ? null : this.container);

      this.viewsheetClient.sendEvent(RANGE_SLIDER_MAX_MODE_URL, event);
      this.maxModeChange.emit(
         {assembly: this.model.absoluteName, maxMode: !this.model.maxMode});
   }

   private updateSelections(start?: number, end?: number) {
      if(this.model.submitOnChange) {
         this.updateSelections0(start, end, this.model.absoluteName);
         this._unappliedSelections = null;
      }
      else {
         this._unappliedSelections = {start: start, end: end};
         this.globalSubmitService.updateState(this.model.absoluteName,
            [this._unappliedSelections]);
      }
   }

   private updateSelections0(start?: number, end?: number, eventSource?: string) {
      this.formDataService.checkFormData(
         this.viewsheetClient.runtimeId, this.model.absoluteName, null,
         () => {
            this.viewsheetClient.sendEvent(
               "/events/selectionList/update/" + this.model.absoluteName,
               new ApplySelectionListEvent(null, ApplySelectionListEvent.APPLY,
                  start, end, eventSource));
         },
         () => {
            let event: GetVSObjectModelEvent =
               new GetVSObjectModelEvent(this.model.absoluteName);
            this.viewsheetClient.sendEvent("/events/vsview/object/model", event);
         }
      );
   }

   onHide() {
      this.viewsheetClient.sendEvent(
         "/events/selectionContainer/update/" + this.model.absoluteName,
         new HideTimeSliderEvent(true));
   }

   onShow() {
      this.viewsheetClient.sendEvent(
         "/events/selectionContainer/update/" + this.model.absoluteName,
         new HideTimeSliderEvent(false));
   }

   getActions(): AssemblyActionGroup[] {
      return this._actions ? this._actions.toolbarActions : [];
   }

   private showAdvancedPaneDialog(): void {
      let options: SlideOutOptions = {windowClass: "property-dialog-window"};
      this.fixSlideOutOptions(options);
      const modelUri: string = "../api/" + RANGESLIDER_PROPERTY_URI +
         Tool.encodeURIPath(this.model.absoluteName) + "/" +
         Tool.encodeURIPath(this.viewsheetClient.runtimeId);
      this.modelService.getModel(modelUri).subscribe((data: RangeSliderPropertyDialogModel) => {
         const dialog: RangeSliderPropertyDialog = ComponentTool.showDialog(
            this.modalService, RangeSliderPropertyDialog,
            (result: RangeSliderPropertyDialogModel) => {
               const eventUri: string =
                  "/events/" + RANGESLIDER_PROPERTY_URI + this.model.absoluteName;
               this.viewsheetClient.sendEvent(eventUri, result);
            }, options);
         dialog.model = data;
         dialog.advancedPaneOnly = true;
         dialog.scriptTreeModel = data[1];
         dialog.variableValues = null;
         dialog.runtimeId = this.viewsheetClient.runtimeId;
         dialog.assemblyName = this.model.absoluteName;
      });
   }

   private showRangeSliderEditDialog(): void {
      let options: SlideOutOptions = {windowClass: ""};
      this.fixSlideOutOptions(options);
      const editDialog: RangeSliderEditDialog = ComponentTool.showDialog(
         this.modalService, RangeSliderEditDialog,
         ({min, max}) => {
            let minFound: boolean = false;
            let maxFound: boolean = false;
            const numLabels = this.model.labels.length;
            min = +min;
            max = +max;

            for(let index: number = 0; index < numLabels && !maxFound; index++) {
               const labelValue = parseFloat(this.model.values[index]);

               if(min <= labelValue && !minFound) {
                  this.model.selectStart = min === labelValue ? index : index - 1;
                  minFound = true;
               }

               if(max <= labelValue && minFound) {
                  this.model.selectEnd = Math.max(this.model.selectStart + 1,
                                                  max === labelValue ? index : index - 1);
                  maxFound = true;
               }
            }

            this.updateSelections(this.model.selectStart, this.model.selectEnd);
         }, options);

      editDialog.currentMin = parseFloat(
         this.model.values[this.model.selectStart]?.replace(/,/g, ""));
      editDialog.currentMax = parseFloat(
         this.model.values[this.model.selectEnd]?.replace(/,/g, ""));
      editDialog.rangeMin = parseFloat(this.model.values[0]?.replace(/,/g, ""));
      editDialog.rangeMax = parseFloat(
         this.model.values[this.model.labels.length - 1]?.replace(/,/g, ""));
   }

   private fixSlideOutOptions(options: SlideOutOptions): void {
      // viewer may be embedded in iframe, and also often shown on mobile, slide out pane
      // is not very easy to use on those media
      if(options.popup == null && this.context && (this.context.viewer || this.context.preview)) {
         options.popup = true;
      }
   }

   selectTitle(event: MouseEvent, sel: boolean): void {
      // select vsobject before select parts
      if(!this.selected && !(this.vsInfo && this.vsInfo.formatPainterMode)) {
         return;
      }

      if(!this._model.selectedRegions || !sel) {
         this._model.selectedRegions = [];
      }

      //Determine whether the title was selected before drag
      if(sel && !this.isTitleSelected()) {
         this._model.selectedRegions.push(DataPathConstants.TITLE);
         event.stopPropagation();
      }

      if(sel && this.isTitleSelected()) {
         event.stopPropagation();
      }
   }

   isTitleSelected(): boolean {
      return this._model.selectedRegions
         && this._model.selectedRegions.indexOf(DataPathConstants.TITLE) != -1;
   }

   isInSelectionContainer(): boolean {
      return this.model.container && this.model.containerType === "VSSelectionContainer" ||
         this.model.adhocFilter;
   }

   private setTopPositions(): void {
      const sliderHeight: number = this.isInSelectionContainer()
         ? this.model.objectFormat.height - this.model.titleFormat.height
         : this.model.objectFormat.height;
      this.sliderTop = Math.ceil(sliderHeight / 2) - 4;
   }

   dragStart(event: DragEvent) {
      event.stopPropagation();
      event.preventDefault();
   }

   /**
    * Keyboard navigation for this component.
    * @param {NavigationKeys} key
    */
   protected navigate(key: NavigationKeys): void {
      this.keyNav = true;

      if(this.mouseHandle === Handle.None &&
         this.menuFocus == FocusRegions.NONE &&
         key != NavigationKeys.SPACE)
      {
         if(this.isInSelectionContainer() && this.model.hidden && this.miniMenu) {
            this.menuFocus = FocusRegions.MENU;
            this.miniMenu.nativeElement.focus();
         }
         else {
            this.mouseHandle = Handle.Left;
            this.rangeSliderContainer.nativeElement.focus();
         }

         return;
      }

      const hasMiniMenu: boolean = !!this.actions && this.isInSelectionContainer();
      const miniMenuFocus: boolean = this.menuFocus != FocusRegions.NONE;

      if(miniMenuFocus && key == NavigationKeys.DOWN) {
         this.menuFocus = FocusRegions.NONE;
         this.mouseHandle = Handle.Left;
         this.rangeSliderContainer.nativeElement.focus();
      }
      else if(key == NavigationKeys.DOWN) {
         this.mouseHandle++;

         if(this.mouseHandle > 2) {
            this.mouseHandle = Handle.Left;
         }

         this.focusSelectedHandle();
      }
      else if(hasMiniMenu && !miniMenuFocus &&
         this.miniMenu && key == NavigationKeys.UP)
      {
         this.mouseHandle = Handle.None;
         this.menuFocus = FocusRegions.MENU;
         this.miniMenu.nativeElement.focus();
      }
      else if(key == NavigationKeys.UP) {
         this.mouseHandle--;

         if(this.mouseHandle < 0) {
            this.mouseHandle = Handle.Right;
         }

         this.focusSelectedHandle();
      }
      else if(this.menuFocus == FocusRegions.DROPDOWN) {
         if(key == NavigationKeys.LEFT && this.miniMenu) {
            this.mouseHandle = Handle.None;
            this.menuFocus = FocusRegions.MENU;
            this.miniMenu.nativeElement.focus();
         }
         else if(key == NavigationKeys.SPACE) {
            this.model.hidden ? this.onShow() : this.onHide();
         }
      }
      else if(this.menuFocus == FocusRegions.MENU) {
         if(key == NavigationKeys.RIGHT && this.collapseButtonRef) {
            this.mouseHandle = Handle.None;
            this.menuFocus = FocusRegions.DROPDOWN;
            this.collapseButtonRef.nativeElement.focus();
         }
         else if(key == NavigationKeys.SPACE  && this.miniMenu) {
            const box: ClientRect =
               this.miniMenu.nativeElement.getBoundingClientRect();
            const x: number = box.left;
            const y: number = box.top + box.height;
            const event: MouseEvent = new MouseEvent("click", {
               bubbles: true,
               clientX: x,
               clientY: y
            });
            this.miniMenuComponent.openMenu(event);
            this.miniMenuDropdownOpen = true;
         }
      }
      else if(key == NavigationKeys.LEFT || key == NavigationKeys.RIGHT) {
         const movement: number = key == NavigationKeys.RIGHT ?
            this.widthBetweenTicks : -this.widthBetweenTicks;

         switch(this.mouseHandle) {
         case Handle.Left:
            this._leftHandlePosition = this.handleMoved(movement, this._leftHandlePosition);
            this.model.selectStart = this.getIndex(this._leftHandlePosition);
            break;
         case Handle.Right:
            this._rightHandlePosition = this.handleMoved(movement, this._rightHandlePosition);
            this.model.selectEnd = this.getIndex(this._rightHandlePosition);
            break;
         case Handle.Middle:
            this.moveMiddle(movement);
            break;
         default:
         }

         const debounceKey: string = `VSRangeSlider.ApplyEvent.${this.model.absoluteName}`;
         const callback: () => void = () => {
            this.updateSelections(this.model.selectStart, this.model.selectEnd);
            this.focusSelectedHandle();
         };
         this.debounceService.debounce(debounceKey, callback, 300, null);
      }
   }

   private focusSelectedHandle(): void {
      if(this.mouseHandle === Handle.Left) {
         this.leftHandle.nativeElement.blur();
         this.leftHandle.nativeElement.focus();
      }
      else if(this.mouseHandle === Handle.Right) {
         this.rightHandle.nativeElement.blur();
         this.rightHandle.nativeElement.focus();
      }
      else if(this.mouseHandle === Handle.Middle) {
         this.middleHandle.nativeElement.blur();
         this.middleHandle.nativeElement.focus();
      }
   }

   /**
    * Clear selection made by navigating.
    */
   protected clearNavSelection(): void {
      this.mouseHandle = Handle.None;
      this.menuFocus = FocusRegions.NONE;
      this.keyNav = false;
   }

   title2ResizeMove(event: any): void {
      const width: number = event.rect.width;
      const objwidth: number = this.model.objectFormat.width;
      this.model.titleRatio = (objwidth - width) / objwidth;
   }

   title2ResizeEnd(): void {
      this.updateTitleRatio(this.model.titleRatio);
   }

   updateTitleRatio(ratio: number): void {
      let event: VSObjectEvent = new VSObjectEvent(this.model.container);
      this.viewsheetClient.sendEvent(URI_UPDATE_TITLE_RATIO + ratio, event);
   }

   miniMenuClosed(): void {
      this.miniMenuDropdownOpen = false;
   }
}
