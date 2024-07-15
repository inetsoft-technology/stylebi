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
import { ChangeDetectionStrategy, Component, Input, OnInit } from "@angular/core";
import { BaseTableCellModel } from "../../model/base-table-cell-model";
import { Tool } from "../../../../../../shared/util/tool";

/**
 * A simple cell for optimization.
 */
/* eslint-disable */
@Component({
   selector: "vs-simple-cell,[vs-simple-cell]",
   templateUrl: "vs-simple-cell.component.html",
   styleUrls: ["vs-table-cell.component.scss", "vs-simple-cell.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush,
})
/* eslint-enable */
export class VSSimpleCell implements OnInit {
   @Input() cell: BaseTableCellModel;
   @Input() width: number = 0;
   @Input() height: number = 0;
   hBorderWidth: number;
   vBorderWidth: number;

   ngOnInit() {
      if(this.cell.vsFormatModel && this.cell.vsFormatModel.border && this.height > 0) {
         // account for no-border on one side. (26621)
         this.vBorderWidth = Math.max(
            0, (Tool.getMarginSize(this.cell.vsFormatModel.border.bottom) +
            Tool.getMarginSize(this.cell.vsFormatModel.border.top)) / 2);
         this.hBorderWidth = Math.max(
            0, Tool.getMarginSize(this.cell.vsFormatModel.border.left) +
            Tool.getMarginSize(this.cell.vsFormatModel.border.right) - 1);
      }
      else {
         this.vBorderWidth = 0;
         this.hBorderWidth = 0;
      }
   }
}
