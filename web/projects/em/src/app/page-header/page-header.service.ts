/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Injectable } from "@angular/core";
import { Title } from "@angular/platform-browser";

@Injectable({
   providedIn: "root"
})
export class PageHeaderService {
   private _title = "";
   private _orgVisible = false;

   get title(): string {
      return this._title;
   }

   set title(title: string) {
      this._title = title;

      if(title !== "") {
         title = `${title} | `;
      }

      this.bodyTitle.setTitle(`${title}Enterprise Manager`);
      this._orgVisible = this.orgPages.includes(this.title);
   }

   get bodyTitleString(): string {
      return this.bodyTitle.getTitle();
   }

   get orgVisible(): boolean {
      return this._orgVisible;
   }

   readonly orgPages = [
      "_#(js:Security Settings Users)",
      "_#(js:Security Settings Actions)",
      "_#(js:Repository)",
      "_#(js:Schedule Tasks)",
      "_#(js:Data Cycles)",
      "_#(js:Organization Presentation Settings)",
      "_#(js:Themes)",
      "_#(js:Inactive Resources)",
      "_#(js:Inactive Users)",
      "_#(js:Identity Information)",
      "_#(js:Logon Errors)",
      "_#(js:Logon History)",
      "_#(js:Modification History)",
      "_#(js:User Sessions)",
      "_#(js:Dependent Assets)",
      "_#(js:Required Assets)",
      "_#(js:Export History)",
      "_#(js:Schedule History)",
      "_#(js:Bookmark History)",
      "_#(js:Dashboard Monitoring)",
      "_#(js:Query Monitoring)",
      "_#(js:Cache Monitoring)",
      "_#(js:User Monitoring)",
      "_#(js:Cluster Monitoring)"
   ]

   constructor(private bodyTitle: Title) {
   }
}
