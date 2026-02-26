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
import {
   COPY_PASTE_CONTEXT_REPOSITORY,
   COPY_PASTE_CONTEXT_SECURITY_ACTIONS
} from "./copy-paste-context";

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
         expect(service.canPaste(COPY_PASTE_CONTEXT_REPOSITORY)).toBe(false);
      });

      it("should be true when paste context matches copy context", () => {
         service.copy([createPermission("admin", [ResourceAction.READ])], false, "provider", COPY_PASTE_CONTEXT_REPOSITORY);
         expect(service.canPaste(COPY_PASTE_CONTEXT_REPOSITORY)).toBe(true);
      });

      it("should be false when paste context differs from copy context", () => {
         service.copy([createPermission("admin", [ResourceAction.READ])], false, "provider", COPY_PASTE_CONTEXT_SECURITY_ACTIONS);
         expect(service.canPaste(COPY_PASTE_CONTEXT_REPOSITORY)).toBe(false);
      });

      it("should be false when no context is set on either side", () => {
         service.copy([createPermission("admin", [ResourceAction.READ])], false, "provider");
         // null context is not a valid clipboard slot; an explicit context is required
         expect(service.canPaste()).toBe(false);
      });

      it("should be false when paste has no context but copy does", () => {
         service.copy([createPermission("admin", [ResourceAction.READ])], false, "provider", COPY_PASTE_CONTEXT_REPOSITORY);
         expect(service.canPaste()).toBe(false);
      });

      it("should be false when copy has no context but paste does", () => {
         service.copy([createPermission("admin", [ResourceAction.READ])], false, "provider");
         expect(service.canPaste(COPY_PASTE_CONTEXT_REPOSITORY)).toBe(false);
      });
   });

   describe("copiedCount", () => {
      it("should be 0 initially", () => {
         expect(service.copiedCount(COPY_PASTE_CONTEXT_REPOSITORY, [ResourceAction.READ])).toBe(0);
      });

      it("should reflect the number of copied entries", () => {
         service.copy([
            createPermission("admin", [ResourceAction.READ]),
            createPermission("editors", [ResourceAction.READ], IdentityType.GROUP)
         ], false, "provider", COPY_PASTE_CONTEXT_REPOSITORY);
         expect(service.copiedCount(COPY_PASTE_CONTEXT_REPOSITORY, [ResourceAction.READ])).toBe(2);
      });

      it("should be 0 after copying an empty array", () => {
         service.copy([], false, "provider", COPY_PASTE_CONTEXT_REPOSITORY);
         expect(service.copiedCount(COPY_PASTE_CONTEXT_REPOSITORY, [ResourceAction.READ])).toBe(0);
      });

      it("should be 0 when context does not match", () => {
         service.copy([createPermission("admin", [ResourceAction.READ])], false, "provider", COPY_PASTE_CONTEXT_SECURITY_ACTIONS);
         expect(service.copiedCount(COPY_PASTE_CONTEXT_REPOSITORY, [ResourceAction.READ])).toBe(0);
      });

      it("should return count when context matches", () => {
         service.copy([
            createPermission("admin", [ResourceAction.READ]),
            createPermission("editors", [ResourceAction.READ], IdentityType.GROUP)
         ], false, "provider", COPY_PASTE_CONTEXT_REPOSITORY);
         expect(service.copiedCount(COPY_PASTE_CONTEXT_REPOSITORY, [ResourceAction.READ])).toBe(2);
      });

      it("should return post-filter count when displayActions provided", () => {
         service.copy([
            createPermission("admin", [ResourceAction.READ, ResourceAction.WRITE]),
            createPermission("editors", [ResourceAction.ACCESS], IdentityType.GROUP)
         ], false, "provider", COPY_PASTE_CONTEXT_REPOSITORY);

         // Only one row has actions that overlap with [READ, WRITE]
         expect(service.copiedCount(COPY_PASTE_CONTEXT_REPOSITORY, [ResourceAction.READ, ResourceAction.WRITE])).toBe(1);
      });

      it("should return 0 when no rows survive the displayActions filter", () => {
         service.copy([
            createPermission("admin", [ResourceAction.ACCESS, ResourceAction.ADMIN])
         ], false, "provider", COPY_PASTE_CONTEXT_REPOSITORY);

         expect(service.copiedCount(COPY_PASTE_CONTEXT_REPOSITORY, [ResourceAction.READ, ResourceAction.WRITE])).toBe(0);
      });

      it("should return 0 when displayActions is null (model not yet loaded)", () => {
         service.copy([
            createPermission("admin", [ResourceAction.READ]),
            createPermission("editors", [ResourceAction.ACCESS], IdentityType.GROUP)
         ], false, "provider", COPY_PASTE_CONTEXT_REPOSITORY);

         expect(service.copiedCount(COPY_PASTE_CONTEXT_REPOSITORY, null)).toBe(0);
      });
   });

   describe("copiedTotal", () => {
      it("should be 0 initially", () => {
         expect(service.copiedTotal(COPY_PASTE_CONTEXT_REPOSITORY)).toBe(0);
      });

      it("should return the raw row count regardless of displayActions", () => {
         service.copy([
            createPermission("admin", [ResourceAction.READ, ResourceAction.WRITE]),
            createPermission("editors", [ResourceAction.ACCESS], IdentityType.GROUP),
            createPermission("viewers", [ResourceAction.READ])
         ], false, "provider", COPY_PASTE_CONTEXT_REPOSITORY);

         expect(service.copiedTotal(COPY_PASTE_CONTEXT_REPOSITORY)).toBe(3);
      });

      it("should differ from copiedCount when action filtering removes rows", () => {
         service.copy([
            createPermission("admin", [ResourceAction.READ, ResourceAction.WRITE]),
            createPermission("editors", [ResourceAction.ACCESS], IdentityType.GROUP)
         ], false, "provider", COPY_PASTE_CONTEXT_REPOSITORY);

         // Only one row overlaps with [READ, WRITE], but total is always 2
         expect(service.copiedCount(COPY_PASTE_CONTEXT_REPOSITORY, [ResourceAction.READ, ResourceAction.WRITE])).toBe(1);
         expect(service.copiedTotal(COPY_PASTE_CONTEXT_REPOSITORY)).toBe(2);
      });

      it("should equal copiedCount when no rows are filtered out", () => {
         service.copy([
            createPermission("admin", [ResourceAction.READ]),
            createPermission("editors", [ResourceAction.READ], IdentityType.GROUP)
         ], false, "provider", COPY_PASTE_CONTEXT_REPOSITORY);

         expect(service.copiedCount(COPY_PASTE_CONTEXT_REPOSITORY, [ResourceAction.READ])).toBe(2);
         expect(service.copiedTotal(COPY_PASTE_CONTEXT_REPOSITORY)).toBe(2);
      });

      it("should be 0 when context does not match", () => {
         service.copy([createPermission("admin", [ResourceAction.READ])], false, "provider", COPY_PASTE_CONTEXT_SECURITY_ACTIONS);
         expect(service.copiedTotal(COPY_PASTE_CONTEXT_REPOSITORY)).toBe(0);
      });

      it("should be 0 when context is null", () => {
         service.copy([createPermission("admin", [ResourceAction.READ])], false, "provider", COPY_PASTE_CONTEXT_REPOSITORY);
         expect(service.copiedTotal(null)).toBe(0);
      });
   });

   describe("copy", () => {
      it("should deep-clone permissions into clipboard", () => {
         const perm = createPermission("admin", [ResourceAction.READ, ResourceAction.WRITE]);
         service.copy([perm], true, "provider", COPY_PASTE_CONTEXT_REPOSITORY);

         // Modify the original to verify the copy is independent
         perm.actions.push(ResourceAction.DELETE);

         const result = service.paste(COPY_PASTE_CONTEXT_REPOSITORY, [ResourceAction.READ, ResourceAction.WRITE, ResourceAction.DELETE]);
         expect(result.permissions[0].actions).toEqual([ResourceAction.READ, ResourceAction.WRITE]);
         expect(result.requiresBoth).toBe(true);
      });
   });

   describe("paste", () => {
      it("should return null when clipboard is empty", () => {
         expect(service.paste(COPY_PASTE_CONTEXT_REPOSITORY, [ResourceAction.READ])).toBeNull();
      });

      it("should return null when displayActions is null (model not yet loaded)", () => {
         service.copy([createPermission("admin", [ResourceAction.READ])], false, "provider", COPY_PASTE_CONTEXT_REPOSITORY);

         expect(service.paste(COPY_PASTE_CONTEXT_REPOSITORY, null)).toBeNull();
      });

      it("should return null when context does not match", () => {
         service.copy([createPermission("admin", [ResourceAction.READ])], false, "provider", COPY_PASTE_CONTEXT_SECURITY_ACTIONS);
         expect(service.paste(COPY_PASTE_CONTEXT_REPOSITORY, [ResourceAction.READ])).toBeNull();
      });

      it("should replace target permissions with copied ones", () => {
         service.copy([
            createPermission("admin", [ResourceAction.READ, ResourceAction.WRITE]),
            createPermission("editors", [ResourceAction.READ], IdentityType.GROUP)
         ], true, "provider", COPY_PASTE_CONTEXT_REPOSITORY);

         const result = service.paste(COPY_PASTE_CONTEXT_REPOSITORY, [ResourceAction.READ, ResourceAction.WRITE, ResourceAction.DELETE]);

         expect(result.permissions.length).toBe(2);
         expect(result.permissions[0].identityID.name).toBe("admin");
         expect(result.permissions[1].identityID.name).toBe("editors");
         expect(result.requiresBoth).toBe(true);
      });

      it("should remove rows whose actions have no overlap with target displayActions", () => {
         service.copy([
            createPermission("admin", [ResourceAction.READ, ResourceAction.WRITE, ResourceAction.DELETE])
         ], false, "provider", COPY_PASTE_CONTEXT_REPOSITORY);

         // Target only supports ACCESS — no overlap, so the row is dropped entirely
         const result = service.paste(COPY_PASTE_CONTEXT_REPOSITORY, [ResourceAction.ACCESS]);

         expect(result.permissions).toEqual([]);
      });

      it("should keep only matching actions when there is partial overlap", () => {
         service.copy([
            createPermission("admin", [ResourceAction.READ, ResourceAction.DELETE])
         ], false, "provider", COPY_PASTE_CONTEXT_REPOSITORY);

         // Target supports READ and ADMIN (not DELETE)
         const result = service.paste(COPY_PASTE_CONTEXT_REPOSITORY, [ResourceAction.READ, ResourceAction.ADMIN]);

         expect(result.permissions[0].actions).toEqual([ResourceAction.READ]);
      });

      it("should expand ADMIN row to all target displayActions when ADMIN is present", () => {
         // Copying from a viewsheet (READ, WRITE, DELETE, SHARE, ADMIN — no ACCESS)
         service.copy([
            createPermission("alice", [ResourceAction.READ, ResourceAction.WRITE, ResourceAction.DELETE, ResourceAction.SHARE, ResourceAction.ADMIN])
         ], false, "provider", COPY_PASTE_CONTEXT_REPOSITORY);

         // Pasting onto a portal dashboard whose displayActions are [ACCESS, ADMIN]
         const result = service.paste(COPY_PASTE_CONTEXT_REPOSITORY, [ResourceAction.ACCESS, ResourceAction.ADMIN]);

         // ADMIN implies full access, so the result should include ACCESS even though
         // the copied row had no ACCESS action
         expect(result.permissions[0].actions).toEqual([ResourceAction.ACCESS, ResourceAction.ADMIN]);
      });

      it("should not expand non-ADMIN rows to all target displayActions", () => {
         service.copy([
            createPermission("alice", [ResourceAction.READ, ResourceAction.WRITE])
         ], false, "provider", COPY_PASTE_CONTEXT_REPOSITORY);

         // Target supports ACCESS and ADMIN — no READ/WRITE
         const result = service.paste(COPY_PASTE_CONTEXT_REPOSITORY, [ResourceAction.ACCESS, ResourceAction.ADMIN]);

         // No overlap and no ADMIN in source row, so the row is dropped entirely
         expect(result.permissions).toEqual([]);
      });

      it("should drop an ADMIN-only row when the target does not support ADMIN", () => {
         service.copy([
            createPermission("alice", [ResourceAction.ADMIN])
         ], false, "provider", COPY_PASTE_CONTEXT_REPOSITORY);

         // Target has no ADMIN — expansion guard fails, intersection filter removes ADMIN,
         // leaving the row empty so it is dropped entirely
         const result = service.paste(COPY_PASTE_CONTEXT_REPOSITORY, [ResourceAction.READ, ResourceAction.WRITE]);

         expect(result.permissions).toEqual([]);
      });

      it("should preserve requiresBoth from the copied source", () => {
         service.copy([createPermission("admin", [ResourceAction.READ])], true, "provider", COPY_PASTE_CONTEXT_REPOSITORY);

         const result = service.paste(COPY_PASTE_CONTEXT_REPOSITORY, [ResourceAction.READ]);
         expect(result.requiresBoth).toBe(true);
      });

      it("should allow pasting multiple times from the same clipboard", () => {
         service.copy([createPermission("admin", [ResourceAction.READ])], false, "provider", COPY_PASTE_CONTEXT_REPOSITORY);

         const result1 = service.paste(COPY_PASTE_CONTEXT_REPOSITORY, [ResourceAction.READ]);
         expect(result1.permissions.length).toBe(1);

         const result2 = service.paste(COPY_PASTE_CONTEXT_REPOSITORY, [ResourceAction.READ]);
         expect(result2.permissions.length).toBe(1);
         expect(result2.permissions[0].identityID.name).toBe("admin");
      });

      it("should deep-clone on each paste so results are independent", () => {
         service.copy([createPermission("admin", [ResourceAction.READ])], false, "provider", COPY_PASTE_CONTEXT_REPOSITORY);

         const result1 = service.paste(COPY_PASTE_CONTEXT_REPOSITORY, [ResourceAction.READ]);
         result1.permissions[0].actions.push(ResourceAction.WRITE);

         const result2 = service.paste(COPY_PASTE_CONTEXT_REPOSITORY, [ResourceAction.READ]);
         expect(result2.permissions[0].actions).toEqual([ResourceAction.READ]);
      });

      it("should return non-null with empty permissions when an empty array was copied", () => {
         service.copy([], false, "provider", COPY_PASTE_CONTEXT_REPOSITORY);

         // canPaste is true because the wrapper object is non-null,
         // even though there is nothing in the clipboard.
         expect(service.canPaste(COPY_PASTE_CONTEXT_REPOSITORY)).toBe(true);
         expect(service.copiedCount(COPY_PASTE_CONTEXT_REPOSITORY, [ResourceAction.READ])).toBe(0);

         const result = service.paste(COPY_PASTE_CONTEXT_REPOSITORY, [ResourceAction.READ]);

         // paste() returns a result object (not null) with an empty permissions array.
         expect(result).not.toBeNull();
         expect(result.permissions).toEqual([]);
         expect(result.requiresBoth).toBe(false);
      });
   });

   describe("org change", () => {
      it("should clear clipboard when the provider changes", () => {
         service.copy([createPermission("admin", [ResourceAction.READ])], false, "provider-a", COPY_PASTE_CONTEXT_REPOSITORY);
         expect(service.canPaste(COPY_PASTE_CONTEXT_REPOSITORY)).toBe(true);

         refreshSubject.next({ provider: "provider-b", providerChanged: true });

         expect(service.canPaste(COPY_PASTE_CONTEXT_REPOSITORY)).toBe(false);
      });

      it("should not clear clipboard when the provider stays the same", () => {
         service.copy([createPermission("admin", [ResourceAction.READ])], false, "provider-a", COPY_PASTE_CONTEXT_REPOSITORY);

         refreshSubject.next({ provider: "provider-a", providerChanged: false });

         expect(service.canPaste(COPY_PASTE_CONTEXT_REPOSITORY)).toBe(true);
      });

      it("should not clear clipboard when nothing has been copied yet", () => {
         refreshSubject.next({ provider: "provider-b", providerChanged: true });

         expect(service.canPaste(COPY_PASTE_CONTEXT_REPOSITORY)).toBe(false);
      });
   });
});
