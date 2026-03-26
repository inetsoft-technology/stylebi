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

import { HttpClient, HttpParams } from "@angular/common/http";
import { Component, Input, OnChanges, OnDestroy, SimpleChanges } from "@angular/core";
import { Subject, takeUntil } from "rxjs";
import { VSObjectModel } from "../../../../vsobjects/model/vs-object-model";
import { Viewsheet } from "../../../data/vs/viewsheet";

export interface DetailItem {
   label: string;
   value: string;
}

@Component({
   selector: "wiz-vs-preview",
   templateUrl: "./wiz-vs-preview.component.html",
   styleUrls: ["./wiz-vs-preview.component.scss"]
})
export class WizVsPreview implements OnChanges, OnDestroy {
   @Input() viewsheet: Viewsheet;
   selectedTab = 0;
   bindingDetails: DetailItem[] = [];
   worksheetDetails: DetailItem[] = [];

   private destroy$ = new Subject<void>();

   constructor(private http: HttpClient) {
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes["viewsheet"]) {
         this.loadDetails();
      }
   }

   ngOnDestroy(): void {
      this.destroy$.next();
      this.destroy$.complete();
   }

   get vsObjects(): VSObjectModel[] {
      return this.viewsheet?.vsObjects ?? [];
   }

   private loadDetails(): void {
      const runtimeId = this.viewsheet?.runtimeId;

      if(!runtimeId) {
         return;
      }

      const params = new HttpParams().set("runtimeId", runtimeId);

      this.http.get<{ bindingDetails: DetailItem[]; worksheetDetails: DetailItem[] }>(
         "../api/composer/wiz/details",
         {params}
      )
         .pipe(takeUntil(this.destroy$))
         .subscribe(response => {
            this.bindingDetails = response?.bindingDetails ?? [];
            this.worksheetDetails = response?.worksheetDetails ?? [];
         });
   }

   trackByFn(index: number, obj: VSObjectModel): string {
      return obj.absoluteName;
   }
}
