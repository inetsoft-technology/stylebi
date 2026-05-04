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
   ChangeDetectionStrategy, Component, EventEmitter, Input, Output, ViewChild, ElementRef
} from "@angular/core";
import { Retrievals } from "../assistant-models";

const THINK_MODES = [
   { value: null, label: "_#(Normal)" },
   { value: "think", label: "_#(Think)" },
   { value: "deepthink", label: "_#(chat.ai.thinkModeDeepThink)" }
];

@Component({
   selector: "assistant-input-area",
   templateUrl: "./assistant-input-area.component.html",
   styleUrls: ["./assistant-input-area.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class AssistantInputAreaComponent {
   @Input() isGenerating: boolean = false;
   @Input() thinkMode: string | null = null;
   @Input() retrievals: Retrievals = {
      agentic: false, hyde: false, stepback: false,
      rewrite_expansion: false, rewrite_decomposition_expansion: false
   };

   @Output() send = new EventEmitter<string>();
   @Output() cancel = new EventEmitter<void>();
   @Output() thinkModeChange = new EventEmitter<string | null>();
   @Output() retrievalsChange = new EventEmitter<Retrievals>();

   @ViewChild("textarea") textareaRef!: ElementRef<HTMLTextAreaElement>;

   inputText: string = "";
   showThinkMenu: boolean = false;
   showRetrievalMenu: boolean = false;
   readonly thinkModes = THINK_MODES;

   get currentThinkLabel(): string {
      return THINK_MODES.find(m => m.value === this.thinkMode)?.label ?? "Normal";
   }

   onTextareaInput(): void {
      const el = this.textareaRef?.nativeElement;

      if(el) {
         el.style.height = "auto";
         el.style.height = `${Math.min(el.scrollHeight, 160)}px`;
      }
   }

   onKeydown(event: KeyboardEvent): void {
      if(event.key === "Enter" && !event.shiftKey) {
         event.preventDefault();
         this.onSend();
      }
   }

   onSend(): void {
      const text = this.inputText.trim();

      if(!text || this.isGenerating) {
         return;
      }

      this.inputText = "";
      const el = this.textareaRef?.nativeElement;

      if(el) {
         el.style.height = "auto";
      }

      this.send.emit(text);
   }

   setThinkMode(value: string | null): void {
      this.thinkMode = value;
      this.thinkModeChange.emit(value);
      this.showThinkMenu = false;
   }

   toggleRetrieval(key: keyof Retrievals): void {
      const updated = { ...this.retrievals, [key]: !this.retrievals[key] };
      this.retrievalsChange.emit(updated);
   }

   closeMenus(): void {
      this.showThinkMenu = false;
      this.showRetrievalMenu = false;
   }
}
