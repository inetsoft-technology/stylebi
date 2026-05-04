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

export enum Role {
   USER = "USER",
   ASSISTANT = "ASSISTANT",
   SYSTEM = "SYSTEM"
}

export enum MessageType {
   TEXT = "text",
   IMAGE = "image",
   SYSTEM = "system"
}

export enum StepStatus {
   PENDING = "pending",
   IN_PROGRESS = "in_progress",
   COMPLETED = "completed"
}

export interface Step {
   content: string;
   status: StepStatus;
   conversationId?: string;
   executionTime?: number;
}

export interface Message {
   _id?: string;
   sessionId?: string;
   sender: Role;
   message: string;
   messageType?: MessageType;
   createdAt?: Date;
   reviewId?: string;
   feedbacks?: string[];
   approved?: boolean;
   steps?: Step[];
   reportIssue?: boolean;
}

export interface Conversation {
   _id?: string;
   userId?: string;
   sessionName: string;
   context?: string;
   generating: boolean;
   summarizedMessages: Message[];
   createdAt: Date;
   updatedAt: Date;
   pinned?: boolean;
   removed?: boolean;
}

export interface Retrievals {
   agentic: boolean;
   hyde: boolean;
   stepback: boolean;
   rewrite_expansion: boolean;
   rewrite_decomposition_expansion: boolean;
}

export interface ReviewRecord {
   recordType: "QUESTION" | "ANSWER";
   messageContent?: string;
}

export interface Review {
   _id?: string;
   userId?: string;
   conversationId?: string;
   messageId?: string;
   reviewStatus?: string;
   records?: ReviewRecord[];
   summary?: { messageContent?: string };
   viewed?: boolean;
   isConversationDeleted?: boolean;
   deletedByUser?: boolean;
}

export type SseEvent =
   | { type: "token"; content: string }
   | { type: "reset" }
   | { type: "done"; answer: Message; logs: string[] }
   | { type: "error"; error: string };
