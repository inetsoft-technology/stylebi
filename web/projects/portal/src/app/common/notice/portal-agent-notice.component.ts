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
import { Component, OnDestroy, OnInit } from "@angular/core";
import { HttpParams } from "@angular/common/http";
import { NgbAlert } from "@ng-bootstrap/ng-bootstrap";
import { Subscription } from "rxjs";
import { createAssetEntry } from "../../../../../shared/data/asset-entry";
import { OpenComposerAssetCommand } from "../../composer/command/open-composer-asset-command";
import { CrossSheetFollowService } from "../../composer/gui/wiz/services/cross-sheet-follow.service";
import { GuiTool } from "../util/gui-tool";
import { PortalAgentNoticeService } from "../services/portal-agent-notice.service";

const AUTO_DISMISS_MILLIS = 15000;

interface Notice {
   text: string;
   /** null for an unsaved/blank asset (create_worksheet's own no-path case) -- there is nothing
    *  to deep-link to yet, see PortalAgentNoticeService's own class doc / D4's documented
    *  limitation, so the notice renders text-only, no click action. */
   openParams: HttpParams | null;
}

/**
 * Small, always-mounted banner: "Claude opened or created an asset, click to open" (D4). Mounted
 * once in the portal shell (`AppComponent`), alongside `<notifications>`, so it renders regardless
 * of which portal page is currently showing -- including when the Composer app is not open in
 * this tab at all (the case `ComposerClientService`/`composer-main` cannot cover, since both are
 * scoped to the Composer app's own component tree).
 *
 * <p>The click handler calls {@link GuiTool.openBrowserTab} SYNCHRONOUSLY, directly inside the
 * `(click)` event binding -- confirmed from source that `openBrowserTab` is a plain
 * `window.open(url, target)` call, which popup blockers kill unless it runs directly inside a
 * user-gesture handler. The STOMP message that populates `notice` arrives asynchronously (via
 * {@link PortalAgentNoticeService}), but only ever SETS component state there; navigation itself
 * only ever happens from `open()`, called by the template's own `(click)`.
 */
@Component({
   selector: "portal-agent-notice",
   templateUrl: "./portal-agent-notice.component.html",
   styleUrls: ["./portal-agent-notice.component.scss"],
   imports: [NgbAlert]
})
export class PortalAgentNoticeComponent implements OnInit, OnDestroy {
   notice: Notice | null = null;

   private subscription: Subscription;
   private dismissTimer: ReturnType<typeof setTimeout>;

   constructor(private noticeService: PortalAgentNoticeService,
               private crossSheetFollowService: CrossSheetFollowService)
   {
   }

   ngOnInit(): void {
      this.noticeService.connect();
      this.subscription = this.noticeService.assetOpened.subscribe(
         (command) => this.showNotice(command));
   }

   ngOnDestroy(): void {
      this.subscription?.unsubscribe();
      clearTimeout(this.dismissTimer);
   }

   /** Gates the cross-sheet-follow toggle (Lane C / D9) -- see
    *  {@link PortalAgentNoticeService#portalSessionActive}'s own doc for why this, and not the
    *  transient {@link notice} field, is the right signal for a *persistent* toggle. */
   get portalSessionActive(): boolean {
      return this.noticeService.portalSessionActive;
   }

   get crossSheetFollowEnabled(): boolean {
      return this.crossSheetFollowService.isEnabled();
   }

   /** Bound to the checkbox's `[disabled]` -- see {@link CrossSheetFollowService#pending}'s own
    *  doc for why this is defense-in-depth, not the actual correctness fix, for overlapping
    *  toggles. */
   get crossSheetFollowPending(): boolean {
      return this.crossSheetFollowService.pending;
   }

   onCrossSheetFollowChange(event: Event): void {
      this.crossSheetFollowService.setEnabled((event.target as HTMLInputElement).checked);
   }

   /** Bound to the template's own `(click)` -- see this class's own doc on why this must be the
    *  ONLY place `openBrowserTab` is ever called from. */
   open(): void {
      if(!this.notice?.openParams) {
         return;
      }

      GuiTool.openBrowserTab("composer", this.notice.openParams);
      this.dismiss();
   }

   dismiss(): void {
      this.notice = null;
      clearTimeout(this.dismissTimer);
   }

   private showNotice(command: OpenComposerAssetCommand): void {
      const entry = command.assetId ? createAssetEntry(command.assetId) : null;
      const label = entry?.path ?? `a new, unsaved ${command.viewsheet ? "viewsheet" : "worksheet"}`;
      const openParams = command.assetId
         ? new HttpParams().set(command.viewsheet ? "vsId" : "wsId", command.assetId)
         : null;

      this.notice = {
         text: `Claude ${command.assetId ? "opened" : "created"} ${label} in Visual Composer.`,
         openParams
      };

      clearTimeout(this.dismissTimer);
      this.dismissTimer = setTimeout(() => this.dismiss(), AUTO_DISMISS_MILLIS);
   }
}
