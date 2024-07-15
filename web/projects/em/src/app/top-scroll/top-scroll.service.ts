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
import { Injectable, OnDestroy } from "@angular/core";
import { NavigationEnd, Router } from "@angular/router";
import { Observable, Subject } from "rxjs";
import { filter, takeUntil } from "rxjs/operators";

@Injectable({
   providedIn: "root"
})
export class TopScrollService implements OnDestroy {
   get visible(): boolean {
      return this._visible;
   }

   set visible(value: boolean) {
      if(value !== this._visible) {
         this._visible = value;
         this.visibilityChanged.next(value);
      }
   }

   visibilityChanged = new Subject<boolean>();
   private scrolled = new Subject<"up" | "down">();
   private destroy$ = new Subject<void>();
   private _visible = true;

   constructor(router: Router) {
      router.events
         .pipe(
            takeUntil(this.destroy$),
            filter((event) => (event instanceof NavigationEnd)),
         )
         .subscribe(() => this.scroll("up"));
   }

   ngOnDestroy(): void {
      this.scrolled.unsubscribe();
      this.destroy$.unsubscribe();
   }

   get onScroll(): Observable<"up" | "down"> {
      return this.scrolled.asObservable();
   }

   scroll(direction: "up" | "down"): void {
      this.scrolled.next(direction);
   }
}
