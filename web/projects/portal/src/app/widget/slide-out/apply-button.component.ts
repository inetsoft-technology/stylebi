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
import { Component, Input, Output, EventEmitter, Optional } from "@angular/core";
import { InSlideOutSignService } from "./in-slide-out-sign.service";

@Component({
   selector: "apply-button",
   templateUrl: "apply-button.component.html",
   styleUrls: ["apply-button.component.scss"]
})
export class ApplyButtonComponent {
   @Input() disabled: boolean = false;
   // true if collapse slide out pane
   @Output() onApply = new EventEmitter<boolean>();

   constructor(@Optional() private inSlideOutSignService?: InSlideOutSignService) {
   }

   inSlidOut(): boolean {
      return !!this.inSlideOutSignService;
   }
}
