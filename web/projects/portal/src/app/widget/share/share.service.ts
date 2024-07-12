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
import { HttpClient } from "@angular/common/http";
import { HttpParams } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { Observable } from "rxjs";
import { convertKeyToID } from "../../../../../em/src/app/settings/security/users/identity-id";
import { createAssetEntry } from "../../../../../shared/data/asset-entry";
import { GuiTool } from "../../common/util/gui-tool";
import { ShareConfig } from "./share-config";
import { ShareEmailModel } from "./share-email-model";

declare const window: any;

@Injectable({
   providedIn: "root"
})
export class ShareService {
   constructor(private http: HttpClient) {
   }

   getConfig(): Observable<ShareConfig> {
      let params = new HttpParams().set("temp", (new Date()).getTime() + "");
      return this.http.get<ShareConfig>("../api/share/config", {params: params});
   }

   getEmailModel(): Observable<ShareEmailModel> {
      return this.http.get<ShareEmailModel>("../api/share/email");
   }

   shareViewsheetInEmail(viewsheetId: string, recipients: string[], subject: string,
                         message: string, ccs?: string[], bccs?: string[]): Observable<void>
   {
      const link = this.getViewsheetLink(viewsheetId);
      const body = { viewsheetId, link, recipients, subject, message, ccs, bccs };
      return this.http.post<void>("../api/share/email", body);
   }

   shareViewsheetOnFacebook(viewsheetId: string): void {
      const link = this.getViewsheetLink(viewsheetId);
      this.shareOnFacebook(link);
   }

   private shareOnFacebook(link: string) {
      const url = `https://www.facebook.com/sharer.php?display=popup&u=${link}`;
      const options = "toolbar=0,status=0,resizable=1,width=626,height=436";
      window.open(url, "share_facebook", options);
   }

   shareViewsheetInGoogleChat(viewsheetId: string, message: string): Observable<void> {
      const link = this.getViewsheetLink(viewsheetId);
      const body = { viewsheetId, link, message };
      return this.http.post<void>("../api/share/google-chat", body);
   }

   shareViewsheetOnLinkedIn(viewsheetId: string): void {
      const link = this.getViewsheetLink(viewsheetId);
      this.shareOnLinkedIn(link);
   }

   private shareOnLinkedIn(link: string): void {
      const url = `https://www.linkedin.com/sharing/share-offsite/?url=${link}`;
      const options = "toolbar=0,status=0,resizable=1,width=626,height=436";
      window.open(url, "share_linkedin", options);
   }

   shareViewsheetInSlack(viewsheetId: string, message: string): Observable<void> {
      const link = this.getViewsheetLink(viewsheetId);
      const body = { viewsheetId, link, message };
      return this.http.post<void>("../api/share/slack", body);
   }

   shareViewsheetOnTwitter(viewsheetId: string, title: string): void {
      const link = encodeURIComponent(this.getViewsheetLink(viewsheetId));
      this.shareOnTwitter(link, title);
   }

   private shareOnTwitter(link: string, title: string): void {
      const url = `https://twitter.com/intent/tweet?text=${title}&url=${link}`;
      const options = "toolbar=0,status=0,resizable=1,width=626,height=436";
      window.open(url, "share_twitter", options);
   }

   getViewsheetLink(viewsheetId: string): string {
      const entry = createAssetEntry(viewsheetId);
      let path = "";

      if(entry.scope === 1) {
         path += "global/";
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
