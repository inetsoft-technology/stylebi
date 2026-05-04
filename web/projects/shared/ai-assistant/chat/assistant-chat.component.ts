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

import {
   ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef,
   OnDestroy, OnInit, ViewChild
} from "@angular/core";
import { Subject, Subscription } from "rxjs";
import { takeUntil } from "rxjs/operators";
import { AiAssistantService } from "../ai-assistant.service";
import { Conversation, Message, Retrievals, Role, SseEvent, Step } from "../assistant-models";
import { AssistantApiService } from "../services/assistant-api.service";
import { AssistantStreamService } from "../services/assistant-stream.service";
import { AssistantWebSocketService } from "../services/assistant-websocket.service";

@Component({
   selector: "assistant-chat",
   templateUrl: "./assistant-chat.component.html",
   styleUrls: ["./assistant-chat.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class AssistantChatComponent implements OnInit, OnDestroy {
   @ViewChild("messageContainer") messageContainerRef!: ElementRef<HTMLElement>;

   // ── State ─────────────────────────────────────────────────────────────────

   conversations: Conversation[] = [];
   messages: Message[] = [];
   currentSessionId: string | null = null;
   isGenerating: boolean = false;
   /** Accumulated text for the current streaming assistant response. */
   streamingText: string = "";
   /** True while the streaming placeholder message is being shown. */
   isStreaming: boolean = false;
   /** Live steps pushed via WebSocket during streaming. */
   streamingSteps: Step[] = [];

   sidebarOpen: boolean = true;
   initializing: boolean = true;
   loadingMessages: boolean = false;
   loadError: string = "";
   authError: boolean = false;

   thinkMode: string | null = null;
   retrievals: Retrievals = {
      agentic: false, hyde: false, stepback: false,
      rewrite_expansion: false, rewrite_decomposition_expansion: false
   };

   reportIssueMessage: Message | null = null;
   pendingReplyConversationIds = new Set<string>();

   // Disapprove dialog
   disapproveDialogMessage: Message | null = null;
   disapproveCategories: Set<string> = new Set<string>();
   disapproveFreeText: string = "";
   disapproveSubmitting: boolean = false;
   readonly disapproveOptions: { label: string; value: string }[] = [
      { label: "_#(Wrong)", value: "Wrong" },
      { label: "_#(Useless)", value: "Useless" },
      { label: "_#(Other)", value: "Other" }
   ];

   // Admin reply inbox
   replies: any[] = [];
   conversationMap: Record<string, string> = {};
   showReplies: boolean = false;

   // Generating indicator (for sidebar)
   generatingSessionId: string | null = null;

   private abortController: AbortController | null = null;
   private readonly destroy$ = new Subject<void>();
   private wsSub: Subscription | null = null;

   get userId(): string {
      return this.aiAssistantService.userId;
   }

   get userEmail(): string {
      return this.aiAssistantService.email;
   }

   get context(): string {
      return this.aiAssistantService.getFullContext();
   }

   get unreadReplyCount(): number {
      return this.replies.filter(r => !r.viewed).length;
   }

   constructor(
      readonly aiAssistantService: AiAssistantService,
      private apiService: AssistantApiService,
      private streamService: AssistantStreamService,
      private wsService: AssistantWebSocketService,
      private cdRef: ChangeDetectorRef
   ) {}

   async ngOnInit(): Promise<void> {
      // Fetch the assistant auth token. The AiAssistantPanelComponent has already
      // verified server health before rendering this component.
      const token = await this.apiService.loadToken();

      if(!token) {
         this.initializing = false;
         this.authError = true;
         this.cdRef.markForCheck();
         return;
      }

      // If AiAssistantService.userId is not yet populated (loadCurrentUser() may not
      // have been called before the panel opened), extract identity from the JWT payload.
      // The token was just verified server-side so decoding the payload is safe.
      if(!this.aiAssistantService.userId) {
         try {
            // JWTs use Base64URL encoding — normalize to standard Base64 before atob().
            const base64url = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
            const payload = JSON.parse(atob(base64url));

            if(payload.sub) {
               this.aiAssistantService.userId = payload.sub;
            }

            if(payload.email && !this.aiAssistantService.email) {
               this.aiAssistantService.email = payload.email;
            }
         }
         catch {
            // ignore malformed token — userId stays empty, backend will reject if needed
         }
      }

      // Subscribe to context changes while the panel is open.
      this.aiAssistantService.contextChange$
         .pipe(takeUntil(this.destroy$))
         .subscribe(() => this.cdRef.markForCheck());

      // Connect WebSocket for step-indicator updates (non-critical).
      this.wsSub = this.wsService.connect()
         .pipe(takeUntil(this.destroy$))
         .subscribe(msg => this.handleWsMessage(msg));

      // Load branding, conversations, and reviews in parallel.
      await Promise.all([
         this.aiAssistantService.refreshBranding(),
         this.loadConversations(),
         this.loadUserReviews()
      ]);

      // Now that conversations are loaded, re-map any review conversation names
      // that were missed due to the parallel load (loadUserReviews runs before
      // loadConversations may have populated this.conversations).
      if(this.replies.length > 0) {
         const ids = [...new Set(this.replies.map((r: any) => r.conversationId).filter(Boolean))] as string[];
         this.updateConversationMap(ids);
      }

      this.initializing = false;

      // Honor the createNewChat flag set by context-change callers (e.g. binding editor).
      if(this.aiAssistantService.createNewChat) {
         this.aiAssistantService.resetNewChat();
         this.startNewChat();
      }
      else {
         const firstId = this.conversations[0]?._id;

         if(firstId) {
            await this.selectSession(firstId);
         }
      }

      this.cdRef.markForCheck();
   }

   ngOnDestroy(): void {
      this.abortController?.abort();
      this.wsService.disconnect();
      this.destroy$.next();
      this.destroy$.complete();
   }

   async loadConversations(): Promise<void> {
      try {
         this.conversations = await this.apiService
            .getConversations(this.userId)
            .toPromise() as Conversation[];
         this.cdRef.markForCheck();
      }
      catch {
         // Non-fatal — user can still start a new chat.
         // Only show an error on the initial load, not on background refreshes after generation.
         if(this.initializing) {
            this.loadError = "_#(chat.ai.loadError)";
            this.cdRef.markForCheck();
         }
      }
   }

   async selectSession(sessionId: string): Promise<void> {
      if(this.isGenerating) {
         return;
      }

      this.currentSessionId = sessionId;
      this.messages = [];
      this.loadingMessages = true;
      this.loadError = "";
      this.cdRef.markForCheck();

      try {
         const res = await this.apiService.getMessages(sessionId).toPromise() as { messages: Message[]; generating: boolean };
         this.messages = res?.messages ?? [];
         this.generatingSessionId = res?.generating ? sessionId : null;
         await this.checkPendingReplies();
      }
      catch {
         this.loadError = "_#(chat.ai.loadError)";
      }
      finally {
         this.loadingMessages = false;
         this.cdRef.markForCheck();
         this.scrollToBottom();
      }
   }

   startNewChat(): void {
      this.currentSessionId = null;
      this.messages = [];
      this.streamingText = "";
      this.streamingSteps = [];
      this.isStreaming = false;
      this.loadError = "";
      this.cdRef.markForCheck();
   }

   async renameSession(event: { id: string; name: string }): Promise<void> {
      try {
         await this.apiService.updateConversation(event.id, { sessionName: event.name }).toPromise();
         const conv = this.conversations.find(c => c._id === event.id);

         if(conv) {
            conv.sessionName = event.name;
            this.cdRef.markForCheck();
         }
      }
      catch {
         this.loadError = "_#(chat.ai.generalError)";
         this.cdRef.markForCheck();
      }
   }

   async pinSession(event: { id: string; pinned: boolean }): Promise<void> {
      try {
         await this.apiService.updateConversation(event.id, { pinned: event.pinned }).toPromise();
         const conv = this.conversations.find(c => c._id === event.id);

         if(conv) {
            conv.pinned = event.pinned;
            this.conversations = [...this.conversations];
            this.cdRef.markForCheck();
         }
      }
      catch {
         this.loadError = "_#(chat.ai.generalError)";
         this.cdRef.markForCheck();
      }
   }

   async deleteSession(sessionId: string): Promise<void> {
      try {
         await this.apiService.deleteConversation(sessionId).toPromise();
      }
      catch {
         this.loadError = "_#(chat.ai.generalError)";
         this.cdRef.markForCheck();
         return;
      }

      this.conversations = this.conversations.filter(c => c._id !== sessionId);
      this.replies = this.replies.filter(r => r.conversationId !== sessionId);
      this.pendingReplyConversationIds.delete(sessionId);

      if(this.currentSessionId === sessionId) {
         this.startNewChat();
         const next = this.conversations[0]?._id;

         if(next) {
            await this.selectSession(next);
         }
      }

      this.cdRef.markForCheck();
   }

   async deleteAllSessions(): Promise<void> {
      try {
         await this.apiService.deleteAllConversations(this.userId).toPromise();
      }
      catch {
         this.loadError = "_#(chat.ai.generalError)";
         this.cdRef.markForCheck();
         return;
      }

      this.conversations = [];
      this.replies = [];
      this.pendingReplyConversationIds.clear();
      this.startNewChat();
      this.cdRef.markForCheck();
   }

   async sendMessage(text: string): Promise<void> {
      if(this.isGenerating || !text.trim()) {
         return;
      }

      this.isGenerating = true;
      this.loadError = "";
      const context = this.context;

      // Optimistically show the user message immediately, before the network call.
      const tempId = `temp-${Date.now()}`;
      this.messages = [...this.messages, {
         _id: tempId,
         sender: Role.USER,
         message: text,
         createdAt: new Date()
      }];
      this.streamingText = "";
      this.streamingSteps = [];
      this.isStreaming = true;
      this.cdRef.markForCheck();
      this.scrollToBottom();

      try {
         // Persist the user message and get/create the session.
         const result = await this.apiService.createMessage({
            sessionId: this.currentSessionId ?? undefined,
            userId: this.userId,
            message: text,
            context
         }).toPromise() as { sessionId: string; messageId: string };

         this.currentSessionId = result.sessionId;

         // Replace the temp entry with the real persisted IDs.
         const tempIdx = this.messages.findIndex(m => m._id === tempId);

         if(tempIdx !== -1) {
            this.messages = [
               ...this.messages.slice(0, tempIdx),
               { ...this.messages[tempIdx], _id: result.messageId, sessionId: result.sessionId },
               ...this.messages.slice(tempIdx + 1)
            ];
         }

         this.abortController = new AbortController();

         for await (const event of this.streamService.streamChat(
            result.sessionId,
            text,
            context,
            this.userId,
            this.retrievals,
            this.thinkMode,
            this.abortController.signal
         )) {
            this.handleSseEvent(event, result.sessionId);
         }
      }
      catch(err: any) {
         if(err?.name !== "AbortError") {
            // Remove the optimistically added message on failure.
            this.messages = this.messages.filter(m => m._id !== tempId);
            this.isStreaming = false;
            this.streamingText = "";
            this.loadError = "_#(chat.ai.generalError)";
         }
      }
      finally {
         this.isGenerating = false;
         this.isStreaming = false;
         this.abortController = null;
         this.cdRef.markForCheck();
      }
   }

   private handleSseEvent(event: SseEvent, sessionId: string): void {
      switch(event.type) {
         case "token":
            this.streamingText += event.content;
            this.cdRef.markForCheck();
            this.scrollToBottom();
            break;

         case "reset":
            // Agentic mode triggered a new retrieval pass — discard partial tokens.
            this.streamingText = "";
            this.cdRef.markForCheck();
            break;

         case "done":
            this.isStreaming = false;
            this.streamingText = "";
            // event.answer is the full persisted Message from the backend.
            this.messages = [...this.messages, event.answer];
            // Clear generating indicator now that this session is done.
            if(this.generatingSessionId === sessionId) {
               this.generatingSessionId = null;
            }
            // Refresh conversation list to pick up the generated session name.
            this.loadConversations();
            this.cdRef.markForCheck();
            this.scrollToBottom();
            break;

         case "error":
            this.isStreaming = false;
            this.streamingText = "";
            this.loadError = event.error || "_#(chat.ai.assistantError)";
            this.cdRef.markForCheck();
            break;
      }
   }

   cancelGeneration(): void {
      this.abortController?.abort();

      if(this.currentSessionId) {
         this.apiService.cancelChat(this.currentSessionId).subscribe({ error: () => {} });
      }
   }

   async regenerateLastMessage(): Promise<void> {
      if(this.isGenerating) {
         return;
      }

      const userMessages = this.messages.filter(m => m.sender === Role.USER);

      if(userMessages.length === 0) {
         return;
      }

      const lastUserMessage = userMessages[userMessages.length - 1];
      // Remove the last assistant response before regenerating.
      const lastAssistant = [...this.messages].reverse().find(m => m.sender === Role.ASSISTANT);

      if(lastAssistant) {
         this.messages = this.messages.filter(m => m !== lastAssistant);
      }

      await this.sendMessage(lastUserMessage.message as string);
   }

   openDisapproveDialog(message: Message): void {
      this.disapproveDialogMessage = message;
      this.disapproveCategories = new Set<string>();
      this.disapproveFreeText = "";
      this.cdRef.markForCheck();
   }

   closeDisapproveDialog(): void {
      this.disapproveDialogMessage = null;
      this.cdRef.markForCheck();
   }

   toggleDisapproveCategory(cat: string): void {
      const next = new Set(this.disapproveCategories);

      if(next.has(cat)) {
         next.delete(cat);
      }
      else {
         next.add(cat);
      }

      this.disapproveCategories = next;
   }

   async submitDisapprove(): Promise<void> {
      const message = this.disapproveDialogMessage;

      if(!message || !message._id || !this.currentSessionId || this.disapproveSubmitting) {
         return;
      }

      this.disapproveSubmitting = true;

      const categoryStr = Array.from(this.disapproveCategories).join(", ");
      const question = categoryStr
         ? (this.disapproveFreeText ? `${categoryStr}\n${this.disapproveFreeText}` : categoryStr)
         : this.disapproveFreeText;

      if(!question.trim()) {
         this.disapproveSubmitting = false;
         return;
      }

      try {
         // Add to existing review or create new one.
         let needCreate = !message.reviewId;

         if(message.reviewId) {
            try {
               await this.apiService.addReviewRecord(message.reviewId, {
                  messageId: message._id,
                  recordType: "QUESTION",
                  messageContent: question
               }).toPromise();
            }
            catch {
               needCreate = true;
            }
         }

         let newReviewId: string | undefined;

         if(needCreate) {
            const res = await this.apiService.createReview({
               newReview: {
                  userId: this.userId,
                  conversationId: this.currentSessionId,
                  messageId: message._id
               },
               question
            }).toPromise() as any;
            newReviewId = res?._id;
         }

         // Record feedback on the message.
         await this.apiService.addFeedback(message._id, question).toPromise();

         // Update message locally.
         const idx = this.messages.findIndex(m => m._id === message._id);

         if(idx !== -1) {
            const updated: Message = {
               ...this.messages[idx],
               feedbacks: [...(this.messages[idx].feedbacks ?? []), question]
            };

            if(newReviewId) {
               updated.reviewId = newReviewId;
            }

            this.messages = [
               ...this.messages.slice(0, idx),
               updated,
               ...this.messages.slice(idx + 1)
            ];
         }
      }
      catch {
         // Non-critical — feedback submission failure is silent.
      }
      finally {
         this.disapproveSubmitting = false;
         this.disapproveDialogMessage = null;
         this.cdRef.markForCheck();
      }
   }

   async approveMessage(message: Message): Promise<void> {
      if(!message._id) {
         return;
      }

      const nextApproved = !message.approved;

      // Optimistic local update.
      const idx = this.messages.findIndex(m => m._id === message._id);

      if(idx !== -1) {
         this.messages = [
            ...this.messages.slice(0, idx),
            { ...this.messages[idx], approved: nextApproved },
            ...this.messages.slice(idx + 1)
         ];
         this.cdRef.markForCheck();
      }

      try {
         await this.apiService.approveMessage(message._id, nextApproved).toPromise();
      }
      catch {
         // Revert on failure.
         const revertIdx = this.messages.findIndex(m => m._id === message._id);

         if(revertIdx !== -1) {
            this.messages = [
               ...this.messages.slice(0, revertIdx),
               { ...this.messages[revertIdx], approved: message.approved },
               ...this.messages.slice(revertIdx + 1)
            ];
            this.cdRef.markForCheck();
         }
      }
   }

   openReportIssue(message: Message): void {
      this.reportIssueMessage = message;
      this.cdRef.markForCheck();
   }

   closeReportIssue(): void {
      this.reportIssueMessage = null;
      this.cdRef.markForCheck();
   }

   async loadUserReviews(): Promise<void> {
      if(!this.userId) {
         return;
      }

      try {
         const reviews = await this.apiService.getUserReviews(this.userId).toPromise() as any[];

         // Only show reviews with status PENDING_REVIEW that have an ANSWER record.
         this.replies = (reviews ?? []).filter(r =>
            r.reviewStatus === "PENDING_REVIEW" &&
            r.records?.some((rec: any) => rec.recordType === "ANSWER") &&
            !r.isConversationDeleted && !r.deletedByUser
         );

         // Build conversation name map for the reply list.
         const ids = [...new Set(this.replies.map((r: any) => r.conversationId).filter(Boolean))];

         if(ids.length > 0) {
            this.updateConversationMap(ids as string[]);
         }

         this.cdRef.markForCheck();
      }
      catch {
         // Non-critical.
      }
   }

   private updateConversationMap(ids: string[]): void {
      const missing = ids.filter(id => !this.conversationMap[id]);

      if(missing.length === 0) {
         return;
      }

      // Map from already-loaded conversations first.
      const fromCache: Record<string, string> = {};

      for(const id of missing) {
         const conv = this.conversations.find(c => c._id === id);

         if(conv) {
            fromCache[id] = conv.sessionName;
         }
      }

      this.conversationMap = { ...this.conversationMap, ...fromCache };
      this.cdRef.markForCheck();
   }

   async handleReplySelect(id: string): Promise<void> {
      const reply = this.replies.find(r => r._id === id);

      if(!reply) {
         return;
      }

      // Mark as viewed locally first.
      this.replies = this.replies.map(r => r._id === id ? { ...r, viewed: true } : r);
      this.showReplies = false;
      this.cdRef.markForCheck();

      try {
         await this.apiService.updateReview(id, { viewed: true }).toPromise();
      }
      catch {
         // Keep local state even if API call fails.
      }

      // Navigate to the conversation.
      if(reply.conversationId) {
         await this.selectSession(reply.conversationId);
      }
   }

   async handleReplyDelete(id: string): Promise<void> {
      try {
         await this.apiService.updateReview(id, { deletedByUser: true }).toPromise();
      }
      catch {
         // Remove from UI regardless.
      }

      this.replies = this.replies.filter(r => r._id !== id);
      this.cdRef.markForCheck();
   }

   toggleReplies(): void {
      this.showReplies = !this.showReplies;
      this.cdRef.markForCheck();
   }

   closeReplies(): void {
      this.showReplies = false;
      this.cdRef.markForCheck();
   }

   private async checkPendingReplies(): Promise<void> {
      if(!this.currentSessionId) {
         return;
      }

      try {
         const reviews = await this.apiService
            .checkPendingReply(this.currentSessionId)
            .toPromise() as any[];

         if(reviews?.length) {
            this.pendingReplyConversationIds.add(this.currentSessionId);
         }
      }
      catch {
         // Non-critical
      }
   }

   private handleWsMessage(msg: any): void {
      if(!msg) {
         return;
      }

      // Review inbox events (independent of streaming state).
      if(msg.type === "review_created" || msg.type === "review_updated") {
         const review = msg.review;

         if(!review) return;

         const hasAnswer = review.records?.some((r: any) => r.recordType === "ANSWER");

         if(hasAnswer &&
            review.reviewStatus === "PENDING_REVIEW" &&
            !review.isConversationDeleted &&
            !review.deletedByUser)
         {
            const exists = this.replies.some(r => r._id === review._id);
            this.replies = exists
               ? this.replies.map(r => r._id === review._id ? review : r)
               : [...this.replies, review];

            if(review.conversationId) {
               this.updateConversationMap([review.conversationId]);
            }
         }
         else {
            this.replies = this.replies.filter(r => r._id !== review._id);
         }

         this.cdRef.markForCheck();
         return;
      }

      if(!this.isStreaming) {
         return;
      }

      // Steps arrive one at a time — accumulate them.
      if(msg.steps) {
         for(const incoming of msg.steps as Step[]) {
            const idx = this.streamingSteps.findIndex(s => s.content === incoming.content);

            if(idx >= 0) {
               this.streamingSteps = [
                  ...this.streamingSteps.slice(0, idx),
                  incoming,
                  ...this.streamingSteps.slice(idx + 1)
               ];
            }
            else {
               this.streamingSteps = [...this.streamingSteps, incoming];
            }
         }

         this.cdRef.markForCheck();
      }
   }

   private scrollToBottom(): void {
      setTimeout(() => {
         const el = this.messageContainerRef?.nativeElement;

         if(el) {
            el.scrollTop = el.scrollHeight;
         }
      }, 0);
   }

   trackMessage(index: number, msg: Message): string {
      return msg._id ?? msg.createdAt?.toString() ?? String(index);
   }

   hasPendingReply(msg: Message): boolean {
      return msg.reviewId != null &&
         this.pendingReplyConversationIds.has(this.currentSessionId ?? "");
   }
}
