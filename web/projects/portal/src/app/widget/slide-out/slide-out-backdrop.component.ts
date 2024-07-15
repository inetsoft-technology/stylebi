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
import { Component, Input } from "@angular/core";

@Component({
   selector: "w-slide-out-backdrop",
   template: "",
   host: { // eslint-disable-line @angular-eslint/no-host-metadata-property
      "[class]": '"modal-backdrop fade show" + (backdropClass ? " " + backdropClass : "")',
      "style": "z-index: 1050"
   }
})
export class SlideOutBackdropComponent {
   @Input() backdropClass: string;
}