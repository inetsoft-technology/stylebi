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

import { BreakpointObserver } from "@angular/cdk/layout";
import { HttpClient } from "@angular/common/http";
import {
   Component,
   NgZone,
   OnDestroy,
   OnInit,
   TemplateRef,
   ViewChild,
   ViewContainerRef
} from "@angular/core";
import { MatDialog, MatDialogRef } from "@angular/material/dialog";
import { Subscription } from "rxjs";
import { SsoHeartbeatDispatcherService } from "../../../shared/sso/sso-heartbeat-dispatcher.service";
import { StompClientConnection } from "../../../shared/stomp/stomp-client-connection";
import { StompClientService } from "../../../shared/stomp/stomp-client.service";
import { LogoutService } from "../../../shared/util/logout.service";
import { SessionExpirationModel } from "../../../shared/util/model/session-expiration-model";
import { CloudLicenseState } from "../../../shared/util/security/cloud-license-state";
import { AuthorizationService } from "./authorization/authorization.service";
import { ComponentPermissions } from "./authorization/component-permissions";
import { TopScrollService } from "./top-scroll/top-scroll.service";
import { SessionExpirationDialog } from "./widget/dialog/session-expiration-dialog/session-expiration-dialog.component";

@Component({
   selector: "em-root",
   templateUrl: "./app.component.html",
   styleUrls: ["./app.component.scss"]
})
export class AppComponent implements OnInit, OnDestroy {
   @ViewChild("notificationDialog", { static: true }) notificationDialog: TemplateRef<any>;

   permissions: ComponentPermissions;
   name: string;
   notificationMessage: string;
   scrollDirection: "up" | "down" = "up";
   navbarTransitioning = false;

   private subscription = new Subscription();
   private connection: StompClientConnection;
   private smallDevice = false;
   private sessionExpirationDialog: MatDialogRef<SessionExpirationDialog>;
   private protectionExpirationDialog: MatDialogRef<SessionExpirationDialog>;

   constructor(private http: HttpClient, private authzService: AuthorizationService,
               private stompClient: StompClientService, private zone: NgZone,
               private dialog: MatDialog, private breakpointObserver: BreakpointObserver,
               private scrollService: TopScrollService,
               private ssoHeartbeatDispatcher: SsoHeartbeatDispatcherService,
               public viewContainerRef: ViewContainerRef,
               private logoutService: LogoutService)
   {
      // viewContainerRef is used by the color picker in the theme page
   }

   ngOnInit(): void {
      this.http.get("../api/em/security/get-current-user").subscribe(userModel => {
         this.name = userModel["name"]?.name;
      });

      this.authzService.getPermissions("").subscribe(p => this.permissions = p);

      console.log("=======")
      this.stompClient.connect("../vs-events", true).subscribe(connection => {
         this.connection = connection;
         this.subscription.add(connection.subscribe(
            "/notifications",
            (message) => this.zone.run(() => this.notify(JSON.parse(message.frame.body)))));
         this.subscription.add(connection.subscribe(
            "/user/session-expiration",
            (message) => this.zone.run(
               () => this.showExpirationDialog(JSON.parse(message.frame.body)))));
         this.subscription.add(connection.subscribe(
            "/user/license-changed",
            (message) => this.zone.run(
               () => this.processCloudLicenseStateChange(JSON.parse(message.frame.body)))));
         this.subscription.add(connection.subscribe(
            "/user/create-org-status-changed",
            (message) => this.zone.run(
               () => this.showEditOrgMessage(JSON.parse(message.frame.body)))));
      });

      this.subscription.add(this.breakpointObserver
         .observe("(min-width: 0) and (max-width: 720px)")
         .subscribe(state => {
            if(state.matches) {
               this.smallDevice = true;
            }
            else {
               this.smallDevice = false;
               this.scrollDirection = "up";
            }
         }));

      this.subscription.add(this.scrollService.onScroll.subscribe(direction => {
         if(!this.navbarTransitioning && this.smallDevice && this.scrollDirection !== direction) {
            this.scrollDirection = direction;
         }
      }));

      this.ssoHeartbeatDispatcher.dispatch();
   }

   ngOnDestroy(): void {
      this.subscription.unsubscribe();

      if(this.connection) {
         this.connection.disconnect();
         this.connection = null;
      }
   }

   onVisibilityChanged(visibility: boolean): void {
      this.scrollService.visible = visibility;
   }

   downloadStarted(url: string): void {
      // TODO: display a message of some kind
   }

   notify(notification: any, width?: string): void {
      this.notificationMessage = notification.message;
      this.dialog.open(this.notificationDialog, { width: !!width ? width : "350px" });
   }

   private showExpirationDialog(model: SessionExpirationModel): void {
      let expirationDialog = model.nodeProtection ?
         this.protectionExpirationDialog : this.sessionExpirationDialog;

      if(expirationDialog) {
         // if dialog is open and session was refreshed then close the dialog
         if(!model.expiringSoon) {
            expirationDialog.close(false);
         }

         return;
      }

      if(!model.expiringSoon) {
         return;
      }

      expirationDialog = this.dialog.open(SessionExpirationDialog, {
         width: "500px",
         data: {
            remainingTime: model.remainingTime,
            nodeProtection: model.nodeProtection
         }
      });

      this.setExpirationDialog(expirationDialog, model.nodeProtection);
      expirationDialog.afterClosed().subscribe(value => {
         if(value) {
            this.connection.send("/user/session/refresh", null, null);
         }

         this.setExpirationDialog(null, model.nodeProtection);
      });

      expirationDialog.componentInstance.onLogout.subscribe(() => {
         this.logoutService.logout(false, true);
      });

      expirationDialog.componentInstance.onTimerFinished.subscribe(() => {
         this.logoutService.logout(false, true);
      });
   }

   private setExpirationDialog(dialog: MatDialogRef<SessionExpirationDialog>, nodeProtection: boolean) {
      if(nodeProtection) {
         this.protectionExpirationDialog = dialog;
      }
      else {
         this.sessionExpirationDialog = dialog;
      }
   }

   private processCloudLicenseStateChange(state: CloudLicenseState): void {
      if(state.terminating) {
         this.notify({ message: "_#(js:common.sessionHoursTerminating)" })
      }

      this.logoutService.setInGracePeriod(state.gracePeriod);
   }

   private showEditOrgMessage(message: any) {
      if(!!message) {
         this.notify({message: message},  "600px");
      }
   }
}
