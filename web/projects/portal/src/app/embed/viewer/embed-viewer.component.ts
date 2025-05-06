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
   @Input() hideToolbar: boolean = true;
   @Input() hideMiniToolbar: boolean = true;
   assetId: string;
   queryParams: Map<string, string[]>;
   mobileDevice: boolean = GuiTool.isMobileDevice();
   connected: boolean = false;
   errorTimeout: any;
   showError: boolean;
   @ViewChild("embedViewer") embedViewer: ElementRef;

   private subscriptions: Subscription = new Subscription();
   private _runtimeId: string;

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
               private cdRef: ChangeDetectorRef)
   {
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
               }, 1000);
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
         console.error(message);
      }
   }
}

