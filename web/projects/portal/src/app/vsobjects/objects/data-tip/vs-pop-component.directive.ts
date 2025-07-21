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
   Directive, DoCheck, ElementRef, Input, Renderer2, ChangeDetectorRef,
   OnInit, OnDestroy
} from "@angular/core";
import { Subject } from "rxjs";
import { takeUntil } from "rxjs/operators";
import { PopComponentService } from "./pop-component.service";
import { OpenDataTipEvent } from "../../event/open-datatip-event";
import { ViewsheetClientService } from "../../../common/viewsheet-client/viewsheet-client.service";
import { GuiTool } from "../../../common/util/gui-tool";
import { DataTipService } from "./data-tip.service";
import { ContextProvider } from "../../context-provider.service";

import { PopLocation } from "./pop-component.service";
import { DateTipHelper } from "./date-tip-helper";

@Directive({
   selector: "[VSPopComponent]"
})
export class VSPopComponentDirective implements DoCheck, OnInit, OnDestroy {
   @Input() public popComponentName: string;
   @Input() public popContainerName: string;
   @Input() public popZIndex: number;
   @Input() public popBackground: string = "white";
   @Input() public miniToolbar: boolean = false;
   @Input() containerBounds: DOMRectInit;
   @Input() actionsWidth: number;
   mobileDevice: boolean = GuiTool.isMobileDevice();
   private outsideClickListener: () => any;
   private destroy$ = new Subject<void>();

   constructor(private popService: PopComponentService,
               private dataTipService: DataTipService,
               private elementRef: ElementRef,
               private renderer: Renderer2,
               private changeRef: ChangeDetectorRef,
               private contextProvider: ContextProvider,
               private viewsheetClient: ViewsheetClientService)
   {
      this.popService.componentRegistered
         .pipe(takeUntil(this.destroy$))
         .subscribe(event => {
            if(event.name === this.popComponentName || event.name === this.popContainerName) {
               this.changeRef.markForCheck();
            }
         });
   }

   private isCurrentPopComponent(): boolean {
      return this.popService.isCurrentPopComponent(this.popComponentName, this.popContainerName);
   }

   private isCurrentDataTip(): boolean {
      return this.dataTipService.isCurrentDataTip(this.popComponentName, this.popContainerName);
   }

   ngOnInit(): void {
      this.popService.registerPopComponentChild(this.popComponentName,
                                                this.outsideClickCallback.bind(this));
   }

   ngOnDestroy(): void {
      this.destroy$.next();
      this.destroy$.unsubscribe();
      this.popService.clearPopViewerOffset();
   }

   /**
    * Instead of hostbinding for styles, use renderer on changes to conditionally
    * apply styles.
    */
   ngDoCheck(): void {
      if(this.dataTipService.dataTipName &&
         (this.dataTipService.dataTipName == this.popComponentName ||
          this.dataTipService.dataTipName == this.popContainerName))
      {
         return;
      }

      if(this.popService.isPopComponent(this.popComponentName) ||
         this.popService.isPopComponent(this.popContainerName))
      {
         const nativeElement = !this.miniToolbar ? this.elementRef.nativeElement
            : this.elementRef.nativeElement.querySelector(".mini-toolbar");
         // main div (self or the associated assembly div for minitoolbar)
         const mainComponent = !this.miniToolbar ? nativeElement :
            document.getElementById(this.dataTipService.getVSObjectId(this.popComponentName));
         let refreshPos: boolean = false;

         if(this.miniToolbar) {
            refreshPos = !this.elementRef.nativeElement.classList.contains("mini-toolbar-in-pop");
         }
         else {
            refreshPos = this.elementRef.nativeElement.style.display == "none";
         }

         if(this.isCurrentPopComponent()) {
            // avoid changing visibility if already visible. otherwise it could
            // cause an infinite cycle to do-check
            if(refreshPos) {
               this.setPopOutPosition(nativeElement, mainComponent);
            }
         }
         else if(!refreshPos && !this.dataTipService.isCurrentDataTip(this.popComponentName,
                                                                      this.popContainerName))
         {
            if(!this.miniToolbar) {
               this.renderer.setStyle(this.elementRef.nativeElement, "display", "none");
            }
            else {
               this.renderer.setStyle(nativeElement.parentElement, "display", "none");
               this.renderer.removeClass(this.elementRef.nativeElement, "mini-toolbar-in-pop");
            }
            this.removeOutsideClickListener();
         }
      }
   }

