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

import {
   Component,
   Input,
   OnDestroy,
   OnInit,
   TemplateRef,
   ViewChild,
   ViewEncapsulation
} from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { NgbModal, NgbModalOptions } from "@ng-bootstrap/ng-bootstrap";
import { Observable, Subscription } from "rxjs";
import { debounceTime, map } from "rxjs/operators";
import { FormValidators } from "../../../../../shared/util/form-validators";
import { Tool } from "../../../../../shared/util/tool";
import { GuiTool } from "../../common/util/gui-tool";
import { EmailPaneModel } from "../../vsobjects/model/email-pane-model";
import { EmailDialogData } from "./email-addr-dialog.component";

@Component({
   selector: "email-pane",
   templateUrl: "email-pane.component.html",
   styleUrls: ["email-pane.component.scss"],
   encapsulation: ViewEncapsulation.None
})
export class EmailPane implements OnInit, OnDestroy {
   @Input()
   get model(): EmailPaneModel {
      return this._model;
   }

   set model(value: EmailPaneModel) {
      this._model = value;
   }

   @Input() historyEmails: string[];
   @Input() form: UntypedFormGroup;
   @Input() securityEnabled: boolean = false;
   @Input() hideCC = false;
   @Input() hideBCC = false;
   @ViewChild("emailAddrDialog") emailAddrDialog: TemplateRef<any>;
   isIE = GuiTool.isIE();
   initialAddresses: string = "";
   subscriptions: Subscription = new Subscription();
   private _model: EmailPaneModel;

   get message(): string {
      return this.model?.message;
   }

   set message(value: string) {
      if(this.model) {
         this.model.message = value;
      }
   }

   constructor(private modalService: NgbModal) {
   }

   ngOnInit(): void {
      this.initForm();
   }

   private initForm(): void {
      this.form = !!this.form ? this.form : new UntypedFormGroup({});

      this.form.setControl("toAddress", new UntypedFormControl(this.model.toAddress, [
         Validators.required,
         FormValidators.emailList(",;", true, false, this.getEmailUsers()),
         FormValidators.duplicateTokens()
      ]));
      this.form.setControl("ccAddress", new UntypedFormControl(this.model.ccAddress, [
         FormValidators.emailList(",;", true, false, this.getEmailUsers()),
         FormValidators.duplicateTokens()
      ]));
      this.form.setControl("bccAddress", new UntypedFormControl(this.model.bccAddress, [
         FormValidators.emailList(",;", true, false, this.getEmailUsers()),
         FormValidators.duplicateTokens()
      ]));
      this.form.setControl("fromAddr", new UntypedFormControl(this.model.fromAddress, [
         Validators.required,
         FormValidators.emailSpecialCharacters,
         FormValidators.emailList(",;", true, false),
         FormValidators.duplicateTokens()
      ]));

      if(!this.model.fromAddressEnabled) {
         this.form.get("fromAddr").disable();
      }
      else {
         this.form.get("fromAddr").enable();
      }

      this.subscriptions.add(this.form.get("toAddress").valueChanges.subscribe((v) => {
         this.model.toAddress = v;
      }));
      this.subscriptions.add(this.form.get("ccAddress").valueChanges.subscribe((v) => {
         this.model.ccAddress = v;
      }));
      this.subscriptions.add(this.form.get("bccAddress").valueChanges.subscribe((v) => {
         this.model.bccAddress = v;
      }));
      this.subscriptions.add(this.form.get("fromAddr").valueChanges.subscribe((v) => {
         this.model.fromAddress = v;
      }));

      if(this.isIE) {
         this.form.setControl("message", new UntypedFormControl(this.message));
         this.subscriptions.add(this.form.get("message").valueChanges.subscribe((v) => {
            this.message = v;
         }));
      }
   }

   ngOnDestroy(): void {
      if(!!this.subscriptions) {
         this.subscriptions.unsubscribe();
         this.subscriptions = null;
      }
   }

   getAddress(label: string): string {
      let address: string = null;

      switch(label) {
         case "to":
            address = "toAddress";
            break;
         case "cc":
            address = "ccAddress";
            break;
         case "bcc":
            address = "bccAddress";
            break;
         default:
      }

      return address;
   }

   selectEmails(label: string): void {
      let address: string = this.getAddress(label);

      if(!address) {
         return;
      }

      this.initialAddresses = this.model[address];
      const options: NgbModalOptions = {
         backdrop: "static",
         windowClass: "email-addr-dialog"
      };
      this.modalService.open(this.emailAddrDialog, options).result.then(
         (result: EmailDialogData) => {
            this.form.get(address).setValue(result.emails);
         },
         (reject: any) => {
            // canceled
         }
      );
   }

   addressSearch = (text: Observable<string>) => {
      return text.pipe(
         debounceTime(200),
         map((term: string) => {
            if(term && this.historyEmails) {
               return this.historyEmails
                  .filter(v => new RegExp(term, "gi").test(v))
                  .slice(0, 10);
            }

            return [];
         })
      );
   };

   private getEmailUsers(): string[] {
      let identities: string[] = [];

      if(this.model.users) {
         identities = identities.concat(this.model.users.map(user => user.name + Tool.USER_SUFFIX));
      }

      if(this.model.groups) {
         identities = identities.concat(this.model.groups.map(user => user + Tool.GROUP_SUFFIX));
      }

      if(this.model.emailGroups) {
         identities = identities.concat(this.model.emailGroups.map(user => user.name + Tool.GROUP_SUFFIX));
      }

      return identities;
   }
}
