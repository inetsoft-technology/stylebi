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
import { HttpClient } from "@angular/common/http";
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { TreeNodeModel } from "../tree/tree-node-model";
import { QueryColumnsModel } from "./query-columns-model";
import { EmailDialogData } from "./email-addr-dialog.component";

const QUERY_COLUMN_URI = "../api/portal/schedule/emails/query/columns";

@Component({
   selector: "query-email-pane",
   templateUrl: "query-email-pane.component.html"
})
export class QueryEmailPane {
   @Input() set addressString(value: string) {
      if(value) {
         const items: string[] = value.split(",");

         if(items[0].substring(0, 7) === "query: ") {
            this.query = items[0].trim().substring(6).trim();
            this.dataSourcePath = this.query.replace(".", "/");
            this.user = !!items[1] ? items[1].trim().substring(5).trim() : null;
            this.email = !!items[2] ? items[2].trim().substring(6).trim() : null;
         }
      }
   }
   @Output() addressStringChange: EventEmitter<EmailDialogData> = new EventEmitter<EmailDialogData>();
   columns: string[] = [];
   columnLabels: string[] = [];
   query: string = null;
   user: string = null;
   email: string = null;
   dataSourcePath: string = "";
   queryType: string = null;

   constructor(private http: HttpClient) {}

   public selectQuery(node: TreeNodeModel): void {
      const query: string = node.data.properties.prefix + "." + node.data.properties.source;
      this.queryType = node.data.properties.type;

      if(this.query != query) {
         this.query = query;
         this.user = null;
         this.email = null;
      }

      this.http.post<QueryColumnsModel>(QUERY_COLUMN_URI, node.data)
         .subscribe(
            (data: QueryColumnsModel) => {
               this.columnLabels = data.columnLabels;
               this.columns = data.columns;
            },
            (err) => {
               // Error
            }
         );
   }

   public updateUser(user: string): void {
      this.user = user;
      this.updateString();
   }

   public updateEmail(email: string): void {
      this.email = email;
      this.updateString();
   }

   private updateString(): void {
      let emails = null;

      if(!this.query || !this.user) {
         emails = "query: ";
      }
      else {
         emails = "query: " + this.query + ", user: " + this.user
            + (!!this.email ? (", email: " + this.email) : "");
      }

      this.addressStringChange.emit(<EmailDialogData> {emails: emails, type: this.queryType});
   }
}
