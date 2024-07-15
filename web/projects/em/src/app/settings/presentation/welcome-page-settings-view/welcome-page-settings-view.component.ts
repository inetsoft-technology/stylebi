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
import { UntypedFormBuilder, UntypedFormGroup, Validators } from "@angular/forms";
import { ContextHelp } from "../../../context-help";
import { Searchable } from "../../../searchable";
import { PresentationSettingsType } from "../presentation-settings-view/presentation-settings-type.enum";
import { PresentationSettingsChanges } from "../presentation-settings-view/presentation-settings-view.component";
import { WelcomePageSettingsModel, WelcomeType } from "./welcome-page-settings-model";

@Searchable({
   route: "/settings/presentation/settings#welcome-page",
   title: "Welcome Page",
   keywords: ["em.settings", "em.settings.presentation", "em.settings.welcome"]
})
@ContextHelp({
   route: "/settings/presentation/settings#welcome-page",
   link: "EMPresentationWelcomePage"
})
@Component({
   selector: "em-welcome-page-settings-view",
   templateUrl: "./welcome-page-settings-view.component.html",
   styleUrls: ["./welcome-page-settings-view.component.scss"]
})
export class WelcomePageSettingsViewComponent {
   @Output() modelChanged = new EventEmitter<PresentationSettingsChanges>();

   private _model: WelcomePageSettingsModel;
   private _form: UntypedFormGroup;
   WelcomeType = WelcomeType;
   oldSource: string;
   oldType: number;

   @Input() set model(model: WelcomePageSettingsModel) {
      this._model = model;

      if(model) {
         this.oldSource = this.model.source;
         this.oldType = this.model.type;
         this.form.controls["type"].setValue(this.model.type);

         if(this.model.type == WelcomeType.URI || this.model.type == WelcomeType.RESOURCE) {
            this.form.controls["source"].setValue(this.model.source);
         }
      }
   }

   get model(): WelcomePageSettingsModel {
      return this._model;
   }

   constructor(private fb: UntypedFormBuilder) {
      this._form = this.fb.group({
         source: ["", [Validators.required]],
         type: [""]
      });
   }

   changeType(type: WelcomeType) {
      switch(type) {

      case WelcomeType.NONE:
         this.form.get("source").setValidators([]);
         this.model.type = type;
         this.updateSource("");
         this.emitModel();

         break;

      case WelcomeType.URI:
      case WelcomeType.RESOURCE:
         this.form.get("source").setValidators([Validators.required]);
         this.model.type = type;

         if(this.oldType === type) {
            this.updateSource(this.oldSource);
            this.emitModel();
         }
         else {
            this.updateSource("");
         }

         break;

      default:
         break;
      }
   }

   updateSource(source: string) {
      this.form.controls["source"].setValue(source.trim());
      this.model.source = this.form.value["source"];

      this.emitModel();
   }

   get form(): UntypedFormGroup {
      return this._form;
   }

   set form(form: UntypedFormGroup) {
      this._form = form;
   }

   private emitModel() {
      this.modelChanged.emit(<PresentationSettingsChanges>{
         model: this.model,
         modelType: PresentationSettingsType.WELCOME_PAGE_SETTINGS_MODEL,
         valid: this.form.valid
      });
   }
}
