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
import { PipeTransform, Pipe } from "@angular/core";
import { DataRef } from "../../common/data/data-ref";

@Pipe({
   name: "searchDataRef"
})
export class SearchDataRefPipe implements PipeTransform {
   transform(refs: DataRef[], input: string): DataRef[] {
      if(!input) {
         return refs;
      }

      const str = input.toLowerCase();
      return refs.filter(r => r.view.toLowerCase().includes(str));
   }
}
