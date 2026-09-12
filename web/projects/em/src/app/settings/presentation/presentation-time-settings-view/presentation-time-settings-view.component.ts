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
import { UntypedFormBuilder, UntypedFormGroup, FormsModule } from "@angular/forms";
import { PresentationSettingsChanges } from "../presentation-settings-view/presentation-settings-view.component";
import { PresentationTimeSettingsModel } from "./presentation-time-settings-model";
import { PresentationSettingsType } from "../presentation-settings-view/presentation-settings-type.enum";
import { ContextHelp } from "../../../context-help";
import { MatCheckbox } from "@angular/material/checkbox";
import { MatOption } from "@angular/material/core";
import { MatSelect } from "@angular/material/select";
import { MatFormField, MatLabel } from "@angular/material/form-field";
import { MatCard, MatCardTitle, MatCardContent } from "@angular/material/card";


@ContextHelp({
   route: "/settings/presentation/settings#time-settings",
   link: "EMTimeSettings"
})
@Component({
    selector: "em-presentation-time-settings-view",
    templateUrl: "./presentation-time-settings-view.component.html",
    styleUrls: ["./presentation-time-settings-view.component.scss"],
    imports: [MatCard, MatCardTitle, MatCardContent, MatFormField, MatLabel, MatSelect, MatOption, FormsModule, MatCheckbox]
})
export class PresentationTimeSettingsViewComponent {
   // week.start only accepts these day names; anything else falls back to Sunday on the
   // server, so the field is a fixed list rather than free text.
   readonly weekStartOptions = [
      {label: "_#(js:Default)", value: ""},
      {label: "_#(js:Sunday)", value: "Sunday"},
      {label: "_#(js:Monday)", value: "Monday"},
      {label: "_#(js:Tuesday)", value: "Tuesday"},
      {label: "_#(js:Wednesday)", value: "Wednesday"},
      {label: "_#(js:Thursday)", value: "Thursday"},
      {label: "_#(js:Friday)", value: "Friday"},
      {label: "_#(js:Saturday)", value: "Saturday"}
   ];

   @Input() isSysAdmin: boolean;
   @Input() set model(model: PresentationTimeSettingsModel) {
      this._model = model;

      if(model && model.weekStart) {
         // the property is matched case-insensitively on the server, so map an existing
         // value onto the option list rather than showing an empty select. A value that
         // matches nothing is left alone: it already means Sunday on the server, and
         // rewriting it here would blank the property on the next unrelated save.
         const match = this.weekStartOptions
            .find(o => !!o.value && o.value.toLowerCase() === model.weekStart.toLowerCase());

         if(match) {
            model.weekStart = match.value;
         }
      }

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

  get model(): PresentationTimeSettingsModel {
    return this._model;
  }

  @Output() modelChanged = new EventEmitter<PresentationSettingsChanges>();
  form: UntypedFormGroup;
  private _model: PresentationTimeSettingsModel;
  subscribed = false;

  constructor(private fb: UntypedFormBuilder) {
    this.form = fb.group({
       weekStart: "",
       scheduleTime12Hours: [true]
    });
  }

  private onModelChanged() {
    this._model = this.form.value;
    this.modelChanged.emit({
      model: this.model,
      modelType: PresentationSettingsType.TIME_SETTINGS_MODEL,
      valid: this.form.valid
    });
  }

   emitModel() {
      this.modelChanged.emit(<PresentationSettingsChanges>{
         model: this.model,
         modelType: PresentationSettingsType.TIME_SETTINGS_MODEL,
         valid: true
      });
   }
}
