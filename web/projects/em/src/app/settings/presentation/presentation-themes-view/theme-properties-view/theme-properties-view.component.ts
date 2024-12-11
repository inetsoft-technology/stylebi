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
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from "@angular/core";
import { AbstractControl, UntypedFormBuilder, UntypedFormGroup, Validators } from "@angular/forms";
import { interval, Subject } from "rxjs";
import { debounce, takeUntil } from "rxjs/operators";
import { FormValidators } from "../../../../../../../shared/util/form-validators";
import { FileData } from "../../../../../../../shared/util/model/file-data";
import { CustomThemeModel } from "../custom-theme-model";
import { ThemePropertiesModel } from "./theme-properties-model";

@Component({
   selector: "em-theme-properties-view",
   templateUrl: "./theme-properties-view.component.html",
   styleUrls: ["./theme-properties-view.component.scss"]
})
export class ThemePropertiesViewComponent implements OnInit, OnDestroy {
   @Input() isMultiTenant = false;
   @Input() orgId: string;

   @Input() get theme() {
      return this._theme;
   }

   set theme(value: CustomThemeModel) {
      this._theme = value;

      if(!!value) {
         const jar = value.jar ? [value.jar] : [];
         this.form.get("name").setValue(value.name, { emitEvent: false });
         this.form.get("defaultThemeGlobal").setValue(value.defaultThemeGlobal, { emitEvent: false });
         this.form.get("defaultThemeOrg").setValue(value.defaultThemeOrg, { emitEvent: false });
         this.form.get("globalTheme").setValue(value.global, { emitEvent: false });
         this.form.get("jar").setValue(jar, { emitEvent: false });

         if(!this.isMultiTenant || this.isSiteAdmin) {
            this.form.enable({ emitEvent: false });
            this.form.get("globalTheme").enable({ emitEvent: false });
            this.form.get("defaultThemeGlobal").enable({ emitEvent: false });
         }
         else {
            if(value.global) {
               this.form.disable({ emitEvent: false });
            }
            else {
               this.form.enable();
               this.form.get("globalTheme").disable({ emitEvent: false });
               this.form.get("defaultThemeGlobal").disable({ emitEvent: false });
            }
         }

         this.form.get("defaultThemeOrg").enable({ emitEvent: false });
      }
   }

   @Input() get themeNames(): string[] {
      return this._themeNames;
   }

   set themeNames(value: string[]) {
      this._themeNames = value;

      if(!!value && this.form) {
         this.form.get("name").setValidators([Validators.required,
            FormValidators.duplicateName(() => this._themeNames)]);
         this.form.get("name").updateValueAndValidity();
      }
   }

   @Input() get isSiteAdmin(): boolean {
      return this._isSiteAdmin;
   }

   set isSiteAdmin(isSiteAdmin: boolean) {
      this._isSiteAdmin = isSiteAdmin;

      if(!!this.form) {
         if(this.theme.global && !isSiteAdmin) {
            this.form.disable();
         }
         else {
            this.form.enable();
         }

         if(!this.isMultiTenant || isSiteAdmin) {
            this.form.get("globalTheme").enable();
            this.form.get("defaultThemeGlobal").enable();
         }
         else {
            this.form.get("globalTheme").disable();
            this.form.get("defaultThemeGlobal").disable();
         }

         this.form.get("defaultThemeOrg").enable();
      }
   }

   @Output() themePropertiesChanged = new EventEmitter<ThemePropertiesModel>();

   form: UntypedFormGroup;
   private _themeNames: string[] = [];
   private _theme: CustomThemeModel;
   private _isSiteAdmin = false;
   private destroy$ = new Subject<void>();

   constructor(fb: UntypedFormBuilder) {
      this.form = fb.group({
         name: ["", [Validators.required, FormValidators.duplicateName(() => this.themeNames)]],
         defaultThemeOrg: [false],
         defaultThemeGlobal: [{value: false, disabled: !this.isSiteAdmin}],
         globalTheme: [{value: false, disabled: !this.isSiteAdmin}],
         jar: [[]]
      });

      if(!this.isSiteAdmin && this.theme?.global) {
         this.form.disable();
      }
      else {
         this.form.enable();
      }
   }

   ngOnInit(): void {
      setTimeout(() => {
         this.form.valueChanges
            .pipe(
               takeUntil(this.destroy$)
            )
            .subscribe(() => this.fireThemePropertiesChanged());
      }, 200);
   }

   ngOnDestroy(): void {
      this.destroy$.next();
      this.destroy$.unsubscribe();
   }

   get defaultThemeGlobalLable(): string {
      return this.isMultiTenant ? "_#(Default for All Organizations)" : "_#(Default)";
   }

   get hostOrg() {
      return this.orgId == "host-org";
   }

   private fireThemePropertiesChanged(): void {
      this.themePropertiesChanged.emit({
         id: this.theme.id,
         name: this.form.get("name").value,
         defaultThemeGlobal: this.form.get("defaultThemeGlobal").value,
         defaultThemeOrg: this.form.get("defaultThemeOrg").value,
         globalTheme: this.form.get("globalTheme").value,
         jar: this.getFile(this.form.get("jar")),
         valid: this.form.valid
      });
   }

   private getFile(control: AbstractControl): FileData {
      return control.value?.length ? control.value[0] : null;
   }
}
