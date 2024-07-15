/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { Tool } from "../../../../../shared/util/tool";
import { ConsoleMessage } from "./console-message";
import { ModelService } from "../services/model.service";
import { SaveConsoleMessageLevelsEvent } from "../../composer/gui/ws/socket/save-console-message-levels-event";

const SAVE_MESSAGE_LEVELS_URI = "../api/composer/console-dialog/save-message-levels/";

@Component({
   selector: "console-dialog",
   templateUrl: "./console-dialog.component.html",
   styleUrls: ["./console-dialog.component.scss"]
})
export class ConsoleDialogComponent implements OnInit {
   @Input() runtimeId: string;
   @Input() messageLevels: string[] = [];
   @Input() messages: ConsoleMessage[];
   @Output() messagesChange = new EventEmitter<ConsoleMessage[]>();
   @Output() onClose = new EventEmitter<ConsoleMessage[]>();
   @Output() onCommit = new EventEmitter<string[]>();
   levelOptions = ["_#(js:Error)", "_#(js:Warning)", "_#(js:Info)"];
   selectedLevels: string[] = [];

   constructor(private modelService: ModelService) {
   }

   ngOnInit() {
      this.selectedLevels = Tool.clone(this.messageLevels);

      if(!this.selectedLevels) {
         this.selectedLevels = ["Error", "Warning", "Info"];
      }

      if(!this.messages) {
         this.messages = [];
      }

   }

   get visibleMessages(): ConsoleMessage[] {
      return this.messages.filter(message => {
         return this.selectedLevels.findIndex(level => Tool.equalsIgnoreCase(level, message.type)) >= 0;
      });
   }

   getLevelButtonLabel(): string {
      return this.selectedLevels.length == this.levelOptions.length ? "_#(js:All levels)" : "_#(js:Custom levels)";
   }

   getLevelCounter(level: string): number {
      return this.messages.filter(message => Tool.equalsIgnoreCase(message.type, level)).length;
   }

   getLevelIcon(level: string): string {
      if(level == this.levelOptions[0]) {
         return "message-error-icon";
      }
      else if(level == this.levelOptions[1]) {
         return "message-warning-icon";
      }
      else {
         return "annotation-icon";
      }
   }

   isSelected(level: string): boolean {
      return this.selectedLevels.indexOf(level) >= 0;
   }

   levelChanged(event: any, type: string): void {
      if(type == "all") {
         this.selectedLevels = [];

         if(event.target.checked) {
            this.levelOptions.forEach(level => this.selectedLevels.push(level));
         }
      }
      else {
         if(event.target.checked) {
            this.selectedLevels.push(event.target.value);
         }
         else {
            this.selectedLevels = this.selectedLevels.filter(level => level != event.target.value);
         }
      }
   }

   closeDialog(): void {
      this.onClose.emit(this.messages);
   }

   clearMessages(): void {
      this.messagesChange.emit([]);
   }

   ok(): void {
      let model = new SaveConsoleMessageLevelsEvent(this.selectedLevels);
      this.modelService.sendModel(SAVE_MESSAGE_LEVELS_URI + Tool.byteEncodeURLComponent(this.runtimeId), model)
         .subscribe((res: any) => {
            if(res.body) {
               this.messageLevels = Tool.clone(this.selectedLevels);
               this.onCommit.emit(this.messageLevels);
            }
            else {
               this.onCommit.emit(this.messageLevels);
            }
         });
   }
}
