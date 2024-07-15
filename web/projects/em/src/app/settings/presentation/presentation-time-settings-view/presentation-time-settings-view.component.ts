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
import { UntypedFormBuilder, UntypedFormGroup } from "@angular/forms";
import { PresentationSettingsChanges } from "../presentation-settings-view/presentation-settings-view.component";
import { PresentationTimeSettingsModel } from "./presentation-time-settings-model";
import { PresentationSettingsType } from "../presentation-settings-view/presentation-settings-type.enum";
import { ContextHelp } from "../../../context-help";

@ContextHelp({
   route: "/settings/presentation/settings#time-settings",
   link: "EMtime-settings"
})
@Component({
  selector: "em-presentation-time-settings-view",
  templateUrl: "./presentation-time-settings-view.component.html",
  styleUrls: ["./presentation-time-settings-view.component.scss"]
})
export class PresentationTimeSettingsViewComponent {
   @Input() isSysAdmin: boolean;
   @Input() set model(model: PresentationTimeSettingsModel) {
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
       localTimezone: "",
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
