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
import { Component, Input } from "@angular/core";

@Component({
   selector: "w-rulers",
   templateUrl: "rulers.component.html",
   styleUrls: ["rulers.component.scss"]
})
export class Rulers {
   @Input() showGuides: boolean = false;
   @Input() guideTop: number = 0;
   @Input() guideLeft: number = 0;
   @Input() guideWidth: number = 0;
   @Input() guideHeight: number = 0;
   @Input() top: number = 0;
   @Input() left: number = 0;
   @Input() bottom: number = 0;
   @Input() right: number = 0;
   @Input() scale: number = 1;
   @Input() scrollTop: number = 0;
   @Input() scrollLeft: number = 0;

   preventMouseEvents(event: any) {
      event.preventDefault();
   }
}
