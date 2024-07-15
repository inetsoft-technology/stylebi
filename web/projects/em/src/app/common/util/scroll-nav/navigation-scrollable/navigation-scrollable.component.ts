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
import { CdkScrollable } from "@angular/cdk/overlay";
import {
   Component,
   ContentChildren,
   EventEmitter, NgZone,
   OnDestroy,
   OnInit,
   Output,
   QueryList,
   ViewChild
} from "@angular/core";
import { Subject } from "rxjs";
import { takeUntil } from "rxjs/operators";
import { NavigationScrollableItemDirective } from "../navigation-scrollable-item/navigation-scrollable-item.directive";

@Component({
   selector: "em-navigation-scrollable",
   templateUrl: "./navigation-scrollable.component.html",
   styleUrls: ["./navigation-scrollable.component.scss"]
})
export class NavigationScrollableComponent implements OnInit, OnDestroy {
   @ViewChild(CdkScrollable, { static: true }) scrollable: CdkScrollable;
   @ContentChildren(NavigationScrollableItemDirective) items: QueryList<NavigationScrollableItemDirective>;
   @Output() navigationScrolled = new EventEmitter<string[]>();
   private destroy$ = new Subject<void>();
   private current: string;

   constructor(private zone: NgZone) {
   }

   ngOnInit() {
      this.scrollable.elementScrolled()
         .pipe(takeUntil(this.destroy$))
         .subscribe(() => this.onScrolled());
   }

   ngOnDestroy(): void {
      this.destroy$.next();
      this.destroy$.unsubscribe();
   }

   private onScrolled(): void {
      const event = this.items
         .filter(item => item.isInScrollView(this.scrollable))
         .map(item => item.emNavigationScrollableItem);
      const next = event.join(",");

      if(this.current !== next) {
         this.current = next;

         this.zone.run(() => this.navigationScrolled.emit(event));
      }
   }
}
