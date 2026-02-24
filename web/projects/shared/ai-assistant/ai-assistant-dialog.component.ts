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

import { Component } from "@angular/core";
import { Router } from "@angular/router";
import { AiAssistantService } from "./ai-assistant.service";

@Component({
  selector: "ai-assistant-dialog",
  templateUrl: "./ai-assistant-dialog.component.html",
  styleUrls: ["./ai-assistant-dialog.component.scss"]
})
export class AiAssistantDialogComponent {
   userId: string = "";
   userEmail: string = "";
   context: string = "";
   chatAppServerUrl: string = "";
   styleBIUrl: string = "";

   constructor(private aiAssistantService: AiAssistantService, private router: Router) {
      this.userId = this.aiAssistantService.userId;
      this.userEmail = this.aiAssistantService.email;
      this.context = this.aiAssistantService.getFullContext();
      this.chatAppServerUrl = this.aiAssistantService.chatAppServerUrl;
      this.styleBIUrl = this.aiAssistantService.styleBIUrl;
      this.aiAssistantService.lastOpenUrl = router.url;
   }

   get newChat(): boolean {
      return this.aiAssistantService.createNewChat;
   }
}
