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
import { CommonModule } from "@angular/common";
import { NgModule } from "@angular/core";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbNavModule, NgbTypeaheadModule } from "@ng-bootstrap/ng-bootstrap";
import { CkeditorWrapperComponent } from "../../../../../shared/ckeditor-wrapper/ckeditor-wrapper.component";

import { AssetTreeModule } from "../asset-tree/asset-tree.module";

import { IdentityTreeModule } from "../identity-tree/identity-tree.module";
import { ModalHeaderModule } from "../modal-header/modal-header.module";


import { EmailAddrDialog } from "./email-addr-dialog.component";
import { EmailPane } from "./email-pane.component";
import { EmbeddedEmailPane } from "./embedded-email-pane.component";
import { QueryEmailPane } from "./query-email-pane.component";

@NgModule({
    imports: [
    CommonModule,
    ModalHeaderModule,
    NgbNavModule,
    ReactiveFormsModule,
    NgbTypeaheadModule,
    FormsModule,
    IdentityTreeModule,
    AssetTreeModule,
    CkeditorWrapperComponent,
    EmailAddrDialog,
    EmailPane,
    EmbeddedEmailPane,
    QueryEmailPane,
],
    exports: [
        EmailAddrDialog,
        EmailPane,
        EmbeddedEmailPane,
        QueryEmailPane
    ],
    providers: [],
})
export class EmailDialogModule {
}
