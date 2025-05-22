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
import { DOCUMENT } from "@angular/common";
import {
   AfterViewInit,
   Component,
   ElementRef,
   EventEmitter,
   HostBinding,
   Inject,
   Input,
   OnDestroy,
   OnInit,
   Output,
   Renderer2
} from "@angular/core";
import { Point } from "../../common/data/point";
import { Rectangle } from "../../common/data/rectangle";
import { GuiTool } from "../../common/util/gui-tool";
import { DropdownStackService } from "./dropdown-stack.service";

@Component({
   selector: "fixed-dropdown",
   templateUrl: "fixed-dropdown.component.html"
})
export class FixedDropdownComponent implements OnInit, AfterViewInit, OnDestroy {
   @Input() container: Element;
   @Input() allowPositionOutsideContainer: boolean = false;
   @Input() autoClose: boolean = true;
   @Input() closeOnOutsideClick: boolean = true;
   @Input() closeOnWindowResize: boolean = true;
   @Input() closeOnEsc: boolean = true;
   @Input() zIndex: number;
   @Output() onOpen = new EventEmitter<void>(true);
   @Output() onClose = new EventEmitter<void>();
   @HostBinding("class.fixed-dropdown") fixedDropdownCSS: boolean = true;
   protected listeners: Function[] = [];
   private listenerTick: any;
   private dropdownPosition: Point;
   private mobile = GuiTool.isMobileDevice();
   private opacity: number;

   constructor(protected elementRef: ElementRef,
               protected renderer: Renderer2,
               @Inject(DOCUMENT) protected document: Document,
               protected dropdownService: DropdownStackService)
   {
      this.dropdownPosition = new Point();
   }

   ngOnInit() {
      // Initially have opacity 0 to prevent showing the viewport repositioning.
      this.opacity = 0;
      // Wait a tick as the event that may have triggered the dynamic creation of this
      // component may not have bubbled to the document layer yet
      this.listenerTick = setTimeout(
         () => {
            if(this.container && this.container.tagName === 'FORM') {
               this.listeners.push(
                  this.renderer.listen(this.container, "mousedown", (e) => this.documentMousedown(e)),
               )
            }
            else {
               this.listeners.push(
                  this.renderer.listen("document", "mousedown", (e) => this.documentMousedown(e)),
               )
            }

            this.listeners.push(
               this.renderer.listen("document", "click", (e) => this.documentClick(e)),
               this.renderer.listen("document", "keyup.esc", (e) => this.closeFromOutsideEsc(e)),
               this.renderer.listen("window", "resize", (e) => this.closeFromWindowResize(e)),
            );
         }, 0);
   }

   ngAfterViewInit() {
      this.onOpen.emit();
   }

   ngOnDestroy() {
      clearTimeout(this.listenerTick);
      this.listeners.forEach((callback) => callback());
   }

   @HostBinding("style.top.px")
   get topPosition() {
      return this.dropdownPosition.y;
   }

   @HostBinding("style.left.px")
   get leftPosition() {
      return this.dropdownPosition.x;
   }

   @HostBinding("style.z-index")
   get _zIndex() {
      return this.zIndex;
   }

   @HostBinding("style.opacity")
   get _opacity() {
      return this.opacity;
   }

   /**
    * Set the boundaries of the dropdown. If a container element defined then use those boundaries
    * to calculate the position
    */
   set dropdownBounds(dropdownBounds: Rectangle) {
      // set the position of the dropdown
      this.dropdownPosition = new Point(dropdownBounds.x, dropdownBounds.y);
      this.opacity = 1;

      // adjust to the given container element
      if(this.container) {
         const containerElement = this.container;
         const restrictBounds = Rectangle.fromClientRect(containerElement.getBoundingClientRect());

         // Restricting to an empty rectangle (such as when the body size is 0) doesn't really
         // make sense so use the viewport size instead
         if(restrictBounds.isEmpty() || this.allowPositionOutsideContainer) {
            [restrictBounds.x, restrictBounds.y] = [0, 0];
            [restrictBounds.width, restrictBounds.height] = GuiTool.getViewportSize();
         }

         // for mobile device the body client height maybe not same with the viewport height
         // such as hide the url input.
         if(GuiTool.isMobileDevice() && containerElement == document.body) {
            restrictBounds.height = window.innerHeight;
         }

         const dropdownRight = dropdownBounds.x + dropdownBounds.width;
         const dropdownBottom = dropdownBounds.y + dropdownBounds.height;
         const restrictRight = restrictBounds.x + restrictBounds.width;
         const restrictBottom = restrictBounds.y + restrictBounds.height;

         if(dropdownRight > restrictRight) {
            this.dropdownPosition.x = Math.max(0, restrictRight - dropdownBounds.width);
         }

         if(dropdownBottom > restrictBottom) {
            this.dropdownPosition.y = Math.max(0, restrictBottom - dropdownBounds.height);
         }
      }
   }

   // check if target is the backdrop of a modal dialog
   private isBackdrop(target: Element): boolean {
      return target.classList.contains("modal") && target.classList.contains("fade");
   }

   private isDropdown(element: Element): boolean {
      return GuiTool.parentContainsClass(element, "fixed-dropdown");
   }

   private documentMousedown(event: MouseEvent) {
      // Bug #62941, when inside the shadow dom, event.target points to the shadow root instead of
      // the actual element that triggered the event so use event.composedPath instead. Need to get
      // the target here before setTimeout, otherwise composedPath will be empty
      let target = event.composedPath()[0] || event.target;

      // wait until a newly popped up fixed dropdown to set the current dropdown so
      // the isCurrent() check would work properly
      setTimeout(() => this.documentMousedown0(event, target), 0);
   }

   private documentMousedown0(event: MouseEvent, target: EventTarget) {
      const element = target as Element;

      if(this.closeOnOutsideClick && !this.elementContainsTarget(target) &&
         !this.isBackdrop(element) &&
         (!this.isDropdown(element) || this.dropdownService.isCurrent(this)))
      {
         this.onClose.emit();
         event.stopPropagation();
      }
   }

   private documentClick(event: MouseEvent) {
      const target = event.target as Element;

      if(event.button !== 2 && this.autoClose && this.dropdownService.isCurrent(this)) {
         this.onClose.emit();
         event.stopPropagation();
      }
   }

   private closeFromOutsideEsc(event: KeyboardEvent) {
      if(this.closeOnEsc) {
         this.onClose.emit();
      }
   }

   private closeFromWindowResize(event: Event) {
      // opening keyboard on mobile causes window resize
      if(this.closeOnWindowResize && !this.mobile) {
         this.onClose.emit();
      }
   }

   tryClose() {
      if(this.autoClose) {
         this.onClose.emit();
      }
   }

   protected elementContainsTarget(target: EventTarget): boolean {
      return this.elementRef.nativeElement.contains(target);
   }
}
