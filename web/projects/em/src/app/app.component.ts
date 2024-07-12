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
import { MatDialog } from "@angular/material/dialog";
import { Subscription } from "rxjs";
import { CkeditorLanguageService } from "../../../shared/ckeditor/ckeditor-language.service";
import { SsoHeartbeatDispatcherService } from "../../../shared/sso/sso-heartbeat-dispatcher.service";
import { StompClientConnection } from "../../../shared/stomp/stomp-client-connection";
import { StompClientService } from "../../../shared/stomp/stomp-client.service";
import { AuthorizationService } from "./authorization/authorization.service";
import { ComponentPermissions } from "./authorization/component-permissions";
import { TopScrollService } from "./top-scroll/top-scroll.service";

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

   constructor(private http: HttpClient, private authzService: AuthorizationService,
               private stompClient: StompClientService, private zone: NgZone,
               private dialog: MatDialog, private breakpointObserver: BreakpointObserver,
               private scrollService: TopScrollService,
               private ssoHeartbeatDispatcher: SsoHeartbeatDispatcherService,
               languageService: CkeditorLanguageService,
               public viewContainerRef: ViewContainerRef)
   {
      // viewContainerRef is used by the color picker in the theme page
      // Make sure to pre-fetch the language so there isn't a race condition later when it is used.
      languageService.getLanguage().subscribe();
   }

   ngOnInit(): void {
      this.http.get("../api/em/security/get-current-user").subscribe(userModel => {
         this.name = userModel["name"]?.name;
      });

      this.authzService.getPermissions("").subscribe(p => this.permissions = p);

      this.stompClient.connect("../vs-events", true).subscribe(connection => {
         this.connection = connection;
         this.subscription.add(connection.subscribe(
            "/notifications",
            (message) => this.zone.run(() => this.notify(JSON.parse(message.frame.body)))));
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

   private notify(notification: any): void {
      this.notificationMessage = notification.message;
      this.dialog.open(this.notificationDialog, { width: "350px" });
   }
}
