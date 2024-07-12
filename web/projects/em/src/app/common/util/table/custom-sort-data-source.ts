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
import { MatTableDataSource } from "@angular/material/table";
import { _isNumberValue } from "@angular/cdk/coercion";
import { TableModel } from "./table-model";
import { UserSessionsMonitoring } from "./user-sessions-monitoring";

const MAX_SAFE_INTEGER = 9007199254740991;

export class CustomSortDataSource<T> extends MatTableDataSource<TableModel> {

   constructor(dataSource: T[], private sortingTimeCols?: string[],
               private customSortingDataAccessor?: (data: T, sortHeaderId: string) => string | number)
   {
      super(dataSource);
      this.sortingDataAccessor = !!customSortingDataAccessor
         ? customSortingDataAccessor
         : !!sortingTimeCols
            ? this.defaultSortingDataAccessor
            : this.sortingDataAccessor;
   }

   defaultSortingDataAccessor(data: UserSessionsMonitoring, sortHeaderId: string): string | number {
      const value: string = data[sortHeaderId];

      if(_isNumberValue(value)) {
         const numberValue = Number(value);

         return numberValue < MAX_SAFE_INTEGER ? numberValue : value;
      }
      else if(this.sortingTimeCols.indexOf(sortHeaderId) >= 0) {
         const date: string[] = value.split(":");

         if(date.length == 3) {
            const hours = Number(date[0]);
            const minutes = Number(date[1]);
            const seconds = Number(date[2]);

            return hours * 60 * 60 + minutes * 60 + seconds;
         }
      }

      return value;
   }
}