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
import {
   Component,
   EventEmitter,
   Inject,
   Input,
   OnInit,
   Output,
   ViewEncapsulation
} from "@angular/core";
import { AbstractControl, UntypedFormBuilder, UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from "@angular/material/dialog";
import { MatSnackBar, MatSnackBarConfig } from "@angular/material/snack-bar";
import { Router } from "@angular/router";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { Tool } from "../../../../../../shared/util/tool";
import { ContextHelp } from "../../../context-help";
import { Searchable } from "../../../searchable";
import { ConnectionStatus } from "../../security/security-provider/security-provider-model/connection-status";
import { GeneralSettingsChanges } from "../general-settings-page/general-settings-page.component";
import { GeneralSettingsType } from "../general-settings-page/general-settings-type.enum";
import { DataSpaceSettingsModel } from "./data-space-settings-model";

export interface BackupData {
   dataspace: string;
}

@Searchable({
   route: "/settings/general#data-space",
   title: "Data Space",
   keywords: [
      "em.settings", "em.settings.general", "em.settings.data",
      "em.settings.space"
   ]
})
@ContextHelp({
   route: "/settings/general#data-space",
   link: "EMSettingsContentDataSpace"
})
@Component({
   selector: "em-data-space-settings-view",
   templateUrl: "./data-space-settings-view.component.html",
   styleUrls: ["./data-space-settings-view.component.scss"],
   encapsulation: ViewEncapsulation.None
})
export class DataSpaceSettingsViewComponent {
   @Output() modelChanged = new EventEmitter<GeneralSettingsChanges>();

   @Input() set model(model: DataSpaceSettingsModel) {
      this._model = model;
   }

   get model(): DataSpaceSettingsModel {
      return this._model;
   }

   get keyValueLabel(): string {
      if(this.model?.keyValueType === "dynamodb") {
         return "AWS DynamoDB";
      }
      else if(this.model?.keyValueType === "cosmosdb") {
         return "Azure CosmosDB";
      }
      else if(this.model?.keyValueType === "database") {
         return "_#(js:Database)";
      }
      else if(this.model?.keyValueType === "firestore") {
         return "Google Firestore";
      }
      else if(this.model?.keyValueType === "mongo") {
         return "MongoDB";
      }
      else if(this.model?.keyValueType === "mapdb") {
         return "MapDB";
      }
      else {
         return "_#(js:File System)";
      }
   }

   get blobLabel(): string {
      if(this.model?.blobType === "s3") {
         return "AWS S3";
      }
      else if(this.model?.blobType === "azure") {
         return "Azure Blob Storage";
      }
      else if(this.model?.blobType === "gcs") {
         return "Google Cloud Storage";
      }
      else if(this.model?.blobType === "filesystem") {
         return "_#(js:Shared File System)";
      }
      else {
         return "_#(js:Local File System)";
      }
   }

   private _model: DataSpaceSettingsModel;

   constructor(private http: HttpClient,
               private snackBar: MatSnackBar,
               private dialog: MatDialog,
               private router: Router)
   {
   }

   backupDB(): void {
      let config = new MatSnackBarConfig();
      config.duration = Tool.SNACKBAR_DURATION;
      config.panelClass = ["max-width"];

      const dialogRef = this.dialog.open(BackupDialog, {
         data: {dataspace: "data.zip"}
      });

      dialogRef.afterClosed().subscribe((result: BackupData) => {
         if(result) {
            this.http.post("../api/em/general/settings/data-space/backup", result)
               .subscribe((msg: ConnectionStatus) => {
                  this.snackBar.open(msg.status + ": _#(js:em.dataspace.backup.success)", "_#(js:Close)", config);
               });
         }
      });
   }

   equalTo(a: string, b: string): boolean {
      return a === b;
   }

   emitModel(): void {
      this.modelChanged.emit({
         model: this.model,
         modelType: GeneralSettingsType.DATASPACE_SETTINGS_MODEL,
         valid: true
      });
   }

   /**
    * Allow user more control over file backups by allowing them to edit the
    * schedule condition.
    */
   editAssetBackup(): void {
      this.router.navigate(["/settings/schedule/tasks", this.model.assetBackupTaskName]);
   }
}

@Component({
   selector: "em-backup-dialog",
   templateUrl: "backup-dialog.html"
})
export class BackupDialog implements OnInit {
   form: UntypedFormGroup;

   get dataspaceControl(): AbstractControl {
      return this.form.get("dataspace");
   }

   constructor(public dialogRef: MatDialogRef<BackupDialog>,
               @Inject(MAT_DIALOG_DATA) public data: BackupData,
               private fb: UntypedFormBuilder)
   {
   }

   ngOnInit(): void {
      this.form = this.fb.group({
         dataspace: new UntypedFormControl("data.zip", [
            Validators.required,
            FormValidators.isValidFileNameAndXMLSafe,
            FormValidators.notWhiteSpace
         ])
      });
   }

   onSaveClick(): void {
      let ds = this.form.value["dataspace"].trim();

      this.dialogRef.close(<BackupData> {
         dataspace: ds,
      });
   }
}
