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
import { AiAssistantService } from "./ai-assistant.service";

@Component({
  selector: "ai-assistant-dialog",
  templateUrl: "./ai-assistant-dialog.component.html",
  styleUrls: ["./ai-assistant-dialog.component.scss"]
})
export class AiAssistantDialogComponent {
   context: string = "";

   // Use getters so Angular re-evaluates on each change-detection cycle.
   get chatAppServerUrl(): string { return this.aiAssistantService.chatAppServerUrl; }
   get styleBIUrl(): string { return this.aiAssistantService.styleBIUrl; }
   get userId(): string { return this.aiAssistantService.userId; }
   get userEmail(): string { return this.aiAssistantService.email; }
   get userEmail(): string { return this.aiAssistantService.email; }

   constructor(private aiAssistantService: AiAssistantService) {

   constructor(private aiAssistantService: AiAssistantService) {
      this.context = this.aiAssistantService.getFullContext();
   }
}
