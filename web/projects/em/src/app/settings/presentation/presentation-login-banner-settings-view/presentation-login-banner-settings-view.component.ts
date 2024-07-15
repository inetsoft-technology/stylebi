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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { PresentationSettingsChanges } from "../presentation-settings-view/presentation-settings-view.component";
import { UntypedFormBuilder, UntypedFormGroup } from "@angular/forms";
import { PresentationLoginBannerSettingsModel } from "./presentation-login-banner-settings-model";
import { Searchable } from "../../../searchable";
import { PresentationSettingsType } from "../presentation-settings-view/presentation-settings-type.enum";
import { ContextHelp } from "../../../context-help";

@Searchable({
   route: "/settings/presentation/settings#login-banner",
   title: "Login Banner",
   keywords: [
      "em.settings", "em.settings.presentation", "em.settings.login",
      "em.settings.banner"
   ]
})
@ContextHelp({
   route: "/settings/presentation/settings#login-banner",
   link: "EMPresentationLoginBanner"
})
@Component({
   selector: "em-presentation-login-banner-settings-view",
   templateUrl: "./presentation-login-banner-settings-view.component.html",
   styleUrls: ["./presentation-login-banner-settings-view.component.scss"]
})
export class PresentationLoginBannerSettingsViewComponent {
   private _model: PresentationLoginBannerSettingsModel;

   @Input() set model(model: PresentationLoginBannerSettingsModel) {
      this._model = model;

      if(this.model) {
         this.form.setValue(this.model, {emitEvent: false});

         if(!this.subscribed) {
            this.subscribed = true;
            // IE may trigger a change event immediately on populating the form
            setTimeout(() => {
               this.form.valueChanges.subscribe(() => this.onModelChanged());
            }, 200);
         }
      }
   }

   get model(): PresentationLoginBannerSettingsModel {
      return this._model;
   }

   @Output() modelChanged = new EventEmitter<PresentationSettingsChanges>();
   form: UntypedFormGroup;
   subscribed = false;

   constructor(private fb: UntypedFormBuilder) {
      this.form = fb.group({
         bannerType: "",
         loginBanner: ""
      });
   }

   private onModelChanged() {
      this._model = this.form.value;
      this.modelChanged.emit({
         model: this.model,
         modelType: PresentationSettingsType.LOGIN_BANNER_SETTINGS_MODEL,
         valid: this.form.valid
      });
   }

}
