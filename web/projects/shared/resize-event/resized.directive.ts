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
import {
   Directive,
   ElementRef,
   EventEmitter,
   NgZone,
   OnDestroy,
   OnInit,
   Output
} from "@angular/core";
import { ResizedEvent } from "./resized.event";

@Directive({
   selector: '[resized]'
})
export class ResizedDirective implements OnInit, OnDestroy {
   private observer: ResizeObserver;
   private oldRect?: DOMRectReadOnly;

   @Output()
   public readonly resized: EventEmitter<ResizedEvent>;

   public constructor(
      private readonly element: ElementRef,
      private readonly zone: NgZone
   )
   {
      this.resized = new EventEmitter<ResizedEvent>();
      this.observer = new ResizeObserver(entries => this.zone.run(() => this.observe(entries)));
   }

   public ngOnInit(): void {
      this.observer.observe(this.element.nativeElement);
   }

   public ngOnDestroy(): void {
      this.observer.disconnect();
   }

   private observe(entries: ResizeObserverEntry[]): void {
      const domSize = entries[0];
      const resizedEvent = new ResizedEvent(domSize.contentRect, this.oldRect);
      this.oldRect = domSize.contentRect;
      this.resized.emit(resizedEvent);
   }
}