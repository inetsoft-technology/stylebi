/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import {
   Component,
   EventEmitter,
   Input,
   NgZone,
   OnChanges,
   OnDestroy,
   Output,
   SimpleChanges
} from "@angular/core";
import { Observable } from "rxjs";
import { Tool } from "../../../../../../shared/util/tool";
import { HyperlinkViewModel } from "../../../common/data/hyperlink-model";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { UpdateZIndexesCommand } from "../../../composer/gui/vs/command/update-zindexes-command";
import { ActionsContextmenuComponent } from "../../../widget/fixed-dropdown/actions-contextmenu.component";
import { DropdownOptions } from "../../../widget/fixed-dropdown/dropdown-options";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { AbstractVSActions } from "../../action/abstract-vs-actions";
import { AssemblyActionFactory } from "../../action/assembly-action-factory.service";
import { AddVSObjectCommand } from "../../command/add-vs-object-command";
import { RefreshEmbeddedVSCommand } from "../../command/refresh-embeddedvs-command";
import { RefreshVSObjectCommand } from "../../command/refresh-vs-object-command";
import { RemoveVSObjectCommand } from "../../command/remove-vs-object-command";
import { ContextProvider } from "../../context-provider.service";
import { ViewsheetInfo } from "../../data/viewsheet-info";
import { RemoveAnnotationEvent } from "../../event/annotation/remove-annotation-event";
import { BaseTableModel } from "../../model/base-table-model";
import { VSChartModel } from "../../model/vs-chart-model";
import { VSObjectModel } from "../../model/vs-object-model";
import { VSViewsheetModel } from "../../model/vs-viewsheet-model";
import { VSUtil } from "../../util/vs-util";
import { NavigationComponent } from "../abstract-nav-component";
import { DataTipService } from "../data-tip/data-tip.service";
import { PopComponentService } from "../data-tip/pop-component.service";
import { NavigationKeys } from "../navigation-keys";
import { Rectangle } from "../../../common/data/rectangle";
import { SelectionMobileService } from "../selection/services/selection-mobile.service";
import { Dimension } from "../../../common/data/dimension";
import { DateTipHelper } from "../data-tip/date-tip-helper";

declare const window: any;

@Component({
   selector: "vs-viewsheet",
   templateUrl: "vs-viewsheet.component.html",
   styleUrls: ["vs-viewsheet.component.scss"],
})
export class VSViewsheet extends NavigationComponent<VSViewsheetModel> implements OnChanges, OnDestroy {
   @Input() deployed: boolean;
   @Input() variableValues: (objName: string) => string[];
   @Input() public selectedAssemblies: number[];
   @Input() submitted: Observable<boolean>;
   @Input() touchDevice: boolean = false;
   @Input() appSize: Dimension;
   @Input() public containerRef: HTMLElement;
   @Output() public onOpenViewsheet = new EventEmitter<string>();
   @Output() public onEditTable = new EventEmitter<BaseTableModel>();
   @Output() public onEditChart = new EventEmitter<VSChartModel>();
   @Output() onSelectedAssemblyChanged =
      new EventEmitter<[number, AbstractVSActions<any>, MouseEvent]>();
   @Output() maxModeChange = new EventEmitter<{assembly: string, maxMode: boolean}>();
   @Output() onOpenChartFormatPane = new EventEmitter<VSChartModel>();
   @Output() submitClicked = new EventEmitter();
   @Output() onOpenConditionDialog = new EventEmitter<BaseTableModel>();
   @Output() onOpenHighlightDialog = new EventEmitter<BaseTableModel>();
   @Output() onOpenAnnotationDialog = new EventEmitter<MouseEvent>();
   public vsObjectActions: AbstractVSActions<any>[] = [];
   preview: boolean;
   composer: boolean;
   href: string;
   variableValuesFunction: (objName: string) => string[] =
      (objName: string) =>  this.getVariableValues(objName);
   public vsObjects: VSObjectModel[] = [];
   vsInfo0: ViewsheetInfo;
   maxMode: boolean = false;
   mySelectedAssemblies: string[] = [];
   public visible = false;

   constructor(private viewsheetClientService: ViewsheetClientService,
               private actionFactory: AssemblyActionFactory,
               private dropdownService: FixedDropdownService,
               private contextProvider: ContextProvider,
               protected dataTipService: DataTipService,
               zone: NgZone,
               private popComponentService: PopComponentService,
               private selectionMobileService: SelectionMobileService)
   {
      super(viewsheetClientService, zone, contextProvider, dataTipService);
      this.preview = this.contextProvider.preview;
      this.composer = this.contextProvider.composer;
   }

