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
import { Component, Input, NgZone, OnDestroy } from "@angular/core";
import { NgIf } from "@angular/common";
import { Subscription } from "rxjs";
import { take } from "rxjs/operators";
import { StompClientConnection } from "../../../../../../shared/stomp/stomp-client-connection";
import { ViewsheetClientService } from "../../../common/viewsheet-client";

@Component({
   selector: "wiz-connect-to-claude",
   templateUrl: "./connect-to-claude.component.html",
   standalone: true,
   imports: [NgIf]
})
export class ConnectToClaudeComponent implements OnDestroy {
   @Input() runtimeId!: string;
   @Input() sheetType!: "WORKSHEET" | "VIEWSHEET";
   @Input() socketConnection!: ViewsheetClientService;

   code: string | null = null;
   loading = false;
   error: string | null = null;
   copied = false;

   private mintSubscription: Subscription | null = null;

   constructor(private zone: NgZone) {}

   requestCode(): void {
      this.loading = true;
      this.code = null;
      this.error = null;
      this.copied = false;

      this.socketConnection.whenConnected().pipe(take(1)).subscribe((conn: StompClientConnection) => {
         const sub = conn.subscribe("/user/queue/wiz/pairing/mint", (msg: any) => {
            sub.unsubscribe();
            this.mintSubscription = null;
            const body = JSON.parse(msg.frame.body);
            this.zone.run(() => {
               this.loading = false;
               if(body.code) {
                  this.code = body.code;
               }
               else {
                  this.error = body.error ?? "Failed to generate pairing code";
               }
            });
         });
         this.mintSubscription = sub;

         conn.send("/app/wiz/pairing/mint", {}, JSON.stringify({
            runtimeId: this.runtimeId,
            sheetType: this.sheetType
         }));
      });
   }

   copyCode(): void {
      navigator.clipboard.writeText(this.code!).then(() => {
         this.zone.run(() => {
            this.copied = true;
            setTimeout(() => {
               this.zone.run(() => {
                  this.copied = false;
               });
            }, 2000);
         });
      });
   }

   ngOnDestroy(): void {
      if(this.mintSubscription) {
         this.mintSubscription.unsubscribe();
         this.mintSubscription = null;
      }
   }
}
