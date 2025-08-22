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
import { Component, EventEmitter, Input, OnInit, OnChanges, Output, SimpleChanges } from "@angular/core";
import {
   FormGroupDirective,
   NgForm,
   UntypedFormBuilder,
   UntypedFormControl,
   UntypedFormGroup,
   Validators
} from "@angular/forms";
import {ErrorStateMatcher} from "@angular/material/core";
import {GuiTool} from "../../../../../../../portal/src/app/common/util/gui-tool";
import {CSVConfigModel} from "../../../../../../../shared/schedule/model/csv-config-model";
import {ExportFormatModel} from "../../../../../../../shared/schedule/model/export-format-model";
import {FormValidators} from "../../../../../../../shared/util/form-validators";
import {DashboardOptions} from "../../model/dashboard-options";
import {ReportOptions} from "../../model/reports-options";
import {BurstEmailDialogData} from "../burst-email-dialog/burst-email-dialog.component";
import {FeatureFlagValue} from "../../../../../../../shared/feature-flags/feature-flags.service";

export interface DeliveryEmails {
   valid: boolean;
   enabled: boolean;
   sender: string;
   recipients: string;
   subject: string;
   burstEmails: string;
   burstQueryType: string;
   bundledAsZip: boolean;
   useCredential: boolean;
   secretId: string;
   zipPassword: string;
   attachmentName: string;
   format: string;
   emailMatchLayout: boolean;
   emailExpandSelections: boolean;
   emailOnlyDataComponents: boolean;
   message: string;
   htmlMessage: boolean;
   deliverLink: boolean;
   ccAddress: string;
   bccAddress: string;
   exportAllTabbedTables?: boolean
}

@Component({
   selector: "em-delivery-emails",
   templateUrl: "./delivery-emails.component.html",
   styleUrls: ["./delivery-emails.component.scss"]
})
export class DeliveryEmailsComponent implements OnInit, OnChanges {
   FeatureFlag = FeatureFlagValue;
   @Input() enabled: boolean = false;
   @Input() senderEnabled: boolean = false;
   @Input() type: string = "";
   @Input() mailFormats: ExportFormatModel[] = [];
   @Input() emailBrowserEnabled: boolean;
   @Input() expandEnabled: boolean = true;
   @Input() splitByColon: boolean = false;
   @Input() users = [];
   @Input() groups = [];
   @Input() csvExportModel: CSVConfigModel;
   @Input() tableDataAssemblies: string[];
   @Input() hasPrintLayout: boolean = false;
   @Input() fipsMode: boolean;
   @Input() cloudSecrets: boolean;
   @Output() deliveryChanged = new EventEmitter<DeliveryEmails>();

   @Input()
   set ccAddress(ccAddress: string) {
      this.form.get("ccAddress").setValue(ccAddress);
   }

   get ccAddress(): string {
      return this.form.get("ccAddress").value;
   }

   @Input()
   set bccAddress(bccAddress: string) {
      this.form.get("bccAddress").setValue(bccAddress);
   }

   get bccAddress(): string {
      return this.form.get("bccAddress").value;
   }

   @Input()
   set burstEmails(burstEmails: string) {
      this.form.get("burstEmails").setValue(!burstEmails ? null : burstEmails);
   }

   get burstEmails(): string {
      return this.form.get("burstEmails").value;
   }

   @Input()
   get sender(): string {
      return this.form.get("sender").value;
   }

   set sender(val: string) {
      this.form.get("sender").setValue(val);

      if(!this.senderEnabled) {
         this.form.get("sender").disable();
      }
      else {
         this.form.get("sender").enable();
      }
   }

   @Input()
   get recipients(): string {
      return this.form.get("recipients").value;
   }

   set recipients(val: string) {
      this.form.get("recipients").setValue(val);
   }

   @Input()
   get subject(): string {
      return this.form.get("subject").value;
   }

   set subject(val: string) {
      this.form.get("subject").setValue(val);
   }

   @Input()
   get format(): string {
      if(this.enabled && !this.form.get("format").value) {
         this.form.get("format").setValue(this.mailFormats[0].type);
      }

      return this.form.get("format").value;
   }

   set format(val: string) {
      this.form.get("format").setValue(val);
      this.togglePasswordForm(false);
   }

   @Input()
   get emailMatchLayout(): boolean {
      return this.form.get("emailMatchLayout").value;
   }

   set emailMatchLayout(val: boolean) {
      this.form.get("emailMatchLayout").setValue(val);
   }

   @Input()
   get bundledAsZip(): boolean {
      return this.form.get("bundledAsZip").value;
   }

