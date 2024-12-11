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
import { Component, EventEmitter, Input, OnInit, Output, ViewChild } from "@angular/core";
import { MatTabChangeEvent } from "@angular/material/tabs";
import { Tool } from "../../../../../../../shared/util/tool";
import { AuthenticationProviderModel } from "../../../security/security-provider/security-provider-model/authentication-provider-model";
import { CustomThemeModel } from "../custom-theme-model";
import { ThemeCssEditorModel } from "../theme-css-view/theme-css-editor-model";
import { ThemeCssViewComponent } from "../theme-css-view/theme-css-view.component";
import { ThemePropertiesModel } from "../theme-properties-view/theme-properties-model";

@Component({
   selector: "em-theme-editor-view",
   templateUrl: "./theme-editor-view.component.html",
   styleUrls: ["./theme-editor-view.component.scss"]
})
export class ThemeEditorViewComponent implements OnInit {
   @Input() get theme(): CustomThemeModel {
      return this._theme;
   }

   set theme(value: CustomThemeModel) {
      this._theme = value;
   }

   @Input() themeNames: string[] = [];
   @Input() smallDevice: boolean = false;
   @Input() isSiteAdmin: boolean = false;
   @Input() orgId: string;

   @Input() get themeModified(): boolean {
      return this._themeModified;
   }

   set themeModified(value: boolean) {
      this._themeModified = value;

      if(!value) {
         this.propertiesValid = true;
         this.cssValid = true;
      }
   }

   @Output() themeChanged = new EventEmitter<CustomThemeModel>();
   @Output() themeSaved = new EventEmitter<CustomThemeModel>();
   @Output() themeReset = new EventEmitter<void>();
   @Output() cancel = new EventEmitter<void>();
   @ViewChild("cssView", { static: false }) cssView: ThemeCssViewComponent;

   selectedTab = 0;
   selectedTabLabel: string;
   isMultiTenant: boolean;

   private propertiesValid = true;
   private cssValid = true;
   private _themeModified = false;
   private _theme: CustomThemeModel;

   get themeValid(): boolean {
      return this.themeModified && this.propertiesValid && this.cssValid;
   }

   constructor(private http: HttpClient) {
   }

   ngOnInit(): void {
      this.http.get<boolean>("../api/em/navbar/isMultiTenant")
         .subscribe(isMultiTenant => {
            this.isMultiTenant = isMultiTenant;
         });
   }

   onSelectedTabChanged(event: MatTabChangeEvent): void {
      this.selectedTab = event.index;
      this.selectedTabLabel = event.tab.textLabel;
   }

   onThemePropertiesChanged(model: ThemePropertiesModel): void {
      if(!!model && !!this.theme) {
         this.propertiesValid = model.valid;

         this.themeChanged.emit({
            id: this.theme.id,
            name: model.name,
            global: model.globalTheme,
            defaultThemeGlobal: model.defaultThemeGlobal,
            defaultThemeOrg: model.defaultThemeOrg,
            jar: model.jar,
            portalCss: this.theme?.portalCss,
            emCss: this.theme?.emCss
         });
      }
   }

   onThemeCssChanged(model: ThemeCssEditorModel): void {
      if(!!model && !!this.theme) {
         this.cssValid = model.valid;

         this.themeChanged.emit({
            id: this.theme.id,
            name: this.theme.name,
            global: this.theme.global,
            defaultThemeGlobal: this.theme.defaultThemeGlobal,
            defaultThemeOrg: this.theme.defaultThemeOrg,
            jar: this.theme.jar,
            portalCss:  model.portalCss,
            emCss: model.emCss
         });
      }
   }

   clearCss(): void {
      if(!!this.cssView) {
         this.cssView.clear();
      }
   }

   reset(): void {
      if(this.smallDevice) {
         this.cancel.emit();
      }

      this.themeReset.emit();
   }
}
