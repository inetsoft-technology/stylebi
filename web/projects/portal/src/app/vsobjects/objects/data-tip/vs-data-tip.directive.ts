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
import { Directive, DoCheck, ElementRef, Input, Renderer2, NgZone } from "@angular/core";
import { PopComponentService } from "./pop-component.service";
import { DataTipService } from "./data-tip.service";
import { GuiTool } from "../../../common/util/gui-tool";
import { DebounceService } from "../../../widget/services/debounce.service";

@Directive({
   selector: "[VSDataTip]"
})
export class VSDataTipDirective implements DoCheck {
   @Input() public dataTipName: string;
   @Input() public popContainerName: string; // this is shared with VSPopComponentDirective
   @Input() public popZIndex: number = 0;
   @Input() public miniToolbar: boolean = false;
   private outsideClickListener: () => any;
   private enterListener: () => any;
   private leaveListener: () => any;
   private inElement: Element = null;
   private mobileDevice = GuiTool.isMobileDevice();

   constructor(private popService: PopComponentService,
               private dataTipService: DataTipService,
               private debounceService: DebounceService,
               private zone: NgZone,
               private elementRef: ElementRef,
               private renderer: Renderer2)
   {
   }

   private isCurrentDataTip(): boolean {
      return this.dataTipService.isCurrentDataTip(this.dataTipName, this.popContainerName);
   }

   get dataTipClass(): string {
      const name = this.dataTipService.dataTipName;
      return "current-datatip-" + (name || "none").replace(/ /g, "_");
   }

   /**
    * Instead of hostbinding for styles, use renderer on changes to conditionally
    * apply styles.
    */
   ngDoCheck(): void {
      if(this.popService.getPopComponent() &&
         (this.popService.getPopComponent() == this.dataTipName ||
            this.popService.getPopComponent() == this.popContainerName))
      {
         return;
      }

      if(this.dataTipService.isDataTip(this.dataTipName) ||
         this.dataTipService.isDataTip(this.popContainerName))
      {
         if(this.isCurrentDataTip() &&
            (this.dataTipService.isDataTipVisible(this.dataTipName) ||
             this.dataTipService.isDataTipVisible(this.popContainerName)))
         {
            // cancel existing hide events
            this.debounceService.cancel(DataTipService.DEBOUNCE_KEY);
            const popInfo = this.popService.getPopInfo(this.dataTipName);
            const containerInfo = this.popService.getPopInfo(this.popContainerName);
            const nativeElement = !this.miniToolbar
               ? this.elementRef.nativeElement
               : this.elementRef.nativeElement.querySelector(".mini-toolbar");
            // main div (self or the associated assembly div for minitoolbar)
            const mainComponent = !this.miniToolbar ? nativeElement :
               document.getElementById(this.dataTipService.getVSObjectId(this.dataTipName));

            let top = this.dataTipService.dataTipY;
            let left = this.dataTipService.dataTipX;
            const alpha = this.dataTipService.dataTipAlpha;
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

            const viewerRect = this.dataTipService.viewerOffset;
            const viewportSize = [viewerRect.width, viewerRect.height];
            let topOffset: number = DataTipService.DATA_TIP_OFFSET;
            let leftOffset: number = DataTipService.DATA_TIP_OFFSET;

            if(containerInfo && left + containerInfo.width > viewportSize[0]) {
               // place on left
               leftOffset = -Math.min(left, containerInfo.width + leftOffset -
                  viewerRect.scrollLeft);
            }
            // same as above for container itself or if not in container
            else if(!containerInfo && left + mainComponent.clientWidth > viewportSize[0]) {
               const selfWidth = mainComponent.clientWidth;

               // place on left
               leftOffset = -Math.min(left, selfWidth + leftOffset - viewerRect.scrollLeft);
            }
            else {
               leftOffset = viewerRect.scrollLeft;
            }

            const containerHeight = containerInfo
               ? (<any>containerInfo.vsObject).objectHeight || containerInfo.height : 0;

            if(containerInfo && top + reducedEmbeddedVsTop + containerHeight > viewportSize[1]) {
               topOffset = -Math.min(top, containerHeight + reducedEmbeddedVsTop + topOffset - viewerRect.scrollTop);
            }
            // same as above for container itself or if not in container
            else if(!containerInfo && top + reducedEmbeddedVsTop + mainComponent.clientHeight > viewportSize[1]) {
               const selfHeight = mainComponent.clientHeight;

               // place on top
               topOffset = -Math.min(top, selfHeight + topOffset - viewerRect.scrollTop);
            }
            else {
               topOffset = viewerRect.scrollTop;
            }

            if(containerInfo) {
               top += popInfo.top - containerInfo.top;
               left += popInfo.left - containerInfo.left;
            }

            top += topOffset;
            left += leftOffset;

            if(this.miniToolbar) {
               top -= GuiTool.MINI_TOOLBAR_HEIGHT;
            }

            // offset the datatip a little so datatip doesn't generate a mouseleave event.
            // otherwise flyover would not work together with datatip
            top += 1;
            left += 1;

            // if displaying top/left, make sure it's not out of bounds
            top = Math.max(top, viewerRect.scrollTop - reducedEmbeddedVsTop);
            left = Math.max(left, viewerRect.scrollLeft - reducedEmbeddedVsLeft);

            this.renderer.setStyle(nativeElement, "left", left + "px");
            this.renderer.setStyle(nativeElement, "top", top + "px");
            this.renderer.addClass(nativeElement, this.dataTipClass);

            if(!this.miniToolbar) {
               this.renderer.setStyle(nativeElement, "display", "block");

               if(alpha != null && alpha != 1) {
                  this.renderer.setStyle(nativeElement, "opacity", alpha / 100);
               }
            }
            else {
               this.renderer.setStyle(this.elementRef.nativeElement, "display", "block");
               this.renderer.setStyle(nativeElement, "width", mainComponent.clientWidth + "px");
            }

            this.renderer.setStyle(nativeElement, "position", "absolute");
            // background set on the server so alpha can be applied to all backgrounds
            //this.renderer.setStyle(nativeElement, "background", "rgba(245,245,245,1.0)");
            this.renderer.setStyle(nativeElement, "z-index", this.popZIndex + 9999);
            this.createOutsideClickListener();
         }
         else {
            this.renderer.setStyle(this.elementRef.nativeElement, "display", "none");
            this.removeOutsideClickListener();
         }
      }
   }

