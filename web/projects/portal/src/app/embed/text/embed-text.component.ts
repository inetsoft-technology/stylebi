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
import { NgbModal, NgbModalConfig } from "@ng-bootstrap/ng-bootstrap";
import { MiniToolbarService } from "../../vsobjects/objects/mini-toolbar/mini-toolbar.service";
import { ScaleService } from "../../widget/services/scale/scale-service";
import { VSTextModel } from "../../vsobjects/model/output/vs-text-model";
import { EmbedTextActions } from "./embed-text-actions";
import { ContextProvider } from "../../vsobjects/context-provider.service";
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";
import { DropdownRef } from "../../widget/fixed-dropdown/fixed-dropdown-ref";
import { DropdownOptions } from "../../widget/fixed-dropdown/dropdown-options";
import {
   ActionsContextmenuComponent
} from "../../widget/fixed-dropdown/actions-contextmenu.component";
import { FixedDropdownService } from "../../widget/fixed-dropdown/fixed-dropdown.service";
import { EMBED_TEXT_URL_MATCHER } from "./embed-text.routes";
import { TooltipService } from "../../widget/tooltip/tooltip.service";
import { ResizedDirective } from "../../../../../shared/resize-event/resized.directive";
import { VSText } from "../../vsobjects/objects/output/text/vs-text.component";
import { MiniToolbar } from "../../vsobjects/objects/mini-toolbar/mini-toolbar.component";
import { InteractContainerDirective } from "../../widget/interact/interact-container.directive";
import { ShadowDomService } from "../shadow-dom.service";
import { ShowHyperlinkService } from "../../vsobjects/show-hyperlink.service";
import { CollectParametersCommand } from "../../vsobjects/command/collect-parameters-command";
import { CollectParametersOverEvent } from "../../common/event/collect-parameters-over-event";
import { first } from "rxjs/operators";
import { VSRefreshEvent } from "../../vsobjects/event/vs-refresh-event";
import { RefreshVsAssemblyEvent } from "../../vsobjects/event/refresh-vs-assembly-event";
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
    imports: [ResizedDirective, VSText, MiniToolbar, InteractContainerDirective],
    selector: "embed-text",
    templateUrl: "./embed-text.component.html",
    styleUrls: ["./embed-text.component.scss"],
    providers: [
      ViewsheetClientService,
      TooltipService,
      NgbModal,
      DialogService,
      FixedDropdownService,
      InteractService,
      DebounceService,
      AdhocFilterService
   ]
})
export class EmbedTextComponent extends CommandProcessor implements OnInit, OnDestroy, AfterViewInit {
   @Input() url: string;
   appSize: Dimension;
   assemblySize: Dimension;
   vsInfo: ViewsheetInfo;
   vsObject: VSTextModel;
   vsObjectActions: AbstractVSActions<any>;
   assetId: string;
   assemblyName: string;
   queryParams: Params = {};
   private inputRuntimeId: string;
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
      if(this.url) {
         const tree = this.router.parseUrl(this.url);
         const segments = tree.root?.children?.primary?.segments ?? tree.root?.segments;
         const result = EMBED_TEXT_URL_MATCHER(segments);

         if(!result) {
            this.showError = true;
            console.error("Invalid embed URL: " + this.url);
            return;
         }

         this.assetId = result.posParams?.assetId?.path;
         this.assemblyName = result.posParams?.assemblyName?.path;
         this.inputRuntimeId = result.posParams?.runtimeId?.path;
         this.queryParams = tree.queryParams;

         this.subscriptions.add(
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
            })
         );
      }
      else {
         if(document.body.className.indexOf("app-loaded") == -1) {
            document.body.className += " app-loaded";
            const splash = document.querySelector<HTMLElement>(".loading-splash");
            splash?.addEventListener("transitionend", () => splash.style.display = "none", { once: true });
         }

         this.subscriptions.add(
            forkJoin([this.route.queryParams, this.route.params].map(obs => obs.pipe(first())))
               .subscribe(([queryParams, params]) => {
                  this.queryParams = queryParams;
                  this.assetId = params.assetId;
                  this.assemblyName = params.assemblyName;
                  this.inputRuntimeId = params.runtimeId;
                  this.openViewsheet();
               }));
      }

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

   processSetRuntimeIdCommand(command: SetRuntimeIdCommand): void {
      this.viewsheetClient.runtimeId = command.runtimeId;
      this.runtimeId = command.runtimeId;

      // A newly opened embedded assembly may need an explicit refresh to apply the
      // requested assembly size after the runtime is established.
      if(this.assemblyName && !this.inputRuntimeId) {
         this.refreshEmbedAssembly();
         return;
      }

      this.onResize();
   }

   processSetViewsheetInfoCommand(command: SetViewsheetInfoCommand): void {
      if(!this.vsInfo || this.vsInfo.linkUri != command.linkUri) {
         this.updateVSInfo(command.linkUri);
      }

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
         this.vsObject = <VSTextModel>VSUtil.replaceObject(Tool.clone(this.vsObject), command.model);
      }
      else {
         this.vsObject = <VSTextModel>command.model;
      }

      this.vsObject.active = true;
      this.updateVSInfo();

      this.vsObjectActions = new EmbedTextActions(this.vsObject,
         this.contextProvider, false, null, null, null, this.miniToolbarService);
      // Force change detection: as an Angular Elements custom element, this view is not refreshed by the zone tick that wraps websocket command processing, so the embedded object would otherwise never render on open.
      this.cdRef.detectChanges();
   }

   processRefreshVSObjectCommand(command: RefreshVSObjectCommand): void {
      if(this.assemblyName != command.info.absoluteName) {
         return;
      }

      if(!this.vsObject) {
         this.vsObject = <VSTextModel> command.info;
      }
      else {
         this.vsObject = <VSTextModel>VSUtil.replaceObject(Tool.clone(this.vsObject), command.info);
      }

      this.vsObject.active = true;
      this.vsObjectActions = new EmbedTextActions(this.vsObject,
         this.contextProvider, false, null, null, null, this.miniToolbarService);
      // Force change detection: as an Angular Elements custom element, this view is not refreshed by the zone tick that wraps websocket command processing, so the embedded object would otherwise never render on open.
      this.cdRef.detectChanges();
   }

   processCollectParametersCommand(command: CollectParametersCommand): void {
      let event: CollectParametersOverEvent = new CollectParametersOverEvent([], true, command.isOpenSheet);
      this.viewsheetClient.sendEvent(COLLECT_PARAMS_URI, event);
   }

   processEmbedErrorCommand(command: EmbedErrorCommand): void {
      this.showError = true;
      console.error(command.message);
   }

   isMiniToolbarVisible(): boolean {
      return !!this.vsObject;
   }

   public getToolbarTop(object: VSObjectModel): number {
      return object.objectFormat.top;
   }

   public getToolbarLeft(object: VSObjectModel): number {
      let left: number = object.objectFormat.left;
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

   private openViewsheet(): void {
      if(this.inputRuntimeId) {
         this.viewsheetClient.runtimeId = this.inputRuntimeId;
         this.runtimeId = this.inputRuntimeId;
         this.subscriptions.add(this.viewsheetClient.whenConnected()
            .subscribe(() => setTimeout(() => this.refreshEmbedAssembly(), 0)));
         this.subscriptions.add(this.viewsheetClient.connectionError().subscribe((error) => {
            this.timeoutError = !!error;
         }));
         this.viewsheetClient.connect(!!this.url);
      }
      else if(this.assetId) {
         this.subscriptions.add(this.viewsheetClient.whenConnected()
            .subscribe(() => setTimeout(() => this.openViewsheet0(), 0)));
         this.subscriptions.add(this.viewsheetClient.connectionError().subscribe((error) => {
            this.timeoutError = !!error;
         }));
         this.viewsheetClient.connect(!!this.url);
         this.viewsheetClient.beforeDestroy = () => this.beforeDestroy();
      }
      else {
         this.showError = true;
         this.timeoutError = false;
         console.error("The runtime or asset identifier must be provided");
      }
   }

   private refreshEmbedAssembly(): void {
      this.setAppSize();

      if(this.assemblySize == null || this.assemblySize.width == 0 || this.assemblySize.height == 0) {
         return;
      }

      const refreshEvent: RefreshVsAssemblyEvent = {
         vsRuntimeId: this.runtimeId,
         assemblyName: this.assemblyName,
         embed: true,
         assemblySize: this.assemblySize
      };
      this.viewsheetClient.sendEvent("/events/vs/refresh/assembly", refreshEvent);
   }

   private openViewsheet0(): void {
      this.setAppSize();
      let event: OpenViewsheetEvent = new OpenViewsheetEvent(
         this.assetId, this.appSize.width, this.appSize.height, this.mobileDevice,
         window.navigator.userAgent);
      event.embed = true;
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

   private closeViewsheetOnServer(): void {
      this.viewsheetClient.sendEvent(CLOSE_VIEWSHEET_SOCKET_URI);
   }

   private updateVSInfo(linkUri: string = null) {
      const vsObjects = this.vsObject != null ? [this.vsObject] : [];

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
      if(!this.vsObjectActions) {
         return;
      }

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

   setServerUpdateInterval(): void {
      this.clearServerUpdateInterval();

      if(this.updateEnabled) {
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

   onResize() {
      if(this.runtimeId == null || this.appSize == null) {
         return;
      }

      const oldAppSize = new Dimension(this.appSize.width, this.appSize.height);
      this.setAppSize();

      if(this.appSize.width == 0 || this.appSize.height == 0 ||
         (oldAppSize.width == this.appSize.width && oldAppSize.height == this.appSize.height))
      {
         return;
      }

      this.debounceService.debounce("embed-text-vs-resize" + this.runtimeId, () => {
         if(this.inputRuntimeId) {
            const refreshEvent: RefreshVsAssemblyEvent = {
               vsRuntimeId: this.runtimeId,
               assemblyName: this.assemblyName,
               assemblySize: this.assemblySize
            };
            this.viewsheetClient.sendEvent("/events/vs/refresh/assembly", refreshEvent);
         }
         else {
            const refreshEvent = new VSRefreshEvent();
            refreshEvent.setWidth(this.appSize.width);
            refreshEvent.setHeight(this.appSize.height);
            refreshEvent.setEmbedAssemblySize(this.assemblySize);
            this.viewsheetClient.sendEvent("/events/vs/refresh", refreshEvent);
         }
      }, 100, []);
   }
}
