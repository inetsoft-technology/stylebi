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
   ComponentRef,
   Directive,
   ElementRef,
   Input,
   NgZone,
   OnChanges,
   OnDestroy,
   OnInit,
   Renderer2,
   SimpleChanges,
   TemplateRef
} from "@angular/core";
import { Point } from "../../common/data/point";
import { Rectangle } from "../../common/data/rectangle";
import { TooltipComponent } from "./tooltip.component";
import { TooltipService } from "./tooltip.service";

@Directive({
   selector: "[wTooltip]",
   exportAs: "wTooltip"
})
export class TooltipDirective implements OnChanges, OnInit, OnDestroy {
   @Input() wTooltip: string | TemplateRef<any> | Function;
   @Input() tooltipCSS: string | string[] | Set<string> = "widget__default-tooltip";
   @Input() offsetTop = 15;
   @Input() offsetLeft = 15;
   @Input() waitTime = 500;
   @Input() followCursor = false;
   @Input() disableTooltipOnMousedown = true;
   private tooltipRef: ComponentRef<TooltipComponent>;
   private mousePosition: Point;
   private timeout: any;
   private mousemoveListener: () => void;
   private mouseenterListener: () => void;
   private mouseleaveListener: () => void;
   private mousedownListener: () => void;

   constructor(private hostRef: ElementRef,
               private tooltipService: TooltipService,
               private renderer: Renderer2,
               private zone: NgZone)
   {
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.hasOwnProperty("wTooltip")) {
         if(!this.wTooltip) {
            this.close();
         }
         else if(this.tooltipShowing()) {
            this.tooltipRef.instance.content = this.getTooltipContent();
         }
         else if(this.mousePosition != null) {
            this.clearTimeout();
            this.timeout = setTimeout(() => this.showTooltip(), this.waitTime);
         }
      }
   }

   ngOnInit() {
      if(this.disableTooltipOnMousedown) {
         this.mousedownListener = this.renderer.listen(this.hostRef.nativeElement,
            "mousedown", (e) => this.mousedown(e));
      }

      this.zone.runOutsideAngular(() => {
         this.mouseenterListener = this.renderer.listen(
            this.hostRef.nativeElement, "mouseenter", () => {
               this.clearMousemoveListener();
               this.clearMouseleaveListener();
               this.mousemoveListener = this.renderer.listen(
                  this.hostRef.nativeElement, "mousemove", (e) => this.mousemove(e));
               this.mouseleaveListener = this.renderer.listen(
                  this.hostRef.nativeElement, "mouseleave", (e) => this.mouseleave(e));
            });
      });
   }

   ngOnDestroy() {
      this.close();
      this.clearMousemoveListener();
      this.clearMouseenterListener();
      this.clearMouseleaveListener();
      this.clearMousedownListener();
   }

   mousedown(event: MouseEvent) {
      if(this.isTooltipHandled(event)) {
         return;
      }

      this.setTooltipHandled(event);
      this.close();
      this.clearMousemoveListener();
   }

   mousemove(event: MouseEvent) {
      if(this.isTooltipHandled(event)) {
         return;
      }

      this.setTooltipHandled(event);

      if(!this.wTooltip) {
         this.mousePosition = new Point(event.clientX, event.clientY);
         return;
      }

      if(!this.tooltipShowing()) {
         this.mousePosition = new Point(event.clientX, event.clientY);
         this.clearTimeout();

         if(this.waitTime > 0) {
            this.timeout = setTimeout(() => this.showTooltip(), this.waitTime);
         }
         else {
            this.showTooltip();
         }
      }
      else if(this.followCursor) {
         this.mousePosition = new Point(event.clientX, event.clientY);
         this.positionTooltipWithinViewport();
      }
   }

   mouseleave(event: MouseEvent) {
      if(this.isTooltipHandled(event)) {
         return;
      }

      this.setTooltipHandled(event);
      this.close();
      this.mousePosition = null;
      this.clearMouseleaveListener();
   }

   public close() {
      if(this.tooltipShowing()) {
         this.tooltipService.removeTooltip(this.tooltipRef);
      }

      this.clearTimeout();
   }

   private isTooltipHandled(event: MouseEvent): boolean {
      return !!((<any> event).tooltipHandled);
   }

   private setTooltipHandled(event: MouseEvent): void {
      (<any> event).tooltipHandled = true;
   }

   private showTooltip() {
      if(!this.followCursor) {
         this.clearMousemoveListener();
      }

      const tooltipStr = this.getTooltipContent();

      if(typeof tooltipStr == "string" && !(<string> tooltipStr).trim() || tooltipStr == null) {
         return;
      }

      this.tooltipRef = this.tooltipService.createTooltip();
      this.tooltipRef.onDestroy(() => this.tooltipRef = null);
      const tooltip = this.tooltipRef.instance;
      tooltip.content = tooltipStr;
      tooltip.tooltipCSS = this.tooltipCSS;
      this.tooltipRef.instance.updateView();
      this.positionTooltipWithinViewport();
   }

   private positionTooltipWithinViewport() {
      if(!!this.tooltipRef && !!this.mousePosition) {
         const tooltipElement = this.tooltipRef.location.nativeElement as HTMLElement;
         const container = tooltipElement.parentElement;

         if(!!container) {
            const tooltipBounds = Rectangle.fromClientRect(tooltipElement.getBoundingClientRect());
            const restrictBounds = Rectangle.fromClientRect(container.getBoundingClientRect());
            const naturalRightBound = this.mousePosition.x + this.offsetLeft + tooltipBounds.width;
            const naturalBottomBound = this.mousePosition.y + this.offsetTop + tooltipBounds.height;
            const tooltipIntersectsRightBoundary = naturalRightBound
               > restrictBounds.x + restrictBounds.width;
            const tooltipIntersectsBottomBoundary = naturalBottomBound
               > restrictBounds.y + restrictBounds.height;
            let xPosition: number;
            let yPosition: number;

            if(tooltipIntersectsRightBoundary) {
               xPosition = restrictBounds.x + restrictBounds.width - tooltipBounds.width;
            }
            else {
               xPosition = this.mousePosition.x + this.offsetLeft;
            }

            if(tooltipIntersectsBottomBoundary && tooltipIntersectsRightBoundary) {
               yPosition = this.mousePosition.y - this.offsetTop - tooltipBounds.height;
            }
            else if(tooltipIntersectsBottomBoundary) {
               yPosition = restrictBounds.y + restrictBounds.height - tooltipBounds.height;
            }
            else {
               yPosition = this.mousePosition.y + this.offsetTop;
            }

            xPosition = Math.max(xPosition, 0);
            yPosition = Math.max(yPosition, 0);
            this.renderer.setStyle(tooltipElement, "top", yPosition + "px");
            this.renderer.setStyle(tooltipElement, "left", xPosition + "px");
            this.tooltipRef.instance.updateView();
         }
      }
   }

   private tooltipShowing(): boolean {
      return !!this.tooltipRef;
   }

   private clearTimeout() {
      if(this.timeout != null) {
         clearTimeout(this.timeout);
         this.timeout = null;
      }
   }

   private clearMousemoveListener() {
      if(!!this.mousemoveListener) {
         this.mousemoveListener();
         this.mousemoveListener = null;
      }
   }

   private clearMouseenterListener() {
      if(!!this.mouseenterListener) {
         this.mouseenterListener();
         this.mouseenterListener = null;
      }
   }

   private clearMouseleaveListener() {
      if(!!this.mouseleaveListener) {
         this.mouseleaveListener();
         this.mouseleaveListener = null;
      }
   }

   private clearMousedownListener() {
      if(!!this.mousedownListener) {
         this.mousedownListener();
         this.mousedownListener = null;
      }
   }

   private getTooltipContent(): string | TemplateRef<any> {
      return (this.wTooltip instanceof Function) ? this.wTooltip() : this.wTooltip;
   }
}
