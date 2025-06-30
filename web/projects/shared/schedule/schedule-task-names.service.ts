/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import { HttpClient } from "@angular/common/http";
import { Inject, Injectable, InjectionToken, OnDestroy, Optional } from "@angular/core";
import { BehaviorSubject, Observable } from "rxjs";
import { NameLabelTuple } from "../util/name-label-tuple";
import { ScheduleTaskNamesModel } from "./model/schedule-task-names-model";

export const PORTAL = new InjectionToken<boolean>("PORTAL");

@Injectable()
export class ScheduleTaskNamesService implements OnDestroy {
   allTasks = new BehaviorSubject<NameLabelTuple[]>(null);
   private reload = false;
   private loading = false;
   private url = "../api/em/schedule/task-names";

   get isLoading(): boolean {
      return this.loading;
   }

   constructor(private http: HttpClient, @Optional() @Inject(PORTAL) private portal: boolean) {
      if(portal) {
         this.url = "../api/portal/schedule/task-names";
      }

      this.loadScheduleTaskNames();
   }

   loadScheduleTaskNames() {
      if(!this.loading) {
         this.loading = true;
         this.http.get<ScheduleTaskNamesModel>(this.url).subscribe(
            (model) => {
               this.allTasks.next((model.allTasks));
            },
            () => {
            },
            () => {
               this.loading = false;

               if(this.reload) {
                  this.reload = false;
                  this.loadScheduleTaskNames();
               }
            }
         );
      }
      else {
         this.reload = true;
      }
   }

   getAllTasks(): Observable<NameLabelTuple[]> {
      return this.allTasks;
   }

   ngOnDestroy(): void {
      this.allTasks.complete();
   }
}
