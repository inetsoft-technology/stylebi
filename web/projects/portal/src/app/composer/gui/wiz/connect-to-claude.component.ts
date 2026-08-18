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
import { Component, Input, NgZone, OnChanges, OnDestroy, SimpleChanges } from "@angular/core";
import { NgIf } from "@angular/common";
import { ClipboardModule } from "ngx-clipboard";
import { Subscription } from "rxjs";
import { take } from "rxjs/operators";
import { StompClientConnection } from "../../../../../../shared/stomp/stomp-client-connection";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { EditorContext } from "./editor-context";

@Component({
   selector: "wiz-connect-to-claude",
   templateUrl: "./connect-to-claude.component.html",
   standalone: true,
   imports: [NgIf, ClipboardModule]
})
export class ConnectToClaudeComponent implements OnChanges, OnDestroy {
   @Input() runtimeId!: string;
   @Input() sheetType!: "WORKSHEET" | "VIEWSHEET";
   @Input() socketConnection!: ViewsheetClientService;
   /** Scopes the pairing session to a single script/formula location rather
    *  than the whole sheet. Omitted from the mint payload entirely (not sent
    *  as null) when absent, e.g. a whole-sheet toolbar mint. */
   @Input() editorContext?: EditorContext;

   code: string | null = null;
   loading = false;
   error: string | null = null;
   copied = false;

   private mintSubscription: Subscription | null = null;
   /**
    * The `editorContext` that was actually SENT with the mint that produced `code` -- captured
    * when the code comes back, and what `detach()` sends.
    *
    * Whole-branch review finding 2: `detach()` used to send `this.editorContext`, the CURRENT
    * value, and the server matches a session by record equality on that field. Several hosts
    * derive `editorContext` from mutable state -- `viewsheet-script-pane` follows the
    * onInit/onLoad radio, `formula-editor-dialog` includes the editable `formulaName` -- so a
    * user who pairs on Init, clicks Load to compare, then cancels detached `viewsheetOnLoad`,
    * matched nothing, and left the `viewsheetOnInit` session live for its full TTL with the
    * editor gone. `socketClosed` does not help: the composer socket is still up.
    *
    * Null until a code has been minted; `detach()` then falls back to the current value, which is
    * correct because with no minted code there is no session of ours to end.
    */
   private mintedEditorContext: EditorContext | null = null;

   constructor(private zone: NgZone) {}

   ngOnChanges(changes: SimpleChanges): void {
      if(changes["runtimeId"] && !changes["runtimeId"].firstChange) {
         this.code = null;
         this.error = null;
         this.loading = false;
         this.copied = false;
         this.mintedEditorContext = null;
         if(this.mintSubscription) {
            this.mintSubscription.unsubscribe();
            this.mintSubscription = null;
         }
      }
   }

   requestCode(): void {
      this.loading = true;
      this.code = null;
      this.error = null;
      this.copied = false;

      // Read ONCE, here, and carry this exact value through to the response handler: the getters
      // that supply it are live, so re-reading it later (in detach, or even in this same
      // subscribe callback) can yield a context the server never saw. See mintedEditorContext.
      const requestedContext = this.editorContext;

      this.socketConnection.whenConnected().pipe(take(1)).subscribe((conn: StompClientConnection) => {
         const sub = conn.subscribe("/user/commands/wiz/pairing/mint", (msg: any) => {
            sub.unsubscribe();
            this.mintSubscription = null;
            this.zone.run(() => {
               this.loading = false;

               try {
                  const body = JSON.parse(msg.frame.body);

                  if(body.code) {
                     this.code = body.code;
                     this.mintedEditorContext = requestedContext ?? null;
                  }
                  else {
                     this.error = body.error ?? "Failed to generate pairing code";
                  }
               }
               catch(e) {
                  this.error = "Failed to generate pairing code";
               }
            });
         });
         this.mintSubscription = sub;

         const payload: any = { runtimeId: this.runtimeId, sheetType: this.sheetType };

         if(requestedContext) {
            payload.editorContext = requestedContext;
         }

         conn.send("/events/wiz/pairing/mint", {}, JSON.stringify(payload));
      });
   }

   onCopySuccess(): void {
      this.copied = true;
      setTimeout(() => {
         this.zone.run(() => {
            this.copied = false;
         });
      }, 2000);
   }

   onCopyError(): void {
      this.error = "Could not copy to clipboard — please copy the code manually.";
   }

   /**
    * Ends any pane-scoped session paired from this exact location. Called explicitly by the
    * script pane / formula editor that hosts this component from their own `ngOnDestroy`, so a
    * pane session dies with its editor -- a user who pairs then cancels or closes the dialog
    * does not leave a live write handle behind.
    *
    * A whole-sheet ("Connect to Claude" toolbar) mint has no `editorContext` and must never
    * reach this endpoint -- its session is TTL-only by design, so this is a no-op there.
    */
   detach(): void {
      // The context the session was MINTED with, not whatever the host's live getter says now --
      // see mintedEditorContext. Falls back to the current value only when nothing was ever
      // minted from this component.
      const context = this.mintedEditorContext ?? this.editorContext;

      if(!context || !this.socketConnection) {
         return;
      }

      this.socketConnection.whenConnected().pipe(take(1)).subscribe((conn: StompClientConnection) => {
         conn.send("/events/wiz/pairing/detach", {},
                   JSON.stringify({ editorContext: context }));
      });
   }

   ngOnDestroy(): void {
      if(this.mintSubscription) {
         this.mintSubscription.unsubscribe();
         this.mintSubscription = null;
      }
   }
}
