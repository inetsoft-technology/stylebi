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
   Injector,
   Input,
   NgZone,
   OnDestroy,
   OnInit,
   ViewChild,
   ViewContainerRef
} from "@angular/core";
import { ActivatedRoute, Params, Router } from "@angular/router";
import { BehaviorSubject, forkJoin, Subscription } from "rxjs";
import { Tool } from "../../../../../shared/util/tool";
import { Dimension } from "../../common/data/dimension";
import { GuiTool } from "../../common/util/gui-tool";
import { CommandProcessor, ViewsheetClientService } from "../../common/viewsheet-client";
import { TouchAssetEvent } from "../../composer/gui/ws/socket/touch-asset-event";
import { AbstractVSActions } from "../../vsobjects/action/abstract-vs-actions";
import { AddVSObjectCommand } from "../../vsobjects/command/add-vs-object-command";
import { RefreshVSObjectCommand } from "../../vsobjects/command/refresh-vs-object-command";
import { SetRuntimeIdCommand } from "../../vsobjects/command/set-runtime-id-command";
import { SetViewsheetInfoCommand } from "../../vsobjects/command/set-viewsheet-info-command";
import { ViewsheetInfo } from "../../vsobjects/data/viewsheet-info";
import { OpenViewsheetEvent } from "../../vsobjects/event/open-viewsheet-event";
import { VSObjectModel } from "../../vsobjects/model/vs-object-model";
import { VSUtil } from "../../vsobjects/util/vs-util";
import { DialogService } from "../../widget/slide-out/dialog-service.service";
import { ComponentTool } from "../../common/util/component-tool";
import { NgbModal, NgbModalConfig } from "@ng-bootstrap/ng-bootstrap";
import { MiniToolbarService } from "../../vsobjects/objects/mini-toolbar/mini-toolbar.service";
import { ScaleService } from "../../widget/services/scale/scale-service";
import { VSChartModel } from "../../vsobjects/model/vs-chart-model";
import { EmbedChartActions } from "./embed-chart-actions";
import { ContextProvider } from "../../vsobjects/context-provider.service";
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";
import { DropdownRef } from "../../widget/fixed-dropdown/fixed-dropdown-ref";
import { DropdownOptions } from "../../widget/fixed-dropdown/dropdown-options";
import {
   ActionsContextmenuComponent
} from "../../widget/fixed-dropdown/actions-contextmenu.component";
import { FixedDropdownService } from "../../widget/fixed-dropdown/fixed-dropdown.service";
import { EMBED_CHART_URL_MATCHER } from "./app-routing.module";
import { DownloadService } from "../../../../../shared/download/download.service";
import { TooltipService } from "../../widget/tooltip/tooltip.service";
import { ShadowDomService } from "../shadow-dom.service";
import { ShowHyperlinkService } from "../../vsobjects/show-hyperlink.service";
import { CollectParametersCommand } from "../../vsobjects/command/collect-parameters-command";
import { CollectParametersOverEvent } from "../../common/event/collect-parameters-over-event";
import { first } from "rxjs/operators";
import { VSRefreshEvent } from "../../vsobjects/event/vs-refresh-event";
import { DebounceService } from "../../widget/services/debounce.service";
import { InteractService } from "../../widget/interact/interact.service";
import { EmbedErrorCommand } from "../embed-error-command";
import { AdhocFilterService } from "../../vsobjects/objects/data-tip/adhoc-filter.service";

const OPEN_VS_URI: string = "/events/open";
const CLOSE_VIEWSHEET_SOCKET_URI: string = "/events/composer/viewsheet/close";
const TOUCH_ASSET_URI: string = "/events/composer/touch-asset";
const COLLECT_PARAMS_URI: string = "/events/vs/collectParameters";
declare const window: any;

@Component({
   selector: "embed-chart",
   templateUrl: "./embed-chart.component.html",
   styleUrls: ["./embed-chart.component.scss"],
   providers: [
      ViewsheetClientService,
      DownloadService,
      TooltipService,
      NgbModal,
      DialogService,
      FixedDropdownService,
      InteractService,
      DebounceService,
      AdhocFilterService
   ]
})
export class EmbedChartComponent extends CommandProcessor implements OnInit, OnDestroy, AfterViewInit {
   @Input() url: string;
   appSize: Dimension;
   assemblySize: Dimension;
   vsInfo: ViewsheetInfo;
   vsObject: VSChartModel;
   vsObjectActions: AbstractVSActions<any>;
   assetId: string;
   assemblyName: string;
   queryParams: Params = {};
   mobileDevice: boolean = GuiTool.isMobileDevice();
   connected: boolean;
   errorTimeout: any;
   showError: boolean;
   timeoutError: boolean;
   @ViewChild("viewerRoot") viewerRoot: ElementRef;

