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
import {
   Component,
   EventEmitter,
   Input,
   OnChanges,
   OnDestroy,
   OnInit,
   Output,
   SimpleChanges
} from "@angular/core";
import { UntypedFormBuilder, UntypedFormGroup, Validators } from "@angular/forms";
import { Subject } from "rxjs";
import { takeUntil } from "rxjs/operators";
import { FormValidators } from "../../../../../../../shared/util/form-validators";
import { Tool } from "../../../../../../../shared/util/tool";
import { DataSourceFolderSettingsModel } from "../repository-data-source-folder-settings-page/data-source-folder-settings-model";

@Component({
   selector: "em-repository-data-source-folder-settings-view",
   templateUrl: "./repository-data-source-folder-settings-view.component.html",
   styleUrls: ["./repository-data-source-folder-settings-view.component.scss"]
})
export class RepositoryDataSourceFolderSettingsViewComponent implements OnInit, OnChanges, OnDestroy {
   @Input() model: DataSourceFolderSettingsModel;
   @Input() selectedTab = 0;
   @Input() smallDevice: boolean;
   @Output() cancel = new EventEmitter<void>();
   @Output() selectedTabChanged = new EventEmitter<number>();
   @Output() folderSettingsChanged = new EventEmitter<DataSourceFolderSettingsModel>();
   @Output() unsavedChanges = new EventEmitter<boolean>();
   form: UntypedFormGroup;

   get disabled(): boolean {
      return !this.model.root && this.form.invalid || Tool.isEquals(this.model, this._oldModel);
   }

   private get existingNames(): string[] {
      if(!this.model || !this._oldModel) {
         return [];
      }

      return this.model.siblingFolders
         .filter(f => f !== this._oldModel.name)
         .concat(this.model.siblingDataSources);
   }

   private _oldModel: DataSourceFolderSettingsModel;
   private destroy$ = new Subject<void>();

   constructor(fb: UntypedFormBuilder) {
      this.form = fb.group({
         folderName: [
            "",
            [
               Validators.required, FormValidators.assetEntryBannedCharacters,
               FormValidators.assetNameStartWithCharDigit,
               FormValidators.duplicateName(() => this.existingNames)
            ]
         ]
      });
   }

   ngOnInit() {
      this.form.valueChanges
         .pipe(takeUntil(this.destroy$))
         .subscribe(() => this.model.name = this.form.get("folderName").value);
   }

   ngOnDestroy(): void {
      this.destroy$.next();
      this.destroy$.unsubscribe();
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes.model) {
         this._oldModel = Tool.clone(this.model);
         this.form.get("folderName").setValue(this.model.name);
      }
   }

   onTabChanged(tab: number): void {
      this.selectedTab = tab;
      this.selectedTabChanged.emit(tab);
   }

   reset(): void {
      if(this.smallDevice) {
         this.cancel.emit();
      }

      this.model = Tool.clone(this._oldModel);
      this.form.get("folderName").setValue(this.model.name);
   }

   apply(): void {
      this.folderSettingsChanged.emit(this.model);
   }
}
