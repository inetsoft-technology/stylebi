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
import { VSInputLabelModel } from "../../model/vs-input-label-model";

@Component({
   selector: "vs-input-label-wrapper",
   templateUrl: "vs-input-label-wrapper.component.html",
   styleUrls: ["vs-input-label-wrapper.component.scss"]
})
export class VSInputLabelWrapper {
   @Input() labelModel: VSInputLabelModel;

   get showLabel(): boolean {
      return this.labelModel?.showLabel ?? false;
   }

   get labelPosition(): string {
      return this.labelModel?.labelPosition ?? "left";
   }

   get labelGap(): number {
      return this.labelModel?.labelGap ?? 5;
   }

   get labelText(): string {
      return this.labelModel?.labelText ?? "";
   }

   get labelStyles(): { [key: string]: string } {
      const styles: { [key: string]: string } = {};

      if (this.labelModel?.labelFormat) {
         const format = this.labelModel.labelFormat;

         if (format.foreground) {
            styles["color"] = format.foreground;
         }
         if (format.font) {
            styles["font"] = format.font;
         }
         if (format.hAlign) {
            styles["text-align"] = format.hAlign;
         }
         if (format.decoration) {
            styles["text-decoration"] = format.decoration;
         }
      }

      return styles;
   }

   get wrapperClass(): string {
      return `label-${this.labelPosition}`;
   }

   get gapStyle(): { [key: string]: string } {
      return { "gap": `${this.labelGap}px` };
   }
}
