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
import { Component, Input, Output, EventEmitter, ViewChildren, ElementRef,
         AfterViewInit, QueryList } from "@angular/core";

/**
 * Component that display a simple message or confirmation dialog.
 */
@Component({
   selector: "message-dialog",
   templateUrl: "message-dialog.component.html",
   styleUrls: ["message-dialog.component.scss"]
})
export class MessageDialog implements AfterViewInit {
   @Input() title = "";
   @Input() message = "";
   @Input() options: DialogOption[] = [];
   @Input() showProgress = false;
   @Input() expandValues: string[] = [];
   @Output() onCommit = new EventEmitter<string>();
   @ViewChildren("btn") buttons: QueryList<ElementRef>;

   static lastMessage: string;
   static lastMessageTS: number = 0;

   ngAfterViewInit() {
      if(this.buttons && this.buttons.first) {
         this.buttons.first.nativeElement.focus();
      }
   }

   clickButton(symbol: string) {
      this.onCommit.emit(symbol);
   }

   errorSource(): string {
      let url: string = "assets/error.png";
      return url;
   }
}

export interface DialogOption {
   symbol: string;
   label: string;
}
