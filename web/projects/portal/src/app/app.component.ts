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
import { DOCUMENT } from "@angular/common";
import {
   AfterViewInit,
   Component,
   Inject,
   NgZone,
   OnDestroy,
   OnInit,
   ViewChild
} from "@angular/core";
import { NavigationEnd, Router } from "@angular/router";
import { NgbModal, NgbModalRef } from "@ng-bootstrap/ng-bootstrap";
import { Subscription } from "rxjs";
import { SsoHeartbeatDispatcherService } from "../../../shared/sso/sso-heartbeat-dispatcher.service";
import { StompClientConnection } from "../../../shared/stomp/stomp-client-connection";
import { StompClientService } from "../../../shared/stomp/stomp-client.service";
import { NotificationsComponent } from "./widget/notifications/notifications.component";
import { GettingStartedService } from "./widget/dialog/getting-started-dialog/service/getting-started.service";
import { FeatureFlagsService, FeatureFlagValue } from "../../../shared/feature-flags/feature-flags.service";

declare const window: any;

interface NotificationMessage {
   message: string;
}

@Component({
   selector: "app-root",
   templateUrl: "./app.component.html",
   styleUrls: ["./app.component.scss"]
})
export class AppComponent implements OnInit, OnDestroy {
   @ViewChild("notifications") notifications: NotificationsComponent;
   loading = true;
   notificationMessage: string = "";
   notificationDialog: NgbModalRef;

   private windowListener: EventListener;
   private subscription: Subscription = new Subscription();
   private connection: StompClientConnection;

   constructor(private router: Router, private stompClient: StompClientService,
               private modalService: NgbModal, private zone: NgZone,
               @Inject(DOCUMENT) document: Document,
               private ssoHeartbeatDispatcher: SsoHeartbeatDispatcherService)
   {
      const subscription = router.events.subscribe((e) => {
         if(e instanceof NavigationEnd) {
            this.loading = false;
            subscription.unsubscribe();
         }
      });

      if(document && document.body && window && window.navigator && window.navigator.userAgent &&
         window.navigator.userAgent.indexOf("JavaFX") >= 0)
      {
         document.body.classList.add("javafx");
      }
   }

   ngOnInit(): void {
      this.windowListener = (event: MessageEvent) => {
         if(!event.data) {
            return;
         }

         if(event.data.event === "navigate") {
            this.router.navigate([event.data.url]);
         }
      };

      window.addEventListener("message", this.windowListener, false);

      this.stompClient.connect("../vs-events").subscribe(connection => {
         this.connection = connection;
         this.subscription.add(connection.subscribe(
            "/notifications",
            (message) => this.zone.run(() => this.notify(JSON.parse(message.frame.body)))));
         this.subscription.add(connection.subscribe(
            "/user/notifications",
            (message) => this.zone.run(() => this.notify(JSON.parse(message.frame.body)))));
      });

      this.ssoHeartbeatDispatcher.dispatch();
   }

   ngOnDestroy(): void {
      window.removeEventListener("message", this.windowListener);

      if(this.subscription) {
         this.subscription.unsubscribe();
         this.subscription = null;
      }

      if(this.connection) {
         this.connection.disconnect();
         this.connection = null;
      }
   }

   private notify(message: NotificationMessage): void {
      // if it's the exact same message as already displayed then return
      if(this.notificationMessage === message.message) {
         return;
      }

      if(this.notificationMessage) {
         this.notificationMessage += "\n" + message.message;
      }
      else {
         this.notificationMessage = message.message;
      }

      this.notifications.info(this.notificationMessage);
   }

   closeNotificationDialog() {
      this.notificationDialog.close();
      this.notificationMessage = "";
   }
}
