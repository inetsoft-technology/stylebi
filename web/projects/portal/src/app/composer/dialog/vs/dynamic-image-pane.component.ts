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
import { DynamicImagePaneModel } from "../../data/vs/dynamic-image-pane-model";
import { ComboMode } from "../../../widget/dynamic-combo-box/dynamic-combo-box-model";

@Component({
   selector: "dynamic-image-pane",
   templateUrl: "dynamic-image-pane.component.html",
})
export class DynamicImagePane {
   @Input() model: DynamicImagePaneModel;
   @Input() variableValues: string[];
   @Input() layoutObject: boolean = false;
   @Input() vsId: string = null;

   get imageName(): string {
      if(!this.model || !this.model.dynamicImageSelected) {
         return "";
      }

      const index = this.imagePrefixLength;

      if(index < 0) {
         return this.model.dynamicImageValue;
      }

      return this.model.dynamicImageValue.substring(index + 1);
   }

   set imageName(value: string) {
      if(!this.model) {
         return;
      }

      if(value && (value[0] === "$" || value[0] === "=")) {
         this.model.dynamicImageValue = value;
      }
      else if(value) {
         const index = value.lastIndexOf("^");

         if(index < 0) {
            const prefix = this.imageType;

            if(prefix) {
               value = prefix + value;
            }
         }

         this.model.dynamicImageValue = value;
      }
      else {
         this.model.dynamicImageValue = "";
      }
   }

   private get imageType(): string {
      const index = this.imagePrefixLength;

      if(index < 0) {
         return null;
      }

      return this.model.dynamicImageValue.substring(0, index + 1);
   }

   private get imagePrefixLength(): number {
      return this.model && this.model.dynamicImageValue ?
         this.model.dynamicImageValue.lastIndexOf("^") : -1;
   }

   updateImageValue(type: ComboMode): void {
      const oldValue = this.model.dynamicImageValue;
      const oldType = oldValue && oldValue.startsWith("$(") ? ComboMode.VARIABLE :
         oldValue && oldValue.startsWith("=") ? ComboMode.EXPRESSION : ComboMode.VALUE;

      if(type !== oldType) {
         if(type == ComboMode.VARIABLE) {
            if(this.variableValues.length > 0) {
               this.model.dynamicImageValue = this.variableValues[0];
            }
            else {
               this.model.dynamicImageValue = "";
            }
         }
         else if(type == ComboMode.VALUE) {
            this.model.dynamicImageValue = "";
         }
         else if(type === ComboMode.EXPRESSION) {
            this.model.dynamicImageValue = "=";
         }
      }
      else if(type === ComboMode.VALUE && !oldValue) {
         this.model.dynamicImageValue = "";
      }
   }
}
