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
import { CommonModule } from "@angular/common";
import { NgModule } from "@angular/core";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatAutocompleteModule } from "@angular/material/autocomplete";
import { MatBottomSheetModule } from "@angular/material/bottom-sheet";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatDialogModule } from "@angular/material/dialog";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatListModule } from "@angular/material/list";
import { MatProgressSpinnerModule } from "@angular/material/progress-spinner";
import { MatSelectModule } from "@angular/material/select";
import { MatSnackBarModule } from "@angular/material/snack-bar";
import { MatTabsModule } from "@angular/material/tabs";
import { EditorPanelModule } from "../../../common/util/editor-panel/editor-panel.module";
import { ErrorHandlerService } from "../../../common/util/error/error-handler.service";
import { MessageDialogModule } from "../../../common/util/message-dialog.module";
import { ModalHeaderModule } from "../../../common/util/modal-header/modal-header.module";
import { SecurityListViewComponent } from "../security-list-view/security-list-view.component";
import { SharedLayoutModule } from "../shared-layout/shared-layout.module";
import { AuthenticationProviderDetailPageComponent } from "./authentication-provider-detail-page/authentication-provider-detail-page.component";
import { AuthenticationProviderDetailViewComponent } from "./authentication-provider-detail-view/authentication-provider-detail-view.component";
import { AuthenticationProviderViewComponent } from "./authentication-provider-list-page/authentication-provider-list-page.component";
import { AuthorizationProviderDetailPageComponent } from "./authorization-provider-detail-page/authorization-provider-detail-page.component";
import { AuthorizationProviderDetailViewComponent } from "./authorization-provider-detail-view/authorization-provider-detail-view.component";
import { AuthorizationProviderListPageComponent } from "./authorization-provider-list-page/authorization-provider-list-page.component";
import { BaseQueryResult } from "./base-query-result/base-query-result.component";
import { CustomProviderViewComponent } from "./custom-provider-view/custom-provider-view.component";
import { DatabaseProviderViewComponent } from "./database-provider-view/database-provider-view.component";
import { QueryItemViewComponent } from "./database-provider-view/query-item-view/query-item-view.component";
import { InputQueryParamsDialogComponent } from "./input-query-params-dialog/input-query-params-dialog.component";
import { LdapProviderViewComponent, LDAPQueryResult } from "./ldap-provider-view/ldap-provider-view.component";
import { SecurityProviderPageComponent } from "./security-provider-page/security-provider-page.component";
import { SecurityProviderRoutingModule } from "./security-provider-routing.module";
import { SecurityProviderViewComponent } from "./security-provider-view/security-provider-view.component";
import { SecurityProviderService } from "./security-provider.service";
import { SystemAdminRolesDialogComponent } from "./system-admin-roles-dialog/system-admin-roles-dialog.component";
import { FeatureFlagsModule } from "../../../../../../shared/feature-flags/feature-flags.module";

@NgModule({
   imports: [
      MatBottomSheetModule,
      SecurityProviderRoutingModule,
      CommonModule,
      FormsModule,
      ReactiveFormsModule,
      MatAutocompleteModule,
      MatButtonModule,
      MatCardModule,
      MatCheckboxModule,
      MatDialogModule,
      MatInputModule,
      MatListModule,
      MatIconModule,
      MatProgressSpinnerModule,
      MatSelectModule,
      MatSnackBarModule,
      MatTabsModule,
      MessageDialogModule,
      EditorPanelModule,
      SharedLayoutModule,
      ModalHeaderModule,
      FeatureFlagsModule
   ],
   exports: [
      SystemAdminRolesDialogComponent
   ],
   declarations: [
      AuthenticationProviderDetailPageComponent,
      AuthenticationProviderDetailViewComponent,
      AuthenticationProviderViewComponent,
      AuthorizationProviderDetailPageComponent,
      AuthorizationProviderDetailViewComponent,
      AuthorizationProviderListPageComponent,
      CustomProviderViewComponent,
      DatabaseProviderViewComponent,
      LdapProviderViewComponent,
      LDAPQueryResult,
      SecurityListViewComponent,
      SecurityProviderPageComponent,
      SecurityProviderViewComponent,
      SystemAdminRolesDialogComponent,
      QueryItemViewComponent,
      BaseQueryResult,
      InputQueryParamsDialogComponent
   ],
   providers: [
      SecurityProviderService,
      ErrorHandlerService
   ]
})
export class SecurityProviderModule {
}
