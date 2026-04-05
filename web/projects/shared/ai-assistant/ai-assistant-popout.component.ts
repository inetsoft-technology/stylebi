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

import { Component, HostListener, NgZone, OnDestroy, OnInit } from "@angular/core";
import { ActivatedRoute } from "@angular/router";
import { AiAssistantService } from "./ai-assistant.service";

@Component({
   selector: "ai-assistant-popout",
   templateUrl: "./ai-assistant-popout.component.html",
   styleUrls: ["./ai-assistant-popout.component.scss"]
})
export class AiAssistantPopoutComponent implements OnInit, OnDestroy {
   serverState: "checking" | "online" | "offline" = "checking";
   context: string = "";
   userId: string = "";
   userEmail: string = "";
   chatAppServerUrl: string = "";
   styleBIUrl: string = "";

   private channel: BroadcastChannel | null = null;

   constructor(
      private route: ActivatedRoute,
      private aiAssistantService: AiAssistantService,
      private zone: NgZone
   ) {}

   ngOnInit(): void {
      const channelId = this.route.snapshot.queryParamMap.get("channel");

      if(!channelId) {
         this.serverState = "offline";
         return;
      }

      // Connect to the parent tab via BroadcastChannel.
      this.channel = new BroadcastChannel("ai-assistant-" + channelId);

      this.channel.onmessage = (event) => {
         this.zone.run(() => {
            if(event.data?.type === "init") {
               this.context = event.data.context || "";
               this.userId = event.data.userId || "";
               this.userEmail = event.data.userEmail || "";
            }
            else if(event.data?.type === "context") {
               this.context = event.data.context || "";
            }
            else if(event.data?.type === "parent-closing") {
               window.close();
            }
         });
      };

      // Seed user identity from the local service instance (resolved from the same session)
      // while waiting for the init message from the parent.
      this.userId = this.aiAssistantService.userId;
      this.userEmail = this.aiAssistantService.email;

      // Signal the parent that this tab is ready for the init payload.
      this.channel.postMessage({type: "ready"});

      // Start health check and web component loading.
      this.aiAssistantService.checkHealth().subscribe(online => {
         if(online) {
            this.aiAssistantService.loadWebComponentScript()
               .then(() => {
                  // The script has loaded and executed. If the element is already registered
                  // (synchronous define in the UMD bundle), go online immediately. Otherwise
                  // wait with a generous timeout — some bundles register lazily via dynamic
                  // imports that may take longer in a fresh tab with no warm connections.
                  if(customElements.get("ai-assistant")) {
                     return Promise.resolve();
                  }

                  return new Promise<void>((resolve) => {
                     const timer = setTimeout(() => {
                        // Element still not defined after 30s — show it anyway; the web
                        // component will either work or render nothing, but "offline" would be
                        // misleading since the server is reachable.
                        resolve();
                     }, 30000);
                     customElements.whenDefined("ai-assistant").then(() => {
                        clearTimeout(timer);
                        resolve();
                     });
                  });
               })
               .then(() => this.zone.run(() => {
                  this.chatAppServerUrl = this.aiAssistantService.chatAppServerUrl;
                  this.styleBIUrl = this.aiAssistantService.styleBIUrl;
                  this.serverState = "online";
               }))
               .catch(() => this.zone.run(() => this.serverState = "offline"));
         }
         else {
            this.serverState = "offline";
         }
      });
   }

   /** Tell the parent the pop-out is closing so it can clean up its channel reference. */
   @HostListener("window:beforeunload")
   onBeforeUnload(): void {
      try { this.channel?.postMessage({type: "popout-closed"}); } catch { /* ignore */ }
   }

   ngOnDestroy(): void {
      this.channel?.close();
      this.channel = null;
   }
}
