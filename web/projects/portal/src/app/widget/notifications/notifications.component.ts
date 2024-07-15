/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { ChangeDetectorRef, Component, Input, OnInit } from "@angular/core";
import { Notification, NotificationType } from "../../common/data/notification";
import { Tool } from "../../../../../shared/util/tool";

/**
 * Component that displays notification messages to the user.
 */
@Component({
   selector: "notifications", // eslint-disable-line @angular-eslint/component-selector
   templateUrl: "notifications.component.html",
   styleUrls: ["notifications.component.scss"]
})
export class NotificationsComponent implements OnInit {
   /* Optional timeout for notifications. */
   @Input() timeout: number = 0;
   @Input() message: string = "";
   @Input() fullWidth: boolean = true;
   alerts: ({id: number} & Notification)[] = [];
   private counter: number = 0;

   constructor(private changeDetectionRef: ChangeDetectorRef) {
   }

   ngOnInit() {
      if(this.message) {
         this.info(this.message);
      }
   }

   /**
    * Displays a success message.
    *
    * @param message the message text.
    */
   public success(message: string): void {
      this.addAlert(message, "success");
   }

   /**
    * Displays an information message.
    *
    * @param message the message text.
    */
   public info(message: string): void {
      message = Tool.getLimitedMessage(message);
      this.addAlert(message, "info");
   }

   /**
    * Displays a warning message.
    *
    * @param message the message text.
    */
   public warning(message: string): void {
      this.addAlert(message, "warning");
   }

   /**
    * Displays a danger message.
    *
    * @param message the message text.
    */
   public danger(message: string): void {
      this.addAlert(message, "danger");
   }

   private alertShowing(message: string): boolean {
      return this.alerts.map(a => a.message).indexOf(message) >= 0;
   }

   private addAlert(message: string, type: NotificationType): void {
      // ignore duplicates
      if(this.alertShowing(message)) {
         return;
      }

      const alert = {
         id: this.counter++,
         type: type,
         message: message
      };
      this.alerts.push(alert);

      if(this.timeout > 0) {
         setTimeout(this.closeAlert.bind(this), this.timeout, alert);
      }
   }

   closeAlert(alert: any): void {
      const index: number = this.alerts.indexOf(alert);

      if(index >= 0) {
         this.alerts.splice(index, 1);
      }

      if(!this.changeDetectionRef["destroyed"]) {
         this.changeDetectionRef.detectChanges();
      }
   }
}
