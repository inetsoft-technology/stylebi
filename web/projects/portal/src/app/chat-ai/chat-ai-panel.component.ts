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
   Component,
   ElementRef,
   EventEmitter,
   HostListener,
   Input,
   Output,
   ViewChild,
   AfterViewInit
} from '@angular/core';
import { NgModel } from '@angular/forms';
import { ChatService } from "./ai-chat.service";

interface ChatMessage {
   text: string;
   isUser: boolean;
   timestamp?: Date;
}

@Component({
   selector: 'app-chat-ai-panel',
   templateUrl: './chat-ai-panel.component.html',
   styleUrls: ['./chat-ai-panel.component.scss']
})
export class ChatAiPanelComponent implements AfterViewInit {
   @Input() isOpen: boolean = false;
   @Output() toggle: EventEmitter<void> = new EventEmitter<void>();
   @Input() context: string = "Viewsheet"
   @ViewChild('messageInput') messageInput!: ElementRef<HTMLInputElement>;
   @ViewChild('messageContainer') messageContainer!: ElementRef<HTMLDivElement>;
   @ViewChild('messageInputModel') messageInputModel!: NgModel;

   width = 350;
   currentMessage = '';
   messages: ChatMessage[] = [];
   isLoading = false;

   // Welcome configuration
   welcomeConfig = {
      title: 'AI Assistant',
      subtitle: 'How can I help you today?',
      description: 'Ask me anything about your data or get help with your tasks.',
      examplePrompts: [
         'Show me sales trends for last quarter',
         'Create a dashboard for customer demographics',
         'Explain this data pattern'
      ]
   };

   constructor(private chatService: ChatService) {}

   ngAfterViewInit() {
      if (this.isOpen) {
         this.focusInput();
      }
   }

   closePanel() {
      this.isOpen = false;
      this.toggle.emit();
   }

   // Message handling methods
   sendMessage() {
      if (!this.currentMessage.trim() || this.isLoading) return;

      const userMessage = this.currentMessage.trim();
      this.currentMessage = '';

      this.addMessage(userMessage, true);
      this.scrollToBottom();

      this.isLoading = true;
      this.simulateAIResponse(userMessage);
   }

   private simulateAIResponse(userMessage: string) {
      this.isLoading = true;

      this.chatService.sendMessage(userMessage, this.context).subscribe({
         next: (response) => {
            console.log(response);
            this.addMessage(response.answer, false);
         },
         error: (error) => {
            console.error('API Error:', error);
            this.addMessage("Sorry, I couldn't get a response.", false);
         },
         complete: () => {
            this.isLoading = false;
            this.scrollToBottom();
            this.focusInput();
         }
      });
   }

   private generateAIResponse(userMessage: string): string {
      // This would be replaced with actual API call
      const responses = [
         `I've analyzed your request about "${userMessage}". Here's what I found...`,
         `Based on the data, here's the information about "${userMessage}"...`,
         `I can help you with "${userMessage}". Let me generate a report...`
      ];
      return responses[Math.floor(Math.random() * responses.length)];
   }

   private addMessage(text: string, isUser: boolean) {
      this.messages.push({
         text,
         isUser,
         timestamp: new Date()
      });
   }

   private addWelcomeMessage() {
      this.addMessage(
         `${this.welcomeConfig.title}\n${this.welcomeConfig.subtitle}\n${this.welcomeConfig.description}`,
         false
      );
   }

   // UI helper methods
   focusInput() {
      this.messageInput?.nativeElement.focus();
   }

   scrollToBottom() {
      setTimeout(() => {
         if (this.messageContainer) {
            this.messageContainer.nativeElement.scrollTop =
               this.messageContainer.nativeElement.scrollHeight;
         }
      }, 50);
   }

   // Resize panel methods
   private isResizing = false;
   private startX = 0;
   private startWidth = 0;

   @HostListener('document:mousemove', ['$event'])
   onMouseMove(event: MouseEvent) {
      if (!this.isResizing) return;

      const deltaX = event.clientX - this.startX;
      this.width = Math.max(250, Math.min(600, this.startWidth - deltaX));
   }

   @HostListener('document:mouseup')
   onMouseUp() {
      this.isResizing = false;
   }

   startResize(event: MouseEvent) {
      this.isResizing = true;
      this.startX = event.clientX;
      this.startWidth = this.width;
      event.preventDefault();
   }

   trackByMessage(index: number, message: ChatMessage): string {
      return `${message.text}-${message.timestamp?.getTime()}`;
   }
}