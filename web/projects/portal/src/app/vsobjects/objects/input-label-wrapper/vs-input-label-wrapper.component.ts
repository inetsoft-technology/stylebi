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
import { VSInputLabelModel } from "../../model/vs-input-label-model";

@Component({
   selector: "vs-input-label-wrapper",
   templateUrl: "vs-input-label-wrapper.component.html",
   styleUrls: ["vs-input-label-wrapper.component.scss"]
})
export class VSInputLabelWrapper {
   @Input() labelModel: VSInputLabelModel;
   @Input() labelSelected: boolean = false;
   @Output() selectLabel = new EventEmitter<MouseEvent>();

   onLabelClick(event: MouseEvent): void {
      // Don't stop propagation - let it bubble up to editable-object-container for proper selection
      this.selectLabel.emit(event);
   }

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

   get labelFormat() {
      return this.labelModel?.labelFormat;
   }

   get labelColor(): string {
      // Return format color or default to black to prevent inheriting .txt-primary color
      return this.labelModel?.labelFormat?.foreground || "black";
   }

   get wrapperClass(): string {
      return `label-${this.labelPosition}`;
   }

   get gapStyle(): { [key: string]: string } {
      return { "gap": `${this.labelGap}px` };
   }
}
