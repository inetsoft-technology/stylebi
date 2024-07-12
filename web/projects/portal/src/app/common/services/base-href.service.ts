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
import { Inject, Injectable, Optional } from "@angular/core";
import { APP_BASE_HREF, LocationStrategy } from "@angular/common";

@Injectable({
   providedIn: "root"
})
export class BaseHrefService {
   constructor(private locationStrategy: LocationStrategy,
               @Optional() @Inject(APP_BASE_HREF) private baseHref: string)
   {
      if(this.baseHref) {
         this.baseHref = this.baseHref.replace(/\/$/, "");
      }
   }

   /**
    * Return the base href of the app regardless if it's set through APP_BASE_HREF or <base> element
    */
   getBaseHref(): string {
      return this.locationStrategy.getBaseHref().replace(/\/$/, "");
   }

   /**
    * Return the base href set in the token
    */
   getTokenBaseHref(): string {
      return this.baseHref;
   }
}