   onLeave(event: MouseEvent) {
      if(this.isCurrentDataTip() && !this.mobileDevice) {
         this.inElement = event.type == "mouseenter" ?
            <Element>event.target : (event as any).toElement;

         this.debounceService.debounce("hide-data-tip", () => {
            if(!this.isMouseInDataTip(this.inElement) &&
               !GuiTool.parentContainsClass(this.inElement, "fixed-dropdown"))
            {
               this.zone.run(() => this.hideDataTip(event.type == "mouseleave"));
            }
         }, 300, []);
      }
   }

   private isMouseInDataTip(elem: Element): boolean {
      return elem &&
         (elem.nodeName.toLowerCase() == "mini-toolbar" ||
            GuiTool.parentContainsClass(elem, "mini-toolbar") ||
             GuiTool.parentContainsClass(elem, "mobile-toolbar") ||
             GuiTool.parentContainsClass(elem, "page-control-container") ||
            GuiTool.parentContainsClass(elem, this.dataTipClass));
   }

   private hideDataTip(clear?: boolean): void {
      if(!this.dataTipService.isFrozen()) {
         this.renderer.removeClass(this.elementRef.nativeElement, this.dataTipClass);
         this.dataTipService.hideDataTip(clear);
         this.removeOutsideClickListener();
      }
   }

   private createOutsideClickListener(): void {
      if(!this.outsideClickListener) {
         let eventName = this.mobileDevice ? "touchstart" : "click";
         this.outsideClickListener = this.renderer.listen("document", eventName, (event: Event) => {
            if(this.isCurrentDataTip()) {
               if(this.dataTipService.isFrozen() || this.mobileDevice) {
                  if(!this.elementRef.nativeElement.parentElement.contains(event.target) &&
                     !this.isMouseInDataTip(<Element>event.target) &&
                     !this.isMouseInDataTip(this.inElement))
                  {
                     this.renderer.removeClass(this.elementRef.nativeElement, this.dataTipClass);
                     this.dataTipService.hideDataTip();
                     this.removeOutsideClickListener();
                  }
               }
            }
         });

         this.enterListener = this.renderer.listen(this.elementRef.nativeElement, "mouseenter",
            (event: MouseEvent) => this.onLeave(event));
         this.leaveListener = this.renderer.listen(this.elementRef.nativeElement, "mouseleave",
            (event: MouseEvent) => this.onLeave(event));
      }
   }

   private removeOutsideClickListener(): void {
      if(this.outsideClickListener) {
         this.outsideClickListener();
         this.outsideClickListener = null;
         this.enterListener();
         this.enterListener = null;
         this.leaveListener();
         this.leaveListener = null;
      }
   }
}
