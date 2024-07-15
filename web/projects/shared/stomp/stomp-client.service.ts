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
import { Injectable, NgZone } from "@angular/core";
import { Observable } from "rxjs";
import { SsoHeartbeatService } from "../sso/sso-heartbeat.service";
import { LogoutService } from "../util/logout.service";
import { StompClient } from "./stomp-client";
import { StompClientConnection } from "./stomp-client-connection";
import { BaseHrefService } from "../../portal/src/app/common/services/base-href.service";

/**
 * Service that encapsulates the communication with a server via the STOMP protocol over
 * websockets.
 */
@Injectable({
   providedIn: "root"
})
export class StompClientService {
   private clients: Map<string, StompClient> = new Map<string, StompClient>();
   private _reloadOnFailure: boolean = false;

   constructor(private zone: NgZone, private ssoHeartbeatService: SsoHeartbeatService,
               private logoutService: LogoutService, private baseHrefService: BaseHrefService)
   {
   }

   connect(endpoint: string, em: boolean = false, customElement: boolean = false): Observable<StompClientConnection> {
      return this.zone.runOutsideAngular(() => {
         let client = this.clients.get(endpoint);

         if(!client) {
            client = new StompClient(
               endpoint, (key) => this.onDisconnect(key), this.ssoHeartbeatService,
               this.logoutService, em, this.baseHrefService.getBaseHref(), customElement);
            this.clients.set(endpoint, client);
         }

         return client.connect();
      });
   }

   public set reloadOnFailure(reload: boolean) {
      this._reloadOnFailure = reload;
      this.clients.forEach(v => v.reloadOnFailure = reload);
   }

   private onDisconnect(endpoint: string) {
      this.clients.delete(endpoint);
   }
}
