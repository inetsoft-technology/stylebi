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
   ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, Input, OnDestroy, OnInit, Output
} from "@angular/core";
import { Message } from "../assistant-models";
import { AssistantApiService } from "../services/assistant-api.service";

const ALLOWED_FILE_TYPES = [
   "image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp",
   "application/pdf", "text/plain", "text/markdown",
   "application/json", "text/csv", "application/zip",
   "application/x-zip-compressed", "text/html", "text/css",
   "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
   "video/mp4", "video/webm"
];

const MAX_SINGLE_FILE_MB = 5;
const MAX_TOTAL_MB = 10;

@Component({
   selector: "report-problem-dialog",
   templateUrl: "./report-problem-dialog.component.html",
   styleUrls: ["./report-problem-dialog.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class ReportProblemDialogComponent implements OnInit, OnDestroy {
   @Input() message!: Message;
   @Input() sessionId!: string;
   @Input() userId: string = "";
   @Input() userEmail: string = "";

   @Output() closed = new EventEmitter<void>();

   subject: string = "";
   email: string = "";
   description: string = "";
   attachments: File[] = [];
   fileError: string = "";
   submitting: boolean = false;
   submitted: boolean = false;
   error: string = "";
   private closeTimer: ReturnType<typeof setTimeout> | null = null;

   get totalSizeMB(): string {
      return (this.attachments.reduce((s, f) => s + f.size, 0) / 1024 / 1024).toFixed(1);
   }

   get canSubmit(): boolean {
      return !!this.subject.trim() && this.isValidEmail(this.email) && !!this.description.trim() && !this.submitting;
   }

   private isValidEmail(email: string): boolean {
      return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim());
   }

   constructor(
      private apiService: AssistantApiService,
      private cdRef: ChangeDetectorRef
   ) {}

   ngOnInit(): void {
      this.email = this.userEmail || "";
   }

   ngOnDestroy(): void {
      if(this.closeTimer !== null) {
         clearTimeout(this.closeTimer);
      }
   }

   handleFileSelect(event: Event): void {
      const input = event.target as HTMLInputElement;
      if(!input.files?.length) return;

      this.fileError = "";
      const newFiles = Array.from(input.files);

      for(const file of newFiles) {
         if(!ALLOWED_FILE_TYPES.includes(file.type)) {
            this.fileError = `_#(chat.ai.reportUnsupportedType) ${file.type}`;
            input.value = "";
            this.cdRef.markForCheck();
            return;
         }

         if(file.size > MAX_SINGLE_FILE_MB * 1024 * 1024) {
            this.fileError = "_#(chat.ai.reportFileTooLarge)".replace("{0}", String(MAX_SINGLE_FILE_MB));
            input.value = "";
            this.cdRef.markForCheck();
            return;
         }
      }

      const existingSize = this.attachments.reduce((s, f) => s + f.size, 0);
      const newSize = newFiles.reduce((s, f) => s + f.size, 0);

      if(existingSize + newSize > MAX_TOTAL_MB * 1024 * 1024) {
         this.fileError = "_#(chat.ai.reportTotalExceed)".replace("{0}", String(MAX_TOTAL_MB));
         input.value = "";
         this.cdRef.markForCheck();
         return;
      }

      const updated = [...this.attachments];

      for(const file of newFiles) {
         const idx = updated.findIndex(f => f.name === file.name);

         if(idx !== -1) {
            updated[idx] = file;
         }
         else {
            updated.push(file);
         }
      }

      this.attachments = updated;
      input.value = "";
      this.cdRef.markForCheck();
   }

   removeAttachment(index: number): void {
      this.attachments = this.attachments.filter((_, i) => i !== index);
      this.cdRef.markForCheck();
   }

   submit(): void {
      if(!this.canSubmit) {
         return;
      }

      this.submitting = true;
      this.error = "";
      this.cdRef.markForCheck();

      this.doSubmit().catch(() => {
         this.error = "_#(chat.ai.reportFailed)";
         this.submitting = false;
         this.cdRef.markForCheck();
      });
   }

   private async doSubmit(): Promise<void> {
      // Step 1: upload any attachments to get back GCS URLs.
      let accessory: { url: string; name: string }[] = [];

      if(this.attachments.length > 0) {
         const res = await this.apiService.uploadFiles(this.attachments).toPromise() as any;
         accessory = res?.urls ?? [];
      }

      // Step 2: POST the issue as JSON.
      const issueBody: Record<string, any> = {
         title: this.subject.trim(),
         description: this.description.trim(),
         email: this.email.trim(),
         conversationId: this.sessionId,
         messageId: this.message._id ?? "",
         accessory,
         status: "NEW"
      };

      if(this.userId) {
         issueBody["userId"] = this.userId;
      }

      await this.apiService.createIssue(issueBody).toPromise();

      this.submitted = true;
      this.submitting = false;
      this.cdRef.markForCheck();
      this.closeTimer = setTimeout(() => this.closed.emit(), 1500);
   }
}
