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
import { Pipe, PipeTransform } from "@angular/core";

/**
 * Map to Iterable Pipe
 *
 * Converts Map to iterable array for angular
 *
 * Example:
 *
 *  <div *ngFor="let entry of someMap | mapToIterable">
 *    key {{entry.key}} and value {{entry.value}}
 *  </div>
 *
 */
//@Pipe({
//   name: "mapToIterable"
//})
export class MapToIterable implements PipeTransform {
   transform(map: any, args: any[]): any {
      let iterable: Array<any> = [];

      if(!map) {
         return null;
      }
      //for maps
      else if(map.entries) {
         map.forEach((value: any, key: any) => {
            iterable.push({ key, value });
         });
      }
      //for objects with paired entries
      else {
         for(let key in map) {
            if(map.hasOwnProperty(key)) {
               iterable.push({ key, value: map[key] });
            }
         }
      }

      return iterable;
   }
}
