/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

/**
 * EmailPane - Pass 1: Interaction
 *
 * Coverage:
 *   Group 1 - initForm control creation and from-address enablement
 *   Group 2 - control subscriptions synchronize back into the model
 *   Group 3 - selectEmails modal flow updates the targeted address control
 *   Group 4 - addressSearch filters history emails
 *   Group 5 - ngOnDestroy cleanup
 */

import { UntypedFormGroup } from "@angular/forms";
import { firstValueFrom } from "rxjs";
import { of } from "rxjs";

import { EmailPaneModel } from "../../vsobjects/model/email-pane-model";
import { EmailPane } from "./email-pane.component";

function makeModel(overrides: Partial<EmailPaneModel> = {}): EmailPaneModel {
   return {
      toAddress: "to@example.com",
      ccAddress: "cc@example.com",
      bccAddress: "bcc@example.com",
      fromAddress: "from@example.com",
      fromAddressEnabled: false,
      subject: "Subject",
      message: "Hello",
      userDialogEnabled: true,
      emailAddrDialogModel: {} as any,
      users: [{ name: "alice", orgID: null }],
      groups: ["admins"],
      emailGroups: [{ name: "mailing", orgID: null }],
      ...overrides,
   };
}

function createComponent(overrides: Partial<EmailPane> = {}) {
   const modalService = {
      open: vi.fn().mockReturnValue({
         result: Promise.resolve({ emails: "picked@example.com", type: "EMAIL" }),
      }),
   };
   const comp = new EmailPane(modalService as any);
   comp.model = makeModel();
   comp.historyEmails = ["alice@example.com", "bob@example.com", "admin@example.com"];
   Object.assign(comp, overrides);
   return { comp, modalService };
}

afterEach(() => vi.restoreAllMocks());

describe("EmailPane - interaction", () => {
   describe("Group 1 - initForm", () => {
      it("should create address controls and disable fromAddr when fromAddressEnabled is false", () => {
         const { comp } = createComponent();

         comp.ngOnInit();

         expect(Object.keys(comp.form.controls)).toEqual(
            expect.arrayContaining(["toAddress", "ccAddress", "bccAddress", "fromAddr"]),
         );
         expect(comp.form.get("fromAddr").disabled).toBe(true);
      });

      it("should enable fromAddr when fromAddressEnabled is true", () => {
         const { comp } = createComponent({ model: makeModel({ fromAddressEnabled: true }) });

         comp.ngOnInit();

         expect(comp.form.get("fromAddr").enabled).toBe(true);
      });
   });

   describe("Group 2 - subscriptions", () => {
      it("should synchronize form value changes back into the model", () => {
         const { comp } = createComponent();
         comp.ngOnInit();

         comp.form.get("toAddress").setValue("new-to@example.com");
         comp.form.get("ccAddress").setValue("new-cc@example.com");
         comp.form.get("bccAddress").setValue("new-bcc@example.com");

         expect(comp.model.toAddress).toBe("new-to@example.com");
         expect(comp.model.ccAddress).toBe("new-cc@example.com");
         expect(comp.model.bccAddress).toBe("new-bcc@example.com");
      });
   });

   describe("Group 3 - selectEmails", () => {
      it("should open the dialog and write the returned emails into the requested field", async () => {
         const { comp, modalService } = createComponent({ form: new UntypedFormGroup({}) as any });
         comp.emailAddrDialog = {} as any;
         comp.ngOnInit();

         comp.selectEmails("cc");
         await Promise.resolve();

         expect(comp.initialAddresses).toBe("cc@example.com");
         expect(modalService.open).toHaveBeenCalledTimes(1);
         expect(comp.form.get("ccAddress").value).toBe("picked@example.com");
      });
   });

   describe("Group 4 - addressSearch", () => {
      it("should return up to ten history emails matching the search term", async () => {
         const { comp } = createComponent();

         await expect(firstValueFrom(comp.addressSearch(of("ali")))).resolves.toEqual(["alice@example.com"]);
      });
   });

   describe("Group 5 - ngOnDestroy", () => {
      it("should unsubscribe from all registered form subscriptions", () => {
         const { comp } = createComponent();
         comp.ngOnInit();
         const unsubscribeSpy = vi.spyOn(comp.subscriptions, "unsubscribe");

         comp.ngOnDestroy();

         expect(unsubscribeSpy).toHaveBeenCalledTimes(1);
         expect((comp as any).subscriptions).toBeNull();
      });
   });
});
