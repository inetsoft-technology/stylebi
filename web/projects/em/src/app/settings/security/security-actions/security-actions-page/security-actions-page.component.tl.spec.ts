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
 * SecurityActionsPageComponent - Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - onActionSelected(): unsaved edits must gate action changes and
 *                      restore the previous tree selection when the user cancels.
 *   Group 2 [Risk 2] - editing/isScreenSmall/ngOnInit: responsive editor state must scroll
 *                      only on real changes and delegate to the 720px breakpoint.
 *
 * Confirmed bugs (it.failing - remove wrapper once fixed):
 *   (none currently identified)
 *
 * KEY contracts:
 *   - Canceling the unsaved-change dialog keeps selectedAction and unsavedChanges intact.
 *   - Confirming the dialog clears unsavedChanges and switches to the requested action.
 *   - The editing setter calls TopScrollService.scroll("up") only when the value changes.
 */

import { BreakpointObserver } from "@angular/cdk/layout";
import { Component } from "@angular/core";
import { MatDialog } from "@angular/material/dialog";
import { provideNoopAnimations } from "@angular/platform-browser/animations";
import { render } from "@testing-library/angular";
import { of, Subject } from "rxjs";

import { MessageDialog, MessageDialogType } from "../../../../common/util/message-dialog";
import { PageHeaderService } from "../../../../page-header/page-header.service";
import { TopScrollService } from "../../../../top-scroll/top-scroll.service";
import { ActionTreeNode } from "../action-tree-node";
import { SecurityActionsPermissionsComponent } from "../security-actions-permissions/security-actions-permissions.component";
import { SecurityActionsTreeComponent } from "../security-actions-tree/security-actions-tree.component";
import { SecurityActionsPageComponent } from "./security-actions-page.component";

// ---------------------------------------------------------------------------
// Stubs — replace child components that inject services / run async ngOnInit.
// importOverrides swaps them inside the component's own imports array so the
// real classes are never instantiated and their DI tokens are never needed.
// ---------------------------------------------------------------------------

@Component({ selector: "em-security-actions-tree", template: "", standalone: true })
class MockSecurityActionsTreeComponent {}

@Component({ selector: "em-security-actions-permissions", template: "", standalone: true })
class MockSecurityActionsPermissionsComponent {}

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

interface RenderOpts {
   isSmall?: boolean;
   dialogResult?: boolean;
}

async function renderComponent(opts: RenderOpts = {}) {
   const dialogSpy = {
      open: vi.fn().mockReturnValue({
         afterClosed: () => of(opts.dialogResult ?? true),
      }),
   };
   const breakpointSpy = {
      isMatched: vi.fn().mockReturnValue(opts.isSmall ?? false),
   };
   const scrollSpy = { scroll: vi.fn(), visibilityChanged: new Subject<boolean>() };
   const pageHeaderSpy = { title: "" };

   const result = await render(SecurityActionsPageComponent, {
      importOverrides: [
         { replace: SecurityActionsTreeComponent, with: MockSecurityActionsTreeComponent },
         { replace: SecurityActionsPermissionsComponent, with: MockSecurityActionsPermissionsComponent },
      ],
      providers: [
         provideNoopAnimations(),
         { provide: MatDialog, useValue: dialogSpy },
         { provide: BreakpointObserver, useValue: breakpointSpy },
         { provide: TopScrollService, useValue: scrollSpy },
         { provide: PageHeaderService, useValue: pageHeaderSpy },
      ],
   });

   const comp = result.fixture.componentInstance as SecurityActionsPageComponent;
   comp.tree = { selectNode: vi.fn() } as any;

   return { ...result, comp, dialogSpy, breakpointSpy, scrollSpy, pageHeaderSpy };
}

// ---------------------------------------------------------------------------
// Group 1 [Risk 3] - onActionSelected()
// ---------------------------------------------------------------------------

