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

import { Injectable, NgZone } from "@angular/core";
import { Observable, Subject } from "rxjs";
import { AiAssistantService } from "../ai-assistant.service";
import { AssistantApiService } from "./assistant-api.service";

@Injectable({ providedIn: "root" })
export class AssistantWebSocketService {
   private ws: WebSocket | null = null;
   private readonly messages$ = new Subject<any>();

   constructor(
      private aiAssistantService: AiAssistantService,
      private assistantApiService: AssistantApiService,
      private zone: NgZone
   ) {}

   /**
    * Obtains a one-time WebSocket token from the assistant backend, then opens a
    * WebSocket connection. Emits parsed JSON messages to the returned Observable.
    * Step-indicator updates are non-critical — errors are silently swallowed so
    * they never surface to the user.
    */
   connect(): Observable<any> {
      this.disconnect();

      this.assistantApiService.getWsToken().subscribe({
         next: ({ token }) => {
            const base = this.aiAssistantService.chatAppServerUrl.replace(/\/$/, "");
            const wsUrl = base.replace(/^http/, "ws") +
               `/ws?clientId=${this.assistantApiService.clientId}&token=${token}`;
            this.ws = new WebSocket(wsUrl);

            this.ws.onmessage = (event: MessageEvent) => {
               try {
                  const data = JSON.parse(event.data);
                  // Run inside Angular zone so change detection fires on step updates.
                  this.zone.run(() => this.messages$.next(data));
               }
               catch {
                  // ignore unparseable frames
               }
            };

            // Step updates are non-critical; log but do not surface errors to the user.
            this.ws.onerror = () => {};
         },
         error: () => {
            // No WS token available — steps will not be shown, which is acceptable.
         }
      });

      return this.messages$.asObservable();
   }

   disconnect(): void {
      if(this.ws) {
         this.ws.close();
         this.ws = null;
      }
   }

}
