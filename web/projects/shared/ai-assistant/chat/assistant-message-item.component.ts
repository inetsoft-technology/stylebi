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
   ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, Input, OnDestroy, Output
} from "@angular/core";
import { Message, Role, Step, StepStatus } from "../assistant-models";

@Component({
   selector: "assistant-message-item",
   templateUrl: "./assistant-message-item.component.html",
   styleUrls: ["./assistant-message-item.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class AssistantMessageItemComponent implements OnDestroy {
   @Input() message!: Message;
   /** When true, this item displays the live streaming text rather than message.message */
   @Input() isStreaming: boolean = false;
   @Input() streamText: string = "";
   @Input() hasPendingReply: boolean = false;

   @Output() regenerate = new EventEmitter<void>();
   @Output() approve = new EventEmitter<void>();
   @Output() disapprove = new EventEmitter<void>();
   @Output() reportIssue = new EventEmitter<void>();

   constructor(private cdRef: ChangeDetectorRef) {}

   readonly Role = Role;
   readonly StepStatus = StepStatus;

   stepsExpanded: boolean = false;
   copied: boolean = false;
   private copyTimer: ReturnType<typeof setTimeout> | null = null;

   get isApproved(): boolean {
      return !!this.message.approved;
   }

   get isUser(): boolean {
      return this.message.sender === Role.USER;
   }

   get displayContent(): string {
      return this.isStreaming ? this.streamText : this.message.message;
   }

   get hasSteps(): boolean {
      return !!(this.message.steps && this.message.steps.length > 0);
   }

   /** During streaming: the single currently-active step to display. */
   get activeStep(): Step | null {
      if(!this.message.steps?.length) return null;
      // Show the last in-progress step; if none, show the last step overall.
      return this.message.steps.slice().reverse().find(s => s.status === StepStatus.IN_PROGRESS)
         ?? this.message.steps[this.message.steps.length - 1];
   }

   async copyContent(): Promise<void> {
      try {
         await navigator.clipboard.writeText(this.message.message);
         this.copied = true;
         this.cdRef.markForCheck();
         this.copyTimer = setTimeout(() => {
            this.copyTimer = null;
            this.copied = false;
            this.cdRef.markForCheck();
         }, 2000);
      }
      catch {
         // clipboard not available
      }
   }

   ngOnDestroy(): void {
      if(this.copyTimer !== null) {
         clearTimeout(this.copyTimer);
      }
   }
}
