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
   ChangeDetectionStrategy,
   ChangeDetectorRef,
   Component,
   ElementRef,
   EventEmitter,
   HostListener,
   Input,
   NgZone,
   OnInit,
   OnDestroy,
   Output,
   Renderer2,
   ViewChild,
   Optional,
} from "@angular/core";
import { Observable, Subscription } from "rxjs";
import { Tool } from "../../../../../../shared/util/tool";
import { AssemblyActionGroup } from "../../../common/action/assembly-action-group";
import { DragEvent } from "../../../common/data/drag-event";
import { TableDataPath } from "../../../common/data/table-data-path";
import { DataPathConstants } from "../../../common/util/data-path-constants";
import { GuiTool } from "../../../common/util/gui-tool";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { SelectionValue } from "../../../composer/data/vs/selection-value";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { ScaleService } from "../../../widget/services/scale/scale-service";
import { AbstractVSActions } from "../../action/abstract-vs-actions";
import { ExpandTreeNodesCommand } from "../../command/expand-tree-nodes-command";
import { ContextProvider } from "../../context-provider.service";
import { ChangeVSObjectTextEvent } from "../../event/change-vs-object-text-event";
import { MaxObjectEvent } from "../../event/table/max-object-event";
import { VSObjectEvent } from "../../event/vs-object-event";
import { VSSetCellHeightEvent } from "../../event/vs-set-cell-height-event";
import { VSSetMeasuresEvent } from "../../event/vs-set-measures-event";
import { isCompositeSelectionValue, SelectionValueModel } from "../../model/selection-value-model";
import { VSFormatModel } from "../../model/vs-format-model";
import { VSSelectionBaseModel } from "../../model/vs-selection-base-model";
import { VSSelectionListModel } from "../../model/vs-selection-list-model";
import { VSSelectionTreeModel } from "../../model/vs-selection-tree-model";
import { CompositeSelectionValueModel } from "../../model/composite-selection-value-model";
import { CheckFormDataService } from "../../util/check-form-data.service";
import { VSUtil } from "../../util/vs-util";
import { NavigationComponent } from "../abstract-nav-component";
import { AdhocFilterService } from "../data-tip/adhoc-filter.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { MiniMenu } from "../mini-toolbar/mini-menu.component";
import { NavigationKeys } from "../navigation-keys";
import { CellRegion } from "./cell-region";
import { SelectionBaseController, SelectionStateModel } from "./selection-base-controller";
import { SelectionListController } from "./selection-list-controller";
import { MODE, SelectionTreeController } from "./selection-tree-controller";
import { GlobalSubmitService } from "../../util/global-submit.service";
import { SelectionListModel } from "../../model/selection-list-model";
import { SelectionMobileService } from "./services/selection-mobile.service";
import { PopComponentService } from "../data-tip/pop-component.service";
import { VSSelectionContainerModel } from "../../model/vs-selection-container-model";

const URI_UPDATE_CELL_HEIGHT: string = "/events/composer/viewsheet/selectionList/updateCellHeight/";
const URI_UPDATE_MEASURE_SIZE: string = "/events/composer/viewsheet/selectionList/updateMeasureSize/";
const URI_CHANGE_COL_COUNT: string = "/events/composer/viewsheet/selectionList/changeColCount/";
const URI_UPDATE_TITLE_RATIO: string = "/events/composer/viewsheet/currentSelection/titleRatio/";
const SELECTION_MAX_MODE_URL: string = "/events/vs/assembly/max-mode/toggle";
const URI_CHANGE_TITLE: string = "/events/composer/viewsheet/objects/changeTitle";

export enum FocusRegions {
   NONE = -5,
   SEARCH_BAR = -4,
   CLEAR_SEARCH = -3,
   MENU = -2,
   DROPDOWN = -1
}