   private subscriptions: Subscription = new Subscription();
   private _runtimeId: string;
   private serverUpdateIntervalId: any;
   private updateEnabled: boolean;
   private touchInterval: number;
   variableValuesFunction: (objName: string) => string[] =
      (objName: string) => this.getVariableValues(objName);

   constructor(public viewsheetClient: ViewsheetClientService,
               private dialogService: DialogService,
               private zone: NgZone,
               private route: ActivatedRoute,
               private router: Router,
               private modalService: NgbModal,
               private miniToolbarService: MiniToolbarService,
               private scaleService: ScaleService,
               private contextProvider: ContextProvider,
               private dropdownService: FixedDropdownService,
               private tooltipService: TooltipService,
               private modalConfig: NgbModalConfig,
               private viewContainerRef: ViewContainerRef,
               private injector: Injector,
               private shadowDomService: ShadowDomService,
               private showHyperlinkService: ShowHyperlinkService,
               private debounceService: DebounceService,
               private cdRef: ChangeDetectorRef)
   {
      super(viewsheetClient, zone, true);
      shadowDomService.addShadowRootHost(injector, viewContainerRef.element?.nativeElement);
      showHyperlinkService.inEmbed = true;
   }

   get runtimeId(): string {
      return this._runtimeId;
   }

   set runtimeId(value: string) {
      this._runtimeId = value;
   }

   getAssemblyName(): string {
      return null;
   }

   ngOnInit(): void {
      // custom element url
      if(this.url) {
         const tree = this.router.parseUrl(this.url);
         const result = EMBED_CHART_URL_MATCHER(tree.root?.children?.primary?.segments);
         this.assetId = result.posParams?.assetId?.path;
         this.assemblyName = result.posParams?.assemblyName?.path;
         this.queryParams = tree.queryParams;

         (window.inetsoftConnected as BehaviorSubject<boolean>).subscribe((connected) => {
            if(!this.connected && connected) {
               this.connected = true;

               if(!!this.errorTimeout) {
                  clearTimeout(this.errorTimeout);
               }

               this.showError = false;
               this.openViewsheet();
               this.cdRef.detectChanges();
            }

            if(!this.connected && !connected) {
               this.errorTimeout = setTimeout(() => {
                  this.showError = true;
                  console.error("InetSoft client not connected. Please make sure to login first.");
                  this.cdRef.detectChanges();
               }, 1000);
            }
         });
      }
      else {
         if(document.body.className.indexOf("app-loaded") == -1) {
            document.body.className += " app-loaded";
         }

         this.subscriptions.add(
            forkJoin([this.route.queryParams, this.route.params].map(obs => obs.pipe(first())))
               .subscribe(([queryParams, params]) => {
                  this.queryParams = queryParams;
                  this.assetId = params.assetId;
                  this.assemblyName = params.assemblyName;
                  this.openViewsheet();
               }));
      }

      // Subscribe to heartbeat and touch asset to prevent expiration
      this.subscriptions.add(this.viewsheetClient.onHeartbeat.subscribe(() => {
         let event = new TouchAssetEvent();
         event.setDesign(false);
         event.setChanged(false);
         event.setUpdate(false);
         this.viewsheetClient.sendEvent(TOUCH_ASSET_URI, event);
      }));
   }

   ngAfterViewInit(): void {
      this.tooltipService.container = this.viewerRoot.nativeElement;
      this.modalConfig.container = this.viewerRoot.nativeElement;
      this.dropdownService.container = this.viewerRoot.nativeElement;
      // handle dropdown in a dialog outside the bounds of the chart element
      this.dropdownService.allowPositionOutsideContainer = true;
      this.dialogService.container = this.viewerRoot.nativeElement;
   }

   ngOnDestroy() {
      this.dialogService.ngOnDestroy();
      this.clearServerUpdateInterval();

      if(!!this.subscriptions) {
         this.subscriptions.unsubscribe();
         this.subscriptions = null;
      }
   }

   // noinspection JSUnusedGlobalSymbols
   /**
    * Sets the runtime identifier of the viewsheet instance.
    *
    * @param command the command object containing the identifier.
    */
   processSetRuntimeIdCommand(command: SetRuntimeIdCommand): void {
      this.viewsheetClient.runtimeId = command.runtimeId;
      this.runtimeId = command.runtimeId;

      // call onResize in case the element was resized while the server was processing
      // open viewsheet event
      this.onResize();
   }

   // noinspection JSUnusedGlobalSymbols
   /**
    * Set info for the viewsheet
    *
    * @param {SetViewsheetInfoCommand} command
    */
   processSetViewsheetInfoCommand(command: SetViewsheetInfoCommand): void {
      if(!this.vsInfo || this.vsInfo.linkUri != command.linkUri) {
         this.updateVSInfo(command.linkUri);
      }

      // reset server update interval if any values are changed
      if(this.updateEnabled != command.info["updateEnabled"] ||
         this.touchInterval != command.info["touchInterval"])
      {
         this.updateEnabled = command.info["updateEnabled"];
         this.touchInterval = command.info["touchInterval"];
         this.setServerUpdateInterval();
      }
   }

