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
import { Subject } from "rxjs";
import { IdentityType } from "../../../../../../shared/data/identity-type";
import { IdentityModel } from "./identity-model";
import {
   IdentityClipboardService,
   COPY_PASTE_CONTEXT_IDENTITY_MEMBERS,
   COPY_PASTE_CONTEXT_IDENTITY_ROLES,
   COPY_PASTE_CONTEXT_IDENTITY_PERMISSIONS
} from "./identity-clipboard.service";

describe("IdentityClipboardService", () => {
   let service: IdentityClipboardService;
   let refreshSubject: Subject<any>;
   let orgChangeSubject: Subject<void>;

   function createIdentity(name: string, type = IdentityType.USER): IdentityModel {
      return {
         identityID: { name, orgID: null },
         type
      };
   }

   beforeEach(() => {
      refreshSubject = new Subject<any>();
      orgChangeSubject = new Subject<void>();
      const mockOrgDropdownService = { onRefresh: refreshSubject, onOrgChange: orgChangeSubject } as any;
      service = new IdentityClipboardService(mockOrgDropdownService);
   });

   describe("canPaste", () => {
      it("should be false initially", () => {
         expect(service.canPaste(COPY_PASTE_CONTEXT_IDENTITY_MEMBERS)).toBe(false);
      });

      it("should be true when paste context matches copy context", () => {
         service.copy([createIdentity("alice")], COPY_PASTE_CONTEXT_IDENTITY_MEMBERS);
         expect(service.canPaste(COPY_PASTE_CONTEXT_IDENTITY_MEMBERS)).toBe(true);
      });

      it("should be false when paste context differs from copy context", () => {
         service.copy([createIdentity("alice")], COPY_PASTE_CONTEXT_IDENTITY_ROLES);
         expect(service.canPaste(COPY_PASTE_CONTEXT_IDENTITY_MEMBERS)).toBe(false);
      });

      it("should be false when no context is set on either side", () => {
         service.copy([createIdentity("alice")]);
         expect(service.canPaste()).toBe(false);
      });

      it("should be false when paste has no context but copy does", () => {
         service.copy([createIdentity("alice")], COPY_PASTE_CONTEXT_IDENTITY_MEMBERS);
         expect(service.canPaste()).toBe(false);
      });

      it("should be false when copy has no context but paste does", () => {
         service.copy([createIdentity("alice")]);
         expect(service.canPaste(COPY_PASTE_CONTEXT_IDENTITY_MEMBERS)).toBe(false);
      });

      it("should be true when paste context array includes the copy context", () => {
         service.copy([createIdentity("alice")], COPY_PASTE_CONTEXT_IDENTITY_MEMBERS);
         expect(service.canPaste([COPY_PASTE_CONTEXT_IDENTITY_ROLES, COPY_PASTE_CONTEXT_IDENTITY_MEMBERS])).toBe(true);
      });

      it("should be false when paste context array does not include the copy context", () => {
         service.copy([createIdentity("alice")], COPY_PASTE_CONTEXT_IDENTITY_PERMISSIONS);
         expect(service.canPaste([COPY_PASTE_CONTEXT_IDENTITY_ROLES, COPY_PASTE_CONTEXT_IDENTITY_MEMBERS])).toBe(false);
      });
   });

   describe("ngOnDestroy", () => {
      it("should stop responding to org change events after destroy", () => {
         service.copy([createIdentity("alice")], COPY_PASTE_CONTEXT_IDENTITY_MEMBERS);
         service.ngOnDestroy();

         orgChangeSubject.next();

         expect(service.canPaste(COPY_PASTE_CONTEXT_IDENTITY_MEMBERS)).toBe(true);
      });

      it("should stop responding to provider change events after destroy", () => {
         service.copy([createIdentity("alice")], COPY_PASTE_CONTEXT_IDENTITY_MEMBERS);
         service.ngOnDestroy();

         refreshSubject.next({ providerChanged: true });

         expect(service.canPaste(COPY_PASTE_CONTEXT_IDENTITY_MEMBERS)).toBe(true);
      });
   });

   describe("copiedCount", () => {
      it("should be 0 initially", () => {
         expect(service.copiedCount(COPY_PASTE_CONTEXT_IDENTITY_MEMBERS)).toBe(0);
      });

      it("should reflect the number of copied entries", () => {
         service.copy([
            createIdentity("alice"),
            createIdentity("admins", IdentityType.GROUP)
         ], COPY_PASTE_CONTEXT_IDENTITY_MEMBERS);
         expect(service.copiedCount(COPY_PASTE_CONTEXT_IDENTITY_MEMBERS)).toBe(2);
      });

      it("should be 0 after copying an empty array", () => {
         service.copy([], COPY_PASTE_CONTEXT_IDENTITY_MEMBERS);
         expect(service.copiedCount(COPY_PASTE_CONTEXT_IDENTITY_MEMBERS)).toBe(0);
      });

      it("should be 0 when context does not match", () => {
         service.copy([createIdentity("alice")], COPY_PASTE_CONTEXT_IDENTITY_ROLES);
         expect(service.copiedCount(COPY_PASTE_CONTEXT_IDENTITY_MEMBERS)).toBe(0);
      });

      it("should count only identities matching the typeFilter", () => {
         service.copy([
            createIdentity("alice", IdentityType.USER),
            createIdentity("admins", IdentityType.GROUP)
         ], COPY_PASTE_CONTEXT_IDENTITY_MEMBERS);
         expect(service.copiedCount(COPY_PASTE_CONTEXT_IDENTITY_MEMBERS, [IdentityType.USER])).toBe(1);
      });

      it("should return 0 when typeFilter matches no copied identities", () => {
         service.copy([createIdentity("alice", IdentityType.USER)], COPY_PASTE_CONTEXT_IDENTITY_MEMBERS);
         expect(service.copiedCount(COPY_PASTE_CONTEXT_IDENTITY_MEMBERS, [IdentityType.ROLE])).toBe(0);
      });
   });

   describe("copiedTotal", () => {
      it("should be 0 initially", () => {
         expect(service.copiedTotal(COPY_PASTE_CONTEXT_IDENTITY_MEMBERS)).toBe(0);
      });

      it("should return total count regardless of type", () => {
         service.copy([
            createIdentity("alice", IdentityType.USER),
            createIdentity("admins", IdentityType.GROUP)
         ], COPY_PASTE_CONTEXT_IDENTITY_MEMBERS);
         expect(service.copiedTotal(COPY_PASTE_CONTEXT_IDENTITY_MEMBERS)).toBe(2);
      });

      it("should be 0 when context does not match", () => {
         service.copy([createIdentity("alice")], COPY_PASTE_CONTEXT_IDENTITY_ROLES);
         expect(service.copiedTotal(COPY_PASTE_CONTEXT_IDENTITY_MEMBERS)).toBe(0);
      });
   });

   describe("copy", () => {
      it("should deep-clone identities into clipboard", () => {
         const identity = createIdentity("alice");
         service.copy([identity], COPY_PASTE_CONTEXT_IDENTITY_MEMBERS);

         // Mutate original after copy
         identity.identityID.name = "mutated";

         const result = service.paste(COPY_PASTE_CONTEXT_IDENTITY_MEMBERS);
         expect(result[0].identityID.name).toBe("alice");
      });
   });

   describe("paste", () => {
      it("should return null when clipboard is empty", () => {
         expect(service.paste(COPY_PASTE_CONTEXT_IDENTITY_MEMBERS)).toBeNull();
      });

      it("should return null when context does not match", () => {
         service.copy([createIdentity("alice")], COPY_PASTE_CONTEXT_IDENTITY_ROLES);
         expect(service.paste(COPY_PASTE_CONTEXT_IDENTITY_MEMBERS)).toBeNull();
      });

      it("should return the copied identities", () => {
         service.copy([
            createIdentity("alice"),
            createIdentity("admins", IdentityType.GROUP)
         ], COPY_PASTE_CONTEXT_IDENTITY_MEMBERS);

         const result = service.paste(COPY_PASTE_CONTEXT_IDENTITY_MEMBERS);

         expect(result.length).toBe(2);
         expect(result[0].identityID.name).toBe("alice");
         expect(result[1].identityID.name).toBe("admins");
      });

      it("should allow pasting multiple times from the same clipboard", () => {
         service.copy([createIdentity("alice")], COPY_PASTE_CONTEXT_IDENTITY_MEMBERS);

         const result1 = service.paste(COPY_PASTE_CONTEXT_IDENTITY_MEMBERS);
         expect(result1.length).toBe(1);

         const result2 = service.paste(COPY_PASTE_CONTEXT_IDENTITY_MEMBERS);
         expect(result2.length).toBe(1);
         expect(result2[0].identityID.name).toBe("alice");
      });

      it("should deep-clone on each paste so results are independent", () => {
         service.copy([createIdentity("alice")], COPY_PASTE_CONTEXT_IDENTITY_MEMBERS);

         const result1 = service.paste(COPY_PASTE_CONTEXT_IDENTITY_MEMBERS);
         result1[0].identityID.name = "mutated";

         const result2 = service.paste(COPY_PASTE_CONTEXT_IDENTITY_MEMBERS);
         expect(result2[0].identityID.name).toBe("alice");
      });

      it("should return an empty array when an empty array was copied", () => {
         service.copy([], COPY_PASTE_CONTEXT_IDENTITY_MEMBERS);

         expect(service.canPaste(COPY_PASTE_CONTEXT_IDENTITY_MEMBERS)).toBe(true);
         const result = service.paste(COPY_PASTE_CONTEXT_IDENTITY_MEMBERS);
         expect(result).not.toBeNull();
         expect(result).toEqual([]);
      });

      it("should not allow pasting members context into roles context", () => {
         service.copy([createIdentity("alice")], COPY_PASTE_CONTEXT_IDENTITY_MEMBERS);
         expect(service.paste(COPY_PASTE_CONTEXT_IDENTITY_ROLES)).toBeNull();
         expect(service.paste(COPY_PASTE_CONTEXT_IDENTITY_PERMISSIONS)).toBeNull();
      });

      it("should filter results by typeFilter when provided", () => {
         service.copy([
            createIdentity("alice", IdentityType.USER),
            createIdentity("admins", IdentityType.GROUP)
         ], COPY_PASTE_CONTEXT_IDENTITY_MEMBERS);

         const result = service.paste(COPY_PASTE_CONTEXT_IDENTITY_MEMBERS, [IdentityType.USER]);
         expect(result.length).toBe(1);
         expect(result[0].identityID.name).toBe("alice");
      });

      it("should return empty array when typeFilter matches nothing", () => {
         service.copy([createIdentity("alice", IdentityType.USER)], COPY_PASTE_CONTEXT_IDENTITY_MEMBERS);

         const result = service.paste(COPY_PASTE_CONTEXT_IDENTITY_MEMBERS, [IdentityType.ROLE]);
         expect(result).not.toBeNull();
         expect(result).toEqual([]);
      });

      it("should accept an array paste context matching the copy context", () => {
         service.copy([createIdentity("alice")], COPY_PASTE_CONTEXT_IDENTITY_MEMBERS);

         const result = service.paste([COPY_PASTE_CONTEXT_IDENTITY_ROLES, COPY_PASTE_CONTEXT_IDENTITY_MEMBERS]);
         expect(result).not.toBeNull();
         expect(result.length).toBe(1);
      });

      it("should return null when array paste context does not include the copy context", () => {
         service.copy([createIdentity("alice")], COPY_PASTE_CONTEXT_IDENTITY_PERMISSIONS);

         expect(service.paste([COPY_PASTE_CONTEXT_IDENTITY_ROLES, COPY_PASTE_CONTEXT_IDENTITY_MEMBERS])).toBeNull();
      });
   });

   describe("org change", () => {
      it("should clear clipboard when the provider changes", () => {
         service.copy([createIdentity("alice")], COPY_PASTE_CONTEXT_IDENTITY_MEMBERS);
         expect(service.canPaste(COPY_PASTE_CONTEXT_IDENTITY_MEMBERS)).toBe(true);

         refreshSubject.next({ provider: "provider-b", providerChanged: true });

         expect(service.canPaste(COPY_PASTE_CONTEXT_IDENTITY_MEMBERS)).toBe(false);
      });

      it("should not clear clipboard when providerChanged is false", () => {
         service.copy([createIdentity("alice")], COPY_PASTE_CONTEXT_IDENTITY_MEMBERS);

         refreshSubject.next({ provider: "provider-a", providerChanged: false });

         expect(service.canPaste(COPY_PASTE_CONTEXT_IDENTITY_MEMBERS)).toBe(true);
      });

      it("should not clear clipboard when nothing has been copied yet", () => {
         refreshSubject.next({ provider: "provider-b", providerChanged: true });

         expect(service.canPaste(COPY_PASTE_CONTEXT_IDENTITY_MEMBERS)).toBe(false);
      });

      it("should clear clipboard when the organization changes", () => {
         service.copy([createIdentity("alice")], COPY_PASTE_CONTEXT_IDENTITY_MEMBERS);
         expect(service.canPaste(COPY_PASTE_CONTEXT_IDENTITY_MEMBERS)).toBe(true);

         orgChangeSubject.next();

         expect(service.canPaste(COPY_PASTE_CONTEXT_IDENTITY_MEMBERS)).toBe(false);
      });

      it("should clear clipboard for all contexts when the organization changes", () => {
         service.copy([createIdentity("alice")], COPY_PASTE_CONTEXT_IDENTITY_ROLES);
         expect(service.canPaste(COPY_PASTE_CONTEXT_IDENTITY_ROLES)).toBe(true);

         orgChangeSubject.next();

         expect(service.canPaste(COPY_PASTE_CONTEXT_IDENTITY_ROLES)).toBe(false);
      });
   });
});