   set bundledAsZip(val: boolean) {
      this.form.get("bundledAsZip").setValue(val);
   }

   @Input()
   get emailExpandSelections(): boolean {
      return this.form.get("emailExpandSelections").value;
   }

   set emailExpandSelections(val: boolean) {
      this.form.get("emailExpandSelections").setValue(val);
   }

   @Input()
   get emailOnlyDataComponents(): boolean {
      return this.form.get("emailOnlyDataComponents").value;
   }

   set emailOnlyDataComponents(val: boolean) {
      this.form.get("emailOnlyDataComponents").setValue(val);
   }

   @Input()
   get exportAllTabbedTables(): boolean {
      return this.form.get("exportAllTabbedTables").value;
   }

   set exportAllTabbedTables(val: boolean) {
      this.form.get("exportAllTabbedTables").setValue(val);
   }

   @Input()
   get useCredential(): boolean {
      return this.form.get("useCredential").value;
   }

   set useCredential(val: boolean) {
      this.form.get("useCredential").setValue(val);
   }

   @Input()
   get secretId(): string {
      return this.form.get("secretId").value;
   }

   set secretId(val: string) {
      this.form.get("secretId").setValue(val);
   }

   @Input()
   get zipPassword(): string {
      return this.form.get("zipPassword").value;
   }

   set zipPassword(val: string) {
      let oldValue = this.zipPassword;
      this.form.get("zipPassword").setValue(val);

      if(oldValue != val) {
         this.initVerifyZipPassword();
      }
   }

   get verifyZipPassword(): string {
      return this.form.get("verifyZipPassword").value;
   }

   set verifyZipPassword(val: string) {
      this.form.get("verifyZipPassword").setValue(val);
   }

   @Input()
   get attachmentName(): string {
      return this.form.get("attachment").value;
   }

   set attachmentName(val: string) {
      this.form.get("attachment").setValue(val);
   }

   @Input()
   get message(): string {
      return this.form.get("message").value;
   }

   set message(val: string) {
      const current = this.form.get("message").value;

      if(current !== val) {
         this.form.get("message").setValue(val, {emitEvent: false});
      }
   }

   @Input()
   get deliverLink(): boolean {
      return this.form.get("deliverLink").value;
   }

   set deliverLink(val: boolean) {
      this.form.get("deliverLink").setValue(val);
   }

   get dataSizeOptionVisible(): boolean {
      return this.type === "viewsheet" && this.format !== "HTML" && this.format !== "CSV" &&
         (this.format != "PDF" || !this.hasPrintLayout);
   }

   form: UntypedFormGroup;
   isIE = GuiTool.isIE();
   errorStateMatcher: ErrorStateMatcher;
   burstQueryType: string;

   constructor(fb: UntypedFormBuilder, defaultErrorMatcher: ErrorStateMatcher) {
      this.form = fb.group(
         {
            sender: ["", [Validators.required, Validators.email]],
            recipients: [""],
            burstEmails: [""],
            subject: [""],
            format: ["", [Validators.required]],
            emailMatchLayout: [true],
            emailExpandSelections: [false],
            emailOnlyDataComponents: [false],
            exportAllTabbedTables: [false],
            bundledAsZip: [false],
            useCredential: [false],
            secretId: [""],
            zipPassword: [""],
            verifyZipPassword: [""],
            attachment: ["", FormValidators.isValidWindowsFileName],
            message: [""],
            deliverLink: [false],
            ccAddress: [],
            bccAddress: []
         },
         {
            validator: FormValidators.passwordsMatch("zipPassword", "verifyZipPassword")
         }
      );

      this.errorStateMatcher = {
         isErrorState: (control: UntypedFormControl | null, form: FormGroupDirective | NgForm | null) =>
            !!this.form.errors && !!this.form.errors.passwordsMatch ||
            defaultErrorMatcher.isErrorState(control, form)
      };
   }