   ngOnChanges(changes: SimpleChanges) {
      this.visible = !this.contextProvider.viewer || this.model.visible;

      if(this.vsObjects && this.vsInfo) {
         this.vsInfo0 =
            new ViewsheetInfo(this.vsObjects, this.vsInfo.linkUri, this.vsInfo.metadata,
                              this.vsInfo.runtimeId);

         if(this.model) {
            const viewModel = HyperlinkViewModel.fromHyperlinkModel(this.model.hyperlinkModel,
               this.vsInfo.linkUri, null);
            this.href = this.viewer && !this.preview ? viewModel.url : undefined;
         }
      }

      if(changes["selectedAssemblies"]) {
         const myIndex = this.vsInfo.vsObjects.indexOf(this.model);

         if(!this.selectedAssemblies || !this.selectedAssemblies.includes(myIndex)) {
            this.mySelectedAssemblies = [];
         }
      }
   }

   ngOnDestroy() {
      super.ngOnDestroy();
   }

   protected getZIndex(): number {
      if(this.dataTipService.hasDataTipShowing() &&
         this.dataTipService.dataTipName.startsWith(this.model.absoluteName + ".") ||
         this.popComponentService.hasPopUpComponentShowing() &&
         this.popComponentService.getPopComponent().startsWith(this.model.absoluteName + "."))
      {
         return DateTipHelper.getPopUpSourceZIndex();
      }

      return this.model.objectFormat.zIndex;
   }

   public processAddVSObjectCommand(command: AddVSObjectCommand): void {
      let updated = this.applyRefreshObject(command.model, command.name);

      if(!updated) {
         if(command.model.objectType === "VSGroupContainer") {
            this.vsObjects.unshift(command.model);
         }
         else {
            this.vsObjects.push(command.model);
         }
      }

      // see viewer-app
      this.vsObjects.forEach(obj => obj.sheetMaxMode = command.model.sheetMaxMode);
      this.vsObjects.sort((a, b) => a.objectFormat.zIndex - b.objectFormat.zIndex);
      this.vsObjectActions = this.vsObjects.map(model => this.actionFactory.createActions(model));

      this.dataTipService.registerDataTip(command.model.dataTip, command.name);
      this.dataTipService.registerDataTipVisible(command.model.dataTip, true);
      this.popComponentService.registerPopComponent(
         command.model.popComponent, command.name,
         command.model.objectFormat.top, command.model.objectFormat.left,
         command.model.objectFormat.width, command.model.objectFormat.height,
         command.model.absoluteName, command.model, command.model.container);
      this.popComponentService.registerPopComponentVisible(command.model.popComponent, true);

      for(let i = 0; i < this.vsObjects.length; i++) {
         if(!!this.mySelectedAssemblies && this.mySelectedAssemblies.indexOf(this.vsObjects[i].absoluteName) >= 0) {
            const myIndex = this.vsInfo.vsObjects.indexOf(this.model);
            this.onSelectedAssemblyChanged.emit([myIndex, this.vsObjectActions[i], null]);
            break;
         }
      }
   }

   public processRefreshEmbeddedVSCommand(command: RefreshEmbeddedVSCommand): void {
      const children: string[] = command.assemblies;

      // remove children no longer in the vs
      for(let i = this.vsObjects.length - 1; i >= 0; i--) {
         if(children.indexOf(this.vsObjects[i].absoluteName) < 0) {
            this.vsObjects.splice(i, 1);
            this.vsObjectActions.splice(i, 1);
         }
      }
   }

   public processRefreshVSObjectCommand(command: RefreshVSObjectCommand): void {
      this.applyRefreshObject(command.info, command.info.absoluteName);
   }

   private applyRefreshObject(vsObject: VSObjectModel, name: string): boolean {
      let updated: boolean = false;
      let offsetLeft = this.model.bounds.x;
      let offsetTop = this.model.bounds.y;

      if((vsObject as any).maxMode === true) {
         // for max mode position, use the object format position
         // include chart, table, selection, rangeSlider now.
         offsetLeft = 0;
         offsetTop = 0;
      }

      // Fix object position
      vsObject.objectFormat.left = vsObject.objectFormat.left - offsetLeft;
      vsObject.objectFormat.top = vsObject.objectFormat.top - offsetTop;

      for(let i = 0; i < this.vsObjects.length; i++) {
         if(this.vsObjects[i].absoluteName === name) {
            updated = true;
            this.vsObjects[i] = VSUtil.replaceObject(Tool.clone(this.vsObjects[i]), vsObject);
            this.vsObjectActions[i] = this.actionFactory.createActions(this.vsObjects[i]);

            if(!!this.mySelectedAssemblies && this.mySelectedAssemblies.indexOf(this.vsObjects[i].absoluteName) >= 0) {
               const myIndex = this.vsInfo.vsObjects.indexOf(this.model);
               this.onSelectedAssemblyChanged.emit([myIndex, this.vsObjectActions[i], null]);
            }

            break;
         }
      }

      return updated;
   }

