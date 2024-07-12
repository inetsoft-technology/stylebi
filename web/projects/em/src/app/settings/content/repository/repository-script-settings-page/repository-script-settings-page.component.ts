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
import {RepositoryEditorModel} from "../../../../../../../shared/util/model/repository-editor-model";
import {Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges} from "@angular/core";
import {UntypedFormControl, UntypedFormGroup, Validators} from "@angular/forms";
import {FormValidators} from "../../../../../../../shared/util/form-validators";
import {ScriptSettingsModel} from "./script-settings.model";
import {HttpClient, HttpParams} from "@angular/common/http";
import {Tool} from "../../../../../../../shared/util/tool";

export interface RepositoryScriptEditorModel extends RepositoryEditorModel {
   scriptSettings: ScriptSettingsModel;
}

@Component({
   selector: "em-repository-script-settings-page",
   templateUrl: "repository-script-settings-page.component.html"
})
export class RepositoryScriptSettingsPageComponent implements OnInit, OnChanges {
   @Input() selectedTab = 0;
   @Input() model: RepositoryScriptEditorModel;
   @Input() smallDevice: boolean;
   @Output() cancel = new EventEmitter<void>();
   @Output() editorChanged = new EventEmitter<string>();
   @Output() unsavedChanges = new EventEmitter<boolean>();
   form: UntypedFormGroup;
   _scriptChanged: boolean = false;
   private _oldModel: RepositoryScriptEditorModel;

   constructor(private http: HttpClient) {
   }

   ngOnInit() {
      this.form = new UntypedFormGroup({
         name: new UntypedFormControl(this.model.scriptSettings.name, [
            Validators.required,
            FormValidators.assetEntryBannedCharacters,
            FormValidators.assetNameStartWithCharDigit]),
         description: new UntypedFormControl(this.model.scriptSettings.description)
      });

      this.form.get("name").valueChanges.subscribe((val) => {
         this.model.scriptSettings.name = val;
         this._scriptChanged = true;
      });

      this.form.get("description").valueChanges.subscribe((val) => {
         this.model.scriptSettings.description = val;
         this._scriptChanged = true;
      });
   }

   ngOnChanges(changes: SimpleChanges): void {
      this._oldModel = Tool.clone(this.model);
   }

   reset() {
      if(this.smallDevice) {
         this.cancel.emit();
      }

      this.model = this._oldModel;
      this.form.get("name").setValue(this.model.scriptSettings.name);
      this.form.get("description").setValue(this.model.scriptSettings.description);
      this._oldModel = Tool.clone(this.model);
   }

   apply() {
      const params: HttpParams = new HttpParams()
         .set("path", this.model.path)
         .set("type", this.model.type + "");

      this.http.post("../api/em/settings/content/repository/script",
         this.model.scriptSettings, {params})
         .subscribe(() => {
            this.editorChanged.emit(this.model.scriptSettings.name);
            this._scriptChanged = false;
         });
   }

   get disabled(): boolean {
      return this.form?.invalid || !this.form?.valid || Tool.isEquals(this._oldModel, this.model) || !this._scriptChanged;
   }
}