   private setPopOutPosition(nativeElement: any, mainComponent: any) {
      const triggerInfo = this.popService.getTriggerPopInfo(this.popComponentName);
      const containerTriggerInfo =
         this.popService.getTriggerPopInfo(this.popContainerName);
      const popInfo = this.popService.getPopInfo(this.popComponentName);
      const containerInfo = this.popService.getPopInfo(this.popContainerName);

      let top = this.popService.popY;
      let left = this.popService.popX;
      let isCenter = this.popService.getPopLocation() &&
         this.popService.getPopLocation().toString() == PopLocation.CENTER.toString();

       if(isCenter) {

          if(containerInfo) {
             left = this.popService.viewerOffset.width / 2 - containerInfo.width / 2;
             top = this.popService.viewerOffset.height / 2 - containerInfo.height / 2;
          }
          else {
             left = this.popService.viewerOffset.width / 2 - popInfo.width / 2;
             top = this.popService.viewerOffset.height / 2 - popInfo.height / 2;
          }
       }

      const alpha = this.popService.popAlpha;
      let parentElem: any = nativeElement;
      let reducedEmbeddedVsTop = 0;
      let reducedEmbeddedVsLeft = 0;

      while(true) {
         parentElem = GuiTool.closest(parentElem, ".embedded-viewsheet");

         if(parentElem) {
            let parentTop = parseInt(parentElem.style["top"], 10);
            reducedEmbeddedVsTop += parentTop;
            top -= parentTop;
            let parentLeft = parseInt(parentElem.style["left"], 10);
            left -= parentLeft;
            reducedEmbeddedVsLeft += parentLeft;
         }
         else {
            break;
         }
      }

      const viewerRect = this.popService.viewerOffset;
      const viewportSize: [number, number] = GuiTool.getViewportSize();
      let topOffset: number = viewerRect.scrollTop;
      let leftOffset: number = viewerRect.scrollLeft;

      if(this.contextProvider.preview) {
         const bottomTabs = document.getElementById("sheet-tabs");
         viewportSize[1] -= bottomTabs ? bottomTabs.clientHeight : 0;
      }

      if(containerInfo && left + containerInfo.width > viewerRect.width) {
         // place on left
         if(left > containerInfo.width + leftOffset) {
            leftOffset = -(containerInfo.width + leftOffset - viewerRect.scrollLeft);
         }
         // just shift up
         else {
            if(left + viewerRect.scrollLeft > containerInfo.width) {
               leftOffset = viewerRect.scrollLeft - containerInfo.width;
            }
            else if(left + containerInfo.width - viewerRect.width > 0) {
               leftOffset = -(left + containerInfo.width - viewerRect.width);
            }

            // Bug #58051, make sure it's not positioned outside the visible view
            if(leftOffset < -left) {
               leftOffset = -left;
            }
         }
      }
      // same as above for container itself or if not in container
      else if(!containerInfo &&
         left + reducedEmbeddedVsLeft + popInfo.width > viewerRect.width)
      {
         const selfWidth = popInfo.width;

         // place on left
         if(left + reducedEmbeddedVsLeft > selfWidth + leftOffset) {
            leftOffset = -(selfWidth + leftOffset - viewerRect.scrollLeft);
         }
         // just shift up
         else {
            if(left + reducedEmbeddedVsLeft + viewerRect.scrollLeft > selfWidth) {
               leftOffset = viewerRect.scrollLeft - selfWidth;
            }
            else if(left + reducedEmbeddedVsLeft + selfWidth - viewerRect.width > 0) {
               leftOffset = -(left + reducedEmbeddedVsLeft + selfWidth - viewerRect.width);
            }

            // Bug #58051, make sure it's not positioned outside the visible view
            if(leftOffset < -left) {
               leftOffset = -left;
            }
         }
      }

      const containerHeight = containerInfo
         ? (<any> containerInfo.vsObject).objectHeight || containerInfo.height : 0;

      if(containerInfo && top + containerHeight > viewerRect.height) {
         // place on top
         if(containerHeight < top + topOffset) {
            topOffset -= containerHeight;
         }
         // just shift up
         else {
            topOffset = -(top + containerHeight - viewerRect.height);
         }
      }
      // same as above for container itself or if not in container
      else if(!containerInfo && top + popInfo.height > viewerRect.height) {
         let selfHeight = popInfo.height;

         // popInfo height may not be accurate. Use css height of the component if possible
         const cssHeightString = window.getComputedStyle(this.elementRef.nativeElement).height;
         selfHeight = !!cssHeightString && cssHeightString.endsWith("px") ?
            parseInt(cssHeightString.substring(0, cssHeightString.length - 2), 10) : selfHeight;

         if(selfHeight === 0 && popInfo.height > 0) {
            // Bug #70767, CSS may return 0px, in this case use the popInfo height
            selfHeight = popInfo.height;
         }

         if(selfHeight < top + topOffset) {
            topOffset -= selfHeight;
         }
         // just shift up
         else {
            topOffset -= (top + selfHeight - viewerRect.height);
         }
      }

      if(containerInfo) {
         top += popInfo.top - containerInfo.top;
         left += popInfo.left - containerInfo.left;
      }

      top += topOffset;
      left += leftOffset;

      if(!this.miniToolbar) {
         this.renderer.setStyle(nativeElement, "display", "block");
      }
      else {
         this.renderer.setStyle(nativeElement.parentElement, "display", "block");
         this.renderer.addClass(this.elementRef.nativeElement,
            "mini-toolbar-in-pop");
      }

      let leftStr = mainComponent.style.left;
      leftStr = leftStr.endsWith("px") ? leftStr.substring(0, leftStr.length - 2) : leftStr;

      if(this.miniToolbar) {
         top -= GuiTool.MINI_TOOLBAR_HEIGHT;
         let miniToolbarWidth = this.getToolbarWidth(parseInt(leftStr, 10), mainComponent.clientWidth);
         this.renderer.setStyle(nativeElement, "width",
            this.containerBounds == null ? mainComponent.clientWidth : miniToolbarWidth + "px");
      }

      this.renderer.setStyle(nativeElement, "position", "absolute");
      this.renderer.setStyle(nativeElement, "top", top + "px");

      if(this.miniToolbar) {
         this.renderer.setStyle(nativeElement, "left", this.containerBounds == null ?
            left : this.getToolbarLeft(parseInt(leftStr, 10)) + "px");
      }
      else {
         this.renderer.setStyle(nativeElement, "left", left + "px");
      }

      if(!this.popContainerName) {
         this.renderer.setStyle(nativeElement, "z-index", this.popZIndex + 99998);
      }
      else {
         this.renderer.setStyle(nativeElement, "z-index", this.popZIndex + 99999);
      }

      if(!nativeElement.style["background-color"]) {
         if(containerInfo) {
            this.renderer.setStyle(nativeElement, "background-color", "inherit");
         }
         else {
            const bg = this.miniToolbar ? "rgba(255,255,255,0)" : this.popBackground;
            this.renderer.setStyle(nativeElement, "background-color", bg);
         }
      }

      if(alpha != null && alpha != 1) {
         this.renderer.setStyle(nativeElement, "opacity", alpha / 100);
      }

      setTimeout(() => this.createOutsideClickListener(), 200);
   }

