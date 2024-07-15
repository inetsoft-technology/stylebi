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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { UntypedFormBuilder, UntypedFormGroup, Validators } from "@angular/forms";
import { PresentationFormatsSettingsModel } from "./presentation-formats-settings-model";
import { PresentationSettingsChanges } from "../presentation-settings-view/presentation-settings-view.component";
import { Searchable } from "../../../searchable";
import { PresentationSettingsType } from "../presentation-settings-view/presentation-settings-type.enum";
import { ContextHelp } from "../../../context-help";

@Searchable({
   route: "/settings/presentation/settings#general-format",
   title: "General Format",
   keywords: ["em.settings", "em.settings.presentation", "em.settings.general"]
})
@ContextHelp({
   route: "/settings/presentation/settings#general-format",
   link: "EMSettingsPresentation"
})
@Component({
   selector: "em-presentation-formats-settings-view",
   templateUrl: "./presentation-formats-settings-view.component.html",
   styleUrls: ["./presentation-formats-settings-view.component.scss"]
})
export class PresentationFormatsSettingsViewComponent {
   DATE_REGEX = "^(?:(?:(?:full)|(?:long)|(?:medium)|(?:short))|(?:(?:[^A-Za-z]+)|(?:[GyYMwWDdFEuaHkKhmsSzZX]+)|(?:'[^']*'))*)$";
   private _model: PresentationFormatsSettingsModel;

   @Input() set model(model: PresentationFormatsSettingsModel) {
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

   get model(): PresentationFormatsSettingsModel {
      return this._model;
   }

   @Output() modelChanged = new EventEmitter<PresentationSettingsChanges>();
   form: UntypedFormGroup;
   subscribed = false;

   constructor(private fb: UntypedFormBuilder) {
      let formatValidator = Validators.pattern(this.DATE_REGEX);
      this.form = this.fb.group({
         dateFormat: ["", formatValidator],
         timeFormat: ["", formatValidator],
         dateTimeFormat: ["", formatValidator],
      });
   }

   onModelChanged() {
      this._model = this.form.value;
      this.modelChanged.emit({
         model: this.model,
         modelType: PresentationSettingsType.FORMATS_SETTINGS_MODEL,
         valid: this.form.valid
      });
   }

}
