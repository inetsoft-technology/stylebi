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
import { RepositorySheetSettingsModel } from "../repository-worksheet-settings-page/repository-sheet-settings.model";
import {
   Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output,
   SimpleChanges
} from "@angular/core";
import { UntypedFormBuilder, UntypedFormGroup, Validators } from "@angular/forms";
import { FormValidators } from "../../../../../../../shared/util/form-validators";
import { Subscription } from "rxjs";
import { Tool } from "../../../../../../../shared/util/tool";

export interface RepositorySheetSettingsChange {
   model: RepositorySheetSettingsModel;
   valid: boolean;
}

@Component({
   selector: "em-repository-sheet-settings-view",
   templateUrl: "./repository-sheet-settings-view.component.html",
   styleUrls: ["./repository-sheet-settings-view.component.scss"]
})
export class RepositorySheetSettingsViewComponent implements OnChanges, OnDestroy, OnInit {
   @Input() model: RepositorySheetSettingsModel;
   @Output() sheetSettingsChanged = new EventEmitter<RepositorySheetSettingsChange>();
   form: UntypedFormGroup;

   constructor(fb: UntypedFormBuilder) {
      this.form = fb.group({
         name: ["", [Validators.required,
            FormValidators.assetEntryBannedCharacters,
            FormValidators.assetNameStartWithCharDigit,
            FormValidators.cannotContain([Tool.MY_REPORTS])]],
         alias: ["", [FormValidators.cannotContain(["/", "\\"])]],
         description: [""]
      });
   }

   ngOnInit() {
      // IE may trigger a change event immediately on populating the form
      setTimeout(() => {
         this.form.valueChanges.subscribe(() => this.fireSheetSettingsChanged());
      }, 200);
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.model && changes.model.currentValue != null) {
         this.form.patchValue(this.model, {emitEvent: false});
      }
   }

   ngOnDestroy() {
   }

   fireSheetSettingsChanged() {
      if(this.model.name == this.form.value["name"] &&
         this.model.alias == this.form.value["alias"] &&
         this.model.description == this.form.value["description"])
      {
         return;
      }

      this.model.name = this.form.value["name"];
      this.model.alias = this.form.value["alias"];
      this.model.description = this.form.value["description"];
      this.sheetSettingsChanged.emit({model: this.model, valid: this.form.valid});
   }
}
