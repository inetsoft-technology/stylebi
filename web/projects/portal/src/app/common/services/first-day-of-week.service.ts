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
import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { Observable } from "rxjs";
import { publishReplay, refCount } from "rxjs/operators";

export const FIRST_DAY_URI = "../api/first-day-of-week";

export interface FirstDayOfWeekModel {
   javaFirstDay: number;
   isoFirstDay: number;
}

@Injectable()
export class FirstDayOfWeekService {
   private firstDayObservable: Observable<FirstDayOfWeekModel>;

   constructor(private http: HttpClient) {
   }

   public getFirstDay(): Observable<FirstDayOfWeekModel> {
      if(!this.firstDayObservable) {
         this.firstDayObservable = this.http.get<FirstDayOfWeekModel>(FIRST_DAY_URI).pipe(
            publishReplay(1),
            refCount()
         );
      }

      return this.firstDayObservable;
   }
}
