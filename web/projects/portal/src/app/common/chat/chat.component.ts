/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Component, Input, OnChanges, OnDestroy, SimpleChanges } from "@angular/core";
import { ChatApi } from "./chat-api";
import { ChatService } from "./chat.service";
import { Subscription } from "rxjs";

/**
 * !!! Duplicated in agile/sr12_3 !!!
 */
@Component({
   selector: "chat-widget",
   templateUrl: "./chat.component.html",
   styleUrls: ["./chat.component.scss"]
})
export class ChatComponent implements OnChanges, OnDestroy {
   @Input() name: string;
   @Input() email = "";
   public chatApi: ChatApi;
   public subscription = Subscription.EMPTY;

   constructor(private chatService: ChatService) {
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes.name || changes.email) {
         this.subscription.unsubscribe();
         this.subscription = this.chatService.connect(this.name, this.email)
            .subscribe((chat) => this.chatApi = chat);
      }
   }

   ngOnDestroy() {
      this.subscription.unsubscribe();
   }
}
