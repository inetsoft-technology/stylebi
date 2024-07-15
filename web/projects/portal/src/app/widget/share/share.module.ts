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
import {CommonModule} from "@angular/common";
import {NgModule} from "@angular/core";
import {ShareEmailDialogComponent} from "./share-email-dialog.component";
import {ShareGoogleChatDialog} from "./share-google-chat-dialog.component";
import {ShareLinkDialog} from "./share-link-dialog.component";
import {ShareSlackDialog} from "./share-slack-dialog.component";
import {WidgetDirectivesModule} from "../directive/widget-directives.module";
import {ModalHeaderModule} from "../modal-header/modal-header.module";
import {FormsModule, ReactiveFormsModule} from "@angular/forms";
import {NotificationsModule} from "../notifications/notifications.module";
import {ClipboardModule} from "ngx-clipboard";
import {EmailDialogModule} from "../email-dialog/email-dialog.module";

@NgModule({
   imports: [
      CommonModule,
      WidgetDirectivesModule,
      ModalHeaderModule,
      ReactiveFormsModule,
      NotificationsModule,
      ClipboardModule,
      FormsModule,
      EmailDialogModule,
   ],
   declarations: [
      ShareEmailDialogComponent,
      ShareGoogleChatDialog,
      ShareLinkDialog,
      ShareSlackDialog
   ],
   exports: [
      ShareEmailDialogComponent,
      ShareGoogleChatDialog,
      ShareLinkDialog,
      ShareSlackDialog
   ],
   providers: [],
})
export class ShareModule {
}
