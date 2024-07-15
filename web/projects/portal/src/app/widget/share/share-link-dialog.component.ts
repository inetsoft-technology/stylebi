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
import { Component, EventEmitter, Input, OnInit, Output, ViewChild } from "@angular/core";
import { ShareService } from "./share.service";
import { NotificationsComponent } from "../notifications/notifications.component";

@Component({
   selector: "share-link-dialog",
   templateUrl: "share-link-dialog.component.html",
   styleUrls: ["share-link-dialog.component.scss"]
})
export class ShareLinkDialog implements OnInit {
   @Input() viewsheetId: string;
   @Input() archive: boolean = false;
   @Input() archiveParameters: string;
   @Output() onCommit = new EventEmitter<void>();
   @Output() onCancel = new EventEmitter<void>();
   @ViewChild("notifications") notifications: NotificationsComponent;
   link: string;
   iframe: string;
   copied: boolean = false;

   constructor(private shareService: ShareService) {
   }

   ngOnInit(): void {
      if(this.viewsheetId) {
         this.link = this.shareService.getViewsheetLink(this.viewsheetId);
      }

      this.iframe = `<iframe src="${this.link}"></iframe>`;
   }

   enter() {
      return () => this.ok();
   }

   showNotification() {
      this.notifications.success("_#(js:em.settings.share.linkCopied)");
   }

   ok(): void {
      this.onCommit.emit();
   }

   cancel(): void {
      this.onCancel.emit();
   }
}
