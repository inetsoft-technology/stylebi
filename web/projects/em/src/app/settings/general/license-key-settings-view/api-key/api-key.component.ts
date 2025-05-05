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
import { Component, Input, OnInit } from "@angular/core";
import { MatDialog } from "@angular/material/dialog";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { map } from "rxjs/operators";
import { ComponentTool } from "../../../../../../../portal/src/app/common/util/component-tool";
import { MessageDialog, MessageDialogType } from "../../../../common/util/message-dialog";

@Component({
   selector: "em-api-key",
   templateUrl: "./api-key.component.html",
   styleUrls: ["./api-key.component.scss"]
})
export class ApiKeyComponent implements OnInit {
   title: string;
   key: string = "";
   editValue: string = "";
   loading = true;
   editing = false;

   constructor(private http: HttpClient, private dialog: MatDialog, protected modalService: NgbModal) {
   }

   ngOnInit() {
      this.key="initial";
      this.title = "_#(js:API Key)";
      this.fetchKey();
   }

   get hasKey(): boolean {
      return !!this.key?.trim();
   }

   fetchKey() {
      this.loading = true;
      this.http.get<string>("../api/em/security/get-api-key")
         .subscribe((response) => {
            this.key = response || "";
            this.loading = false;
         });
   }

   startEditing() {
      this.editValue = this.key || "";  // Use existing key if editing, or blank if adding
      this.editing = true;
   }

   cancelEdit() {
      this.editing = false;
   }

   saveKey() {
      const newKey = this.editValue.trim();
      this.http.post("../api/em/security/set-api-key", newKey).subscribe(() => {
            this.key = newKey;
            this.editing = false;
      });
   }

   deleteKey() {
      this.dialog.open(MessageDialog, {
         data: {
            title: "_#(js:Confirm Delete)",
            content: "_#(js:confirm.delete.apiKey)",
            type: MessageDialogType.CONFIRMATION
         }
      }).afterClosed().subscribe(result => {
            if(result) {
               this.http.post("../api/em/security/set-api-key", "").subscribe(() => {
                     this.key = "";
                     this.editing = false;
                  }
               )
            }
      });
   }
}
