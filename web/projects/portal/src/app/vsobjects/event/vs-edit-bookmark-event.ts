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
import { ViewsheetEvent } from "../../common/viewsheet-client";
import { VSBookmarkInfoModel } from "../model/vs-bookmark-info-model";

/**
 * Event used to edit a bookmark
 */
export class VSEditBookmarkEvent implements ViewsheetEvent {
   private instruction: string;
   private vsBookmarkInfoModel: VSBookmarkInfoModel;
   private name?: string;
   private oldName?: string;
   private confirmed: boolean = false;
   private bookmarkConfirmed?: string;
   private clientId: string;
   private windowWidth: number;
   private windowHeight: number;
   private mobile = false;
   private userAgent: string;

   public setOldName(oldName: string): void {
      this.oldName = oldName;
   }

   public setInstruction(instruction: string): void {
      this.instruction = instruction;
   }

   public setVSBookmarkInfoModel(vsBookmarkInfoModel: VSBookmarkInfoModel): void {
      this.vsBookmarkInfoModel = vsBookmarkInfoModel;
   }

   public setBookmarkConfirmed(confirmedString: string) {
      this.bookmarkConfirmed = confirmedString;
   }

   public setConfirmed(confirmed: boolean): void {
      this.confirmed = confirmed;
   }

   public setClientId(clientId: string): void {
      this.clientId = clientId;
   }

   setWindowWidth(value: number) {
      this.windowWidth = value;
   }

   setWindowHeight(value: number) {
      this.windowHeight = value;
   }

   setMobile(value: boolean) {
      this.mobile = value;
   }

   setUserAgent(value: string) {
      this.userAgent = value;
   }
}