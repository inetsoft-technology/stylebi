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
import { IdentityType } from "../../../../../../shared/data/identity-type";
import { ResourceAction } from "../../../../../../shared/util/security/resource-permission/resource-action.enum";
import { ResourcePermissionTableModel } from "./resource-permission-table-model";
import { PermissionClipboardService } from "./permission-clipboard.service";

describe("PermissionClipboardService", () => {
   let service: PermissionClipboardService;

   function createPermission(name: string, actions: ResourceAction[], type = IdentityType.USER): ResourcePermissionTableModel {
      return {
         identityID: { name, orgID: null },
         type,
         actions
      };
   }

   beforeEach(() => {
      service = new PermissionClipboardService();
   });

   describe("canPaste", () => {
      it("should be false initially", () => {
         expect(service.canPaste).toBe(false);
      });

      it("should be true after copying permissions", () => {
         service.copy([createPermission("admin", [ResourceAction.READ])], false);
         expect(service.canPaste).toBe(true);
      });
   });

   describe("copy", () => {
      it("should deep-clone permissions into clipboard", () => {
         const perm = createPermission("admin", [ResourceAction.READ, ResourceAction.WRITE]);
         service.copy([perm], true);

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

      it("should replace target permissions with copied ones", () => {
         service.copy([
            createPermission("admin", [ResourceAction.READ, ResourceAction.WRITE]),
            createPermission("editors", [ResourceAction.READ], IdentityType.GROUP)
         ], true);

         const result = service.paste([ResourceAction.READ, ResourceAction.WRITE, ResourceAction.DELETE]);

         expect(result.permissions.length).toBe(2);
         expect(result.permissions[0].identityID.name).toBe("admin");
         expect(result.permissions[1].identityID.name).toBe("editors");
         expect(result.requiresBoth).toBe(true);
      });

      it("should filter actions to target displayActions", () => {
         service.copy([
            createPermission("admin", [ResourceAction.READ, ResourceAction.WRITE, ResourceAction.DELETE])
         ], false);

         // Target only supports ACCESS — no overlap
         const result = service.paste([ResourceAction.ACCESS]);

         // Falls back to all target displayActions
         expect(result.permissions[0].actions).toEqual([ResourceAction.ACCESS]);
      });

      it("should keep only matching actions when there is partial overlap", () => {
         service.copy([
            createPermission("admin", [ResourceAction.READ, ResourceAction.DELETE])
         ], false);

         // Target supports READ and ADMIN (not DELETE)
         const result = service.paste([ResourceAction.READ, ResourceAction.ADMIN]);

         expect(result.permissions[0].actions).toEqual([ResourceAction.READ]);
      });

      it("should preserve requiresBoth from the copied source", () => {
         service.copy([createPermission("admin", [ResourceAction.READ])], true);

         const result = service.paste([ResourceAction.READ]);
         expect(result.requiresBoth).toBe(true);
      });

      it("should allow pasting multiple times from the same clipboard", () => {
         service.copy([createPermission("admin", [ResourceAction.READ])], false);

         const result1 = service.paste([ResourceAction.READ]);
         expect(result1.permissions.length).toBe(1);

         const result2 = service.paste([ResourceAction.READ]);
         expect(result2.permissions.length).toBe(1);
         expect(result2.permissions[0].identityID.name).toBe("admin");
      });

      it("should deep-clone on each paste so results are independent", () => {
         service.copy([createPermission("admin", [ResourceAction.READ])], false);

         const result1 = service.paste([ResourceAction.READ]);
         result1.permissions[0].actions.push(ResourceAction.WRITE);

         const result2 = service.paste([ResourceAction.READ]);
         expect(result2.permissions[0].actions).toEqual([ResourceAction.READ]);
      });
   });
});
