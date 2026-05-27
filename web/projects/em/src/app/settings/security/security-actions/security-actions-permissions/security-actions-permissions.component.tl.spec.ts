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
 * SecurityActionsPermissionsComponent - Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - action input/loadPermissions: selected action must load the
 *                      correct permission model and preserve a cloned original for dirty checks.
 *   Group 2 [Risk 3] - save()/restore(): persistence and cancellation must emit parent state,
 *                      close the editor, and keep tableModel/originalModel separated.
 *   Group 3 [Risk 2] - error handlers: service failures must surface through snackbar and
 *                      rethrow the original error to the RxJS chain.
 *
 * Confirmed bugs (it.failing - remove wrapper once fixed):
 *   (none currently identified)
 *
 * KEY contracts:
 *   - action=null clears both tableModel and originalModel.
 *   - save() posts the current tableModel using action.type/resource/grant.
 *   - save() performs a follow-up getPermissions() access check after a successful save.
 *   - restore() emits unsavedChanges=false and closed, then restores a clean model snapshot.
 */

import { HttpErrorResponse } from "@angular/common/http";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { MatSnackBar } from "@angular/material/snack-bar";
import { render } from "@testing-library/angular";
import { Observable, of } from "rxjs";

import { Tool } from "../../../../../../../shared/util/tool";
import { ResourcePermissionModel } from "../../resource-permission/resource-permission-model";
import { ActionTreeNode } from "../action-tree-node";
import { SecurityActionService } from "../security-action.service";
import { SecurityActionsPermissionsComponent } from "./security-actions-permissions.component";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeAction(overrides: Partial<ActionTreeNode> = {}): ActionTreeNode {
   return {
      resource: "/portal/dashboard",
      label: "Dashboard",
      folder: false,
      grant: true,
      type: "DASHBOARD",
      actions: [],
      children: [],
      ...overrides,
   };
}

function makeModel(overrides: Partial<ResourcePermissionModel> = {}): ResourcePermissionModel {
   return {
      permissions: [],
      displayActions: [],
      hasOrgEdited: false,
      securityEnabled: true,
      requiresBoth: false,
      derivePermissionLabel: "Derive permissions from parent",
      grantReadToAll: false,
      grantReadToAllVisible: false,
      grantReadToAllLabel: "Grant read to all",
      changed: false,
      ...overrides,
   };
}

interface RenderOpts {
   action?: ActionTreeNode | null;
   initialModel?: ResourcePermissionModel;
   savedModel?: ResourcePermissionModel;
   getPermissions?: jest.Mock<Observable<ResourcePermissionModel>, any[]>;
   setPermissions?: jest.Mock<Observable<ResourcePermissionModel>, any[]>;
}

