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
import { Injectable, NgZone, OnDestroy, Renderer2 } from "@angular/core";
import { Subject } from "rxjs";
import { map, pairwise, share, takeUntil, throttleTime } from "rxjs/operators";
import { TopScrollService } from "./top-scroll.service";

@Injectable()
export class TopScrollSupport implements OnDestroy {
   private destroy$ = new Subject<void>();
   private scroll$ = new Subject<number>();
   private element: any;
   private scrollListener: () => void;
   private swipeListener: () => void;

   constructor(private scrollService: TopScrollService, private renderer: Renderer2,
               private zone: NgZone)
   {
      this.scroll$
         .pipe(
            takeUntil(this.destroy$),
            throttleTime(100),
            pairwise(),
            map(([y1, y2]): "up" | "down" => (y2 < y1 ? "up" : "down")),
            share())
         .subscribe(direction => this.scroll(direction));
      this.scrollService.visibilityChanged
         .pipe(takeUntil(this.destroy$))
         .subscribe(() => setTimeout(() => this.addSwipeListener(), 0));
   }

   ngOnDestroy(): void {
      this.detach();
      this.destroy$.next();
      this.destroy$.unsubscribe();
   }

   attach(element: any): void {
      if(!!element && element.classList.contains("em-top-scroll")) {
         return;
      }

      this.detach();

      if(!!element) {
         this.renderer.addClass(element, "em-top-scroll");
         this.element = element;
         this.scrollListener = this.renderer.listen(element, "scroll", () => {
            this.scroll$.next(element.scrollTop);
            setTimeout(() => this.addSwipeListener(), 0);
         });
         this.addSwipeListener();
      }
   }

   private detach(): void {
      if(!!this.scrollListener) {
         this.scrollListener();
         this.scrollListener = null;
      }

      if(!!this.swipeListener) {
         this.swipeListener();
         this.swipeListener = null;
      }

      if(!!this.element) {
         this.renderer.removeClass(this.element, "em-top-scroll");
         this.element = null;
      }
   }

   private addSwipeListener() {
      if(this.scrollService.visible && !!this.swipeListener) {
         this.swipeListener();
         this.swipeListener = null;
      }
      else if(!this.scrollService.visible && !this.swipeListener && !!this.element &&
              this.element.scrollTop === 0 && this.element.scrollHeight > 0 &&
              this.element.scrollHeight === this.element.clientHeight)
      {
         this.swipeListener = this.renderer.listen(this.element, "swipedown", () => {
            this.scroll("up");
            this.swipeListener();
            this.swipeListener = null;
         });
      }
   }

   private scroll(direction: "up" | "down"): void {
      this.zone.run(() => this.scrollService.scroll(direction));
   }
}