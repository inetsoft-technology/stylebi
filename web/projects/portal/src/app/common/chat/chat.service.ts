/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { DOCUMENT } from "@angular/common";
import { Inject, Injectable, InjectionToken } from "@angular/core";
import { ChatApi } from "./chat-api";
import { NEVER, Observable } from "rxjs";
import { shareReplay, tap } from "rxjs/operators";
import { HttpParams } from "@angular/common/http";

export const CHAT_API_KEY = new InjectionToken<string>("CHAT_API_KEY");

(window as any).Tawk_API = {};
(window as any).Tawk_LoadStart = new Date();
declare const Tawk_API: ChatApi;

/**
 * Service to initialize and return the tawk API object
 */
@Injectable()
export class ChatService {
   private readonly tawk$: Observable<ChatApi>;
   private chat: ChatApi;

   constructor(@Inject(DOCUMENT) private document: Document,
               @Inject(CHAT_API_KEY) private apiKey: string)
   {
      this.tawk$ = new Observable<ChatApi>((observer) => {
         const s1: any = this.document.createElement("script");
         const s0: any = this.document.getElementsByTagName("script")[0];
         s1.async = true;
         s1.src = `https://embed.tawk.to/${this.apiKey}`;
         s1.charset = "UTF-8";
         s1.setAttribute("crossorigin", "*");
         s0.parentNode.insertBefore(s1, s0);

         Tawk_API.onLoad = () => {
            observer.next(Tawk_API);
         };
      }).pipe(
         tap(chat => this.chat = chat),
         shareReplay(1)
      );
   }

   public connect(name: string, email: string): Observable<ChatApi> {
      if(name === "anonymous") {
         return NEVER;
      }

      // need to set before chat is initialized
      Tawk_API.visitor = { name, email };
      return this.tawk$;
   }

   public openSession(vsID: string, runtimeID: string): void {
      const queryParameters = this.getQueryString(vsID, runtimeID);
      this.chat.setAttributes({
         queryParameters
      }, (error) => {
         if(error != null) {
            window.prompt("Please copy and paste this to the chat agent", queryParameters);
         }
         else {
            window.alert("Opened Session To Agent");
         }
      });
   }

   public closeSession(): void {
      // indicate to dashboard that session was closed
      if(this.chat != null) {
         this.chat.setAttributes({
            queryParameters: "CLOSED"
         });
      }
   }

   public isChatOngoing(): boolean {
      return this.chat != null && this.chat.isChatOngoing();
   }

   private getQueryString(vsID: string, runtimeID: string): string {
      return "?" + new HttpParams()
         .set("vsId", vsID)
         .set("runtimeId", runtimeID)
         .toString();
   }
}
