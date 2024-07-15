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
   Input, NgZone,
   OnChanges,
   OnDestroy,
   Output,
   SimpleChanges
} from "@angular/core";
import { Subscription } from "rxjs";
import { InteractService } from "./interact.service";
import { Point } from "../../common/data/point";

@Directive({
   selector: "[wInteractContainer]",
   providers: [InteractService]
})
export class InteractContainerDirective implements OnChanges, OnDestroy {
   // whether sub-elements are tethered
   @Input() composited: boolean = true;

   // draggable() configuration
   @Input() draggableElementRect: { top: number, left: number, bottom: number, right: number } = {
      top: 0, left: 0, bottom: 0, right: 0
   };

   @Input() draggableRestriction: string |
      { top: number, left: number, bottom: number, right: number } |
      { x: number, y: number, width: number, height: number };

   // snap() configuration
   @Input() snapToGrid: boolean = false;
   @Input() snapGridSize: number = 10;
   @Input() snapToGuides: boolean = false;
   @Input() snapHorizontalGuides: number[];
   @Input() snapVerticalGuides: number[];
   @Input() snapGuideRange: number;
   @Input() snapGuideOffset: number = 0;

   @Output() onSnap = new EventEmitter<{x: number, y: number}>();
   private snapSubscription: Subscription;

   constructor(public element: ElementRef, private service: InteractService, private zone: NgZone) {
      service.setContainer(this);
      this.snapSubscription = service.onSnap.subscribe(
         (snap) => this.zone.run(() => this.onSnap.emit(snap)));
   }

   ngOnChanges(changes: SimpleChanges): void {
      this.service.notify();
   }

   ngOnDestroy(): void {
      if(this.snapSubscription) {
         this.snapSubscription.unsubscribe();
      }
   }

   // snap to grid/objects
   snap(point: Point): Point {
      if(!this.snapToGrid && !this.snapToGuides) {
         return point;
      }

      return this.service.snap(point);
   }
}
