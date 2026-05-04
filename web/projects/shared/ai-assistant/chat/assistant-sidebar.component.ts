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
   ChangeDetectionStrategy, Component, EventEmitter, Input, OnChanges, Output
} from "@angular/core";
import { Conversation } from "../assistant-models";

interface ConversationGroup {
   label: string;
   items: Conversation[];
}

@Component({
   selector: "assistant-sidebar",
   templateUrl: "./assistant-sidebar.component.html",
   styleUrls: ["./assistant-sidebar.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class AssistantSidebarComponent implements OnChanges {
   @Input() conversations: Conversation[] = [];
   @Input() currentSessionId: string | null = null;
   @Input() title: string | null = null;
   @Input() vendorName: string | null = null;
   @Input() logoUrl: string | null = null;
   @Input() generatingSessionId: string | null = null;

   @Output() sessionSelected = new EventEmitter<string>();
   @Output() newChat = new EventEmitter<void>();
   @Output() deleteSession = new EventEmitter<string>();
   @Output() pinSession = new EventEmitter<{ id: string; pinned: boolean }>();
   @Output() renameSession = new EventEmitter<{ id: string; name: string }>();
   @Output() deleteAll = new EventEmitter<void>();

   groups: ConversationGroup[] = [];
   renamingId: string | null = null;
   renameValue: string = "";
   activeMenuId: string | null = null;
   deleteConfirmId: string | null = null;
   showDeleteAllConfirm: boolean = false;

   ngOnChanges(): void {
      this.buildGroups();
   }

   private buildGroups(): void {
      const now = new Date();
      const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
      const yesterday = new Date(today.getTime() - 86400000);
      const sevenDays = new Date(today.getTime() - 7 * 86400000);
      const thirtyDays = new Date(today.getTime() - 30 * 86400000);

      const pinned: Conversation[] = [];
      const todayList: Conversation[] = [];
      const yesterdayList: Conversation[] = [];
      const last7: Conversation[] = [];
      const last30: Conversation[] = [];
      const older: Conversation[] = [];

      const sorted = [...this.conversations].sort(
         (a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
      );

      for(const c of sorted) {
         if(c.pinned) {
            pinned.push(c);
            continue;
         }

         const d = new Date(c.updatedAt);

         if(d >= today) {
            todayList.push(c);
         }
         else if(d >= yesterday) {
            yesterdayList.push(c);
         }
         else if(d >= sevenDays) {
            last7.push(c);
         }
         else if(d >= thirtyDays) {
            last30.push(c);
         }
         else {
            older.push(c);
         }
      }

      this.groups = [
         { label: "_#(Pinned)", items: pinned },
         { label: "_#(Today)", items: todayList },
         { label: "_#(Yesterday)", items: yesterdayList },
         { label: "_#(Last 7 days)", items: last7 },
         { label: "_#(Last 30 days)", items: last30 },
         { label: "_#(Older)", items: older }
      ].filter(g => g.items.length > 0);
   }

   startRename(conv: Conversation, event: MouseEvent): void {
      event.stopPropagation();
      this.renamingId = conv._id!;
      this.renameValue = conv.sessionName;
      this.activeMenuId = null;
   }

   commitRename(id: string): void {
      const name = this.renameValue.trim();

      if(name) {
         this.renameSession.emit({ id, name });
      }

      this.renamingId = null;
   }

   onRenameKey(event: KeyboardEvent, id: string): void {
      if(event.key === "Enter") {
         this.commitRename(id);
      }
      else if(event.key === "Escape") {
         this.renamingId = null;
      }
   }

   toggleMenu(id: string, event: MouseEvent): void {
      event.stopPropagation();
      this.activeMenuId = this.activeMenuId === id ? null : id;
   }

   closeMenu(): void {
      this.activeMenuId = null;
   }

   onPin(conv: Conversation, event: MouseEvent): void {
      event.stopPropagation();
      this.activeMenuId = null;
      this.pinSession.emit({ id: conv._id!, pinned: !conv.pinned });
   }

   onDelete(id: string, event: MouseEvent): void {
      event.stopPropagation();
      this.activeMenuId = null;
      this.deleteConfirmId = id;
   }

   confirmDelete(): void {
      if(this.deleteConfirmId) {
         this.deleteSession.emit(this.deleteConfirmId);
         this.deleteConfirmId = null;
      }
   }

   cancelDelete(): void {
      this.deleteConfirmId = null;
   }

   onDeleteAll(): void {
      this.showDeleteAllConfirm = true;
   }

   confirmDeleteAll(): void {
      this.showDeleteAllConfirm = false;
      this.deleteAll.emit();
   }

   cancelDeleteAll(): void {
      this.showDeleteAllConfirm = false;
   }

   trackById(_: number, c: Conversation): string {
      return c._id ?? c.sessionName;
   }
}
