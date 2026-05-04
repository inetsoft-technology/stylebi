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

import { HttpClient, HttpHeaders } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
import { AiAssistantService } from "../ai-assistant.service";
import { Conversation, Message, MessageType, Role } from "../assistant-models";

@Injectable({ providedIn: "root" })
export class AssistantApiService {
   // Stable client ID for this browser session — used by the backend to route WebSocket messages to the correct tab.
   readonly clientId: string = crypto.randomUUID();

   private authToken: string | null = null;
   // Shared promise so concurrent callers await the same in-flight request.
   private tokenPromise: Promise<string | null> | null = null;

   constructor(
      private http: HttpClient,
      private aiAssistantService: AiAssistantService
   ) {}

   /**
    * Fetches a short-lived JWT from StyleBI's /api/assistant/get-auth-token endpoint
    * and caches it for all subsequent assistant API calls. Safe to call multiple times —
    * concurrent callers share the same in-flight promise.
    */
   loadToken(): Promise<string | null> {
      if(this.tokenPromise) {
         return this.tokenPromise;
      }

      // TODO: replace .toPromise() with firstValueFrom() when upgrading to RxJS 7+
      this.tokenPromise = this.http
         .get<{ token: string }>("../api/assistant/get-auth-token")
         .toPromise()
         .then(res => {
            this.authToken = res?.token ?? null;
            return this.authToken;
         })
         .catch(() => {
            this.tokenPromise = null;
            return null;
         });

      return this.tokenPromise;
   }

   /** Clears the cached token and promise, forcing a re-fetch on next loadToken() call. */
   clearToken(): void {
      this.authToken = null;
      this.tokenPromise = null;
   }

   getAuthToken(): string | null {
      return this.authToken;
   }

   private apiUrl(path: string): string {
      const base = this.aiAssistantService.chatAppServerUrl.replace(/\/$/, "");
      return `${base}/api${path}`;
   }

   private headers(): HttpHeaders {
      let h = new HttpHeaders({ "x-client-id": this.clientId });

      if(this.authToken) {
         h = h.set("Authorization", `Bearer ${this.authToken}`);
      }

      return h;
   }

   // ── Conversations ─────────────────────────────────────────────────────────

   getConversations(userId: string): Observable<Conversation[]> {
      return this.http.get<Conversation[]>(
         this.apiUrl("/conversations"),
         { headers: this.headers(), params: { userId } }
      );
   }

   updateConversation(id: string, updates: Partial<Pick<Conversation, "sessionName" | "pinned">>): Observable<Conversation> {
      return this.http.put<Conversation>(this.apiUrl(`/conversations/${id}`), updates, { headers: this.headers() });
   }

   deleteConversation(id: string): Observable<void> {
      return this.http.delete<void>(this.apiUrl(`/conversations/${id}`), { headers: this.headers() });
   }

   deleteAllConversations(userId: string): Observable<void> {
      return this.http.delete<void>(this.apiUrl(`/conversations/deleteAll/${userId}`), { headers: this.headers() });
   }

   getMessages(sessionId: string): Observable<{ messages: Message[]; generating: boolean }> {
      return this.http.get<{ messages: Message[]; generating: boolean }>(
         this.apiUrl(`/messages/${sessionId}`), { headers: this.headers() });
   }

   createMessage(opts: {
      sessionId?: string;
      userId: string;
      message: string;
      context: string;
   }): Observable<{ sessionId: string; messageId: string }> {
      const body = {
         userId: opts.userId,
         userMessage: {
            sessionId: opts.sessionId,
            sender: Role.USER,
            message: opts.message,
            messageType: MessageType.TEXT,
            createdAt: new Date()
         },
         context: opts.context
      };

      return this.http.post<any>(
         this.apiUrl("/messages/createMessage"), body, { headers: this.headers() })
         .pipe(
            // Backend returns { message } for existing sessions, { conversation, message } for new.
            // Normalize to { sessionId, messageId }.
            map((res: any) => ({
               sessionId: (res.conversation?._id ?? res.message?.sessionId) as string,
               messageId: res.message?._id as string
            }))
         );
   }

   addFeedback(messageId: string, feedback: string): Observable<void> {
      return this.http.post<void>(
         this.apiUrl(`/messages/feedback/${messageId}`), { feedback }, { headers: this.headers() });
   }

   cancelChat(conversationId: string): Observable<void> {
      return this.http.post<void>(
         this.apiUrl(`/chat/cancel/${conversationId}`), {}, { headers: this.headers() });
   }

   getWsToken(): Observable<{ token: string }> {
      return this.http.get<{ token: string }>(this.apiUrl("/auth/ws-token"), { headers: this.headers() });
   }

   createReview(review: object): Observable<any> {
      return this.http.post<any>(this.apiUrl("/reviews/createReview"), review, { headers: this.headers() });
   }

   checkPendingReply(conversationId: string): Observable<any[]> {
      return this.http.get<any[]>(
         this.apiUrl(`/reviews/checkPendingReply/${conversationId}`), { headers: this.headers() });
   }

   /**
    * Upload attachments to GCS via /upload/files (multipart).
    * Returns an array of { url, name } objects.
    */
   uploadFiles(files: File[]): Observable<{ urls: { url: string; name: string }[] }> {
      const formData = new FormData();
      files.forEach(f => formData.append("files", f));
      // Omit Content-Type — browser sets it with the multipart boundary.
      const h = new HttpHeaders({ "x-client-id": this.clientId });
      const withAuth = this.authToken ? h.set("Authorization", `Bearer ${this.authToken}`) : h;
      return this.http.post<{ urls: { url: string; name: string }[] }>(
         this.apiUrl("/upload/files"), formData, { headers: withAuth });
   }

   /**
    * Create a support issue. Files (if any) must already be uploaded via uploadFiles()
    * and their URLs passed in the `accessory` field. The endpoint expects JSON.
    */
   createIssue(issue: object): Observable<any> {
      return this.http.post<any>(this.apiUrl("/issue"), issue, { headers: this.headers() });
   }

   approveMessage(messageId: string, approved: boolean): Observable<void> {
      return this.http.post<void>(
         this.apiUrl(`/messages/approved/${messageId}`), { approved }, { headers: this.headers() });
   }

   getUserReviews(userId: string): Observable<any[]> {
      return this.http.get<any[]>(
         this.apiUrl(`/reviews/getUserReviews/${encodeURIComponent(userId)}`), { headers: this.headers() });
   }

   updateReview(reviewId: string, updates: object): Observable<void> {
      return this.http.post<void>(
         this.apiUrl(`/reviews/updateReview/${reviewId}`), updates, { headers: this.headers() });
   }

   addReviewRecord(reviewId: string, record: object): Observable<void> {
      return this.http.post<void>(
         this.apiUrl(`/reviews/addRecord`), { reviewId, record }, { headers: this.headers() });
   }
}
