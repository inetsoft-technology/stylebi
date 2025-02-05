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

import { Component, EventEmitter, HostListener, Input, OnDestroy, Output } from "@angular/core";
import { DateTypeFormatter } from "../../../../../../shared/util/date-type-formatter";

@Component({
   selector: "session-expiration-dialog",
   templateUrl: "session-expiration-dialog.component.html",
   styleUrls: ["session-expiration-dialog.component.scss"]
})
export class SessionExpirationDialog implements OnDestroy {
   @Input() nodeProtection: boolean;
   @Output() onCommit: EventEmitter<string> = new EventEmitter<string>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @Output() onLogout: EventEmitter<void> = new EventEmitter<void>();
   @Output() onTimerFinished: EventEmitter<void> = new EventEmitter<void>();
   private interval: any;
   private _remainingTime: number;
   duration: string;

   set remainingTime(value: number) {
      this._remainingTime = value;
      this.duration = DateTypeFormatter.formatDuration(this._remainingTime, "mm:ss");

      if(this.interval != null) {
         clearInterval(this.interval);
      }

      this.interval = setInterval(() => {
         this._remainingTime = Math.max(0, this._remainingTime - 1000);
         this.duration = DateTypeFormatter.formatDuration(this._remainingTime, "mm:ss");

         if(this._remainingTime == 0) {
            clearInterval(this.interval);
            this.interval = null;
            this.onTimerFinished.emit();
         }
      }, 1000);
   }

   constructor() {
   }

   closeDialog(): void {
      this.onCancel.emit("cancel");
   }

   stayLoggedInClicked(): void {
      this.onCommit.emit("ok");
   }

   logoutClicked(): void {
      this.onLogout.emit();
   }

   ngOnDestroy(): void {
      if(this.interval != null) {
         clearInterval(this.interval);
         this.interval = null;
      }
   }

   // to prevent mousedown event from being propagated to the document when it originates on
   // this dialog and being counted as user activity in session-inactivity.service.ts
   @HostListener("mousedown", ["$event"])
   mousedown(event: MouseEvent): void {
      event.stopPropagation();
      event.stopImmediatePropagation();
      event.preventDefault();
   }

   @HostListener("wheel", ["$event"])
   wheel(event: WheelEvent): void {
      event.stopPropagation();
      event.stopImmediatePropagation();
      event.preventDefault();
   }
}