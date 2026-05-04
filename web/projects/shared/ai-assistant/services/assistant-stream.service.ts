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

import { Injectable } from "@angular/core";
import { AiAssistantService } from "../ai-assistant.service";
import { Retrievals, SseEvent } from "../assistant-models";
import { AssistantApiService } from "./assistant-api.service";

@Injectable({ providedIn: "root" })
export class AssistantStreamService {
   constructor(
      private aiAssistantService: AiAssistantService,
      private assistantApiService: AssistantApiService
   ) {}

   /**
    * Streams a chat response from the assistant backend as an async generator of
    * SSE events. Uses the fetch API directly — Angular HttpClient has no support
    * for streaming responses.
    *
    * Event types:
    *   - token  — incremental content chunk
    *   - reset  — agentic step triggered a fresh generation; discard prior tokens
    *   - done   — generation complete; final answer and log list included
    *   - error  — backend reported an error
    */
   async *streamChat(
      sessionId: string,
      message: string,
      context: string,
      userId: string,
      retrievals: Retrievals,
      thinkMode: string | null,
      signal?: AbortSignal
   ): AsyncGenerator<SseEvent> {
      const base = this.aiAssistantService.chatAppServerUrl.replace(/\/$/, "");
      const url = `${base}/api/chat`;

      const headers: Record<string, string> = {
         "Content-Type": "application/json",
         "Accept": "text/event-stream",
         "x-client-id": this.assistantApiService.clientId
      };

      const token = this.assistantApiService.getAuthToken();

      if(token) {
         headers["Authorization"] = `Bearer ${token}`;
      }

      const response = await fetch(url, {
         method: "POST",
         headers,
         credentials: "include",
         body: JSON.stringify({ conversationId: sessionId, question: message, context, userId, retrievals, thinkMode }),
         signal
      });

      if(!response.ok || !response.body) {
         const errorData = await response.json().catch(() => ({}));
         throw new Error((errorData as any).error || `Chat request failed: ${response.status}`);
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";

      try {
         while(true) {
            const { done, value } = await reader.read();

            if(done) {
               break;
            }

            buffer += decoder.decode(value, { stream: true });

            // SSE events are delimited by double newlines
            const parts = buffer.split("\n\n");
            buffer = parts.pop() ?? "";

            for(const part of parts) {
               const line = part.trim();

               if(!line.startsWith("data: ")) {
                  continue;
               }

               try {
                  yield JSON.parse(line.slice(6)) as SseEvent;
               }
               catch {
                  // skip malformed events
               }
            }
         }
      }
      finally {
         reader.releaseLock();
      }
   }
}
