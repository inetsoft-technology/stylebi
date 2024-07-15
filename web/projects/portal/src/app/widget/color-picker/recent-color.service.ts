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
import { Injectable } from "@angular/core";
import { RecentColorPalette } from "./color-classes";

/**
 * Service that handles tracking the most recently selected colors.
 */
@Injectable({
   providedIn: "root"
})
export class RecentColorService {
   private colors: RecentColorPalette = [null, null, null, null, null, null, null, null];
   lastColor: string;

   /**
    * The palette of recently selected colors.
    */
   public get recentColorPalette(): RecentColorPalette {
      return <RecentColorPalette> this.colors.slice(0);
   }

   /**
    * Notifies the service that a color has been selected.
    *
    * @param color the CSS color string.
    */
   public colorSelected(color: string): void {
      let index: number = -1;

      for(let i: number = 0; i < this.colors.length; i++) {
         if(this.colors[i] == color) {
            index = i;
            break;
         }
      }

      if(index < 0) {
         let newColors: string[] = [];
         newColors.push(color);

         for(let i = 0; i < 7; i++) {
            newColors.push(this.colors[i]);
         }

         this.colors = <RecentColorPalette> newColors;
      }
      else if(index > 0) {
         let newColors: string[] = [];
         newColors.push(color);

         for(let i = 0; i < 8; i++) {
            if(i != index) {
               newColors.push(this.colors[i]);
            }
         }

         this.colors = <RecentColorPalette> newColors;
      }
   }
}