   processAddVSObjectCommand(command: AddVSObjectCommand): void {
      if(this.assemblyName != command.name) {
         return;
      }

      if(this.vsObject) {
         this.vsObject = <VSChartModel>VSUtil.replaceObject(Tool.clone(this.vsObject), command.model);
      }
      else {
         this.vsObject = <VSChartModel>command.model;
      }

      this.vsObject.active = true;
      this.updateVSInfo();

      if(this.vsObject) {
         this.vsObjectActions = new EmbedChartActions(this.vsObject, null,
            this.contextProvider, false, null, null, this.miniToolbarService);
      }
   }

   // noinspection JSUnusedGlobalSymbols
   processRefreshVSObjectCommand(command: RefreshVSObjectCommand): void {
      if(this.assemblyName != command.info.absoluteName) {
         return;
      }

      this.vsObject = <VSChartModel>VSUtil.replaceObject(Tool.clone(this.vsObject), command.info);
      this.vsObject.active = true;
      this.vsObjectActions = new EmbedChartActions(this.vsObject, null,
         this.contextProvider, false, null, null, this.miniToolbarService);
   }

   // noinspection JSUnusedGlobalSymbols
   /**
    * Receive parameter prompts.
    * @param {CollectParametersCommand} command
    */
   processCollectParametersCommand(command: CollectParametersCommand): void {
      // query params are sent in the open sheet event, send empty list here
      let event: CollectParametersOverEvent = new CollectParametersOverEvent([], true, command.isOpenSheet);
      this.viewsheetClient.sendEvent(COLLECT_PARAMS_URI, event);
   }

   // noinspection JSUnusedGlobalSymbols
   processEmbedErrorCommand(command: EmbedErrorCommand): void {
      this.showError = true;
      console.error(command.message);
   }

   downloadStarted(url: string): void {
      ComponentTool.showMessageDialog(this.modalService, "_#(js:Info)", "_#(js:common.downloadStart)");
   }

   isMiniToolbarVisible(): boolean {
      if(!this.vsObject) {
         return false;
      }

      if(this.vsObject.objectType == "VSChart" && (<any>this.vsObject).showPlotResizers &&
         (<any>this.vsObject).horizontallyResizable)
      {
         return false;
      }

      return true;
   }

   public getToolbarTop(object: VSObjectModel): number {
      return object.objectFormat.top;
   }

   public getToolbarLeft(object: VSObjectModel): number {
      let left: number;

      // 1. the left property of max mode has setting to 0 in server
      // 2. some max mode assembly has not start from 0. e.g. selection list.
      left = object.objectFormat.left;
      const containerBounds = this.viewerRoot.nativeElement.getBoundingClientRect();

      return this.miniToolbarService.getToolbarLeft(left, containerBounds,
         this.scaleService.getCurrentScale(), 0, false,
         this.vsObjectActions.toolbarActions, null);
   }

   public getToolbarWidth(object: VSObjectModel): number {
      const containerBounds = this.viewerRoot.nativeElement.getBoundingClientRect();
      return this.miniToolbarService.getToolbarWidth(object, containerBounds,
         this.scaleService.getCurrentScale(), 0, false, null);
   }

   onMouseEnter(event: any): void {
      this.miniToolbarService.handleMouseEnter(this.vsObject?.absoluteName, event);
   }

   showMiniToolbar(): boolean {
      return !this.miniToolbarService?.isMiniToolbarHidden(this.vsObject?.absoluteName);
   }

   private openViewsheet(runtimeId: string = null): void {
      if(this.assetId) {
         this.subscriptions.add(this.viewsheetClient.whenConnected()
            .subscribe(() => setTimeout(() => this.openViewsheet0(), 0)));
         this.subscriptions.add(this.viewsheetClient.connectionError().subscribe((error) => {
            this.timeoutError = !!error;
         }));
         this.viewsheetClient.connect(!!this.url);
         this.viewsheetClient.beforeDestroy = () => this.beforeDestroy();
      }
      else {
         console.error("The runtime or asset identifier must be provided");
      }
   }

   /**
    * Requests that the server open the viewsheet.
    */
   private openViewsheet0(): void {
      this.setAppSize();
      let event: OpenViewsheetEvent = new OpenViewsheetEvent(
         this.assetId, this.appSize.width, this.appSize.height, this.mobileDevice,
         window.navigator.userAgent);
      event.embedAssemblyName = this.assemblyName;
      event.embedAssemblySize = this.assemblySize;
      event.disableParameterSheet = true;

      for(let param in this.queryParams) {
         if(this.queryParams.hasOwnProperty(param)) {
            event.parameters[param] = [this.queryParams[param]];
         }
      }

      this.viewsheetClient.sendEvent(OPEN_VS_URI, event);
   }

