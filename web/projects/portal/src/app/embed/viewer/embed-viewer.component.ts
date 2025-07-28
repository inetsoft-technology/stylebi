/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
   HostListener,
   Injector,
   Input,
   OnDestroy,
   OnInit,
   ViewChild,
   ViewContainerRef
} from "@angular/core";
import { Router } from "@angular/router";
import { NgbModal, NgbModalConfig } from "@ng-bootstrap/ng-bootstrap";
import { BehaviorSubject, Subscription } from "rxjs";
import { DownloadService } from "../../../../../shared/download/download.service";
import { FullScreenService } from "../../common/services/full-screen.service";
import { ComponentTool } from "../../common/util/component-tool";
import { GuiTool } from "../../common/util/gui-tool";
import { ViewsheetClientService } from "../../common/viewsheet-client";
import { ShowHyperlinkService } from "../../vsobjects/show-hyperlink.service";
import { FixedDropdownService } from "../../widget/fixed-dropdown/fixed-dropdown.service";
import { InteractService } from "../../widget/interact/interact.service";
import { DebounceService } from "../../widget/services/debounce.service";
import { DialogService } from "../../widget/slide-out/dialog-service.service";
import { TooltipService } from "../../widget/tooltip/tooltip.service";
import { ShadowDomService } from "../shadow-dom.service";
import { EMBED_VIEWER_URL_MATCHER } from "./embed-viewer-url-matcher";
import { ViewerAppComponent } from "../../vsobjects/viewer-app.component";

declare const window: any;

@Component({
   selector: "embed-viewer",
   templateUrl: "./embed-viewer.component.html",
   styleUrls: ["./embed-viewer.component.scss"],
   providers: [
      DownloadService,
      TooltipService,
      NgbModal,
      DialogService,
      FixedDropdownService,
      InteractService,
      DebounceService
   ]
})
export class EmbedViewerComponent implements OnInit, OnDestroy, AfterViewInit {
   @Input() url: string;

   @Input()
   set globalLoadingIndicator(value: boolean | string) {
      this._globalLoadingIndicator = this.isTrue(value);
   }

   get globalLoadingIndicator(): boolean {
      return this._globalLoadingIndicator;
   }

   @Input()
   set hideMiniToolbar(value: boolean | string) {
      this._hideMiniToolbar = this.isTrue(value);
   }

   get hideMiniToolbar(): boolean {
      return this._hideMiniToolbar;
   }

   @Input()
   set hideToolbar(value: boolean | string) {
      this._hideToolbar = this.isTrue(value);
   }

   get hideToolbar(): boolean {
      return this._hideToolbar;
   }

   private _hideToolbar: boolean = true;
   private _hideMiniToolbar: boolean = true;
   private _globalLoadingIndicator: boolean = true;
   assetId: string;
   queryParams: Map<string, string[]>;
   mobileDevice: boolean = GuiTool.isMobileDevice();
   connected: boolean = false;
   errorTimeout: any;
   showError: boolean;
   loading: boolean;
   dataTipPopComponentVisible: boolean;
   width: number;
   height: number;
   private loadingSet: Set<string> = new Set<string>();
   @ViewChild("embedViewer") embedViewer: ElementRef;
   @ViewChild("viewerApp") viewerApp: ViewerAppComponent;

   private subscriptions: Subscription = new Subscription();

   constructor(public viewsheetClient: ViewsheetClientService,
               private dialogService: DialogService,
               private router: Router,
               private modalService: NgbModal,
               private dropdownService: FixedDropdownService,
               private tooltipService: TooltipService,
               private modalConfig: NgbModalConfig,
               private viewContainerRef: ViewContainerRef,
               private injector: Injector,
               private shadowDomService: ShadowDomService,
               private showHyperlinkService: ShowHyperlinkService,
               private cdRef: ChangeDetectorRef,
               private debounceService: DebounceService,
               private fullScreenService: FullScreenService)
   {
      shadowDomService.addShadowRootHost(injector, viewContainerRef.element?.nativeElement);
      showHyperlinkService.inEmbed = true;
   }

