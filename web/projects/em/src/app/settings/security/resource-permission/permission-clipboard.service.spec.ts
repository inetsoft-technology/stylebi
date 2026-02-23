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
import { ResourceAction } from "../../../../../../shared/util/security/resource-permission/resource-action.enum";
import { ResourcePermissionTableModel } from "./resource-permission-table-model";
import { PermissionClipboardService } from "./permission-clipboard.service";

describe("PermissionClipboardService", () => {
   let service: PermissionClipboardService;
   let refreshSubject: Subject<any>;

   function createPermission(name: string, actions: ResourceAction[], type = IdentityType.USER): ResourcePermissionTableModel {
      return {
         identityID: { name, orgID: null },
         type,
         actions
      };
   }

   beforeEach(() => {
      refreshSubject = new Subject<any>();
      const mockOrgDropdownService = { onRefresh: refreshSubject } as any;
      service = new PermissionClipboardService(mockOrgDropdownService);
   });

   describe("canPaste", () => {
      it("should be false initially", () => {
         expect(service.canPaste()).toBe(false);
      });

      it("should be true after copying permissions", () => {
         service.copy([createPermission("admin", [ResourceAction.READ])], false, "provider");
         expect(service.canPaste()).toBe(true);
      });

      it("should be true when paste context matches copy context", () => {
         service.copy([createPermission("admin", [ResourceAction.READ])], false, "provider", "repository");
         expect(service.canPaste("repository")).toBe(true);
      });

      it("should be false when paste context differs from copy context", () => {
         service.copy([createPermission("admin", [ResourceAction.READ])], false, "provider", "security-actions");
         expect(service.canPaste("repository")).toBe(false);
      });

      it("should be true when no context is set on either side", () => {
         service.copy([createPermission("admin", [ResourceAction.READ])], false, "provider");
         expect(service.canPaste()).toBe(true);
      });

      it("should be false when paste has no context but copy does", () => {
         service.copy([createPermission("admin", [ResourceAction.READ])], false, "provider", "repository");
         expect(service.canPaste()).toBe(false);
      });

      it("should be false when copy has no context but paste does", () => {
         service.copy([createPermission("admin", [ResourceAction.READ])], false, "provider");
         expect(service.canPaste("repository")).toBe(false);
      });
   });

   describe("copiedCount", () => {
      it("should be 0 initially", () => {
         expect(service.copiedCount()).toBe(0);
      });

      it("should reflect the number of copied entries", () => {
         service.copy([
            createPermission("admin", [ResourceAction.READ]),
            createPermission("editors", [ResourceAction.READ], IdentityType.GROUP)
         ], false, "provider");
         expect(service.copiedCount()).toBe(2);
      });

      it("should be 0 after copying an empty array", () => {
         service.copy([], false, "provider");
         expect(service.copiedCount()).toBe(0);
      });

      it("should be 0 when context does not match", () => {
         service.copy([createPermission("admin", [ResourceAction.READ])], false, "provider", "security-actions");
         expect(service.copiedCount("repository")).toBe(0);
      });

      it("should return count when context matches", () => {
         service.copy([
            createPermission("admin", [ResourceAction.READ]),
            createPermission("editors", [ResourceAction.READ], IdentityType.GROUP)
         ], false, "provider", "repository");
         expect(service.copiedCount("repository")).toBe(2);
      });

      it("should return post-filter count when displayActions provided", () => {
         service.copy([
            createPermission("admin", [ResourceAction.READ, ResourceAction.WRITE]),
            createPermission("editors", [ResourceAction.ACCESS], IdentityType.GROUP)
         ], false, "provider");

         // Only one row has actions that overlap with [READ, WRITE]
         expect(service.copiedCount(null, [ResourceAction.READ, ResourceAction.WRITE])).toBe(1);
      });

      it("should return 0 when no rows survive the displayActions filter", () => {
         service.copy([
            createPermission("admin", [ResourceAction.ACCESS, ResourceAction.ADMIN])
         ], false, "provider");

         expect(service.copiedCount(null, [ResourceAction.READ, ResourceAction.WRITE])).toBe(0);
      });

      it("should return full count when displayActions is null", () => {
         service.copy([
            createPermission("admin", [ResourceAction.READ]),
            createPermission("editors", [ResourceAction.ACCESS], IdentityType.GROUP)
         ], false, "provider");

         expect(service.copiedCount(null, null)).toBe(2);
      });
   });

   describe("copy", () => {
      it("should deep-clone permissions into clipboard", () => {
         const perm = createPermission("admin", [ResourceAction.READ, ResourceAction.WRITE]);
         service.copy([perm], true, "provider");

         // Modify the original to verify the copy is independent
         perm.actions.push(ResourceAction.DELETE);

         const result = service.paste([ResourceAction.READ, ResourceAction.WRITE, ResourceAction.DELETE]);
         expect(result.permissions[0].actions).toEqual([ResourceAction.READ, ResourceAction.WRITE]);
         expect(result.requiresBoth).toBe(true);
      });
   });

   describe("paste", () => {
      it("should return null when clipboard is empty", () => {
         expect(service.paste([ResourceAction.READ])).toBeNull();
      });

      it("should return null when context does not match", () => {
         service.copy([createPermission("admin", [ResourceAction.READ])], false, "provider", "security-actions");
         expect(service.paste([ResourceAction.READ], "repository")).toBeNull();
      });

      it("should replace target permissions with copied ones", () => {
         service.copy([
            createPermission("admin", [ResourceAction.READ, ResourceAction.WRITE]),
            createPermission("editors", [ResourceAction.READ], IdentityType.GROUP)
         ], true, "provider");

         const result = service.paste([ResourceAction.READ, ResourceAction.WRITE, ResourceAction.DELETE]);

         expect(result.permissions.length).toBe(2);
         expect(result.permissions[0].identityID.name).toBe("admin");
         expect(result.permissions[1].identityID.name).toBe("editors");
         expect(result.requiresBoth).toBe(true);
      });

      it("should remove rows whose actions have no overlap with target displayActions", () => {
         service.copy([
            createPermission("admin", [ResourceAction.READ, ResourceAction.WRITE, ResourceAction.DELETE])
         ], false, "provider");

         // Target only supports ACCESS — no overlap, so the row is dropped entirely
         const result = service.paste([ResourceAction.ACCESS]);

         expect(result.permissions).toEqual([]);
      });

      it("should keep only matching actions when there is partial overlap", () => {
         service.copy([
            createPermission("admin", [ResourceAction.READ, ResourceAction.DELETE])
         ], false, "provider");

         // Target supports READ and ADMIN (not DELETE)
         const result = service.paste([ResourceAction.READ, ResourceAction.ADMIN]);

         expect(result.permissions[0].actions).toEqual([ResourceAction.READ]);
      });

      it("should preserve requiresBoth from the copied source", () => {
         service.copy([createPermission("admin", [ResourceAction.READ])], true, "provider");

         const result = service.paste([ResourceAction.READ]);
         expect(result.requiresBoth).toBe(true);
      });

      it("should allow pasting multiple times from the same clipboard", () => {
         service.copy([createPermission("admin", [ResourceAction.READ])], false, "provider");

         const result1 = service.paste([ResourceAction.READ]);
         expect(result1.permissions.length).toBe(1);

         const result2 = service.paste([ResourceAction.READ]);
         expect(result2.permissions.length).toBe(1);
         expect(result2.permissions[0].identityID.name).toBe("admin");
      });

      it("should deep-clone on each paste so results are independent", () => {
         service.copy([createPermission("admin", [ResourceAction.READ])], false, "provider");

         const result1 = service.paste([ResourceAction.READ]);
         result1.permissions[0].actions.push(ResourceAction.WRITE);

         const result2 = service.paste([ResourceAction.READ]);
         expect(result2.permissions[0].actions).toEqual([ResourceAction.READ]);
      });

      it("should return non-null with empty permissions when an empty array was copied", () => {
         service.copy([], false, "provider");

         // canPaste is true because the wrapper object is non-null,
         // even though there is nothing in the clipboard.
         expect(service.canPaste()).toBe(true);
         expect(service.copiedCount()).toBe(0);

         const result = service.paste([ResourceAction.READ]);

         // paste() returns a result object (not null) with an empty permissions array.
         expect(result).not.toBeNull();
         expect(result.permissions).toEqual([]);
         expect(result.requiresBoth).toBe(false);
      });
   });

   describe("org change", () => {
      it("should clear clipboard when the provider changes", () => {
         service.copy([createPermission("admin", [ResourceAction.READ])], false, "provider-a");
         expect(service.canPaste()).toBe(true);

         refreshSubject.next({ provider: "provider-b", providerChanged: true });

         expect(service.canPaste()).toBe(false);
      });

      it("should not clear clipboard when the provider stays the same", () => {
         service.copy([createPermission("admin", [ResourceAction.READ])], false, "provider-a");

         refreshSubject.next({ provider: "provider-a", providerChanged: false });

         expect(service.canPaste()).toBe(true);
      });

      it("should not clear clipboard when nothing has been copied yet", () => {
         refreshSubject.next({ provider: "provider-b", providerChanged: true });

         expect(service.canPaste()).toBe(false);
      });
   });
});
