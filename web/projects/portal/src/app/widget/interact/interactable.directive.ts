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
   Directive,
   ElementRef,
   EventEmitter,
   Input,
   OnChanges,
   OnDestroy,
   OnInit,
   Output,
   SimpleChanges
} from "@angular/core";
import { Rectangle } from "../../common/data/rectangle";
import { InteractService } from "./interact.service";

export type DropzoneOverlap = "pointer" | "center" | number;
const VALID_POSITIONS = ["absolute", "relative", "fixed"];

@Directive({
   selector: "[wInteractable]"
})
export class InteractableDirective implements OnInit, OnDestroy, OnChanges {
   // interact() configuration
   @Input() interactableIgnoreFrom: string;
   @Input() interactableAllowFrom: string;
   // this is not used in code by is used to trigger notify(). since invisible element
   // is skipped in service, we need to notify() service again when visibility changed.
   @Input() interactableVisible: boolean;

   // draggable() configuration
   @Input() interactableDraggable: boolean = false;
   @Input() interactableDraggableGroup: string = "__default_drag_group";
   @Input() draggableRestriction: Element | ClientRect | Rectangle;

   // the interactable may belong to a container that is composited but want to disable its
   // own composition so provide a flag to override that behavior here
   @Input() composited: boolean = true;

   // draggable() events
   @Output() onDraggableStart = new EventEmitter<any>();
   @Output() onDraggableMove = new EventEmitter<any>();
   @Output() onDraggableEnd = new EventEmitter<Rectangle>();

   // dropzone() configuration
   @Input() interactableDropzone: boolean = false;
   @Input() dropzoneAccept: string;
   @Input() dropzoneOverlap: DropzoneOverlap;

   // dropzone() events
   @Output() onDropzoneActivate = new EventEmitter<any>();
   @Output() onDropzoneDeactivate = new EventEmitter<any>();
   @Output() onDropzoneEnter = new EventEmitter<any>();
   @Output() onDropzoneLeave = new EventEmitter<any>();
   @Output() onDropzoneMove = new EventEmitter<any>();
   @Output() onDropzoneDrop = new EventEmitter<any>();

   // resizable() configuration
   @Input() interactableResizable: boolean = false;
   @Input() resizableMargin: number;
   @Input() resizablePreserveAspectRatio: boolean;
   @Input() resizableElementRect: { top: number, left: number, bottom: number, right: number } = {
      top: 0, left: 0, bottom: 1, right: 1
   };
   @Input() resizableRestriction: string |
      { top: number, left: number, bottom: number, right: number } |
      { x: number, y: number, width: number, height: number };
   @Input() resizableTopEdge: any = true;
   @Input() resizableLeftEdge: any = true;
   @Input() resizableBottomEdge: any = true;
   @Input() resizableRightEdge: any = true;
   // this is used to adjust for resize borders
   @Input() resizeAdjustment: number = 2;

   // resizable() events
   @Output() onResizableStart = new EventEmitter<any>();
   @Output() onResizableMove = new EventEmitter<any>();
   @Output() onResizableEnd = new EventEmitter<Rectangle>();

   // Special field for tab resizing
   @Input() compositeResizeable = false;

   private hostTop: number;
   private hostLeft: number;
   private hostWidth: number;
   private hostHeight: number;
   private initialTop: number;
   private initialLeft: number;
   private initialWidth: number;
   private initialHeight: number;
   private initialized: boolean = false;

   constructor(public element: ElementRef, private service: InteractService) {
   }

   ngOnInit(): void {
      this.service.addInteractable(this);
      this.service.notify();
      this.initialized = true;
   }

   ngOnDestroy(): void {
      this.service.removeInteractable(this);
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(this.initialized) {
         this.service.notify();
      }
   }

   public handleDragStart(event: any): void {
      this.saveInitialStyle(event.target);
      this.onDraggableStart.emit(event);
      this.markMoving(true);
   }

   public handleDragMove(event: any): void {
      this.hostTop += event.dy;
      this.hostLeft += event.dx;
      this.onDraggableMove.emit(event);
   }

   public handleDragEnd(event: any): void {
      const deltaRect = new Rectangle(event.dx, event.dy, 0, 0);
      this.onDraggableEnd.emit(deltaRect);
      this.markMoving(false);
   }

   public handleResizeStart(event: any): void {
      this.saveInitialStyle(event.target);
      this.onResizableStart.emit(event);
      this.markMoving(true);
   }

   public handleResizeMove(event: any): void {
      this.hostWidth = event.rect.width;
      this.hostHeight = event.rect.height;

      // translate when resizing from top or left edges
      if(event.deltaRect.top != 0) {
         this.hostTop += event.dy;
      }

      if(event.deltaRect.left != 0) {
         this.hostLeft += event.dx;
      }

      this.onResizableMove.emit(event);
   }

   /**
    * Since we already store the top/left/width/height on resize end we can just calculate
    * the delta rectangle and emit without looking at the event
    */
   public handleResizeEnd(event: any): void {
      const dx = this.hostLeft - this.initialLeft;
      const dy = this.hostTop - this.initialTop;
      const dwidth = this.hostWidth - this.initialWidth;
      const dheight = this.hostHeight - this.initialHeight;
      const deltaRect = new Rectangle(dx, dy, dwidth, dheight);
      this.onResizableEnd.emit(deltaRect);
      this.markMoving(false);
   }

   // mark moving/resizing with class
   private markMoving(moving: boolean): void {
      const all: NodeList = document.querySelectorAll(".vs-object");
      let vsobj: Element;

      for(let i = 0; i < all.length; i++) {
         vsobj = <Element> all.item(i);

         if(moving) {
            vsobj.classList.add("bd-gray");
         }
         else {
            vsobj.classList.remove("bd-gray");
         }
      }

      vsobj = this.element.nativeElement;

      if(vsobj) {
         if(moving) {
            vsobj.classList.add("moving-resizing");
         }
         else {
            vsobj.classList.remove("moving-resizing");
         }
      }
   }

   public handleDropzoneActivate(event: any): void {
      this.onDropzoneActivate.emit(event);
   }

   public handleDropzoneDeactivate(event: any): void {
      this.onDropzoneDeactivate.emit(event);
   }

   public handleDropzoneEnter(event: any): void {
      this.onDropzoneEnter.emit(event);
   }

   public handleDropzoneLeave(event: any): void {
      this.onDropzoneLeave.emit(event);
   }

   public handleDropzoneMove(event: any): void {
      this.onDropzoneMove.emit(event);
   }

   public handleDropzoneDrop(event: any): void {
      this.onDropzoneDrop.emit(event);
   }

   /**
    * Save the style of the given HTMLElement. This way if the element already has a
    * position we can offset by that amount instead of starting from 0.
    *
    * @param {HTMLElement} element the element used to get a computed style
    */
   private saveInitialStyle(element: HTMLElement): void {
      const elementStyle = window.getComputedStyle(element);
      const position = elementStyle.position;

      if(VALID_POSITIONS.indexOf(position) === -1) {
         throw new Error("Host element of interact directive must be positioned: " + position);
      }

      this.hostTop = this.initialTop = parseInt(elementStyle.top, 10) || 0;
      this.hostLeft = this.initialLeft = parseInt(elementStyle.left, 10) || 0;
      this.hostWidth = this.initialWidth = parseInt(elementStyle.width, 10) || 0;
      this.hostHeight = this.initialHeight = parseInt(elementStyle.height, 10) || 0;
   }
}