   private beforeDestroy(): void {
      this.closeViewsheetOnServer();
   }

   /**
    * Close the viewsheet on the server side.
    */
   private closeViewsheetOnServer(): void {
      this.viewsheetClient.sendEvent(CLOSE_VIEWSHEET_SOCKET_URI);
   }

   private updateVSInfo(linkUri: string = null) {
      const vsObjects = this.vsObject != null ? [] : [this.vsObject];

      if(!this.vsInfo) {
         this.vsInfo = new ViewsheetInfo(vsObjects, linkUri, false,
            this.runtimeId);
      }

      if(linkUri) {
         this.vsInfo.linkUri = linkUri;
      }

      if(this.runtimeId) {
         this.vsInfo.runtimeId = this.runtimeId;
      }

      this.vsInfo.vsObjects = vsObjects;
   }

   setAppSize(): void {
      this.appSize = new Dimension(this.viewerRoot.nativeElement.offsetWidth,
         this.viewerRoot.nativeElement.offsetHeight);

      const sbSize = GuiTool.measureScrollbars();
      this.assemblySize = new Dimension(Math.max(0, this.appSize.width - sbSize),
         Math.max(0, this.appSize.height - sbSize));
   }

   onOpenContextMenu(event: MouseEvent) {
      let actions: AssemblyActionGroup[];

      if(event.type === "click") {
         actions = [new AssemblyActionGroup([this.vsObjectActions.clickAction])];
      }
      else {
         actions = this.vsObjectActions.menuActions;
      }

      const dropdown: DropdownRef = this.showContextMenu(actions, event);
      this.miniToolbarService.hiddenFreeze(this.vsObject?.absoluteName);

      const sub2 = dropdown.closeEvent.subscribe(() => {
         sub2.unsubscribe();
         this.miniToolbarService.hiddenUnfreeze(this.vsObject?.absoluteName);
      });
   }

   /**
    * Interface with the dropdown service
    *
    * @param {AssemblyActionGroup[]} actions
    * @param {MouseEvent} event
    */
   private showContextMenu(actions: AssemblyActionGroup[], event: MouseEvent): DropdownRef {
      let options: DropdownOptions = {
         position: {x: event.clientX, y: event.clientY},
         contextmenu: true
      };

      let dropdownRef = this.dropdownService.open(ActionsContextmenuComponent, options);
      let contextmenu: ActionsContextmenuComponent = dropdownRef.componentInstance;
      contextmenu.sourceEvent = event;
      contextmenu.actions = actions;
      event.preventDefault();
      return dropdownRef;
   }

   /**
    * Update data from server at the specified interval if update is enabled.
    */
   setServerUpdateInterval(): void {
      this.clearServerUpdateInterval();

      if(this.updateEnabled) {
         // clear old server update interval
         let interval: number = this.touchInterval ? this.touchInterval * 1000 : 60000;
         this.serverUpdateIntervalId = setInterval(() => {
            let event = new TouchAssetEvent();
            event.setDesign(false);
            event.setChanged(false);
            event.setUpdate(true);
            event.setWidth(this.viewerRoot.nativeElement.offsetWidth);
            event.setHeight(this.viewerRoot.nativeElement.offsetHeight);
            this.viewsheetClient.sendEvent(TOUCH_ASSET_URI, event);
         }, interval);
      }
   }

   clearServerUpdateInterval(): void {
      if(this.serverUpdateIntervalId != null && !isNaN(this.serverUpdateIntervalId)) {
         clearInterval(this.serverUpdateIntervalId);
      }
   }

   public getVariableValues(name: string): string[] {
      return VSUtil.getVariableList(this.vsInfo.vsObjects, name);
   }

   onResize() {
      // if runtime id not set yet then ignore the resize events
      if(this.runtimeId == null || this.appSize == null) {
         return;
      }

      const oldAppSize = new Dimension(this.appSize.width, this.appSize.height);
      this.setAppSize();

      // no change or set to 0 then ignore
      if(this.appSize.width == 0 || this.appSize.height == 0 ||
         (oldAppSize.width == this.appSize.width && oldAppSize.height == this.appSize.height))
      {
         return;
      }

      this.debounceService.debounce("embed-chart-vs-resize" + this.runtimeId, () => {
         const refreshEvent = new VSRefreshEvent();
         refreshEvent.setWidth(this.appSize.width);
         refreshEvent.setHeight(this.appSize.height);
         refreshEvent.setEmbedAssemblySize(this.assemblySize);
         this.viewsheetClient.sendEvent("/events/vs/refresh", refreshEvent);
      }, 100, []);
   }
}

