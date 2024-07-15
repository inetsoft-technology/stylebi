/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Injectable } from "@angular/core";
import { Observable } from "rxjs";
import { publishReplay, refCount, map } from "rxjs/operators";
import { ModelService } from "./model.service";

@Injectable({
   providedIn: "root"
})
export class FontService {
   private styleList: string[] = ["PLAIN", "BOLD", "ITALIC", "BOLD ITALIC"];
   private fontSizes: number[] = [
      6, 7, 8, 9, 10, 11, 12, 14, 16, 18, 20, 22, 24, 26, 28, 36, 48, 72
   ];
   private fonts: Observable<string[]>;
   public defaultFont: string = "Roboto";

   constructor(private modelService: ModelService) {
   }

   getAllFonts(): Observable<string[]> {
      if(!this.fonts) {
         this.fonts = this.modelService.getModel<string[]>("../api/format/fonts").pipe(
            map(arr => {
               this.defaultFont = arr.shift();
               return arr;
            }),
            publishReplay(1),
            refCount()
         );
      }

      return this.fonts;
   }

   getAllFontSizes(): number[] {
      return this.fontSizes;
   }

   getAllStyles(): string[] {
      return this.styleList;
   }
}
