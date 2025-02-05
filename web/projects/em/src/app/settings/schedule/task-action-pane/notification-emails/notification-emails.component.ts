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
import { UntypedFormControl } from "@angular/forms";

export interface NotificationEmails {
   valid: boolean;
   enabled: boolean;
   emails: string;
   notifyIfFailed: boolean;
   notifyLink: boolean;
}

@Component({
   selector: "em-notification-emails",
   templateUrl: "./notification-emails.component.html",
   styleUrls: ["./notification-emails.component.scss"]
})
export class NotificationEmailsComponent implements OnInit {
   @Input() enabled: boolean = false;
   @Input() notifyIfFailed: boolean = false;
   @Input() notifyLink: boolean = false;
   @Input() emailBrowserEnabled: boolean;
   @Input() users = [];
   @Input() groups = [];
   @Input() burst: boolean;
   @Output() notificationsChanged = new EventEmitter<NotificationEmails>();
   _emails: string = "";

   @Input()
   get emails(): string {
      return this._emails;
   }

   set emails(val: string) {
      if(!!this.emails || !!val) {
         this._emails = val;
         this.emailControl.setValue(this._emails, { emitEvent: false });
      }
   }

   emailControl = new UntypedFormControl();

   constructor() {
   }

   ngOnInit() {
      if(this.emails) {
         this.emailControl.setValue(this.emails);
      }

      this.emailControl.valueChanges.subscribe(() => this.fireNotificationsChanged());
   }

   fireNotificationsChanged() {
      let emails = this.emailControl.value;

      if(!!emails) {
         emails = emails.replace(/\s+/g, "");
      }

      this.notificationsChanged.emit({
         valid: !this.enabled || (this.emailControl.value != null && this.emailControl.value !== 0 && this.emailControl.valid),
         enabled: this.enabled,
         emails: emails,
         notifyIfFailed: this.notifyIfFailed,
         notifyLink: this.notifyLink
      });
   }
}
