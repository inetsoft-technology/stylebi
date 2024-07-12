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
import { convertToParamMap, Data, ParamMap, Params } from "@angular/router";
import { ReplaySubject } from "rxjs";

export class ActivatedRouteStub {
   // Use a ReplaySubject to share previous values with subscribers
   // and pump new values into the `paramMap` observable
   private paramMapSubject = new ReplaySubject<ParamMap>();
   private queryParamMapSubject = new ReplaySubject<ParamMap>();
   private dataSubject = new ReplaySubject<Data>();

   constructor(initialParams?: Params, initialQueryParams?: Params, initialData?: Data) {
      this.setParamMap(initialParams);
      this.setQueryParamMap(initialQueryParams);
      this.setData(initialData);
   }

   /** The mock paramMap observable */
   readonly paramMap = this.paramMapSubject.asObservable();
   /** The mock queryParamMap observable */
   readonly queryParamMap = this.queryParamMapSubject.asObservable();
   /** The mock data observable */
   readonly data = this.dataSubject.asObservable();

   /** Set the paramMap observable's next value */
   setParamMap(params?: Params) {
      this.paramMapSubject.next(convertToParamMap(params));
   }

   /** Set the queryParamMap observable's next value */
   setQueryParamMap(params?: Params) {
      this.queryParamMapSubject.next(convertToParamMap(params));
   }

   /**
    * Set the data observables's next value */
   setData(data?: Data) {
      this.dataSubject.next(data);
   }
}
