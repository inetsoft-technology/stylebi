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

import { Component, EventEmitter, HostListener, Input, Output } from '@angular/core';

@Component({
   selector: 'app-chat-ai-panel',
   templateUrl: './chat-ai-panel.component.html',
   styleUrls: ['./chat-ai-panel.component.scss']
})
export class ChatAiPanelComponent {
   @Input() isOpen = false;
   @Output() toggle = new EventEmitter<void>();

   width = 300;
   private isResizing = false;
   private startX = 0;
   private startWidth = 0;

   @HostListener('document:mousemove', ['$event'])
   onMouseMove(event: MouseEvent) {
      if (!this.isResizing) return;

      const deltaX = event.clientX - this.startX;
      this.width = Math.max(200, Math.min(500, this.startWidth - deltaX)); // 限制在200-500px之间
   }

   @HostListener('document:mouseup')
   onMouseUp() {
      this.isResizing = false;
   }

   startResize(event: MouseEvent) {
      this.isResizing = true;
      this.startX = event.clientX;
      this.startWidth = this.width;
      event.preventDefault(); // 防止文本选中
   }

   closePanel() {
      this.toggle.emit();
   }
}