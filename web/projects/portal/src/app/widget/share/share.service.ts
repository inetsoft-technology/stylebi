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
import { HttpClient } from "@angular/common/http";
import { HttpParams } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { Observable } from "rxjs";
import { map, switchMap, take } from "rxjs/operators";
import { convertKeyToID } from "../../../../../em/src/app/settings/security/users/identity-id";
import { createAssetEntry } from "../../../../../shared/data/asset-entry";
import { AppInfoService } from "../../../../../shared/util/app-info.service";
import { CommonKVModel } from "../../common/data/common-kv-model";
import { GuiTool } from "../../common/util/gui-tool";
import { ShareConfig } from "./share-config";
import { ShareEmailModel } from "./share-email-model";

declare const window: any;

@Injectable({
   providedIn: "root"
})
export class ShareService {
   // Snapshotted once for this root-singleton's lifetime; getCurrentOrgInfo() re-fetching per
   // subscription (#76996) doesn't help this field, since it's only ever subscribed once, below.
   private orgInfo: CommonKVModel<string, string> = null;

   constructor(private http: HttpClient, private appInfoService: AppInfoService) {
      this.appInfoService.getCurrentOrgInfo().subscribe((orgInfo) => {
         this.orgInfo = orgInfo;
      })
   }

   getConfig(orgId: string): Observable<ShareConfig> {
      let params = new HttpParams().set("temp", (new Date()).getTime() + "");

      if(orgId) {
         params = params.set("orgId", orgId);
      }

      return this.http.get<ShareConfig>("../api/share/config", {params: params});
   }

   getEmailModel(): Observable<ShareEmailModel> {
      return this.http.get<ShareEmailModel>("../api/share/email");
   }

   shareViewsheetInEmail(viewsheetId: string, recipients: string[], subject: string,
                         message: string, ccs?: string[], bccs?: string[]): Observable<void>
   {
      return this.getViewsheetLinkAsync(viewsheetId).pipe(switchMap((link) => {
         const body = { viewsheetId, link, recipients, subject, message, ccs, bccs };
         return this.http.post<void>("../api/share/email", body);
      }));
   }

   shareViewsheetOnFacebook(viewsheetId: string): void {
      // Kept synchronous (reads the last-known org info rather than awaiting it) so the
      // window.open() below stays in the same call stack as the user click; browsers treat an
      // async-triggered window.open() as a popup and block it.
      const link = this.getViewsheetLink(viewsheetId);
      this.shareOnFacebook(link);
   }

   private shareOnFacebook(link: string) {
      const url = `https://www.facebook.com/sharer.php?display=popup&u=${link}`;
      const options = "toolbar=0,status=0,resizable=1,width=626,height=436";
      window.open(url, "share_facebook", options);
   }

   shareViewsheetInGoogleChat(viewsheetId: string, message: string): Observable<void> {
      return this.getViewsheetLinkAsync(viewsheetId).pipe(switchMap((link) => {
         const body = { viewsheetId, link, message };
         return this.http.post<void>("../api/share/google-chat", body);
      }));
   }

   shareViewsheetOnLinkedIn(viewsheetId: string): void {
      // See comment in shareViewsheetOnFacebook() re: keeping window.open() synchronous.
      const link = this.getViewsheetLink(viewsheetId);
      this.shareOnLinkedIn(link);
   }

   private shareOnLinkedIn(link: string): void {
      const url = `https://www.linkedin.com/sharing/share-offsite/?url=${link}`;
      const options = "toolbar=0,status=0,resizable=1,width=626,height=436";
      window.open(url, "share_linkedin", options);
   }

   shareViewsheetInSlack(viewsheetId: string, message: string): Observable<void> {
      return this.getViewsheetLinkAsync(viewsheetId).pipe(switchMap((link) => {
         const body = { viewsheetId, link, message };
         return this.http.post<void>("../api/share/slack", body);
      }));
   }

   shareViewsheetOnTwitter(viewsheetId: string, title: string): void {
      // See comment in shareViewsheetOnFacebook() re: keeping window.open() synchronous.
      const link = encodeURIComponent(this.getViewsheetLink(viewsheetId));
      this.shareOnTwitter(link, title);
   }

   private shareOnTwitter(link: string, title: string): void {
      const url = `https://twitter.com/intent/tweet?text=${title}&url=${link}`;
      const options = "toolbar=0,status=0,resizable=1,width=626,height=436";
      window.open(url, "share_twitter", options);
   }

   /**
    * Builds the link from whatever org info is cached so far. Only safe to call once org info
    * has actually resolved (see getViewsheetLinkAsync()) or from a context that must stay
    * synchronous (e.g. immediately before window.open(), to avoid popup blockers) and can
    * tolerate the rare case where org info hasn't loaded yet.
    *
    * Known limitation (#76996): unlike getViewsheetLinkAsync(), this reads `this.orgInfo`, a
    * value cached once at ShareService construction, so it won't see an org change mid-session.
    * Accepted since that scenario has no reachable UI path today; a fresh-per-call fetch here
    * would reintroduce the popup-blocker issue the synchronous design avoids.
    */
   getViewsheetLink(viewsheetId: string): string {
      return this.buildViewsheetLink(viewsheetId, this.orgInfo);
   }

   /**
    * Same as getViewsheetLink(), but waits for AppInfoService's org info to resolve to a real
    * value before building the link, instead of racing ahead on a possibly-still-null value.
    * Use this whenever the caller doesn't need to stay synchronous.
    */
   getViewsheetLinkAsync(viewsheetId: string): Observable<string> {
      return this.appInfoService.getCurrentOrgInfo().pipe(
         take(1),
         map((orgInfo) => this.buildViewsheetLink(viewsheetId, orgInfo))
      );
   }

   private buildViewsheetLink(viewsheetId: string,
                              orgInfo: CommonKVModel<string, string>): string
   {
      const entry = createAssetEntry(viewsheetId);
      let path = "";
      let currentOrgId = orgInfo?.key;
      let sharedGlobal = !!currentOrgId && currentOrgId !== "host-org" && viewsheetId.endsWith("host-org");

      if(entry.scope === 1) {
         path += sharedGlobal ? "shared_global/" : "global/";
      }
      else {
         path += "user/" + convertKeyToID(entry.user).name + "/";
      }

      path += entry.path;
      const link = "viewer/view/" + this.encodePath(path);
      return GuiTool.resolveUrl(link);
   }

   private encodePath(path: string): string {
      return path.split("/").map(part => encodeURIComponent(part)).join("/");
   }
}