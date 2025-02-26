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
import { HttpClient, HttpParams } from "@angular/common/http";
import { Injectable, NgZone } from "@angular/core";
import { BehaviorSubject, Observable, Subject } from "rxjs";
import { map } from "rxjs/operators";
import { PropertySetting } from "../property-settings-view/property-settings-view.component";

@Injectable()
export class PropertySettingsDatasourceService {
   dataObservable: BehaviorSubject<PropertySetting[]> = new BehaviorSubject<PropertySetting[]>([]);
   rowBeingAdded: PropertySetting = null;
   data: PropertySetting[] = [];

   constructor(private http: HttpClient, private zone: NgZone) {
   }

   /**
    * Used by the MatTable. Called when it connects to the data source.
    */
   connect(): Observable<PropertySetting[]> {
      return this.dataObservable.asObservable();
   }

   /**
    * Used by the MatTable. Called when it is destroyed.
    */
   disconnect(): void {
      this.dataObservable.complete();
   }

   fetchData(sortColumn: string = "", sortDirection: string = "asc",
             pageIndex: number = 0, pageSize: number = 20,
             searchFilter: string = "", sortCaseSensitive: boolean = true): void {
      this.http.get("../api/admin/properties").pipe(
         map(property => {
            let propertyArray: PropertySetting[] = [];
            Object.keys(property).forEach((key) => {
               let val = property[key];
               propertyArray.push(<PropertySetting> {
                  editing: false,
                  propertyValue: val,
                  propertyName: key
               });
            });

            return propertyArray;
         })
      ).subscribe((data: PropertySetting[]) => {
         data = Object.assign([], data);

         if(data.length < 1) {
            return;
         }

         if(sortColumn in data[0]) {
            let d = sortDirection === "asc" ? 1 : -1;
            data = data.sort((a, b) => {
               let compareA = a[sortColumn];
               let compareB = b[sortColumn];

               if(!sortCaseSensitive && typeof compareA === "string") {
                  compareA = compareA.toLowerCase();
               }

               if(!sortCaseSensitive && typeof compareB === "string") {
                  compareB = compareB.toLowerCase();
               }

               return compareA < compareB ? -d : d;
            });
         }

         if(searchFilter !== "") {
            searchFilter = searchFilter.toLowerCase();
            data = data.filter(s => {
               if(s.propertyName.length > searchFilter.length) {
                  return s.propertyName.toLowerCase().includes(searchFilter);
               }
               else {
                  return searchFilter.includes(s.propertyName.toLowerCase());
               }
            });
         }

         this.data = data;

         if(data.length > pageIndex * pageSize) {
            data = data.slice(pageIndex * pageSize, (pageIndex + 1) * pageSize);
         }

         if(!!this.rowBeingAdded) {
            data.unshift(this.rowBeingAdded);
         }

         this.zone.run(() => this.dataObservable.next(data));
      });
   }

   changeRow(row: PropertySetting): Observable<void> {
      let property = {
         name: row.propertyName,
         value: row.propertyValue
      };

      return this.http.put<void>("../api/admin/properties/edit", property);
   }

   cancelRow() {
      this.rowBeingAdded = null;
   }

   deleteRow(row: PropertySetting): Observable<void> {
      let params: HttpParams = new HttpParams()
         .set("property", row.propertyName);
      return this.http.delete<void>("../api/admin/properties/delete", {params: params});
   }

   addRow(): Observable<PropertySetting> {
      let addedRowSubject = new Subject<PropertySetting>();
      this.rowBeingAdded = <PropertySetting> {
         propertyName: "",
         propertyValue: "",
         editing: true,
         newRow: true
      };

      this.dataObservable.subscribe(propertySettings => {
         if(propertySettings.length > 0) {
            addedRowSubject.next(propertySettings[0]);
         }

         addedRowSubject.complete();
      });

      return addedRowSubject;
   }
}
