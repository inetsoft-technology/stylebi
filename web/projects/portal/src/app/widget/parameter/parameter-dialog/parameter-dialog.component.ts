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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { ParameterPageModel } from "../parameter-page-model";
import { RepletParameterModel } from "../replet-parameter-model";

@Component({
   selector: "w-parameter-dialog",
   templateUrl: "./parameter-dialog.component.html",
   styleUrls: ["./parameter-dialog.component.scss"]
})
export class ParameterDialogComponent {

   @Input() model: ParameterPageModel;
   @Output() onCommit = new EventEmitter<RepletParameterModel[]>();
   @Output() onCancel = new EventEmitter<void>();

   constructor() {
   }

   submit(parameters: RepletParameterModel[]): void {
      if(!!parameters) {
         this.onCommit.emit(parameters);
      }
      else {
         this.onCancel.emit();
      }
   }
}
