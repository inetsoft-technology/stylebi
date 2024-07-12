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
import { Injectable } from "@angular/core";
import { DropdownObserver } from "../services/dropdown-observer.service";
import { FixedDropdownComponent } from "./fixed-dropdown.component";

/**
 * Tracks dropdown stack. Separate from FixedDropdownService to avoid recursive dependency.
 */
@Injectable({
   providedIn: "root"
})
export class DropdownStackService {
   private dropdowns: Array<any> = [];

   constructor(private dropdownObserver: DropdownObserver) {}

   public push(dropdown: FixedDropdownComponent): void {
      this.dropdowns.push(dropdown);
      this.dropdownObserver.onDropdownOpened();
   }

   public pop(dropdown: FixedDropdownComponent): void {
      if(this.isCurrent(dropdown)) {
         this.dropdowns.pop();
         this.dropdownObserver.onDropdownClosed();
      }
      else {
         // this shouldn't happen
         console.log("Pop dropdown is not on top, ignored!");
      }
   }

   // check if the dropdown is on top
   public isCurrent(dropdown: FixedDropdownComponent): boolean {
      return this.dropdowns[this.dropdowns.length - 1] == dropdown;
   }
}
