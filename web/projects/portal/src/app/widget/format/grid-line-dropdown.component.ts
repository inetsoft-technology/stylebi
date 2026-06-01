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
import { Component, Input, Output, OnInit, EventEmitter, ViewChild, ElementRef
       } from "@angular/core";
import { StyleConstants } from "../../common/util/style-constants";
import { ChartConfig } from "../../common/util/chart-config";
import { FixedDropdownDirective } from "../fixed-dropdown/fixed-dropdown.directive";


@Component({
    selector: "grid-line-dropdown",
    templateUrl: "grid-line-dropdown.component.html",
    styleUrls: ["grid-line-dropdown.component.scss"],
    imports: [FixedDropdownDirective]
})
export class GridLineDropdown implements OnInit {
   @Input() lineStyle: number;
   @Input() color: string;
   @Input() disabled: boolean = false;
   @Input() supportDefault: boolean = false;
   @Output() lineStyleChange: EventEmitter<number> = new EventEmitter<number>();
   @ViewChild("dropdownBody") dropdownBody: ElementRef<HTMLButtonElement>;
   @ViewChild(FixedDropdownDirective) dropdown: FixedDropdownDirective;
   private lineStyles0: number[] = [
      StyleConstants.NONE,
      StyleConstants.ULTRA_THIN_LINE,
      StyleConstants.THIN_THIN_LINE,
      StyleConstants.THIN_LINE,
      StyleConstants.MEDIUM_LINE,
      StyleConstants.THICK_LINE,
      StyleConstants.DOT_LINE,
      StyleConstants.DASH_LINE,
      StyleConstants.MEDIUM_DASH,
      StyleConstants.LARGE_DASH
   ];
   lineStyles: number[] = this.lineStyles0;
   open: boolean = false;

   ngOnInit() {
      if(this.supportDefault) {
         this.lineStyles = [-1].concat(this.lineStyles0);
      }
   }

   public choose(lineStyle: number): void {
      this.lineStyle = lineStyle;
      this.lineStyleChange.emit(lineStyle);
      this.dropdown?.close();
      setTimeout(() => this.dropdownBody?.nativeElement?.focus());
   }

   getLineStyleName(val: number) {
      return ChartConfig.getLineStyleName(val);
   }

   isNoneLineStyle(val: number): boolean {
      return this.getLineStyleName(val) === "NONE";
   }

   handleOpenChange(open: boolean): void {
      this.open = open;
   }

   get dropdownMinWidth(): number {
      return this.dropdownBody && this.dropdownBody.nativeElement
         ? this.dropdownBody.nativeElement.offsetWidth : null;
   }
}
