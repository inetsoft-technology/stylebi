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
import { Component, Input, AfterViewInit } from "@angular/core";
import { RotationRadioGroupModel } from "./rotation-radio-group-model";

@Component({
   selector: "rotation-radio-group",
   templateUrl: "rotation-radio-group.component.html",
   styleUrls: ["rotation-radio-group.component.scss"]
})
export class RotationRadioGroup implements AfterViewInit {
   @Input() auto: boolean;
   @Input() model: RotationRadioGroupModel;
   rotationStyles: {clazz: string, value: string}[] = [
      {clazz: "rotation-radio-button rotation--90", value: "90.0"},
      {clazz: "rotation-radio-button rotation--45", value: "45.0"},
      {clazz: "rotation-radio-button rotation-0", value: "0.0"},
      {clazz: "rotation-radio-button rotation-45", value: "-45.0"},
      {clazz: "rotation-radio-button rotation-90", value: "-90.0"}
   ];

   ngAfterViewInit(): void {
      if(this.auto) {
         this.rotationStyles.push({clazz: "rotation-auto", value: "auto"});
      }
   }

   isChecked(_i: number): boolean {
      if(this.model.rotation == null && _i == 2) {
         return true;
      }

      return this.model.rotation === this.rotationStyles[_i].value;
   }

   select(_i: number): void {
      this.model.rotation = this.rotationStyles[_i].value;
   }
}
