/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
import { Injectable } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { convertToKey } from "../../em/src/app/settings/security/users/identity-id";
import { ComponentTool } from "../../portal/src/app/common/util/component-tool";
import { CurrentUser } from "../../portal/src/app/portal/current-user";
import { AiAssistantDialogComponent } from "./ai-assistant-dialog.component";

const PORTAL_CURRENT_USER_URI: string = "../api/portal/get-current-user";
const EM_CURRENT_USER_URI: string = "../api/em/security/get-current-user";

@Injectable({
   providedIn: "root"
})
export class AiAssistantService {
   _userId: string = "";
   _context: string = "chart";

   get userId(): string {
      return this._userId;
   }

   set userId(value: string) {
      this._userId = value;
   }

   get context(): string {
      return this._context;
   }

   set context(value: string) {
      this._context = value;
   }

   constructor(private http: HttpClient,
               private modalService: NgbModal)
   {
   }

   loadCurrentUser(em: boolean = false): void {
      const uri = em ? EM_CURRENT_USER_URI : PORTAL_CURRENT_USER_URI;
      this.http.get(uri).subscribe((model: CurrentUser) => {
         this.userId = convertToKey(model.name);
      });
   }

   openAiAssistantDialog(): void {
      ComponentTool.showDialog(this.modalService, AiAssistantDialogComponent, () => {},
         {
            backdrop: true,
            windowClass: "ai-assistant-container"
         }
      );
   }
}