describe("SecurityActionsPageComponent - onActionSelected(): unsaved-change guard", () => {

   // Regression-sensitive: cancel is the only path that preserves dirty permissions when
   // users click a different action in the tree.
   it("should keep the current action, keep unsavedChanges, and restore tree selection when dialog is cancelled", async () => {
      const current = makeAction({ label: "Current", resource: "/current" });
      const next = makeAction({ label: "Next", resource: "/next" });
      const { comp } = await renderComponent({ dialogResult: false });

      comp.selectedAction = current;
      comp.unsavedChanges = true;

      comp.onActionSelected(next);

      expect(comp.selectedAction).toBe(current);
      expect(comp.unsavedChanges).toBe(true);
      expect(comp.tree.selectNode).toHaveBeenCalledWith(current);
   });

   // Regression-sensitive: confirmation must clear the dirty flag before switching actions,
   // otherwise the editor remains blocked after the user explicitly accepted the loss.
   it("should clear unsavedChanges and select the requested action when dialog is confirmed", async () => {
      const current = makeAction({ label: "Current", resource: "/current" });
      const next = makeAction({ label: "Next", resource: "/next" });
      const { comp, dialogSpy } = await renderComponent({ dialogResult: true });

      comp.selectedAction = current;
      comp.unsavedChanges = true;

      comp.onActionSelected(next);

      expect(dialogSpy.open).toHaveBeenCalledWith(
         MessageDialog,
         expect.objectContaining({
            data: expect.objectContaining({
               title: "_#(js:em.settings.actionSettingsChanged)",
               content: "_#(js:em.settings.actionSettings.confirm)",
               type: MessageDialogType.CONFIRMATION,
            }),
         }),
      );
      expect(comp.unsavedChanges).toBe(false);
      expect(comp.selectedAction).toBe(next);
      expect(comp.tree.selectNode).not.toHaveBeenCalled();
   });

   // Regression-sensitive: reselecting the same action must not open a discard dialog,
   // or ordinary tree refreshes can interrupt an in-progress edit.
   it("should not open a confirmation dialog when the same action is selected again", async () => {
      const action = makeAction();
      const { comp, dialogSpy } = await renderComponent();

      comp.selectedAction = action;
      comp.unsavedChanges = true;

      comp.onActionSelected(action);

      expect(dialogSpy.open).not.toHaveBeenCalled();
      expect(comp.selectedAction).toBe(action);
      expect(comp.unsavedChanges).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 2 [Risk 2] - responsive/editor state
// ---------------------------------------------------------------------------

describe("SecurityActionsPageComponent - responsive editor state", () => {

   // Regression-sensitive: small-screen drawer navigation depends on this side effect;
   // repeated assignments should not create duplicate scroll jumps.
   it("should scroll up only when editing changes value", async () => {
      const { comp, scrollSpy } = await renderComponent();

      comp.editing = true;
      comp.editing = true;
      comp.editing = false;

      expect(scrollSpy.scroll).toHaveBeenCalledTimes(2);
      expect(scrollSpy.scroll).toHaveBeenNthCalledWith(1, "up");
      expect(scrollSpy.scroll).toHaveBeenNthCalledWith(2, "up");
      expect(comp.editing).toBe(false);
   });

   // Regression-sensitive: the 720px breakpoint controls whether the tree and editor are
   // shown together or as a small-screen step-through flow.
   it("should delegate isScreenSmall to BreakpointObserver with the 720px max-width query", async () => {
      const { comp, breakpointSpy } = await renderComponent({ isSmall: true });

      breakpointSpy.isMatched.mockClear();

      expect(comp.isScreenSmall()).toBe(true);
      expect(breakpointSpy.isMatched).toHaveBeenCalledWith("(max-width: 720px)");
   });

   it("should set the page title on init", async () => {
      const { pageHeaderSpy } = await renderComponent();

      expect(pageHeaderSpy.title).toBe("_#(js:Security Settings Actions)");
   });
});
