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
   @Input() emails: string = "";
   @Input() users = [];
   @Input() groups = [];
   @Input() burst: boolean;
   @Output() notificationsChanged = new EventEmitter<NotificationEmails>();
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