   private processRemoveVSObjectCommand(command: RemoveVSObjectCommand): void {
      for(let i in this.vsObjects) {
         if(this.vsObjects[i].absoluteName === command.name) {
            this.vsObjects.splice(parseInt(i, 10), 1);
            this.vsObjectActions.splice(parseInt(i, 10), 1);
            break;
         }
      }
   }
   /**
    * Updates the z-indexes of the listed assemblies.
    * @param command the command.
    */
   private processUpdateZIndexesCommand(command: UpdateZIndexesCommand): void {
      for(let i = 0; i < command.assemblies.length; i++) {
         const idx = this.vsObjects.findIndex((obj) => obj.absoluteName === command.assemblies[i]);

         if(idx >= 0) {
            const object = Object.assign({}, this.vsObjects[idx]);
            object.objectFormat.zIndex = command.zIndexes[i];
            this.vsObjects[idx] = object;
            this.vsObjectActions[idx] = this.actionFactory.createActions(object);
         }
      }
   }

   openViewsheet(): void {
      if(!this.href) {
         this.onOpenViewsheet.emit(this.model.id);
      }
   }

   // If viewsheet link icon is going to appear above the viewsheet-pane and be hidden, move it
   // down but at most so that it's top edge matches the viewsheet-pane top edge
   get iconHeight(): number {
      return (this.viewer && this._model.iconHeight >= this._model.objectFormat.top) ?
         this._model.iconHeight - this._model.objectFormat.top : this._model.iconHeight;
   }

   /**
    * Show context menus for the objects.
    *
    * @param { actions: AbstractVSActions<any>, event: MouseEvent } payload
    */
   showContextMenu(payload: {
      actions: AbstractVSActions<any>,
      event: MouseEvent }): void
   {
      const event = payload.event;
      let options: DropdownOptions = {
         position: {x: event.clientX, y: event.clientY},
         contextmenu: true
      };

      let dropdownRef = this.dropdownService.open(ActionsContextmenuComponent, options);
      let contextmenu: ActionsContextmenuComponent = dropdownRef.componentInstance;
      contextmenu.sourceEvent = event;
      contextmenu.actions = payload.actions.menuActions;
      event.preventDefault();
   }

   private getVariableValues(objName: string): string[] {
      return this.variableValues(objName)
         .concat(VSUtil.getVariableList(this.vsObjects, objName));
   }

   get showIconContainer(): boolean {
      return !this.deployed && !this.maxMode &&
         (this.model.embeddedIconVisible && this.composer ||
          ((this.viewer || this.preview) &&  this.model.embeddedOpenIconVisible));
   }

   selectViewsheet(payload: [number, AbstractVSActions<any>, MouseEvent]) {
      const [index, , event] = payload;

      if(event && !event.ctrlKey && !event.metaKey && !event.shiftKey) {
         this.vsInfo.clearFocusedAssemblies();

         for(let i = 0; i < this.vsObjects.length; i++) {
            if(i != index) {
               this.vsObjects[i].selectedAnnotations = [];
            }
         }
      }

      if(this.viewer || this.preview) {
         if(this.vsObjects[index].objectType != "VSViewsheet") {
            this.mySelectedAssemblies = [this.vsObjects[index].absoluteName];
            this.selectionMobileService.toggleSelectionMaxMode(this.vsObjects[index]);
         }
      }

      this.vsInfo.selectAssembly(this.model);
      this.model.selectedAnnotations = this.vsObjects[index].selectedAnnotations;

      const myIndex = this.vsInfo.vsObjects.indexOf(this.model);

      if(myIndex >= 0) {
         payload = [myIndex, payload[1], event];
         this.onSelectedAssemblyChanged.emit(payload);
      }
   }

   public removeSelectedAnnotations(): void {
      let selectedIndex = this.mySelectedAssemblies
         .map(name => this.vsObjects.findIndex(obj => obj?.absoluteName === name));
      const event = RemoveAnnotationEvent.create(this.vsObjects, selectedIndex);

      if(event) {
         this.viewsheetClientService.sendEvent(RemoveAnnotationEvent.REMOVE_ANNOTATION_URI, event);
      }
   }

   onMaxModeChanged($event: {assembly: string, maxMode: boolean}) {
      this.maxMode = $event.maxMode;
      this.maxModeChange.emit($event);
   }

   protected clearNavSelection(): void {
      // Do nothing
   }

   protected navigate(key: NavigationKeys): void {
      if(key == NavigationKeys.SPACE) {
         this.openViewsheet();
      }
   }

   getEmbeddedVSBounds() {
      return Rectangle.fromClientRect(<any> this.model.objectFormat);
   }
}
