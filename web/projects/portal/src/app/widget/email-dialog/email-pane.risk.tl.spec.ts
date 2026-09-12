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
 * EmailPane - Pass 2: Risk
 *
 * Coverage:
 *   Group 1 - getAddress mapping and invalid-label guard
 *   Group 2 - getEmailUsers suffix composition
 *   Group 3 - IE fallback creates a message control that syncs to model.message
 *   Group 4 - selectEmails ignores invalid labels without opening a modal
 */

import { Tool } from "../../../../../shared/util/tool";
import { EmailPaneModel } from "../../vsobjects/model/email-pane-model";
import { EmailPane } from "./email-pane.component";

function makeModel(overrides: Partial<EmailPaneModel> = {}): EmailPaneModel {
   return {
      toAddress: "to@example.com",
      ccAddress: "cc@example.com",
      bccAddress: "bcc@example.com",
      fromAddress: "from@example.com",
      fromAddressEnabled: true,
      subject: "Subject",
      message: "Body",
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
         result: Promise.resolve({ emails: "picked@example.com" }),
      }),
   };
   const comp = new EmailPane(modalService as any);
   comp.model = makeModel();
   Object.assign(comp, overrides);
   return { comp, modalService };
}

afterEach(() => vi.restoreAllMocks());

describe("EmailPane - risk", () => {
   describe("Group 1 - getAddress", () => {
      it("should map supported labels to their form control names", () => {
         const { comp } = createComponent();

         expect(comp.getAddress("to")).toBe("toAddress");
         expect(comp.getAddress("cc")).toBe("ccAddress");
         expect(comp.getAddress("bcc")).toBe("bccAddress");
      });

      it("should return null for unsupported labels", () => {
         const { comp } = createComponent();

         expect(comp.getAddress("replyTo")).toBeNull();
      });
   });

   describe("Group 2 - getEmailUsers", () => {
      it("should combine user, group, and email-group identities with the correct suffixes", () => {
         const { comp } = createComponent();

         // getEmailUsers is a private helper — cast needed to verify identity suffix composition
         expect((comp as any).getEmailUsers()).toEqual([
            `alice${Tool.USER_SUFFIX}`,
            `admins${Tool.GROUP_SUFFIX}`,
            `mailing${Tool.GROUP_SUFFIX}`,
         ]);
      });
   });

   describe("Group 3 - IE fallback", () => {
      it("should create a message form control and sync it back to model.message in IE mode", () => {
         const { comp } = createComponent();
         comp.isIE = true;

         comp.ngOnInit();
         comp.form.get("message").setValue("Updated message");

         expect(comp.form.contains("message")).toBe(true);
         expect(comp.model.message).toBe("Updated message");
      });
   });

   describe("Group 4 - invalid label guard", () => {
      it("should not open the modal when the requested address slot is unsupported", () => {
         const { comp, modalService } = createComponent();
         comp.ngOnInit();

         comp.selectEmails("replyTo");

         expect(modalService.open).not.toHaveBeenCalled();
      });
   });
});
