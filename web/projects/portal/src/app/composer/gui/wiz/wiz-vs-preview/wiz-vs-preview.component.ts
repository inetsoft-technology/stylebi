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
import { AfterViewInit, Component, ElementRef, EventEmitter, Input, OnDestroy, Output, ViewChild } from "@angular/core";
import { Viewsheet } from "../../../data/vs/viewsheet";
import { VSObjectModel } from "../../../../vsobjects/model/vs-object-model";

@Component({
   selector: "wiz-vs-preview",
   templateUrl: "./wiz-vs-preview.component.html",
   styleUrls: ["./wiz-vs-preview.component.scss"]
})
export class WizVsPreview implements AfterViewInit, OnDestroy {
   @Input() viewsheet: Viewsheet;
   @ViewChild("wizCanvas") canvasEl: ElementRef;
   @Output() canvasResize = new EventEmitter<void>();

   private resizeObserver: ResizeObserver;
   private resizeTimer: any;
   private initialized = false;

   ngAfterViewInit(): void {
      this.resizeObserver = new ResizeObserver(() => {
         if(!this.initialized) {
            this.initialized = true;
            return;
         }

         clearTimeout(this.resizeTimer);

         this.resizeTimer = setTimeout(() => {
            this.canvasResize.emit();
         }, 200);
      });
      this.resizeObserver.observe(this.canvasEl.nativeElement);
   }

   ngOnDestroy(): void {
      this.resizeObserver?.disconnect();
      clearTimeout(this.resizeTimer);
   }

   get vsObjects(): VSObjectModel[] {
      return this.viewsheet?.vsObjects ?? [];
   }

   get description(): string {
      return this.vsObjects[0]?.description ?? "";
   }

   trackByFn(index: number, obj: VSObjectModel): string {
      return obj.absoluteName;
   }
}