   private outsideClickCallback(event: MouseEvent): boolean {
      const parentElement = this.elementRef.nativeElement.parentElement;

      if((!parentElement || !parentElement.contains(event.target)) &&
         !GuiTool.parentContainsClass(<any> event.target, "mini-toolbar") &&
         !GuiTool.parentContainsClass(<any> event.target, "mobile-toolbar"))
      {
         return false;
      }

      return true;
   }

   private getToolbarLeft(left: number) {
      let scrollbarOffset = (this.miniToolbar ? GuiTool.measureScrollbars() : 0);
      let viewerRect = this.getViewerOffset(this.popComponentName);
      let containerRightBound = this.containerBounds.width + viewerRect.scrollLeft - scrollbarOffset;

      if(left + this.actionsWidth > containerRightBound) {
         return containerRightBound - this.actionsWidth;
      }

      return left;
   }

   private getToolbarWidth(left: number, width: number) {
      let viewerRect = this.getViewerOffset(this.popComponentName);
      let leftOffset: number = viewerRect.scrollLeft;
      let containerRightEdge = this.containerBounds.width - left + leftOffset;
      let scrollbarOffset = (this.miniToolbar ? GuiTool.measureScrollbars() : 0);
      return Math.min(width, containerRightEdge - scrollbarOffset);
   }

   private insideOfThisPopContainer(event: MouseEvent): boolean {
      if(this.isCurrentPopComponent()) {
         return this.popService.isInPopContainer(event);
      }

      return false;
   }

   private createOutsideClickListener(): void {
      if(!this.outsideClickListener) {
         this.outsideClickListener = this.renderer.listen("document", "click", (event: MouseEvent) => {
            if((this.isCurrentPopComponent() || !this.popService.getPopComponent()) &&
               event.button === 0)
            {
               if(!this.outsideClickCallback(event) && !this.insideOfThisPopContainer(event)) {
                  let popComponent = this.popService.getPopComponentModel();
                  let isSelection = popComponent?.objectType == "VSSelectionContainer" ||
                     popComponent?.objectType == "VSSelectionList" || popComponent?.objectType == "VSSelectionTree";

                  if(GuiTool.isMobileDevice() && isSelection && (<any> popComponent).maxMode) {
                     return;
                  }

                  this.popService.hidePopComponent();
                  this.removeOutsideClickListener();
                  this.changeRef.detectChanges();
               }
            }
         });
      }
   }

   private removeOutsideClickListener(): void {
      if(this.outsideClickListener) {
         this.outsideClickListener();
         this.outsideClickListener = null;
      }
   }

   private getViewerOffset(popName: string): any {
      if(this.popService.getPopViewerOffset(popName) == null) {
         this.popService.putPopViewerOffset(popName, this.popService.viewerOffset);

         return this.popService.viewerOffset;
      }

      return this.popService.getPopViewerOffset(popName);
   }
}
