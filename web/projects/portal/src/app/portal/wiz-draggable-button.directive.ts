/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
import { AfterViewInit, Directive, ElementRef, HostListener } from "@angular/core";

export const WIZ_BUTTON_POSITION_KEY = "inetsoft.portal.wizButtonPosition";

/** Pointer travel (px) before a press becomes a drag instead of a click. */
const DRAG_THRESHOLD = 4;
/** Minimum distance (px) kept between the button and the viewport edge. */
const EDGE_MARGIN = 8;
/** Gap (px) between the bottom of the portal toolbar and the button's default position. */
const TOOLBAR_GAP = 16;
const DEFAULT_TOP = 72;

interface Position {
   left: number;
   top: number;
}

/**
 * Makes a floating, fixed-position host element draggable with the mouse, a pen or a finger.
 *
 * A press that moves less than {@link DRAG_THRESHOLD} is an ordinary click (the host is a link
 * that opens the Wiz portal), a longer press-and-move repositions the element and swallows the
 * click that follows. The position is kept within the viewport, survives window resizes, and is
 * remembered per browser in local storage (a missing or blocked storage only loses the memory).
 * Without a remembered position the element sits at the right edge just below the portal toolbar.
 */
@Directive({
   selector: "[wizDraggableButton]",
   standalone: true
})
export class WizDraggableButtonDirective implements AfterViewInit {
   private pointerId: number = null;
   private startX = 0;
   private startY = 0;
   private origin: Position = null;
   private dragging = false;
   private suppressClick = false;
   private placed: Position = null;

   constructor(private host: ElementRef<HTMLElement>) {
   }

   ngAfterViewInit(): void {
      const saved = this.loadPosition();

      if(saved) {
         this.applyPosition(saved);
      }
      else {
         this.placeDefault();
      }
   }

   @HostListener("pointerdown", ["$event"])
   onPointerDown(event: PointerEvent): void {
      if(this.pointerId !== null || (event.pointerType === "mouse" && event.button !== 0)) {
         return;
      }

      const rect = this.host.nativeElement.getBoundingClientRect();
      this.pointerId = event.pointerId;
      this.startX = event.clientX;
      this.startY = event.clientY;
      this.origin = { left: rect.left, top: rect.top };
      this.dragging = false;

      try {
         this.host.nativeElement.setPointerCapture(event.pointerId);
      }
      catch(e) {
         // Pointer capture is only an optimisation for fast drags.
      }
   }

   @HostListener("pointermove", ["$event"])
   onPointerMove(event: PointerEvent): void {
      if(event.pointerId !== this.pointerId) {
         return;
      }

      const dx = event.clientX - this.startX;
      const dy = event.clientY - this.startY;

      if(!this.dragging) {
         if(Math.hypot(dx, dy) < DRAG_THRESHOLD) {
            return;
         }

         this.dragging = true;
         this.host.nativeElement.classList.add("dragging");
      }

      event.preventDefault();
      this.applyPosition({ left: this.origin.left + dx, top: this.origin.top + dy });
   }

   @HostListener("pointerup", ["$event"])
   @HostListener("pointercancel", ["$event"])
   onPointerEnd(event: PointerEvent): void {
      if(event.pointerId !== this.pointerId) {
         return;
      }

      try {
         this.host.nativeElement.releasePointerCapture(event.pointerId);
      }
      catch(e) {
         // Already released.
      }

      if(this.dragging) {
         // The click that follows the release must not open the link.
         this.suppressClick = true;
         setTimeout(() => this.suppressClick = false, 100);
         this.host.nativeElement.classList.remove("dragging");

         if(this.placed) {
            this.savePosition(this.placed);
         }
      }

      this.pointerId = null;
      this.dragging = false;
   }

   @HostListener("click", ["$event"])
   onClick(event: Event): void {
      if(this.suppressClick) {
         this.suppressClick = false;
         event.preventDefault();
         event.stopPropagation();
      }
   }

   /** The browser's own link drag would fight with the pointer handling above. */
   @HostListener("dragstart", ["$event"])
   onDragStart(event: Event): void {
      event.preventDefault();
   }

   @HostListener("window:resize")
   onResize(): void {
      if(this.placed) {
         this.applyPosition(this.placed);
      }
   }

   private placeDefault(): void {
      const toolbar = this.host.nativeElement.ownerDocument.querySelector(".portal-navbar");
      const bottom = toolbar ? toolbar.getBoundingClientRect().bottom : 0;
      const top = bottom > 0 ? bottom + TOOLBAR_GAP : DEFAULT_TOP;
      this.host.nativeElement.style.top = `${top}px`;
      // The stylesheet's right edge anchor stays in effect; nothing is remembered until a drag.
   }

   private applyPosition(position: Position): void {
      const view = this.host.nativeElement.ownerDocument.defaultView;
      const size = this.host.nativeElement.getBoundingClientRect();
      const maxLeft = Math.max(EDGE_MARGIN, view.innerWidth - size.width - EDGE_MARGIN);
      const maxTop = Math.max(EDGE_MARGIN, view.innerHeight - size.height - EDGE_MARGIN);
      const left = Math.min(Math.max(position.left, EDGE_MARGIN), maxLeft);
      const top = Math.min(Math.max(position.top, EDGE_MARGIN), maxTop);
      const style = this.host.nativeElement.style;

      style.left = `${left}px`;
      style.top = `${top}px`;
      style.right = "auto";
      style.bottom = "auto";
      this.placed = { left, top };
   }

   private loadPosition(): Position {
      try {
         const raw = window.localStorage.getItem(WIZ_BUTTON_POSITION_KEY);
         const value = raw ? JSON.parse(raw) : null;

         if(value && Number.isFinite(value.left) && Number.isFinite(value.top)) {
            return { left: value.left, top: value.top };
         }
      }
      catch(e) {
         // Storage unavailable or the stored value is damaged: use the default placement.
      }

      return null;
   }

   private savePosition(position: Position): void {
      try {
         window.localStorage.setItem(WIZ_BUTTON_POSITION_KEY, JSON.stringify(position));
      }
      catch(e) {
         // Storage unavailable: the button simply starts at the default place next time.
      }
   }
}
