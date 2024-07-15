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
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, ViewChild } from "@angular/core";
import { TableStyleUtil } from "../../../../common/util/table-style-util";
import { TableStyleFormatModel } from "../../../data/tablestyle/table-style-format-model";
import { TableStyleModel } from "../../../data/tablestyle/table-style-model";
import { NotificationsComponent } from "../../../../widget/notifications/notifications.component";
import { SpecificationModel } from "../../../data/tablestyle/specification-model";

@Component({
   selector: "style-pane",
   templateUrl: "style-pane.component.html",
   styleUrls: ["style-pane.component.scss"]
})
export class StylePaneComponent {
   @Input() tableStyle: TableStyleModel;
   @Output() onUpdateTableStyle = new EventEmitter();
   @ViewChild("notifications") notifications: NotificationsComponent;

   showNotifications() {
      this.notifications.success("_#(js:common.TableStyle.saveSuccess)");
   }

   selectRegion(region: string) {
      this.tableStyle.selectedRegion = region;
      TableStyleUtil.selectRegionTree(this.tableStyle);
   }

   getRegionFormat() {
      let style = this.getStyleFormat();

      if(this.isRegion(this.selectedRegion)) {
         switch(this.selectedRegion) {
            case TableStyleUtil.BODY:
               return style.bodyRegionFormat;
            case TableStyleUtil.HEADER_ROW:
               return style.headerRowFormat;
            case TableStyleUtil.HEADER_COLUMN:
               return style.headerColFormat;
            case TableStyleUtil.TRAILER_ROW:
               return style.trailerRowFormat;
            case TableStyleUtil.TRAILER_COLUMN:
               return style.trailerColFormat;
            default:
               return style.bodyRegionFormat;
         }
      }
      else if(!this.isRegion(this.selectedRegion) && !this.isRegionBorder(this.selectedRegion)) {
         return style.specList[parseInt(this.selectedRegion, 10)]?.specFormat;
      }
      else {
         return style.bodyRegionFormat;
      }
   }

   getBorderFormat() {
      let style = this.getStyleFormat();

      if(this.isRegionBorder(this.selectedRegion)) {
         switch(this.selectedRegion) {
         case TableStyleUtil.TOP_BORDER:
            return style.topBorderFormat;
         case TableStyleUtil.RIGHT_BORDER:
            return style.rightBorderFormat;
         case TableStyleUtil.LEFT_BORDER:
            return style.leftBorderFormat;
         case TableStyleUtil.BOTTOM_BORDER:
            return style.bottomBorderFormat;
         }
      }

      return null;
   }

   isRegionBorder(region: string){
      return region === TableStyleUtil.TOP_BORDER ||
             region === TableStyleUtil.RIGHT_BORDER ||
             region === TableStyleUtil.LEFT_BORDER ||
             region === TableStyleUtil.BOTTOM_BORDER;
   }

   isRegion(region: string) {
      return region === TableStyleUtil.BODY ||
             region === TableStyleUtil.TRAILER_ROW ||
             region === TableStyleUtil.TRAILER_COLUMN ||
             region === TableStyleUtil.HEADER_COLUMN ||
             region === TableStyleUtil.HEADER_ROW;
   }

   updateFormat() {
      this.tableStyle.isModified = true;
      this.onUpdateTableStyle.emit();
   }

   getStyleFormat(): TableStyleFormatModel {
      return this.tableStyle.styleFormat;
   }

   get selectedRegion(): string {
      return this.tableStyle.selectedRegion;
   }

   get selectedRegionLabel(): string {
      return this.tableStyle.selectedRegionLabel;
   }

   get specList(): SpecificationModel[] {
      return this.tableStyle.styleFormat.specList;
   }

   protected readonly TableStyleUtil = TableStyleUtil;
}