@Component({
   selector: "vs-selection",
   templateUrl: "vs-selection.component.html",
   styleUrls: ["vs-selection.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class VSSelection extends NavigationComponent<VSSelectionBaseModel>
   implements OnInit, OnDestroy
{
   @Output() onTitleResizeMove = new EventEmitter<number>();
   @Output() onTitleResizeEnd = new EventEmitter<void>();
   @Input() atBottom = false;
   @Input() container: Element;
   @Input() isWizard = false;
   @Input() maxMode = false;
   @Input() popupShowing = false; // used to force change detection
   @Input() objectContainerHeight: number;
   @Input() submittedSelections: Observable<boolean>;
   @Input() containerSelected: boolean;
   @Input() set selected(value: boolean) {
      this._selected = value;

      if(!value) {
         this.showOutsideResizeHandle = false;
         this.clearMap();

         if(this.model) {
            this.model.contextMenuCell = null;
         }
      }

      this.calcCellWidth();
   }
   get selected(): boolean {
      return this._selected;
   }

   @Output() public maxModeChange = new EventEmitter<{assembly: string, maxMode: boolean}>();
   @Output() removeChild = new EventEmitter();
   @Output() onOpenFormatPane = new EventEmitter<VSSelectionBaseModel>();
   @ViewChild("selectionListSearchInput") selectionListSearchInputElementRef: ElementRef;
   @ViewChild("scrollBody") scrollBody: ElementRef;
   @ViewChild("verticalScrollWrapper") verticalScrollWrapper: ElementRef;
   @ViewChild("menu", { read: ElementRef }) miniMenu: ElementRef;
   @ViewChild("menu") miniMenuComponent: MiniMenu;
   @ViewChild("clearSearch") clearSearch: ElementRef;
   @ViewChild("dropdownToggleRef", { read: ElementRef }) dropdownToggleRef: ElementRef;
   @ViewChild("cellContent") cellContent: ElementRef;
   _controller: SelectionBaseController<any>;
   listSelectedString: string = null;
   resizeColumns: Array<number> = [];
   selectedCells: Map<string, Map<number, boolean>> = new Map<string, Map<number, boolean>>();
   selectionValues: SelectionValueModel[] = [];
   selectionValuesTable: SelectionValueModel[][] = [];
   // Max number of cells to show in vs-selection
   visibleValues: number = VSSelection.MAX_VALUES_DISPLAYED_INCREMENT * 5;
   private _actions: AbstractVSActions<VSSelectionBaseModel>;
   private actionSubscription: Subscription;
   private _selected: boolean = false;
   private unApplySubscription: Subscription;
   private subscriptions: Subscription = new Subscription();
   measureMin: number;
   measureMax: number;
   measureRatio: number;
   minTextWidth: number;
   cellWidth: number;
   topMarginTitle: number = 0;
   leftMarginTitle: number = 0;
   leftMargin: number = 0;
   rightMargin: number = 0;
   scrollbarVisible: boolean;
   scrollbarWidth: number = GuiTool.measureScrollbars();
   editingTitle: boolean = false;
   private adhocFilterListener: () => any;
   position = "absolute";
   private scriptApplied = false;
   private mouseUpListener: Function;
   private searchTimer: any;
   private miniMenuOpen: boolean = false;
   FocusRegions = FocusRegions;
   keyNavFocused: boolean = false;
   scale = 1;

   resizeCellTop: number;
   resizeCellHeight: number = 0;
   resizeCellLeft: number;
   resizeCellWidth: number = 0;
   resizeHandleTop: number = 0;
   showResizeBorder: boolean = false;
   showOutsideResizeHandle: boolean = false;
   lastCellSelectedIndex: number = FocusRegions.NONE;
   prevMouseX: number = -1;
   mouseMoveResizeListener: Function;
   mouseUpResizeListener: Function;
   submitOnChange: boolean = true;
   headerBorderBottomColor: string;
   headerSeparatorBorderColor: string;
   private searchPending: boolean = false;

   // Number of values displayed when the selection list is first loaded as well as the number
   // of values to show when click 'More' to show values not displayed
   public static MAX_VALUES_DISPLAYED_INCREMENT: number = 100;

   public static LIST_INDENT: number = 4;
   public static TREE_INDENT: number = 16;

   get topPosition(): number {
      if((this.viewer || this.embeddedVS) && !this.model.maxMode && !this.inContainer) {
         if(this.atBottom && this.model.dropdown &&
            !SelectionBaseController.isHidden(this.model))
         {
            let bodyHeight = this.getBodyHeight();
            let popDown = this.objectContainerHeight - this.model.objectFormat.top -
               this.model.titleFormat.height - bodyHeight > 0;

            return popDown ? this.model.objectFormat.top : this.model.objectFormat.top - bodyHeight;
         }
         else {
            return this.model.objectFormat.top;
         }
      }

      return null;
   }

   get controller(): SelectionBaseController<any> {
      return this._controller;
   }

   set controller(controller: SelectionBaseController<any>) {
      if(this.unApplySubscription) {
         this.unApplySubscription.unsubscribe();
      }

      if(controller) {
         this.unApplySubscription = controller.unappliedSubject.subscribe(unApply => {
            this.globalSubmitService.updateState(this.model.absoluteName, unApply);
         });
      }

      this._controller = controller;
   }

   constructor(protected viewsheetClient: ViewsheetClientService,
               private formDataService: CheckFormDataService,
               private renderer: Renderer2,
               private adhocFilterService: AdhocFilterService,
               private elementRef: ElementRef,
               protected changeDetectorRef: ChangeDetectorRef,
               zone: NgZone,
               private scaleService: ScaleService,
               protected context: ContextProvider,
               protected dataTipService: DataTipService,
               private dropdownService: FixedDropdownService,
               private globalSubmitService: GlobalSubmitService,
               private popService: PopComponentService,
               @Optional() private selectionMobileService?: SelectionMobileService)
   {
      super(viewsheetClient, zone, context, dataTipService);
      this.subscriptions.add(this.scaleService.getScale().subscribe((scale) => {
         this.scale = scale;

         if(this.model?.maxMode) {
            this.changeDetectorRef.detectChanges();
         }
      }));

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

   @Input() set model(value: VSSelectionBaseModel) {
      // if search string has not been submitted, don't refresh otherwise the pending
      // search string is discarded
      if(this._model && this.searchPending) {
         return;
      }

      if(this.model) {
         value.selectedRegions = this.model.selectedRegions;
      }

      let hidden: boolean = this.getHidden(value.dropdown);
      let treeItemChanged = value?.objectType === "VSSelectionTree" &&
          !Tool.isEquals((value as VSSelectionTreeModel)?.root, (this._model as VSSelectionTreeModel)?.root);
      this._model = value;
      this._model.hidden = value.dropdown ? hidden : false;
      this.submitOnChange = this._model.submitOnChange;
      this._model.objectHeight = this._model.dropdown
         ? this._model.titleFormat.height : this._model.objectFormat.height;

      // Reset number of values displayed when the model updates
      this.visibleValues = value.objectType == "VSSelectionList"
         ? VSSelection.MAX_VALUES_DISPLAYED_INCREMENT * 5 : 10000;

      if(value) {
         if(value.objectType === "VSSelectionTree" && (<VSSelectionTreeModel> value).root) {
            this.fillValue((<VSSelectionTreeModel> value).root);
         }
         else if((<VSSelectionListModel> value).selectionList &&
            (<VSSelectionListModel> value).selectionList.selectionValues != null)
         {
            (<VSSelectionListModel> value).selectionList.selectionValues
               .forEach(a => this.fillValue(a));
         }
      }

      if(!this.controller || this.controller.model.absoluteName != value.absoluteName) {
         if(value && value.objectType === "VSSelectionTree") {
            let treeModel: VSSelectionTreeModel = <VSSelectionTreeModel> value;
            let tree: SelectionTreeController = new SelectionTreeController(
               this.viewsheetClient, this.formDataService, value.absoluteName);
            tree.model = treeModel;
            this.controller = tree;
         }
         else {
            let listModel: VSSelectionListModel = <VSSelectionListModel> value;
            let list: SelectionListController = new SelectionListController(
               this.viewsheetClient, this.formDataService, value.absoluteName);
            list.model = listModel;
            this.controller = list;

            if(!this.adhocFilterListener && listModel.adhocFilter && !listModel.container) {
               this.adhocFilterListener = this.adhocFilterService.showFilter(
                  this.elementRef, listModel.absoluteName, () => this.adhocFilterListener = null,
                  () => !this.model.maxMode);
            }
         }
      }
      else {
         this.controller.model = value;
         // if model is refreshed, do not save unapplied selections
         this.controller.unappliedSelections = [];
      }

      let selectionModel = this.controller?.model;

      if(selectionModel?.objectType === "VSSelectionTree" && selectionModel?.expandAll &&
         (!this.scriptApplied || this.viewer || treeItemChanged))
      {
         (<SelectionTreeController> this.controller).expandAllNodes();
         this.scriptApplied = true;
      }

      this.controller.indent = this.controller.model.levels > 1 ?
         VSSelection.TREE_INDENT : VSSelection.LIST_INDENT;

      this.controller.hideExcludedValues();
      this.updateSelectionValues();
      this.updateListSelectedString();
      this.calcCellWidth();
      // Add non-positive margin to title cell and selection cells so that their border
      // appears on top
      const titleBorders = this._model.titleFormat.border;
      const objectBorders = this._model.objectFormat.border;

      if(this.inContainer) {
         if(value.dropdown) {
            objectBorders.bottom = null;
         }

         this.topMarginTitle = -(Tool.getMarginSize(titleBorders.top) +
            Tool.getMarginSize(objectBorders.top));
         this.leftMarginTitle = -(Tool.getMarginSize(titleBorders.left) +
            Tool.getMarginSize(objectBorders.left));
      }
      else {
         this.topMarginTitle = -Tool.getMarginSize(titleBorders.top);
         this.leftMarginTitle = -Math.min(Tool.getMarginSize(titleBorders.left),
            Tool.getMarginSize(objectBorders.left));
      }

      if(this.selectionValues && this.selectionValues.length) {
         const cellFormat: VSFormatModel = this.controller.getCellFormat(this.selectionValues[0]);

         if(cellFormat) {
            let margin = Math.min(Tool.getMarginSize(cellFormat.border.left),
               Tool.getMarginSize(objectBorders.left));
            this.leftMargin = margin === 0 ? 0 : -margin;
            margin = -Math.min(Tool.getMarginSize(cellFormat.border.right),
               Tool.getMarginSize(objectBorders.right));
            this.rightMargin = margin === 0 ? 0 : -margin;
         }
      }

      this.updatePosition();

      const totalCellHeight: number = this.selectionValues.length * this.cellHeight;
      this.scrollbarVisible = totalCellHeight > this.getBodyHeight();

      if(this.inContainer) {
         this.model.objectFormat.height = this.height;
      }

      // set context menu cell when set model, for 'menu actions' button.
      this.setContextMenuCell();

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

   get model(): VSSelectionBaseModel {
      return this._model;
   }

   get disPlayZIndex(): number {
      if(!this.viewer) {
         return null;
      }

      if(this.model.dropdown && !this.isHidden) {
         return this.model.objectFormat.zIndex + 9999;
      }

      let pop = this.popService.isCurrentPopComponent(this.model?.absoluteName, this.model?.container);

      if(pop && this.model.maxMode && this.mobileDevice) {
         return this.model.objectFormat.zIndex + (this.model.container ? 9999 : 9998);
      }

      return this.model.objectFormat.zIndex;
   }

   // optimization, fill in default values omitted in json
   private fillValue(value: SelectionValueModel) {
      if(value.level == null) {
         value.level = 0;
      }

      if(value.label == null) {
         value.label = value.value;
      }

      if(value.formatIndex == null) {
         value.formatIndex = 0;
      }

      if(value.state == null) {
         value.state = SelectionValue.STATE_COMPATIBLE;
      }

      if((<any> value).selectionList) {
         (<CompositeSelectionValueModel> value).selectionList.selectionValues
            .forEach(a => this.fillValue(a));
      }
   }

   setContextMenuCell(): void {
      if(this.selectedCells.size == 1 && this.selectionValues.length > 0) {
         for(let cell of this.selectionValues) {
            let identifier: string = this.getIdentifier(cell);

            if(this.selectedCells.has(identifier)) {
               this.model.contextMenuCell = cell;

               return;
            }
         }
      }
   }

   get showScroll(): boolean {
      let othersCell = this.controller.showOther ? 1 : 0;

      return !this.isHidden &&
         // hide scrollbar in selection container so the right resize handle is not covered.
         (this.selectedCells.size == 0 || !this.inContainer || this.context.preview || this.context.viewer) &&
         this.getBodyHeight() < this.cellHeight * (this.selectionValuesTable.length + othersCell);
   }

   get isHidden(): boolean {
      return SelectionBaseController.isHidden(this.model);
   }

   get height(): number {
      return this.model.dropdown && SelectionBaseController.isHidden(this.model) ?
         this.model.titleFormat.height : this.model.objectFormat.height;
   }

   get width(): number {
      return this.model.objectFormat.width -
         // see editable-object-container.component.html style.width
         (this.selected && this.model.containerType == "VSSelectionContainer" ? 2 : 0);
   }

   @Input()
   get actions(): AbstractVSActions<VSSelectionBaseModel> {
      return this._actions;
   }

   set actions(value: AbstractVSActions<VSSelectionBaseModel>) {
      if(this.actionSubscription) {
         this.actionSubscription.unsubscribe();
         this.actionSubscription = null;
      }

      this._actions = value;

      if(value) {
         this.actionSubscription = value.onAssemblyActionEvent.subscribe((event) => {
            switch(event.id) {
            case "selection-list unselect":
            case "selection-tree unselect":
               this.onUnselect();
               break;
            case "selection-list hide":
            case "selection-tree hide":
               this.onHide();
               break;
            case "selection-list show":
            case "selection-tree show":
               this.onShow();
               break;
            case "selection-list reverse":
            case "selection-tree reverse":
               this.onReverse();
               break;
            case "selection-list sort":
            case "selection-list sort-asc":
            case "selection-list sort-desc":
            case "selection-tree sort":
            case "selection-tree sort-asc":
            case "selection-tree sort-desc":
               this.onSort();
               break;
            case "selection-list search":
            case "selection-tree search":
               this.onSearch();
               break;
            case "selection-list open-max-mode":
            case "selection-list close-max-mode":
            case "selection-tree open-max-mode":
            case "selection-tree close-max-mode":
               this.toggleMaxMode();
               break;
            case "selection-list apply":
            case "selection-tree apply":
               this.controller.applySelections();
               break;
            case "selection-list viewer-remove-from-container":
               this.removeChild.emit();
               break;
            case "selection-tree select-subtree":
               this.onSelectSubtree();
               break;
            case "selection-tree clear-subtree":
               this.onClearSubtree();
               break;
            case "selection-list select-all":
            case "selection-tree select-all":
               this.onSelectAll();
               break;
            case "menu actions":
               let hiddenItem: string[] = [];

               if(this.viewer) {
                  hiddenItem.push("selection-tree select-subtree");
                  hiddenItem.push("selection-tree clear-subtree");
               }

               VSUtil.showDropdownMenus(event.event, this.getMenuActions(),
                                        this.dropdownService, hiddenItem);
               break;
            case "selection-list show-format-pane":
            case "selection-tree show-format-pane":
               this.onOpenFormatPane.emit(this.model);
               break;
            case "more actions":
               VSUtil.showDropdownMenus(event.event, this.getMoreActions(),
                  this.dropdownService, []);
               break;
            }
         });
      }
   }

   ngOnInit() {
      if(!!this.globalSubmitService) {
         this.subscriptions.add(this.globalSubmitService.globalSubmit()
            .subscribe(eventSource => {
               if(!this.model.submitOnChange) {
                  this.controller.applySelections(eventSource);
               }
         }));

         this.subscriptions.add(this.globalSubmitService.updateSelections()
            .subscribe(changes => {
               let changeCells = changes.get(this.model.absoluteName);

               if(!changeCells || changeCells.length == 0) {
                  return;
               }

               if(this.model.submitOnChange) {
                  this.controller.unappliedSelections.push(...changeCells);
                  this.controller.applySelections();
               }
               else {
                  this.controller.updateStatusByValues(changeCells);
                  this.changeDetectorRef.detectChanges();
               }
            }));
      }
   }

   ngOnDestroy(): void {
      if(this.actionSubscription) {
         this.actionSubscription.unsubscribe();
         this.actionSubscription = null;
      }

      if(this.adhocFilterListener) {
         this.adhocFilterListener();
      }

      if(this.subscriptions) {
         this.subscriptions.unsubscribe();
         this.subscriptions = null;
      }

      if(this.unApplySubscription) {
         this.unApplySubscription.unsubscribe();
         this.unApplySubscription = null;
      }

      super.ngOnDestroy();
   }

   // Get the hidden state if the selection is being displayed as a dropdown
   // If the model is not set or the "Show As" was just changed from list to dropdown,
   // hidden is true
   // Otherwise hidden is the same as it was before the model changed
   private getHidden(newDropdownState: boolean): boolean {
      if(this._model) {
         if(newDropdownState && !this._model.dropdown) {
            return true;
         }

         return this._model.hidden;
      }

      return newDropdownState;
   }

   getTitleHeight(): number {
      return this.model.titleFormat.height;
   }

   getTitleWidth(): number {
      const leftMargin: number = Tool.getMarginSize(this._model.objectFormat.border.left);
      const rightMargin: number = Tool.getMarginSize(this._model.objectFormat.border.right);
      const rightTitleMargin: number = Tool.getMarginSize(this._model.titleFormat.border.right);
      const leftTitleMargin: number = Tool.getMarginSize(this._model.titleFormat.border.left);
      const titlew: number = this.model.titleFormat.width;

      if(leftMargin == 0 && rightMargin == 0) {
         return this.inContainer ? titlew - 2 : titlew;
      }
      else {
         let titleWidth = titlew - leftMargin - rightMargin;
         titleWidth += (rightMargin != 0 ? rightTitleMargin : 0);
         titleWidth += (leftMargin != 0 ? leftTitleMargin : 0);
         return titleWidth;
      }
   }

   getTitleCellHeight(): number {
      const bottomMargin: number = Tool.getMarginSize(this.model.titleFormat.border.bottom);
      const topMargin: number = Tool.getMarginSize(this.model.titleFormat.border.top);
      return this.getTitleHeight() - bottomMargin - topMargin;
   }

   getBodyHeight(): number {
      const bottomMargin: number = Tool.getMarginSize(this._model.objectFormat.border.bottom);
      const topMargin: number = Tool.getMarginSize(this._model.objectFormat.border.top);
      const offset = Math.max(0, bottomMargin + topMargin + this.topMarginTitle);
      return this.inContainer ?
         this.model.objectFormat.height - this.model.titleFormat.height :
         this.model.dropdown && !this.model.maxMode ? this.cellHeight * this.model.listHeight
            : this.model.objectFormat.height -
            (!this.viewer || this.model.titleVisible ? this.model.titleFormat.height : 0) - offset;
   }

   getBodyWidth(): number {
      const bodyw: number = this.model.objectFormat.width -
         Tool.getMarginSize(this.model.objectFormat.border.right) -
         Tool.getMarginSize(this.model.objectFormat.border.left) -
         this.leftMargin - this.rightMargin;

      if(this.leftMargin == 0 && this.rightMargin == 0 && this.inContainer) {
         return bodyw - 2 - (this.selected ? 2 : 0);
      }

      return bodyw;
   }

   calcCellWidth(): void {
      if(!this.model) {
         return;
      }

      let width: number = this.getBodyWidth();
      let cols: number = 1;

      if(this.model.objectType === "VSSelectionList") {
         cols = (<VSSelectionListModel> this.model).numCols; // User defined cols
         const maxCols: number = this.selectionValues && this.selectionValues.length || 0;
         cols = Math.max(1, Math.min(cols, maxCols));
      }

      this.cellWidth = Math.floor(width / cols);
   }

   updateListSelectedString(): void {
      this.listSelectedString = null;

      if(this.model.objectType === "VSSelectionList") {
         let selectionValues = this.controller.model.selectionList.selectionValues;

         for(let i = 0; i < selectionValues.length; i++) {
            let v = selectionValues[i];
            if(v.state == 1 || v.state == 9 || v.state == 10) {
               let label = v.label == undefined ? "" : v.label;

               if(this.listSelectedString == null) {
                  this.listSelectedString = label;
               }
               else {
                  this.listSelectedString += ", " + label;
               }
            }
         }
      }

      if(this.listSelectedString == null) {
         this.listSelectedString = "(none)";
      }
   }

   updateTitle(newTitle: string): void {
      if(!this.viewer) {
         this.model.title = newTitle;
         let event: ChangeVSObjectTextEvent = new ChangeVSObjectTextEvent(
            this.model.absoluteName, this.model.title);

         this.viewsheetClient.sendEvent(URI_CHANGE_TITLE, event);
      }
   }

   get pendingSubmit(): boolean {
      return this.controller.unappliedSelections.length > 0;
   }

   // Add asterisk to end of displayed title if selection is set as a dropdown and at least one
   // selection list cell is selected
   getTitle(): string {
      return this.model.title || "";
   }

   onSelectSubtree() {
      const state = this.model.contextMenuCell.state | SelectionValue.STATE_SELECTED;
      (<SelectionTreeController> this.controller).selectSubtree(this.model.contextMenuCell, state);

      if(!this.model.submitOnChange) {
         this.changeDetectorRef.detectChanges();
      }
   }

   onClearSubtree(): void {
      const state = this.model.contextMenuCell.state & ~SelectionValue.STATE_SELECTED;

      if(this.isParentIDTree()) {
         (<SelectionTreeController> this.controller).selectSubtree(this.model.contextMenuCell,
            state);
      }
      else {
         if(this.isCompositeValue(this.model.contextMenuCell) &&
            this.isValueSingleSelection(this.model.contextMenuCell))
         {
            (<SelectionTreeController> this.controller)
               .clearSingleCellSubTree(<CompositeSelectionValueModel> this.model.contextMenuCell);
         }
         else {
            (<SelectionTreeController> this.controller).setSubtree(this.model.contextMenuCell,
               state);
         }
      }

      if(!this.model.submitOnChange) {
         this.changeDetectorRef.detectChanges();
      }
   }

   toggleMaxMode(): void {
      let event: MaxObjectEvent = new MaxObjectEvent(this.model.absoluteName,
         this.model.maxMode ? null : this.container);

      this.viewsheetClient.sendEvent(SELECTION_MAX_MODE_URL, event);
      this.maxModeChange.emit(
         {assembly: this.model.absoluteName, maxMode: !this.model.maxMode});

      if(this.isHidden && !this.model.maxMode) {
         this.controller.showSelf();
      }
   }

   onSearch() {
      this.clearNavSelection();
      this.lastCellSelectedIndex = FocusRegions.SEARCH_BAR;
      this.updateMiniToolbarFocus(false);
      this.model.searchDisplayed = true;
      this.changeDetectorRef.detectChanges();
      let elementRef = this.selectionListSearchInputElementRef;

      setTimeout(function() {
         elementRef.nativeElement.focus();
      }, 200);
   }

   hideSearchDisplay() {
      if(this.lastCellSelectedIndex != FocusRegions.CLEAR_SEARCH) {
         this.model.searchDisplayed = false;
      }
   }

   onSearchKeyUp() {
      clearTimeout(this.searchTimer);
      this.searchPending = true;
      this.searchTimer = setTimeout(() => {
         this.searchPending = false;
         this.controller.searchSelections(this.model.searchString);
      }, 500);
   }

   preventPropagation(event: KeyboardEvent) {
      if(event.keyCode === 32) {
         event.stopPropagation();
      }
   }

   onCloseSearch() {
      this.controller.searchSelections("");
   }

   onSort() {
      this.controller.sortSelections();
   }

   onReverse() {
      if(this.submitOnChange) {
         this.controller.reverseSelections();
      }
      else {
         let submitOnchange = this.model.submitOnChange;
         this.model.submitOnChange = false;
         let selectionList: SelectionListModel = this.getSelectionList();

         if(!selectionList || !selectionList.selectionValues) {
            return;
         }

         if(this.model.objectType === "VSSelectionList") {
            this.reverseSelectionList(selectionList.selectionValues);
         }
         else if(this.model.objectType === "VSSelectionTree") {
            this.reverseSelectionTree(selectionList.selectionValues);
         }

         this.model.submitOnChange = submitOnchange;
         this.changeDetectorRef.detectChanges();
      }
   }

   onUnselect() {
      if(this.submitOnChange) {
         this.controller.clearSelections();
         this.controller.unappliedSelections = [];
      }
      else {
         let selectionList: SelectionListModel = this.getSelectionList();

         if(!selectionList || !selectionList.selectionValues) {
            return;
         }

         this.selectAll(selectionList.selectionValues, false, true);
         this.changeDetectorRef.detectChanges();
      }
   }

   private getSelectionList(): SelectionListModel {
      let selectionList: SelectionListModel;

      if(this.model && this.model.objectType === "VSSelectionList") {
         selectionList = (<VSSelectionListModel> this.model).selectionList;
      }
      else {
         let root: CompositeSelectionValueModel = (
             <VSSelectionTreeModel> this.model).root;

         if(!root || !root.selectionList) {
            return null;
         }

         selectionList = root.selectionList;
      }

      return selectionList;
   }

   private reverseSelectionList(selectionValues: SelectionValueModel[]): void {
      for(let i = 0; i < selectionValues.length; i++) {
         const selectionValue = selectionValues[i];
         this.updateSelectionState(selectionValue);
      }
   }

   private reverseSelectionTree(selectionValues: SelectionValueModel[]) {
      let reverseSelected: SelectionValueModel[] = [];
      this.reverseSelectionTree0(selectionValues, reverseSelected);

      reverseSelected.forEach(reverse => {
         let state = reverse.state;
         state = state & ~SelectionValue.STATE_SELECTED;
         this.controller.selectionStateUpdated(reverse, state);
      });

      this.changeDetectorRef.detectChanges();
   }

   private reverseSelectionTree0(selectionValues: SelectionValueModel[] ,
                                 reverseSelected: SelectionValueModel[]): SelectionValueModel[]
   {
      let reverse: SelectionValueModel[] = [];

      for(let i = 0; i < selectionValues.length; i++) {
         const selectionValue = selectionValues[i];
         let state = selectionValue.state;

         if(!SelectionValue.isSelected(state)) {
            if(SelectionValue.isExcluded(state)) {
               state = SelectionValue.applyWasExcluded(state);
            }

            state = state & ~SelectionValue.STATE_EXCLUDED;
            state = state | SelectionValue.STATE_SELECTED;
            this.controller.selectionStateUpdated(selectionValue, state);
            reverse.push(selectionValue);

            if(isCompositeSelectionValue(selectionValue) &&
                selectionValue.selectionList.selectionValues &&
                selectionValue.selectionList.selectionValues.length > 0)
            {
               this.selectAll(selectionValue.selectionList.selectionValues, true);
            }
         }
         else {
            if(isCompositeSelectionValue(selectionValue) &&
                selectionValue.selectionList.selectionValues &&
                selectionValue.selectionList.selectionValues.length > 0)
            {
               selectionValue.selectionList.selectionValues
                   .forEach(v => v.parentNode = selectionValue);
               let reverseChildren =
                   this.reverseSelectionTree0(selectionValue.selectionList.selectionValues,
                       reverseSelected);

               if(reverseChildren.length > 0) {
                  reverse.push(selectionValue);
               }
               else {
                  reverseSelected.push(selectionValue);
               }
            }
            else {
               reverseSelected.push(selectionValue);
            }
         }
      }

      return reverse;
   }

   onHide() {
      if(this.inContainer) {
         this.controller.hideChild();
      }
      else {
         this.controller.hideSelf();
      }
   }

   onShow() {
      if(this.inContainer) {
         this.controller.showChild();
      }
      else {
         this.controller.showSelf();

         // Listen for mouseup if the mousedown was not on the viewsheet-pane or
         // viewer-app scrollbar
         this.mouseUpListener = this.renderer.listen(
            "document", "mousedown", (event: MouseEvent) => {
               if(GuiTool.parentContainsClass(<any> event.target, "fixed-dropdown") ||
                  GuiTool.parentContainsClass(<any> event.target, "mobile-toolbar"))
               {
                  return;
               }

               if(!this.elementRef.nativeElement.contains(event.target)) {
                  this.onHide();
                  this.mouseUpListener();
               }
            });
      }
   }

   getActions(): AssemblyActionGroup[] {
      return this._actions ? this._actions.toolbarActions : [];
   }

   getMenuActions(): AssemblyActionGroup[] {
      return this._actions ? this._actions.menuActions : [];
   }

   getMoreActions(): AssemblyActionGroup[] {
      return this._actions ? this._actions.getMoreActions() : [];
   }

   startResize(event: MouseEvent): void {
      if(event.button != 0) {
         return;
      }

      let newWidth: number = this.cellWidth;
      this.prevMouseX = event.pageX;

      this.mouseMoveResizeListener = this.renderer.listen("document", "mousemove",
         (evt: MouseEvent) => {
            let diff: number = evt.pageX - this.prevMouseX;
            newWidth += diff;
            this.resizeCells(diff);
         });

      this.mouseUpResizeListener = this.renderer.listen("document", "mouseup",
         (evt: MouseEvent) => {
            this.cellWidth = newWidth;
            this.mouseMoveResizeListener();
            this.mouseUpResizeListener();
         });
      event.stopPropagation();
      event.preventDefault();
   }

   resizeCells(diff: number): void {
      if(this.viewer) {
         return;
      }

      // user-set number of columns
      const cols: number = (<VSSelectionListModel> this.model).numCols;
      const bodyWidth = this.getBodyWidth();
      const curWidth: number = Math.floor(bodyWidth / cols);
      const width: number = curWidth + diff;

      if(width < 0 || width > bodyWidth) {
         return;
      }

      let columns: number = Math.round(bodyWidth / width);
      columns = Math.min(columns, this.selectionValues && this.selectionValues.length || 0);
      this.resizeColumns = Array(columns).fill(width);
      this.showResizeBoundary(width, false);
   }

   get resizeColumnWidth(): number {
      return Math.floor(this.getBodyWidth() / this.resizeColumns.length);
   }

   @HostListener("document: mouseup")
   onMouseUp() {
      if(!this.viewer && this.resizeColumns.length > 0) {
         let cols: number = this.resizeColumns.length;
         this.updateColumns(cols);
      }

      this.resizeColumns = [];
   }

   onKeyDown(event: KeyboardEvent) {
      if(event.keyCode == 17) {
         this.model.submitOnChange = false;
      }
   }

   @HostListener("document: keyup", ["$event"])
   onKeyUp(event: KeyboardEvent) {
      if(event.keyCode == 17) {
         this.model.submitOnChange = this.submitOnChange;

         if(this.submitOnChange) {
            this.controller.applySelections();
         }
      }
   }

   updateSelectionState(value: SelectionValueModel, event?: {toggle: boolean, toggleAll: boolean}) {
      if(this.mobileDevice && !this.isMaxMode()) {
         return;
      }

      let state: number = value.state;
      const cellSelected = SelectionValue.isSelected(state);
      const toggle: boolean = !!event?.toggle;
      const toggleAll = !!event?.toggleAll;
      let old_singleSelection = this.isValueSingleSelection(value);
      this.updateSingleSelection(value, toggle, toggleAll);
      let curr_singleSelection = this.isValueSingleSelection(value);

      // when toggle and click the selected cell, should keep the selected state.
      if((curr_singleSelection || old_singleSelection) && !cellSelected) {
         state = this.selectCell(state);
      }
      // if not toggle, change cell the state.
      else if(!curr_singleSelection && !old_singleSelection) {
         state = this.updateCellState(cellSelected, state);
      }

      this.controller.selectionStateUpdated(value, state, toggle, toggleAll);

      if(!this.model.submitOnChange && curr_singleSelection) {
         this.updateSelectionValues();
      }

      if(this.model.dropdown && curr_singleSelection && !this.model.maxMode && !toggle && !toggleAll) {
         this.controller.hideSelf();
      }

      if(this.model.submitOnChange) {
         this.changeDetectorRef.detectChanges();
      }
   }

   private updateCellState(cellSelected: boolean, state: number) {
      return cellSelected ? this.unSelectCell(state) : this.selectCell(state);
   }

   private selectCell(state) {
      if(SelectionValue.isExcluded(state)) {
         state = SelectionValue.applyWasExcluded(state);
      }

      state = state & ~SelectionValue.STATE_EXCLUDED;
      state = state | SelectionValue.STATE_SELECTED;

      return state;
   }

   private unSelectCell(state) {
      state = state & ~SelectionValue.STATE_SELECTED;

      if(SelectionValue.wasExcluded(state)) {
         state = state | SelectionValue.STATE_EXCLUDED;
         state = SelectionValue.applyWasNotExcluded(state);
      }

      return state;
   }

   private updateSingleSelection(value: SelectionValueModel, toggle: boolean, toggleAll: boolean) {
      if(!toggle && !toggleAll) {
         return;
      }

      if(this.model.objectType == "VSSelectionList") {
         this.model.singleSelection = !this.model.singleSelection;
      }
      else if(this.model.objectType == "VSSelectionTree") {
         let selectTreeModel = <VSSelectionTreeModel> this.model;

         if(!selectTreeModel.singleSelectionLevels) {
            selectTreeModel.singleSelectionLevels = [];
         }

         let singleSelectionLevels = selectTreeModel.singleSelectionLevels;
         let idx = singleSelectionLevels.indexOf(value.level);

         if(idx == -1) {
            singleSelectionLevels.push(value.level);
         }
         else {
            singleSelectionLevels.splice(idx, 1);
         }

         this.model.singleSelection = singleSelectionLevels.length != 0;
      }
   }

   selectRegion(event: any, index: number, region: CellRegion,
                selectionValue: SelectionValueModel): void
   {
      if(!this.selected && !this.viewer && this.vsInfo && !this.vsInfo.formatPainterMode) {
         return;
      }

      this.lastCellSelectedIndex = index;
      let cell: SelectionValueModel = this.selectionValues[index];
      let identifier: string = this.getIdentifier(cell);

      if(event.ctrlKey || event.shiftKey) {
         if(this.selectedCells.has(identifier)) {
            this.selectedCells.get(identifier).set(region, true);
         }
         else {
            this.selectedCells.set(identifier, new Map<number, boolean>());
            this.selectedCells.get(identifier).set(region, true);
         }
      }
      else {
         // if measure text/bar is already selected, clicking on it again selects the
         // entire cell so it can be selected/resized more easily.
         if(this.selectedCells.has(identifier) && region != CellRegion.LABEL) {
            if(this.selectedCells.get(identifier).get(region)) {
               region = CellRegion.LABEL;
            }
         }

         this.clearMap();
         this.clearSelectedRegion();
         this.selectedCells.set(identifier, new Map<number, boolean>());
         this.selectedCells.get(identifier).set(region, true);
         this.model.contextMenuCell = selectionValue;
      }

      this.selectedCellsChanged();
      this.addDataPath(cell, region);
      this.setOutsideHandlePosition();
   }

   selectTitle(event: MouseEvent): void {
      // select vsobject before select parts
      if(!this.selected && !this.vsInfo.formatPainterMode || this.viewer) {
         return;
      }

      this.model.contextMenuCell = null;
      this.vsInfo.selectAssembly(this.model);

      if(!event.shiftKey && !event.ctrlKey) {
         this.clearMap();
         this.clearSelectedRegion();
         this.model.selectedRegions.push(DataPathConstants.TITLE);
      }
      else if(this.model.selectedRegions.indexOf(DataPathConstants.TITLE) == -1) {
         this.model.selectedRegions.push(DataPathConstants.TITLE);
      }
   }

   isTitleSelected(): boolean {
      return this.model.selectedRegions
         && this.model.selectedRegions.indexOf(DataPathConstants.TITLE) != -1;
   }

   clearSelectedRegion() {
      if(this.model.selectedRegions != null) {
         this.model.selectedRegions.splice(0, this.model.selectedRegions.length);
      }
      else {
         this.model.selectedRegions = [];
      }
   }

   showAllValues(event: MouseEvent): void {
      event.stopPropagation();
      this.controller.showOther = false;
      this.controller.showAllValues();
      this.updateSelectionValues();
      this.updateTable();
   }

   updateSelectionValues(): void {
      this.selectionValues = this.controller.visibleValues;
      this.updateTable();

      if(this.controller.model.selectionList) {
         this.measureMin = this.controller.model.selectionList.measureMin;
         this.measureMax = this.controller.model.selectionList.measureMax;
      }
      else if(this.controller.model.root) {
         this.measureMin = this.controller.model.root.selectionList.measureMin;
         this.measureMax = this.controller.model.root.selectionList.measureMax;
      }

      this.measureRatio = this.getMeasureRatio();
      this.selectedCellsChanged();
   }

   public folderToggled() {
      this.updateSelectionValues();
   }

   // force selected status to be updated in cells
   private selectedCellsChanged() {
      this.selectedCells = Tool.clone(this.selectedCells);
   }

   get numCols(): number {
      return this.model.objectType === "VSSelectionList" ?
         (<VSSelectionListModel> this.model).numCols : 1;
   }

   getIndex(row: number, col: number): number {
      return row * this.numCols + col;
   }

   updateTable(): void {
      const table: SelectionValueModel[][] = [];
      const ncol = this.numCols;

      if(ncol > 0) {
         const nrow = Math.ceil(this.selectionValues.length / this.numCols);
         let row: SelectionValueModel[] = [];
         let index: number;

         for(let r = 0; r < nrow; r++) {
            for(let c = 0; c < ncol; c++) {
               index = r * ncol + c;
               row.push(this.selectionValues[index]);
            }

            if(row.length > 0) {
               table.push(row);
               row = [];
            }

            if(index >= this.visibleValues) {
               break;
            }
         }
      }

      this.selectionValuesTable = table;
      this.calcCellWidth();
   }

   getMeasureRatio(): number {
      if(this.selectionValues == null || this.selectionValues.length == 0) {
         return null;
      }

      let label: number = 0; // max label length
      let measure: number = 0; // max measure text length
      let measureText: string = ""; // longest measure label

      for(let value of this.selectionValues) {
         label = Math.max(label, value.label.length);

         if(value.measureLabel && value.measureLabel.length > measure) {
            measureText = value.measureLabel;
            measure = value.measureLabel.length;
         }
      }

      this.minTextWidth = GuiTool.measureText(measureText, this.model.objectFormat.font);
      const ratio = measure / (label + measure);
      return Math.max(0.25, ratio);
   }

   private setOutsideHandlePosition(): void {
      if(this.model.objectType === "VSSelectionList" && !this.viewer) {
         const numCols = (<VSSelectionListModel> this.model).numCols;
         this.showOutsideResizeHandle = ((this.lastCellSelectedIndex + 1) % numCols) == 0;

         const row = Math.floor(this.lastCellSelectedIndex / numCols);
         this.resizeHandleTop = this.model.titleFormat.height - 4
            + (row + 0.5) * this.cellHeight - this.scrollBody.nativeElement.scrollTop;
      }
   }

   showResizeBoundary(dim: number, heightChange: boolean) {
      this.showResizeBorder = true;
      const numCols = this.model.objectType === "VSSelectionList" ?
         (<VSSelectionListModel> this.model).numCols : 1;
      const row = Math.floor(this.lastCellSelectedIndex / numCols);
      const col = this.lastCellSelectedIndex % numCols;

      this.resizeCellTop = this.cellHeight * row;
      this.resizeCellLeft = this.cellWidth * col;
      this.resizeCellHeight = heightChange ? dim : this.cellHeight;
      this.resizeCellWidth = heightChange ? this.cellWidth : dim;
      this.changeDetectorRef.detectChanges();
   }

   updateCellHeight(): void {
      this.showResizeBorder = false;

      let vsEvent: VSSetCellHeightEvent =
         new VSSetCellHeightEvent(this.model.absoluteName, this.model.cellHeight);
      this.viewsheetClient.sendEvent(URI_UPDATE_CELL_HEIGHT, vsEvent);
      this.changeDetectorRef.detectChanges();
   }

   updateMeasures(event: {text: number, bar: number}): void {
      let vsEvent: VSSetMeasuresEvent =
         new VSSetMeasuresEvent(this.model.absoluteName, event.text, event.bar);
      this.viewsheetClient.sendEvent(URI_UPDATE_MEASURE_SIZE, vsEvent);
   }

   updateColumns(cols: number): void {
      this.showResizeBorder = false;
      (<VSSelectionListModel> this.model).numCols = cols;
      this.setOutsideHandlePosition();
      this.changeDetectorRef.detectChanges();

      let event: VSObjectEvent = new VSObjectEvent(this.model.absoluteName);
      this.viewsheetClient.sendEvent(URI_CHANGE_COL_COUNT + cols, event);
      this.changeDetectorRef.detectChanges();
   }

   updateTitleRatio(ratio: number): void {
      let event: VSObjectEvent = new VSObjectEvent(this.model.container);
      this.viewsheetClient.sendEvent(URI_UPDATE_TITLE_RATIO + ratio, event);
   }

   private addDataPath(cell: SelectionValueModel, region: CellRegion): void {
      let path: TableDataPath;
      let isFolder: boolean = cell && "selectionList" in cell;

      if(region == CellRegion.MEASURE_TEXT) {
         path = Tool.clone(DataPathConstants.MEASURE_TEXT);
         path.level = cell.level;
      }
      else if(region == CellRegion.MEASURE_BAR) {
         path = Tool.clone(DataPathConstants.MEASURE_BAR);
         path.level = cell.level;
      }
      else if(region == CellRegion.MEASURE_N_BAR) {
         path = Tool.clone(DataPathConstants.MEASURE_N_BAR);
         path.level = cell.level;
      }
      else if(isFolder) {
         path = Tool.clone(DataPathConstants.GROUP_HEADER_CELL);
         path.level = cell.level;
      }
      else {
         path = Tool.clone(DataPathConstants.DETAIL);
      }

      if(this.model.selectedRegions && this.model.selectedRegions.indexOf(path) == -1) {
         this.model.selectedRegions.push(path);
      }
   }

   private getSingleSelection(): SelectionStateModel {
      if(this.model.singleSelection) {
         const firstSelection = this.selectionValues[0];

         if(isCompositeSelectionValue(firstSelection)) {
            const value: string[] = [];
            let selection: SelectionValueModel = firstSelection;

            while(isCompositeSelectionValue(selection) && selection.selectionList.selectionValues.length > 0) {
               value.push(selection.value);
               selection = selection.selectionList.selectionValues[0];
            }

            value.push(selection.value);
            return {value, selected: SelectionValue.isSelected(firstSelection.state)};
         }
         else {
            return {
               value: [firstSelection.value],
               selected: SelectionValue.isSelected(this.selectionValues[0].state)
            };
         }
      }

      return null;
   }

   private clearMap(): void {
      this.selectedCells = new Map<string, Map<number, boolean>>();
   }

   expandList(event: MouseEvent): void {
      event.stopPropagation();
      this.visibleValues += VSSelection.MAX_VALUES_DISPLAYED_INCREMENT;
      this.updateTable();
   }

   private updatePosition(): void {
      if(!this.viewer) {
         this.position = "relative";
      }
      else if(!this.model || !this.model.container ||
              this.model.inEmbeddedViewsheet && !this.model.container)
      {
         this.position = "absolute";
      }
      else {
         this.position = "relative";
      }
   }

   titleResizeMove(height: number): void {
      this.onTitleResizeMove.emit(height);
   }

   titleResizeEnd(): void {
      this.onTitleResizeEnd.emit();
   }

   title2ResizeMove(event: any): void {
      const width: number = event.rect.width;
      const objwidth: number = this.model.objectFormat.width;
      this.model.titleRatio = (objwidth - width) / objwidth;
   }

   title2ResizeEnd(): void {
      this.updateTitleRatio(this.model.titleRatio);
   }

   processExpandTreeNodesCommand(command: ExpandTreeNodesCommand): void {
      if(command.scriptChanged && command.expand && this.controller) {
         (<SelectionTreeController> this.controller).expandAllNodes();
         this.scriptApplied = true;
         this.updateSelectionValues();
      }
   }

   get othersFormat(): VSFormatModel {
      return this.controller.getCellFormat(<SelectionValueModel> { formatIndex: -1 });
   }

   dragStart(event: DragEvent) {
      if(this.editingTitle) {
         event.stopPropagation();
         event.preventDefault();
      }
   }

   /**
    * Keyboard navigation for this component.
    * @param {NavigationKeys} key
    */
   protected navigate(key: NavigationKeys): void {
      this.keyNavFocused = true;

      if(this.miniMenuOpen || key == null) {
         return;
      }

      let index: number = 0;
      const list: boolean = this.model.objectType === "VSSelectionList";
      let step: number = 1;

      if(this.lastCellSelectedIndex == FocusRegions.SEARCH_BAR) {
         if(key == NavigationKeys.RIGHT) {
            index = FocusRegions.CLEAR_SEARCH;
            this.lastCellSelectedIndex = index;

            if(!!this.clearSearch) {
               this.clearSearch.nativeElement.focus();
            }
         }
         else if(key == NavigationKeys.DOWN) {
            this.hideSearchDisplay();
            index = this.model.dropdown || this.inContainer ? FocusRegions.MENU : 0;
            this.model.searchDisplayed = false;
         }
      }
      else if(this.lastCellSelectedIndex == FocusRegions.CLEAR_SEARCH) {
         if(key == NavigationKeys.LEFT) {
            index = FocusRegions.SEARCH_BAR;

            if(!!this.selectionListSearchInputElementRef) {
               this.selectionListSearchInputElementRef.nativeElement.focus();
            }
         }
         else if(key == NavigationKeys.SPACE) {
            this.onCloseSearch();
            index = FocusRegions.CLEAR_SEARCH;
         }
         else if(key == NavigationKeys.DOWN) {
            this.hideSearchDisplay();
            index = this.model.dropdown || this.inContainer ? FocusRegions.MENU : 0;
            this.model.searchDisplayed = false;
         }
      }
      else if(!list && (key == NavigationKeys.LEFT || key == NavigationKeys.RIGHT)) {
         index = this.lastCellSelectedIndex;
         let cell: SelectionValueModel = this.selectionValues[this.lastCellSelectedIndex];

         if(key == NavigationKeys.LEFT && this.controller.isNodeOpen(cell)) {
            this.controller.toggleNode(cell);
            this.updateSelectionValues();
         }
         else if(key == NavigationKeys.RIGHT && !this.controller.isNodeOpen(cell)) {
            this.controller.toggleNode(cell);
            this.updateSelectionValues();
         }
      }
      else if(key == NavigationKeys.SPACE) {
         index = this.lastCellSelectedIndex;

         if(this.lastCellSelectedIndex == FocusRegions.MENU) {
            if(!!this.miniMenu) {
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
               this.miniMenuOpen = true;
            }
         }
         else if(this.lastCellSelectedIndex == FocusRegions.DROPDOWN){
            this.model.hidden ? this.onShow() : this.onHide();
         }
         else {
            let cell: SelectionValueModel = this.selectionValues[this.lastCellSelectedIndex];
            this.updateSelectionState(cell);
         }
      }
      else {
         if(key == NavigationKeys.DOWN) {
            this.updateMiniToolbarFocus(false);
         }

         if(list && (key == NavigationKeys.DOWN || key == NavigationKeys.UP)) {
            step = (<VSSelectionListModel> this.model).numCols;
         }

         if(!!this.selectedCells && this.selectedCells.size != 0) {
            if(key == NavigationKeys.DOWN || key == NavigationKeys.RIGHT) {
               this.lastCellSelectedIndex += step;
               index = this.lastCellSelectedIndex;

               if(index >= this.selectionValues.length) {
                  this.lastCellSelectedIndex = this.selectionValues.length - 1;
                  return;
               }
            }
            else if(key == NavigationKeys.UP || key == NavigationKeys.LEFT) {
               this.lastCellSelectedIndex -= step;
               index = this.lastCellSelectedIndex;

               if(index < 0 && key == NavigationKeys.UP) {
                  this.clearNavSelection();

                  if(this.model.dropdown || this.inContainer) {
                     this.keyNavFocused = true;
                     this.lastCellSelectedIndex = FocusRegions.MENU;
                  }
                  else {
                     this.lastCellSelectedIndex = FocusRegions.NONE;
                     this.updateMiniToolbarFocus(true);
                  }

                  return;
               }
               else if(index < 0) {
                  index = 0;
                  this.lastCellSelectedIndex = 0;
               }
            }
         }
         else if(this.model.dropdown || this.inContainer) {
            const open: boolean = !this.model.hidden;

            if(this.model.hidden && key != null &&
               this.lastCellSelectedIndex == FocusRegions.NONE)
            {
               index = FocusRegions.MENU;
            }
            else if(!this.model.hidden && key != null &&
               this.lastCellSelectedIndex == FocusRegions.NONE)
            {
               index = 0;
            }
            else if(this.lastCellSelectedIndex == FocusRegions.MENU &&
               key == NavigationKeys.RIGHT)
            {
               index = FocusRegions.DROPDOWN;
            }
            else if(this.lastCellSelectedIndex == FocusRegions.DROPDOWN &&
               key == NavigationKeys.LEFT)
            {
               index = FocusRegions.MENU;
            }
            else if(open && (this.lastCellSelectedIndex == FocusRegions.DROPDOWN ||
                  this.lastCellSelectedIndex == FocusRegions.MENU) &&
               key == NavigationKeys.DOWN)
            {
               index = 0;
            }
            else {
               return;
            }
         }
      }

      if(index >= 0) {
         let cell: SelectionValueModel = this.selectionValues[index];
         let identifier: string = this.getIdentifier(cell);

         this.clearMap();
         this.selectedCells.set(identifier, new Map<number, boolean>());
         this.selectedCells.get(identifier).set(CellRegion.LABEL, true);

         if(!!this.scrollBody) {
            // If the selected cell is out of view, scroll to it.
            const row: number = Math.ceil((index + 1) / step);
            const height: number = this.cellHeight * row;

            const containerHeight: number = this.getBodyHeight();
            const visible: number =
               containerHeight + this.scrollBody.nativeElement.scrollTop;

            if(visible < height) {
               this.scrollBody.nativeElement.scrollTop = height - containerHeight;
            }
            else if((visible - containerHeight) >= (height - this.cellHeight)) {
               this.scrollBody.nativeElement.scrollTop = height - this.cellHeight;
            }
         }
      }

      if(index == FocusRegions.MENU && !!this.miniMenu) {
         this.miniMenu.nativeElement.focus();
      }
      else if(index == FocusRegions.DROPDOWN && !!this.dropdownToggleRef) {
         this.dropdownToggleRef.nativeElement.focus();
      }

      this.lastCellSelectedIndex = index;
   }

   public getIdentifier(cell: SelectionValueModel) {
      let id: string = cell.value;

      while(cell.parentNode) {
         cell = cell.parentNode;
         id += ":" + cell.value;
      }

      return id;
   }

   /**
    * Clear selection make by navigating.
    */
   protected clearNavSelection(): void {
      this.clearMap();
      this.lastCellSelectedIndex = FocusRegions.NONE;
      this.keyNavFocused = false;
   }

   miniMenuClosed(): void {
      this.miniMenuOpen = false;
   }

   onSelectAll(): void {
      let submitOnChange = this.model.submitOnChange;

      this.model.submitOnChange = false;
      this.selectAll(this.selectionValues);
      this.model.submitOnChange = submitOnChange;

      if(this.submitOnChange) {
         this.controller.applySelections();
      }
      else {
         this.changeDetectorRef.detectChanges();
      }
   }

   private selectAll(selectionValues: SelectionValueModel[], select: boolean = true,
                     force: boolean = false)
   {
      for(let i = 0; i < selectionValues.length; i++) {
         const selectionValue = selectionValues[i];

         if(force || SelectionValue.isIncluded(selectionValue.state) ||
            SelectionValue.isCompatible(selectionValue.state))
         {
            let stateValue = selectionValue.state & ~SelectionValue.DISPLAY_STATES;

            if(select) {
               stateValue = stateValue | SelectionValue.STATE_SELECTED;
            }
            else {
               stateValue = stateValue & ~SelectionValue.STATE_SELECTED;
            }

            this.controller.selectionStateUpdated(selectionValue, stateValue);
         }

         if(isCompositeSelectionValue(selectionValue) &&
            selectionValue.selectionList.selectionValues)
         {
            selectionValue.selectionList.selectionValues
               .forEach(v => v.parentNode = selectionValue);
            this.selectAll(selectionValue.selectionList.selectionValues, select);
         }
      }
   }

   public resized(): void {
      super.resized();
      this.model.titleFormat.width = this.model.objectFormat.width;
      // OnPush so we need to explicitly call change detection
      // to make sure the table is redrawn to match the new size during resizing
      this.changeDetectorRef.detectChanges();
   }

   // expand cell height on mobile for easier click (matches combobox behavior on mobile)
   public get cellHeight(): number {
      return this.mobileDevice ? Math.max(40, this.model.cellHeight) : this.model.cellHeight;
   }

   // toggle dropdown on entire header on mobile (usability)
   public headerClick() {
      if(this.mobileDevice && this.model.dropdown && this.isMaxMode()) {
         if(this.model.hidden) {
            this.onShow();
         }
         else {
            this.onHide();
         }
      }
   }

   private isMaxMode(): boolean {
      if(this.model.maxMode) {
         return true;
      }

      let selectionContainer = this.model.container;
      let containerObj = this.vsInfo.vsObjects.find(obj => obj?.absoluteName === selectionContainer);

      return this.model?.maxMode || !!containerObj && containerObj.objectType === "VSSelectionContainer" &&
         !!(<VSSelectionContainerModel> containerObj).maxMode;
   }

   private isParentIDTree(): boolean {
      return this.model.objectType === "VSSelectionTree" && (<VSSelectionTreeModel> this.model).mode === MODE.ID;
   }

   private isValueSingleSelection(selection: SelectionValueModel) {
      if(this.model.objectType == "VSSelectionList") {
         return this.model.singleSelection;
      }
      else if(this.model.objectType == "VSSelectionTree") {
         return (<VSSelectionTreeModel> this.model).singleSelectionLevels?.indexOf(selection.level) >= 0;
      }

      return false;
   }

   private isCompositeValue(cell: SelectionValueModel): boolean {
      return cell && "selectionList" in cell;
   }

   get leftPos(): number {
      if(!this.model?.maxMode) {
         return this.model.objectFormat.left;
      }

      return this.model.objectFormat.left / (this.scale ?? 1);
   }

   get verticalScrollbarTop(): number {
      return this.model.titleVisible ? this.model.titleFormat.height : 0;
   }

   /**
    * Calculates the total height of the selection items
    */
   get scrollHeight(): number {
      let h: number = this.cellHeight * Math.ceil(Math.min(this.visibleValues + 2,
         this.selectionValues.length) / this.numCols);

      let othersContainerHeight = (this.selectionValues.length > this.visibleValues &&
         this.model.objectType == "VSSelectionList") || this.controller.showOther ?
         this.cellHeight : 0;

      h = !!this.cellContent ? this.cellContent.nativeElement.clientHeight : h;
      return h + othersContainerHeight;
   }

   /**
    * Match scroll in selection list body to the scrollbar outside
    *
    * @param event the mouse event, an 'any' type to support the scrolltop property
    *              that's not listed in the typescript definition
    */
   public verticalScrollHandler(event: number) {
      this.scrollBody.nativeElement.scrollTop = this.verticalScrollWrapper.nativeElement.scrollTop;
   }

   public touchVScroll(delta: number) {
      this.scrollBody.nativeElement.scrollTop = Math.max(0, this.scrollBody.nativeElement.scrollTop - delta);

      if(!!this.verticalScrollWrapper && !!this.verticalScrollWrapper.nativeElement) {
         this.verticalScrollWrapper.nativeElement.scrollTop = Math.max(0, this.verticalScrollWrapper.nativeElement.scrollTop - delta);
      }
   }

   /**
    * Calculates the wheel scroll value and changes scroll bar deltaY
    * @param event
    */
   public wheelScrollHandler(event: any): void {
      if(!!this.verticalScrollWrapper && !!this.verticalScrollWrapper.nativeElement) {
         this.verticalScrollWrapper.nativeElement.scrollTop += event.deltaY;
      }

      event.preventDefault();
   }
   get inContainer(): boolean {
      return this.model.containerType == "VSSelectionContainer";
   }
   get headerBorderBottom(): string {
      if(this.model?.titleFormat?.border?.bottom) {
         return this.model.titleFormat.border.bottom;
      }

      return this.model.containerType === "VSSelectionContainer" ?
         "1px solid " + this.headerBorderBottomColor : "";
   }
}
