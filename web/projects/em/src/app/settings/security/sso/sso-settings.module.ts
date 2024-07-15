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
import { MatAutocompleteModule } from "@angular/material/autocomplete";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatChipsModule } from "@angular/material/chips";
import { MatOptionModule } from "@angular/material/core";
import { MatDialogModule } from "@angular/material/dialog";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatRadioModule } from "@angular/material/radio";
import { MatSelectModule } from "@angular/material/select";
import { FeatureFlagsModule } from "../../../../../../shared/feature-flags/feature-flags.module";
import { EditorPanelModule } from "../../../common/util/editor-panel/editor-panel.module";
import { MessageDialogModule } from "../../../common/util/message-dialog.module";
import { CustomSsoFormComponent } from "./custom-sso-form/custom-sso-form.component";
import { OpenidSettingsFormComponent } from "./openid-settings-form/openid-settings-form.component";
import { SSOSettingsFormComponent } from "./sso-settings-form/sso-settings-form.component";
import { SsoSettingsPageComponent } from "./sso-settings-page/sso-settings-page.component";
import { SsoSettingsRoutingModule } from "./sso-settings-routing.module";

@NgModule({
   declarations: [
      SsoSettingsPageComponent,
      SSOSettingsFormComponent,
      CustomSsoFormComponent,
      OpenidSettingsFormComponent
   ],
   imports: [
      CommonModule,
      EditorPanelModule,
      FormsModule,
      MatCardModule,
      MatDialogModule,
      MatFormFieldModule,
      MatInputModule,
      MatRadioModule,
      MatSelectModule,
      MatOptionModule,
      ReactiveFormsModule,
      SsoSettingsRoutingModule,
      MatButtonModule,
      MatIconModule,
      MatCheckboxModule,
      FeatureFlagsModule,
      MatAutocompleteModule,
      MessageDialogModule,
      MatChipsModule
   ]
})
export class SSOSettingsModule {
}
