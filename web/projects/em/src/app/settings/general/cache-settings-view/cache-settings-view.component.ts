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
import { HttpClient } from "@angular/common/http";
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { UntypedFormBuilder, UntypedFormGroup } from "@angular/forms";
import { ContextHelp } from "../../../context-help";
import { Searchable } from "../../../searchable";
import { GeneralSettingsChanges } from "../general-settings-page/general-settings-page.component";
import { GeneralSettingsType } from "../general-settings-page/general-settings-type.enum";
import { CacheSettingsModel } from "./cache-settings-model";

const CLEAN_UP_CACHE_URI = "../api/em/general/settings/cache/cleanup";

@Searchable({
   route: "/settings/general#cache",
   title: "Cache",
   keywords: ["em.settings", "em.settings.general", "em.settings.cache"]
})
@ContextHelp({
   route: "/settings/general#cache",
   link: "EMGeneralCaching"
})
@Component({
   selector: "em-cache-settings-view",
   templateUrl: "./cache-settings-view.component.html",
   styleUrls: ["./cache-settings-view.component.scss"]
})
export class CacheSettingsViewComponent {
   @Output() modelChanged = new EventEmitter<GeneralSettingsChanges>();
   private _model: CacheSettingsModel;
   form: UntypedFormGroup;

   @Input() set model(model: CacheSettingsModel) {
      this._model = model;

      if(this.model) {
         this.initForm();
      }
   }

   get model(): CacheSettingsModel {
      return this._model;
   }

   constructor(private formBuilder: UntypedFormBuilder, private http: HttpClient) {
   }

   initForm() {
      this.form = this.formBuilder.group({
         directory: [this.model.directory],
         cleanUpStartup: [this.model.cleanUpStartup],
      });

      this.form.controls["directory"].valueChanges.subscribe((value: string) => {
         this.model.directory = value.trim();
         this.onModelChanged();
      });

      this.form.controls["cleanUpStartup"].valueChanges.subscribe((value) => {
         this.model.cleanUpStartup = value;
         this.onModelChanged();
      });
   }

   onModelChanged() {
      this.form.updateValueAndValidity();
      this.modelChanged.emit({
         model: this.model,
         modelType: GeneralSettingsType.CACHE_SETTINGS_MODEL,
         valid: this.form.valid
      });
   }

   cleanUp() {
      this.http.get(CLEAN_UP_CACHE_URI).subscribe();
   }
}
