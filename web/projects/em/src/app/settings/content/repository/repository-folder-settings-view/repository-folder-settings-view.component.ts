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
   Output,
   SimpleChanges
} from "@angular/core";
import { UntypedFormBuilder, UntypedFormGroup, Validators } from "@angular/forms";
import { MatDialog } from "@angular/material/dialog";
import { MatTabChangeEvent } from "@angular/material/tabs";
import { Subject } from "rxjs";
import { takeUntil } from "rxjs/operators";
import { FormValidators } from "../../../../../../../shared/util/form-validators";
import { Tool } from "../../../../../../../shared/util/tool";
import { RepositoryFolderSettingsModel } from "../repository-folder-settings-page/repository-folder-settings.model";

@Component({
   selector: "em-repository-folder-settings-view",
   templateUrl: "./repository-folder-settings-view.component.html",
   styleUrls: ["./repository-folder-settings-view.component.scss"]
})
export class RepositoryFolderSettingsViewComponent implements OnChanges, OnDestroy {
   @Input() model: RepositoryFolderSettingsModel;
   @Input() isWSFolder = false;
   @Input() selectedTab = 0;
   @Input() smallDevice: boolean;
   @Output() cancel = new EventEmitter<void>();
   @Output() selectedTabChanged = new EventEmitter<number>();
   @Output() folderSettingsChanged = new EventEmitter<RepositoryFolderSettingsModel>();
   @Output() unsavedChanges = new EventEmitter<boolean>();
   form: UntypedFormGroup;
   folderChanged: boolean = false;
   private _oldModel: RepositoryFolderSettingsModel;
   private destroy$: Subject<void> = new Subject();

   constructor(private fb: UntypedFormBuilder,
      private dialog: MatDialog) {
      this.form = this.fb.group({
         folderName: [""],
         alias: [""],
         description: [""],
         parentFolder: [{ value: "", disabled: true }],
      });
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.model && changes.model.currentValue != null) {
         this.init();
      }
   }

   ngOnDestroy() {
      this.destroy$.next();
      this.destroy$.unsubscribe();
   }

   init(): void {
      this._oldModel = Tool.clone(this.model);

      if(!this.model.editable) {
         this.form.disable();
         return;
      }

      this.form.enable({ emitEvent: false });
      this.form.get("folderName").setValidators([Validators.required,
      FormValidators.assetEntryBannedCharacters,
      FormValidators.assetNameStartWithCharDigit,
      FormValidators.assetNameMyReports,
      FormValidators.duplicateName(() => this.model.folders)]);

      if(this.isRoot || this.isUsersReportsFolder || this.isMyReportsFolder ||
         this.isMyDashboardsFolder || this.isWSFolder) {
         this.form.get("description").disable({ emitEvent: false });

         if(this.isRoot || this.isUsersReportsFolder || this.isMyReportsFolder ||
            this.isMyDashboardsFolder) {
            this.form.get("folderName").disable({ emitEvent: false });
            this.form.get("alias").disable({ emitEvent: false });
         }
      }

      if(this.isWSFolder &&
         (this.model.folderName === "/" || this.model.parentFolder === Tool.MY_REPORTS)) {
         this.form.get("folderName").disable({ emitEvent: false });
      }

      this.form.patchValue(this.model, { emitEvent: false });
      this.form.valueChanges.pipe(takeUntil(this.destroy$))
         .subscribe(() => { // don't use value because form controls can be disabled
            this.model.oname = this.model.folderName;
            this.model.folderName = this.form.get("folderName").value;
            this.model.alias = this.form.get("alias").value;
            this.model.description = this.form.get("description").value;
            this.model.parentFolder = this.form.get("parentFolder").value;
            this.folderChanged = true;
         });
   }

   get disabled(): boolean {
      const oldModel = Tool.clone(this._oldModel);
      oldModel.oname = "";
      const currModel = Tool.clone(this.model);
      currModel.oname = "";

      return this.form.invalid || this.isUsersReportsFolder || Tool.isEquals(oldModel, currModel) || !this.folderChanged;
   }

   get alert(): boolean {
      return this.form.value["folderName"] === "My Alerts";
   }

   get isRoot(): boolean {
      return this.model.folderName === "/";
   }

   onTabChange(event: MatTabChangeEvent): void {
      this.selectedTab = event.index;
      this.selectedTabChanged.emit(event.index);
   }

   get isUsersReportsFolder(): boolean {
      return this.model.folderName === Tool.USERS_REPORTS;
   }

   get isMyReportsFolder(): boolean {
      return this.model.folderName === Tool.MY_REPORTS;
   }

   get isMyDashboardsFolder(): boolean {
      return this.model.folderName === Tool.MY_DASHBOARDS;
   }

   reset() {
      if(this.smallDevice) {
         this.cancel.emit();
      }

      this.model = this._oldModel;
      this.init();
      this.folderChanged = false;
   }

   apply() {
      this.folderChanged = false;
      this.folderSettingsChanged.emit(this.model);
   }
}
