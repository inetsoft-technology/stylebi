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

import { CommonModule } from "@angular/common";
import { HTTP_INTERCEPTORS } from "@angular/common/http";
import { NgModule } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { MatIconModule } from "@angular/material/icon";
import { AiAssistantDialogComponent } from "./ai-assistant-dialog.component";
import { AiAssistantPanelComponent } from "./ai-assistant-panel.component";
import { AssistantChatComponent } from "./chat/assistant-chat.component";
import { AssistantInputAreaComponent } from "./chat/assistant-input-area.component";
import { AssistantMarkdownPipe } from "./chat/assistant-markdown.pipe";
import { AssistantMessageItemComponent } from "./chat/assistant-message-item.component";
import { AssistantSidebarComponent } from "./chat/assistant-sidebar.component";
import { ReportProblemDialogComponent } from "./chat/report-problem-dialog.component";
import { SessionNamePipe } from "./chat/session-name.pipe";
import { AssistantAuthInterceptor } from "./services/assistant-auth.interceptor";

@NgModule({
   imports: [
      CommonModule,
      FormsModule,
      MatIconModule
   ],
   providers: [
      { provide: HTTP_INTERCEPTORS, useClass: AssistantAuthInterceptor, multi: true }
   ],
   declarations: [
      AiAssistantDialogComponent,
      AiAssistantPanelComponent,
      AssistantChatComponent,
      AssistantInputAreaComponent,
      AssistantMarkdownPipe,
      AssistantMessageItemComponent,
      AssistantSidebarComponent,
      ReportProblemDialogComponent,
      SessionNamePipe
   ],
   exports: [
      AiAssistantDialogComponent,
      AiAssistantPanelComponent
   ]
})
export class AiAssistantModule {
}