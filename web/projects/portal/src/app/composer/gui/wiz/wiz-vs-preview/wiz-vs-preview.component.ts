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
import { Viewsheet } from "../../../data/vs/viewsheet";
import { VSObjectModel } from "../../../../vsobjects/model/vs-object-model";

@Component({
   selector: "wiz-vs-preview",
   templateUrl: "./wiz-vs-preview.component.html",
   styleUrls: ["./wiz-vs-preview.component.scss"]
})
export class WizVsPreview {
   @Input() viewsheet: Viewsheet;

   get vsObjects(): VSObjectModel[] {
      return this.viewsheet?.vsObjects ?? [];
   }

   get description(): string {
      return this.vsObjects[0]?.description ?? "";
   }

   trackByFn(index: number, obj: VSObjectModel): string {
      return obj.absoluteName;
   }
}
