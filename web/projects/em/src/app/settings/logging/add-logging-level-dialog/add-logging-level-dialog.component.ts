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
import { Component, Inject, OnInit } from "@angular/core";
import {
   UntypedFormBuilder,
   UntypedFormGroup,
   Validators
} from "@angular/forms";
import { Tool } from "../../../../../../shared/util/tool";
import { MAT_DIALOG_DATA, MatDialog, MatDialogConfig, MatDialogRef } from "@angular/material/dialog";
import { LogLevelDTO } from "../LogLevelDTO";
import { MessageDialog, MessageDialogType } from "../../../common/util/message-dialog";

@Component({
   selector: "em-add-logging-level-dialog",
   templateUrl: "./add-logging-level-dialog.component.html",
   styleUrls: ["./add-logging-level-dialog.component.scss"]
})
export class AddLoggingLevelDialogComponent implements OnInit {
   index: number;
   loggingLevels: LogLevelDTO[];
   enterprise: boolean;
   isMultiTenant: boolean;
   organizations: string[];
   model: LogLevelDTO;
   title: string;
   form: UntypedFormGroup;

   constructor(private dialogRef: MatDialogRef<AddLoggingLevelDialogComponent>,
               @Inject(MAT_DIALOG_DATA) public data: any, private dialog: MatDialog,
               fb: UntypedFormBuilder)
   {
      this.index = data.index;
      this.loggingLevels = data.loggingLevels;
      this.enterprise = data.enterprise;
      this.isMultiTenant = data.isMultiTenant;
      this.organizations = data.organizations;
      this.form = fb.group({
         context: ["", Validators.required],
         name: ["", Validators.required],
         orgName: [""],
         level: ["", Validators.required]
      });
   }

   ngOnInit() {
      if(this.index == -1) {
         this.model = <LogLevelDTO> {
            context: "DASHBOARD",
            name: "dashboard1",
            level: "info"
         };

         this.title = "_#(js:Add Logging Level)";
      }
      else {
         this.model = Tool.clone(this.loggingLevels[this.index]);
         this.title = "_#(js:Edit Logging Level)";
      }

      this.form.get("context").setValue(this.model.context);
      this.form.get("name").setValue(this.model.name);
      this.form.get("orgName").setValue(this.model.orgName);
      this.form.get("level").setValue(this.model.level);
   }

   onFormChanged() {
      this.model.context = this.form.get("context").value;

      if(this.model.context == "CATEGORY") {
         this.form.get("orgName").setValue(null);
      }

      if(this.model.context == "ORGANIZATION") {
         this.model.name = this.form.get("orgName").value;
         this.model.orgName = null;
      }
      else {
         this.model.name = this.form.get("name").value.trim();
         this.model.orgName = this.form.get("orgName").value;
      }

      this.model.level = this.form.get("level").value;
   }

   isOrganizationFieldVisible() {
      return this.enterprise && this.isMultiTenant && this.model.context != "CATEGORY";
   }

   isNameFieldVisible() {
      return this.model.context != "ORGANIZATION";
   }

   ok(): void {
      if(this.index > -1) {
         this.loggingLevels[this.index] = this.model;
         this.dialogRef.close(this.loggingLevels);
      }
      else {
         if(!this.loggingLevels) {
            this.loggingLevels = [];
         }

         let copyIndex: number = -1;

         for(let i = 0; i < this.loggingLevels.length; i++) {
            const level: LogLevelDTO = this.loggingLevels[i];

            if(this.model.name === level.name && this.model.context === level.context && this.model.orgName === level.orgName) {
               copyIndex = i;
            }
         }

         if(copyIndex < 0) {
            this.loggingLevels.push(Tool.clone(this.model));
            this.dialogRef.close(this.loggingLevels);
         }
         else {
            this.dialog.open(MessageDialog, <MatDialogConfig>{
               data: {
                  title: "_#(js:Error)",
                  content: "_#(js:add.log.level.duplicate)",
                  type: MessageDialogType.ERROR
               }
            });
            this.dialogRef.close();
         }
      }
   }
}
