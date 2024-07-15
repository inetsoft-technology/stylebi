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
   AfterViewInit,
   ChangeDetectorRef,
   Component,
   EventEmitter,
   Input,
   OnChanges,
   Output,
   SimpleChanges,
   ViewChild,
} from "@angular/core";
import { UntypedFormGroup } from "@angular/forms";
import { NgbNav } from "@ng-bootstrap/ng-bootstrap";
import { NgbNavChangeEvent } from "@ng-bootstrap/ng-bootstrap/nav/nav";
import { EmailAddrDialogModel } from "./email-addr-dialog-model";
import { EmbeddedEmailPane } from "./embedded-email-pane.component";

export interface EmailDialogData {
   emails?: string;
   type?: string;
}

@Component({
   selector: "email-addr-dialog",
   templateUrl: "email-addr-dialog.component.html"
})
export class EmailAddrDialog implements OnChanges, AfterViewInit {
   @Input() embeddedOnly: boolean = true;
   @Input() model: EmailAddrDialogModel;
   @Input() addresses: string = "";
   @Output() onCommit: EventEmitter<EmailDialogData> = new EventEmitter<EmailDialogData>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @ViewChild("embeddedEmailPane") emailPane: EmbeddedEmailPane;
   @ViewChild("nav") tabs: NgbNav;
   queryType: string = null;
   queryAddress: string = null;
   embeddedAddress: string = null;
   selectedTabIndex: number = 0;
   form: UntypedFormGroup;
   formValid = () => !!this.form && this.form.valid;

   constructor(private changeDetectorRef: ChangeDetectorRef) {
      this.form = new UntypedFormGroup({
         emailForm: new UntypedFormGroup({}),
      });
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes["addresses"]) {
         const items: string[] = this.addresses == null ? [] : this.addresses.split(",");
         let queryTab = items.length > 0 && items[0].startsWith("query: ");
         this.selectedTabIndex = !queryTab ? 0 : 1;

         if(queryTab) {
            this.queryAddress = this.addresses;
         }
         else {
            this.embeddedAddress = this.addresses;
         }
      }
   }

   ngAfterViewInit() {
      if(this.addresses) {
         if(this.selectedTabIndex == 1) {
            this.queryAddress = this.addresses;
            this.setTab(false);
         }
         else {
            this.embeddedAddress = this.addresses;
            this.setTab(true);
         }
      }

      this.changeDetectorRef.detectChanges();
   }

   private setTab(embedded: boolean): void {
      if(this.tabs) {
         const tab: string = embedded ? "embedded-email-tab" : "query-email-tab";
         this.tabs.select(tab);
      }
   }

   close(): void {
      this.onCancel.emit("cancel");
   }

   ok(): void {
      // query not changed, don't submit with an empty queryType
      if(this.queryType == null && this.addresses.startsWith("query:")) {
         this.close();
      }
      else {
         this.onCommit.emit(<EmailDialogData>{emails: this.addresses, type: this.queryType});
      }
   }

   reset(): void {
      if(this.emailPane) {
         this.emailPane.reset();
      }
   }

   changeTab(event: NgbNavChangeEvent<string>): void {
      this.selectedTabIndex = event && event.nextId == "embedded-email-tab" ? 0 : 1;
   }

   updateEmbeddedAddressString(addresses: string): void {
      if(this.selectedTabIndex == 1) {
         return;
      }

      this.addresses = this.embeddedAddress = addresses;
   }

   changeAddressString(result: EmailDialogData): void {
      this.addresses = result.emails;
      this.queryType = result.type;
   }
}
