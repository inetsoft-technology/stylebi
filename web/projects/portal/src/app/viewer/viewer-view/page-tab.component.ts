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
   AfterViewInit,
   Component,
   ElementRef,
   HostListener,
   OnDestroy,
   ViewChild
} from "@angular/core";
import { Subscription } from "rxjs";
import {
   FeatureFlagsService,
   FeatureFlagValue
} from "../../../../../shared/feature-flags/feature-flags.service";
import { PageTabService, TabInfoModel } from "../services/page-tab.service";

@Component({
   selector: "page-tab",
   templateUrl: "page-tab.component.html",
   styleUrls: ["page-tab.component.scss"]
})
export class PageTabComponent implements AfterViewInit, OnDestroy {
   @ViewChild("tabScroller") tabScroller: ElementRef;
   readonly SCROLL_ADJ: number = 20;
   buttonHoldIntervalId: any;
   scrollButtonsVisible: boolean;
   leftScrollEnabled: boolean;
   rightScrollEnabled: boolean;
   private subscriptions = new Subscription();
   FeatureFlagValue = FeatureFlagValue;

   constructor(private pageTabService: PageTabService, private featureFlagsService: FeatureFlagsService) {
      this.subscriptions.add(this.pageTabService.onTabAddedRemoved.subscribe((tabAdded: boolean) => {
         this.refreshScroll(tabAdded);
      }));
   }

   ngAfterViewInit(): void {
      this.refreshScroll();
   }

   ngOnDestroy(): void {
      this.subscriptions.unsubscribe();
   }

   get tabs(): TabInfoModel[] {
      return this.pageTabService.tabs;
   }

   onTabSelect(tab: TabInfoModel): void {
      this.pageTabService.changeCurrentTab(tab);
   }

   closeTab(tab: TabInfoModel): void {
      this.pageTabService.closeTab(tab);
   }

   refreshScroll(tabAdded: boolean = false): void {
      setTimeout(() => {
         // scroll into view the newly added tab
         if(tabAdded) {
            this.tabScroller.nativeElement.scrollLeft = this.tabScroller.nativeElement.scrollWidth -
               this.tabScroller.nativeElement.offsetWidth;
         }

         this.scrollButtonsVisible = this.tabScroller != null &&
            this.tabScroller.nativeElement.scrollWidth > this.tabScroller.nativeElement.offsetWidth;
         this.leftScrollEnabled = this.tabScroller != null &&
            this.tabScroller.nativeElement.scrollLeft > 0;
         this.rightScrollEnabled = this.tabScroller != null &&
            (this.tabScroller.nativeElement.scrollWidth - this.tabScroller.nativeElement.offsetWidth -
               this.tabScroller.nativeElement.scrollLeft > 1);
      });
   }

   scroll(event: MouseEvent | TouchEvent, left: boolean): void {
      if(event instanceof MouseEvent && event.button != 0) {
         return;
      }

      this.clearButtonHoldInterval();

      if(left) {
         this.scrollLeft();
      }
      else {
         this.scrollRight();
      }

      this.buttonHoldIntervalId = setInterval(() => {
         if(left) {
            this.scrollLeft();
         }
         else {
            this.scrollRight();
         }
      }, 20);
   }

   private scrollLeft(): void {
      let scrollValue = this.tabScroller.nativeElement.scrollLeft;
      scrollValue = Math.max(0, scrollValue - this.SCROLL_ADJ);
      this.tabScroller.nativeElement.scrollLeft = scrollValue;
   }

   private scrollRight(): void {
      let scrollValue = this.tabScroller.nativeElement.scrollLeft;
      scrollValue = Math.min(this.tabScroller.nativeElement.scrollWidth, scrollValue + this.SCROLL_ADJ);
      this.tabScroller.nativeElement.scrollLeft = scrollValue;
   }

   private clearButtonHoldInterval() {
      if(this.buttonHoldIntervalId != null) {
         clearInterval(this.buttonHoldIntervalId);
         this.buttonHoldIntervalId = null;
      }
   }

   @HostListener("document: mouseup", ["$event"])
   @HostListener("document: touchend", ["$event"])
   mouseup(event: MouseEvent): void {
      this.clearButtonHoldInterval();
   }
}
