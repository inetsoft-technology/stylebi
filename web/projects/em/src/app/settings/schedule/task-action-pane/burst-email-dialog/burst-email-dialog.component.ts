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
import { Component, Inject, OnInit } from "@angular/core";
import { MAT_DIALOG_DATA } from "@angular/material/dialog";

export interface BurstEmailDialogData {
   emails?: string;
   type?: string;
}

@Component({
   selector: "em-burst-email-dialog",
   templateUrl: "./burst-email-dialog.component.html",
   styleUrls: ["./burst-email-dialog.component.scss"]
})
export class BurstEmailDialogComponent implements OnInit {
   type: "EMBEDDED" | "QUERY";
   data: BurstEmailDialogData;
   embeddedResult: string;
   queryData: BurstEmailDialogData = {};

   constructor(@Inject(MAT_DIALOG_DATA) data: BurstEmailDialogData) {
      if(!!data.emails) {
         this.data = data;
         const items: string[] = data.emails.split(",");

         if(items[0].substring(0, 7) === "query: ") {
            this.type = "QUERY";
            this.queryData = data;
         }
         else {
            this.type = "EMBEDDED";
            this.embeddedResult = data.emails;
         }
      }
   }

   ngOnInit() {
      if(!this.type) {
         this.type = "EMBEDDED";

         if(this.data) {
            this.embeddedResult = this.data.emails;
         }
      }
   }

   onTabSelectedIndexChange(index: number) {
      if(index === 0) {
         this.type = "EMBEDDED";
      }
      else {
         this.type = "QUERY";
      }
   }

   get result(): BurstEmailDialogData {
      if(this.type === "QUERY") {
         return this.queryData;
      }
      else {
         return {emails: this.embeddedResult, type: null};
      }
   }

   onEmailsChange(data: BurstEmailDialogData) {
      this.queryData = data;
   }
}