   ngOnInit() {
      this.initVerifyZipPassword();
      this.initForm();
      this.togglePasswordForm(false);
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes["attachmentName"]) {
         this.togglePasswordForm(false);
      }
   }

   get bundledDisabled(): boolean {
      return this.format === ReportOptions.HTML_BUNDLE ||
         this.format === ReportOptions.HTML_BUNDLE_NO_PAGINATION ||
         this.format === ReportOptions.HTML_NO_PAGINATION_EMAIL ||
         this.format === DashboardOptions.PNG ||
         (this.format === ReportOptions.CSV && this.type === "viewsheet");
   }

   get passwordVisible(): boolean {
      return !this.fipsMode && (this.bundledAsZip || this.format === ReportOptions.HTML_BUNDLE ||
         this.format === ReportOptions.HTML_BUNDLE_NO_PAGINATION);
   }

   initForm() {
      let formatValue = this.form.get("format").value;

      if(!formatValue) {
         formatValue = (!!this.mailFormats && this.mailFormats.length > 0) ?
            this.mailFormats[0].type : null;
      }

      this.form.get("format").setValue(formatValue);
      this.form.get("message").valueChanges.subscribe(() => this.fireDeliveryChanged());

      this.form.valueChanges.subscribe(() => {
         this.updateEnable();
      });
   }

   private updateEnable(): void {
      if(this.form.get("emailExpandSelections")) {
         if(!this.expandEnabled || this.emailMatchLayout || (this.emailOnlyDataComponents && this.format == "Excel")) {
            this.form.get("emailExpandSelections").disable({ emitEvent: false });
         }
         else {
            this.form.get("emailExpandSelections").enable({ emitEvent: false });
         }
      }

      if(this.form.get("emailOnlyDataComponents")) {
         if(!this.expandEnabled || this.emailMatchLayout) {
            this.form.get("emailOnlyDataComponents").disable({ emitEvent: false });
         }
         else {
            this.form.get("emailOnlyDataComponents").enable({ emitEvent: false });
         }
      }
   }

   togglePasswordForm(changed: boolean) {
      if(this.passwordVisible) {
         this.form.get("useCredential").enable();
         this.form.get("secretId").enable();
         this.form.get("zipPassword").enable();
         this.form.get("verifyZipPassword").enable();
      }
      else {
         this.form.get("useCredential").disable();
         this.form.get("secretId").disable();
         this.form.get("zipPassword").disable();
         this.form.get("verifyZipPassword").disable();
      }

      if(this.bundledDisabled) {
         this.form.get("bundledAsZip").disable();
         this.form.get("useCredential").disable();
      }
      else {
         this.form.get("bundledAsZip").enable();
         this.form.get("useCredential").enable();
      }

      if(changed) {
         this.fireDeliveryChanged();
      }
   }

   changeFormat() {
      this.bundledAsZip = !this.bundledDisabled && this.bundledAsZip;

      if(this.form.get("format").value === DashboardOptions.HTML) {
         this.form.get("emailMatchLayout").disable();
      }
      else if(this.form.get("format").value === ReportOptions.CSV && this.type === "viewsheet") {
         this.bundledAsZip = true;
      }
      else {
         this.form.get("emailMatchLayout").enable();
      }

      this.togglePasswordForm(true);
   }

   changeEmails(data: string | BurstEmailDialogData) {
      this.recipients = <string> data;
      this.fireDeliveryChanged();
   }

   changeCCEmails(data: string | BurstEmailDialogData) {
      this.ccAddress = <string> data;
      this.fireDeliveryChanged();
   }

   changeBCCEmails(data: string | BurstEmailDialogData) {
      this.bccAddress = <string> data;
      this.fireDeliveryChanged();
   }

   fireDeliveryChanged() {
      this.emailOnlyDataComponents = this.emailMatchLayout ? false : this.emailOnlyDataComponents;

      this.deliveryChanged.emit({
         valid: this.isValid(),
         enabled: this.enabled,
         sender: this.sender,
         recipients: this.recipients,
         burstEmails: !this.enabled ? null : this.burstEmails,
         burstQueryType: this.burstQueryType,
         subject: this.subject,
         bundledAsZip: this.bundledAsZip,
         useCredential: this.useCredential,
         secretId: this.secretId == null ? null : this.secretId.trim(),
         zipPassword: this.zipPassword,
         attachmentName: this.attachmentName,
         format: this.format,
         emailMatchLayout: this.emailMatchLayout,
         emailExpandSelections: this.emailExpandSelections,
         emailOnlyDataComponents: this.emailOnlyDataComponents,
         message: this.message,
         htmlMessage: true,
         deliverLink: this.deliverLink,
         ccAddress: this.ccAddress,
         bccAddress: this.bccAddress,
         exportAllTabbedTables: this.exportAllTabbedTables
      });
   }

   private isValid(): boolean {
      if(this.format == "CSV" && this.type === "viewsheet" &&
         this.csvExportModel?.selectedAssemblies?.length == 0)
      {
         return false;
      }

      return !this.enabled || this.form.valid;
   }

   private initVerifyZipPassword() {
      if(this.zipPassword != null) {
         this.verifyZipPassword = this.zipPassword;
      }
      else {
         this.verifyZipPassword = this.zipPassword = "";
      }
   }
}
