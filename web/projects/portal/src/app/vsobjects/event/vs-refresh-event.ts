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
export class VSRefreshEvent {
   private tableMetaData: boolean = false;
   private userRefresh: boolean = false;
   private initing: boolean = false;
   private checkShareFilter: boolean = false;
   private resizing: boolean = false;
   private width: number = 0;
   private height: number = 0;
   private bookmarkUser?: string;
   private bookmarkName?: string;
   private parameters?: {[name: string]: string[]};
   private name?: string = "";
   private confirmed: boolean = false;

   public setTableMetaData(value: boolean) {
      this.tableMetaData = value;
   }

   public setUserRefresh(value: boolean) {
      this.userRefresh = value;
   }

   public setIniting(value: boolean) {
      this.initing = value;
   }

   public setCheckShareFilter(value: boolean) {
      this.checkShareFilter = value;
   }

   public setResizing(value: boolean) {
      this.resizing = value;
   }

   public setWidth(value: number) {
      this.width = value;
   }

   public setHeight(value: number) {
      this.height = value;
   }

   public setBookmarkUser(value: string) {
      this.bookmarkUser = value;
   }

   public setBookmarkName(value: string) {
      this.bookmarkName = value;
   }

   public setParameters(value: {[name: string]: string[]}) {
      this.parameters = value;
   }

   public setName(value: string) {
      this.name = value;
   }

   public setConfirmed(value: boolean) {
      this.confirmed = value;
   }
}