async function renderComponent(opts: RenderOpts = {}) {
   const initialModel = opts.initialModel ?? makeModel();
   const savedModel = opts.savedModel ?? initialModel;
   const actionServiceSpy = {
      getPermissions: opts.getPermissions ?? jest.fn().mockReturnValue(of(initialModel)),
      setPermissions: opts.setPermissions ?? jest.fn().mockReturnValue(of(savedModel)),
   };
   const snackBarSpy = { open: jest.fn() };

   const componentProperties: Partial<SecurityActionsPermissionsComponent> = {};

   if("action" in opts) {
      componentProperties.action = opts.action;
   }

   const result = await render(SecurityActionsPermissionsComponent, {
      componentProperties,
      providers: [
         { provide: SecurityActionService, useValue: actionServiceSpy },
         { provide: MatSnackBar, useValue: snackBarSpy },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });

   const comp = result.fixture.componentInstance as SecurityActionsPermissionsComponent;
   result.fixture.detectChanges();
   await result.fixture.whenStable();

   return { ...result, comp, actionServiceSpy, snackBarSpy };
}

// ---------------------------------------------------------------------------
// Group 1 [Risk 3] - action input/loadPermissions
// ---------------------------------------------------------------------------

describe("SecurityActionsPermissionsComponent - action input and dirty baseline", () => {

   // Regression-sensitive: dirty detection depends on originalModel being a deep clone.
   // If tableModel and originalModel share identity, nested permission edits leave Save disabled.
   it("should load permissions for the selected action and keep a cloned original model", async () => {
      const action = makeAction({ type: "VIEWSHEET", resource: "/folder/view", grant: false });
      const loadedModel = makeModel({ securityEnabled: true, changed: false });
      const { comp, actionServiceSpy } = await renderComponent({ action, initialModel: loadedModel });

      expect(actionServiceSpy.getPermissions).toHaveBeenCalledWith("VIEWSHEET", "/folder/view", false);
      expect(comp.tableModel).toEqual(loadedModel);
      expect(comp.isModelChanged()).toBe(false);

      comp.tableModel.changed = true;

      expect(comp.isModelChanged()).toBe(true);
   });

   // Regression-sensitive: clearing action on parent navigation must remove stale permission
   // data from the editor so the next selected action does not inherit old access rules.
   it("should clear tableModel and originalModel when action is set to null", async () => {
      const { comp } = await renderComponent({
         action: makeAction(),
         initialModel: makeModel({ securityEnabled: true }),
      });

      comp.action = null;

      expect(comp.tableModel).toBeNull();
      expect(comp.isModelChanged()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 2 [Risk 3] - save()/restore()
// ---------------------------------------------------------------------------

describe("SecurityActionsPermissionsComponent - save and restore", () => {

   // Regression-sensitive: save must post the currently edited model and then mark the
   // parent/editor clean; missing either event leaves the outer page in an unsaved state.
   it("should save current permissions, emit clean/closed events, and refresh the clean baseline", async () => {
      const action = makeAction({ type: "SCRIPT", resource: "/script/run", grant: true });
      const loadedModel = makeModel({ changed: false, requiresBoth: false });
      const editedModel = makeModel({ changed: true, requiresBoth: true });
      const savedModel = makeModel({ changed: false, requiresBoth: true });
      const { comp, actionServiceSpy } = await renderComponent({
         action,
         initialModel: loadedModel,
         savedModel,
      });
      const unsavedEvents: boolean[] = [];
      let closedCount = 0;
      comp.unsavedChanges.subscribe(value => unsavedEvents.push(value));
      comp.closed.subscribe(() => closedCount++);
      comp.tableModel = editedModel;

      comp.save();

      expect(actionServiceSpy.setPermissions).toHaveBeenCalledWith(
         "SCRIPT",
         "/script/run",
         true,
         editedModel,
      );
      expect(actionServiceSpy.getPermissions).toHaveBeenCalledTimes(2);
      expect(actionServiceSpy.getPermissions).toHaveBeenNthCalledWith(2, "SCRIPT", "/script/run", true);
      expect(unsavedEvents).toEqual([false]);
      expect(closedCount).toBe(1);
      expect(comp.tableModel).toEqual(savedModel);
      expect(comp.isModelChanged()).toBe(false);

      comp.tableModel.changed = true;

      expect(comp.isModelChanged()).toBe(true);
   });

   // Regression-sensitive: Reset is a destructive local action; it must roll back the table
   // to the last loaded snapshot and tell the parent that navigation is safe.
   it("should restore the original model, emit clean/closed events, and keep future dirty checks working", async () => {
      const loadedModel = makeModel({ securityEnabled: true, changed: false });
      const { comp } = await renderComponent({
         action: makeAction(),
         initialModel: loadedModel,
      });
      const unsavedEvents: boolean[] = [];
      let closedCount = 0;
      comp.unsavedChanges.subscribe(value => unsavedEvents.push(value));
      comp.closed.subscribe(() => closedCount++);
      comp.tableModel = makeModel({ securityEnabled: false, changed: true });

      comp.restore();

      expect(unsavedEvents).toEqual([false]);
      expect(closedCount).toBe(1);
      expect(comp.tableModel).toEqual(loadedModel);
      expect(comp.isModelChanged()).toBe(false);

      comp.tableModel.changed = true;

      expect(comp.isModelChanged()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 3 [Risk 2] - error handlers
// ---------------------------------------------------------------------------

describe("SecurityActionsPermissionsComponent - permission service error reporting", () => {

   // Regression-sensitive: set failures must be visible to the user and rethrown so the
   // save success branch cannot run against a failed request.
   it("should show snackbar and rethrow the original error from handleSetPermissionsError", async () => {
      const { comp, snackBarSpy } = await renderComponent();
      const error = new HttpErrorResponse({ status: 500, statusText: "Server Error", url: "/set" });
      const consoleSpy = jest.spyOn(console, "error").mockImplementation(() => {});
      let thrownError: unknown = null;

      (comp as any).handleSetPermissionsError(error).subscribe({
         error: value => thrownError = value,
      });

      expect(snackBarSpy.open).toHaveBeenCalledWith(error.message, null, {
         duration: Tool.SNACKBAR_DURATION,
      });
      expect(thrownError).toBe(error);

      consoleSpy.mockRestore();
   });
});
