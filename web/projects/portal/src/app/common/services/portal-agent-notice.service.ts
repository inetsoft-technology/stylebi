/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
import { Subject, Observable } from "rxjs";
import { StompClientConnection } from "../../../../../shared/stomp/stomp-client-connection";
import { StompClientService } from "../../../../../shared/stomp/stomp-client.service";
import { OpenComposerAssetCommand } from "../../composer/command/open-composer-asset-command";

/**
 * Portal-wide notice that a `wiz` agent (Claude) just opened or created a Composer asset --
 * `create_viewsheet`/`create_worksheet`/`open_composer_asset` (portal-session-pairing Lane B, D4)
 * -- fired regardless of which entry point (a pane-scoped session or a directly-established
 * portal session) produced the acting session, and regardless of whether the Composer app
 * (`composer/app.component.ts`) happens to be open in this tab at all.
 *
 * <p>{@link ComposerClientService} (`composer/gui/composer-client.service.ts`) already receives
 * the exact same {@link OpenComposerAssetCommand} broadcast, over the exact same
 * `/user/composer-client` STOMP destination -- but it is `@Injectable()` with no
 * `providedIn: "root"`, i.e. provided at `composer-main`'s own component scope, so a portal-shell
 * component genuinely cannot inject "the same instance" the way {@link AppComponent} already does
 * with the confirmed-root {@link StompClientService} for `/notifications`,
 * `/user/session-expiration`, etc. (see that component's own `ngOnInit`).
 *
 * <p>This service instead subscribes to `/user/composer-client` directly, over the SAME shared
 * root STOMP transport -- {@link StompClientService} keys its underlying `StompClient` by
 * endpoint (`"../vs-events"`) and reference-counts it (see `StompClient.connect`/
 * `onConnectionDisconnect`), so calling `connect("../vs-events")` here reuses whatever connection
 * any other root/component-scoped caller already opened, or opens its own if none exists yet --
 * either way, no new WebSocket per subscriber. Multiple independent STOMP subscriptions to the
 * same destination on the same connection each receive their own copy of every published message
 * (ordinary STOMP pub/sub fan-out), so this and {@link ComposerClientService} coexist without
 * interfering: when `composer-main` is mounted, both react (its own tab switch, and this
 * service's own toast, redundant-but-harmless); when it is not, only this service's toast fires.
 */
@Injectable({
   providedIn: "root"
})
export class PortalAgentNoticeService {
   private connection: StompClientConnection;
   private connecting = false;
   private readonly commandSubject = new Subject<OpenComposerAssetCommand>();
   private portalSessionActiveFlag = false;

   constructor(private stompClient: StompClientService, private zone: NgZone) {
   }

   /**
    * Starts listening. Idempotent -- safe to call from every consumer's own `ngOnInit` (there is
    * currently exactly one, {@link PortalAgentNoticeComponent}, but nothing here assumes that).
    */
   public connect(): void {
      if(this.connecting || this.connection) {
         return;
      }

      this.connecting = true;
      this.stompClient.connect("../vs-events").subscribe(
         (connection) => {
            this.connecting = false;
            this.connection = connection;
            connection.subscribe("/user/composer-client", (message) => {
               this.zone.run(() => {
                  if(message.frame.body) {
                     this.portalSessionActiveFlag = true;
                     this.commandSubject.next(JSON.parse(message.frame.body));
                  }
               });
            });
         },
         (error: any) => {
            this.connecting = false;
            console.error("PortalAgentNoticeService failed to connect: ", error);
         }
      );
   }

   /** Every `OpenComposerAssetCommand` broadcast to this browser's own socket session. */
   public get assetOpened(): Observable<OpenComposerAssetCommand> {
      return this.commandSubject.asObservable();
   }

   /**
    * Whether a portal-derived (directly-established) session is known to exist in this browser
    * tab -- `true` from the first `assetOpened` broadcast onward, never cleared again. This is the
    * only signal the browser has: D10's login-triggered establish (portal-session-pairing Lane D)
    * is a pure JWT/server-side act with no browser round-trip of its own, so there is nothing to
    * observe until the first `open_composer_asset`/`create_viewsheet`/`create_worksheet` call
    * succeeds and pushes this same `/user/composer-client` notice. Gates whether
    * {@link PortalAgentNoticeComponent}'s cross-sheet-follow toggle (Lane C / D9) renders at all --
    * see that component's own template.
    */
   public get portalSessionActive(): boolean {
      return this.portalSessionActiveFlag;
   }
}