   ngOnInit(): void {
      // custom element url
      if(this.url) {
         const tree = this.router.parseUrl(this.url);
         const result = EMBED_VIEWER_URL_MATCHER(tree.root?.children?.primary?.segments);
         this.assetId = result.posParams?.assetId?.path;
         this.queryParams = new Map();
         this.queryParams.set("disableParameterSheet", ["true"]);
         const paramMap = tree.queryParamMap;

         paramMap.keys.forEach(key => {
            const value = paramMap.getAll(key);

            if(value) {
               this.queryParams.set(key, value);
            }
         });

         (window.inetsoftConnected as BehaviorSubject<boolean>).subscribe((connected) => {
            if(!this.connected && connected) {
               this.connected = true;

               if(!!this.errorTimeout) {
                  clearTimeout(this.errorTimeout);
               }

               this.showError = false;
               this.cdRef.detectChanges();
            }

            if(!this.connected && !connected) {
               this.errorTimeout = setTimeout(() => {
                  this.showError = true;
                  console.error("InetSoft client not connected. Please make sure to login first.");
                  this.cdRef.detectChanges();
               }, 2000);
            }
         });
      }
   }

   ngAfterViewInit(): void {
      this.tooltipService.container = this.embedViewer.nativeElement;
      this.modalConfig.container = this.embedViewer.nativeElement;
      this.dropdownService.container = this.embedViewer.nativeElement;
      // handle dropdown in a dialog outside the bounds of the element
      this.dropdownService.allowPositionOutsideContainer = true;
      this.dialogService.container = this.embedViewer.nativeElement;
   }

   ngOnDestroy() {
      this.dialogService.ngOnDestroy();

      if(!!this.subscriptions) {
         this.subscriptions.unsubscribe();
         this.subscriptions = null;
      }
   }

   downloadStarted(url: string): void {
      ComponentTool.showMessageDialog(this.modalService, "_#(js:Info)", "_#(js:common.downloadStart)");
   }

   onEmbedError(message: string): void {
      this.showError = !!message;

      if(this.showError) {
         this.loading = false;
         console.error(message);
      }

      this.cdRef.detectChanges();
   }

   onLoadingStateChanged(event: { name: string, loading: boolean }) {
      if(!event.loading) {
         this.loadingSet.delete(event.name);
      }
      else {
         this.loadingSet.add(event.name);
      }

      // if not loading then immediately set the value, otherwise debounce to get a
      // smoother loading animation and prevent loading icon from stuttering
      if(!this.loading) {
         this.loading = this.loadingSet.size > 0;
      }
      else {
         this.debounceService.debounce("embed-viewer-loading", () => {
            this.loading = this.loadingSet.size > 0;
            this.cdRef.detectChanges();
         }, 500, []);
      }
   }

   private isTrue(value: boolean | string) {
      return value === true || value === "true" || value === "";
   }

   onDataTipPopComponentVisible(visible: boolean) {
      this.dataTipPopComponentVisible = visible;
   }

   getViewerOffsetFunc() {
      if(this.fullScreenService.fullScreenMode) {
         return null;
      }

      return this.viewerOffsetFunc;
   }

   viewerOffsetFunc = () => {
      let embedViewerRect = this.embedViewer.nativeElement.getBoundingClientRect();

      return {
         x: embedViewerRect.left,
         y: embedViewerRect.top,
         width: window.innerWidth - embedViewerRect.left,
         height: window.innerHeight - embedViewerRect.top,
         scrollLeft: 0,
         scrollTop: 0
      };
   };

   @HostListener("mouseleave", ["$event"])
   onMouseLeave(event: MouseEvent): void {
      this.viewerApp?.clearDataTipPopComponents();
   }

   onViewerSizeChanged(size: {width: number, height: number}) {
      this.width = size.width;
      this.height = size.height;
   }
}
