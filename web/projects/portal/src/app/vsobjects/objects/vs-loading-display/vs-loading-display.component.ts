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
   Component,
   Input,
   Output,
   EventEmitter,
   AfterViewInit,
   ChangeDetectorRef,
   NgZone,
   OnDestroy
} from "@angular/core";

@Component({
   selector: "vs-loading-display",
   templateUrl: "vs-loading-display.component.html",
   styleUrls: ["vs-loading-display.component.scss"]
})
export class VSLoadingDisplay implements AfterViewInit, OnDestroy {
   @Input() message: string;
   @Input() autoShowCancel: boolean = true;
   @Input() autoShowMetaButton: boolean = false;
   @Input() runtimeId: string;
   @Input() showIcon: boolean = true;
   @Input() justShowIcon: boolean = false;
   @Input() allowInteraction: boolean = false;
   @Input() assemblyLoading: boolean = false;
   @Input() preparingData: boolean = false;
   @Output() cancelLoading: EventEmitter<null> = new EventEmitter<null>();
   @Output() switchToMeta: EventEmitter<null> = new EventEmitter<null>();
   showCancelLoading: boolean = false;
   showSwitchToMeta: boolean = false;
   loadingCanceled: boolean = false;
   switchingToMeta: boolean = false;
   mvHintMessage: string = "_#(js:vs.mv.hint)";

   private cancelTimeoutId: any = null;

   constructor(private changeRef: ChangeDetectorRef,
               private zone: NgZone) {
   }

   ngAfterViewInit(): void {
      if(this.showIcon && (this.autoShowCancel || this.autoShowMetaButton)) {
         // show cancel loading button after 2 sec if viewsheet is still loading
         this.zone.runOutsideAngular(() => {
            this.cancelTimeoutId = setTimeout(() => {
               if(this.autoShowCancel) {
                  this.showCancelLoading = true;
               }

               if(this.autoShowMetaButton) {
                  this.showSwitchToMeta = true;
               }

               this.cancelTimeoutId = null;
               this.changeRef.detectChanges();
            }, 2000);
         });
      }
   }

   ngOnDestroy(): void {
      if(this.cancelTimeoutId) {
         clearTimeout(this.cancelTimeoutId);
         this.cancelTimeoutId = null;
      }
   }

   switchToMetaMode(): void {
      this.switchingToMeta = true;
      this.switchToMeta.emit();
   }

   cancelViewsheetLoading(): void {
      this.loadingCanceled = true;
      this.cancelLoading.emit();
   }

   getBtnsPosition(): string {
      return this.showSwitchToMeta && this.showCancelLoading ?
         "space-between" : "center";
   }

}
