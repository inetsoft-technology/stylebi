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

/**
 * Testing Library migration for AddRepositoryFolderDialog.
 *
 * Original spec: add-repository-folder-dialog.spec.ts (1 case, uses querySelector)
 *
 * Design principle: every case answers "what does the user see/experience?"
 *   - NO assertions on internal state (nameControl.valid, loading, etc.)
 *   - NO assertions on method call counts (sendModel was called once)
 *   - YES assertions on visible UI state and HTTP-driven flow outcomes
 *
 * ATL + MSW coverage map:
 *   Validation feedback  → ATL (no HTTP needed)
 *   Submit success       → ATL click + MSW null-body 200 → onCommit spy
 *   Submit error         → ATL click + MSW ERROR body   → showMessageDialog spy
 *   Edit mode pre-fill   → ATL input value assertion (no HTTP needed)
 */
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { render, screen, waitFor } from "@testing-library/angular";
import userEvent from "@testing-library/user-event";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { HttpClientModule } from "@angular/common/http";
import { http, HttpResponse as MswHttpResponse } from "msw";

import { server } from "../../../../../../mocks/server";
import { ComponentTool } from "../../common/util/component-tool";
import { EnterSubmitDirective } from "../directive/enter-submit.directive";
import { ModalHeaderComponent } from "../modal-header/modal-header.component";
import { ModelService } from "../services/model.service";
import { DialogButtonsDirective } from "../standard-dialog/dialog-buttons.directive";
import { DialogContentDirective } from "../standard-dialog/dialog-content.directive";
import { StandardDialogComponent } from "../standard-dialog/standard-dialog.component";
import { RepositoryEntry } from "../../../../../shared/data/repository-entry";
import { AddRepositoryFolderDialog } from "./add-repository-folder-dialog.component";

// ---------------------------------------------------------------------------
// Shared render helper
// ---------------------------------------------------------------------------

/**
 * Renders AddRepositoryFolderDialog with the minimal set of declarations needed
 * to project <ng-template wDialogButtons> content (i.e. the OK / Cancel buttons).
 *
 * Only pass props that differ per case — everything else stays at defaults.
 *
 * Note: HttpClientModule (not HttpClientTestingModule) is required so MSW can
 * intercept at the network layer. HttpClientTestingModule short-circuits before
 * the request leaves Angular's HttpClient internals.
 */
async function renderDialog(props: { edit?: boolean; entry?: Partial<RepositoryEntry> } = {}) {
   return render(AddRepositoryFolderDialog, {
      imports: [ReactiveFormsModule, FormsModule, NgbModule, HttpClientModule],
      declarations: [
         // StandardDialogComponent must be declared (not schema-stubbed) so that
         // <ng-template wDialogButtons> content is projected and the OK/Cancel
         // buttons actually appear in the DOM.
         StandardDialogComponent,
         DialogContentDirective,
         DialogButtonsDirective,
         EnterSubmitDirective,
         ModalHeaderComponent,
      ],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [ModelService],
      componentProperties: {
         edit: props.edit ?? false,
         entry: (props.entry ?? null) as RepositoryEntry,
      },
   });
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("AddRepositoryFolderDialog — scenario-based (Testing Library + MSW)", () => {

   // -------------------------------------------------------------------------
   // Group 1: Form validation — no HTTP involved
   // -------------------------------------------------------------------------

   describe("form validation", () => {

      // Corresponds to Bug #18923 in the original spec, migrated to ATL style.
      // Original: querySelector('button.btn.btn-primary') + hasAttribute('disabled')
      // New: getByRole + toBeDisabled() — no dependency on CSS class names
      it("should disable OK when folder name is empty", async () => {
         await renderDialog();

         // Locate by ARIA role + visible text (i18n strings render literally in test env)
         const okButton = screen.getByRole("button", { name: "_#(OK)" });
         expect(okButton).toBeDisabled();
      });

      it("should enable OK after typing a valid folder name", async () => {
         const user = userEvent.setup();
         await renderDialog();

         // Floating-label inputs don't associate <label> via `for`, so
         // getByPlaceholderText is more reliable than getByLabelText here.
         const input = screen.getByPlaceholderText("_#(Folder Name)");
         await user.type(input, "My Reports");

         expect(screen.getByRole("button", { name: "_#(OK)" })).not.toBeDisabled();
      });

      it("should keep OK disabled and show error when name contains banned characters", async () => {
         const user = userEvent.setup();
         await renderDialog();

         // Banned chars: \/"<%^~  (from FormValidators.assetEntryBannedCharacters)
         // Using "%" — avoids userEvent special-key interpretation
         const input = screen.getByPlaceholderText("_#(Folder Name)");
         await user.type(input, "folder%name");
         // Blur to ensure dirty + touched state is fully set
         await user.tab();

         const okButton = screen.getByRole("button", { name: "_#(OK)" });
         expect(okButton).toBeDisabled();

         // The invalid-feedback span is shown when nameControl has the error
         // and is dirty. The message is the raw i18n key in the test environment.
         expect(screen.getByText("_#(repository.tree.SpecialChar)")).toBeVisible();
      });

      it("should keep OK disabled and show error when name ends with a period", async () => {
         const user = userEvent.setup();
         await renderDialog();

         const input = screen.getByPlaceholderText("_#(Folder Name)");
         await user.type(input, "trailing.");
         await user.tab();

         expect(screen.getByRole("button", { name: "_#(OK)" })).toBeDisabled();
         expect(screen.getByText("_#(name.end.period)")).toBeVisible();
      });
   });

   // -------------------------------------------------------------------------
   // Group 2: HTTP-driven flows — MSW handles the network
   // -------------------------------------------------------------------------

   describe("HTTP flows", () => {

      it("should emit onCommit when server returns empty body (success)", async () => {
         // MSW: POST add-folder → 200 with null body
         // Component logic: !!res.body === false → onCommit.emit("ok")
         server.use(
            http.post("*/api/portal/tree/add-folder", () =>
               new MswHttpResponse(null, { status: 200 })
            )
         );

         const user = userEvent.setup();
         const { fixture } = await renderDialog();

         // Spy on the EventEmitter after render so componentInstance is available
         const commitSpy = jest.spyOn(fixture.componentInstance.onCommit, "emit");

         await user.type(screen.getByPlaceholderText("_#(Folder Name)"), "New Folder");
         await user.click(screen.getByRole("button", { name: "_#(OK)" }));

         // HTTP response is async — waitFor polls until the assertion passes
         await waitFor(() => expect(commitSpy).toHaveBeenCalledWith("ok"));
      });

      it("should show error dialog when server returns a non-CONFIRM message", async () => {
         // Set up spy BEFORE render so it captures any init-time calls too
         // Component logic: messageCommand.type !== "CONFIRM" → showMessageDialog
         const errorDialogSpy = jest
            .spyOn(ComponentTool, "showMessageDialog")
            .mockReturnValue(undefined);

         // Guarantee cleanup even if the waitFor assertion throws
         try {
            // MSW: override for this case only — server rejects with ERROR type
            server.use(
               http.post("*/api/portal/tree/add-folder", () =>
                  MswHttpResponse.json({
                     type: "ERROR",
                     message: "A folder with this name already exists.",
                     events: {},
                  })
               )
            );

            const user = userEvent.setup();
            await renderDialog();

            await user.type(screen.getByPlaceholderText("_#(Folder Name)"), "Duplicate Folder");
            await user.click(screen.getByRole("button", { name: "_#(OK)" }));

            await waitFor(() =>
               expect(errorDialogSpy).toHaveBeenCalledWith(
                  expect.anything(),
                  expect.anything(),
                  "A folder with this name already exists."
               )
            );
         } finally {
            errorDialogSpy.mockRestore();
         }
      });
   });

   // -------------------------------------------------------------------------
   // Group 3: Edit mode — server not needed, focus on UI pre-population
   // -------------------------------------------------------------------------

   describe("edit mode", () => {

      it("should pre-fill name and enable OK when opened for an existing folder", async () => {
         const existingEntry: Partial<RepositoryEntry> = {
            name: "Existing Reports",
            alias: "Reports",
            description: "Monthly report archive",
         };

         await renderDialog({ edit: true, entry: existingEntry });

         // Name input should be pre-filled — OK should be enabled immediately
         const input = screen.getByPlaceholderText("_#(Folder Name)") as HTMLInputElement;
         expect(input.value).toBe("Existing Reports");
         expect(screen.getByRole("button", { name: "_#(OK)" })).not.toBeDisabled();
      });

      it("should show Edit Folder title (not Create Folder) in edit mode", async () => {
         const existingEntry: Partial<RepositoryEntry> = { name: "My Folder" };

         await renderDialog({ edit: true, entry: existingEntry });

         // The title is rendered by StandardDialogComponent from the [title] binding.
         // In create mode the binding is '_#(Create Folder)', in edit mode '_#(Edit Folder)'.
         expect(screen.getByText("_#(Edit Folder)")).toBeInTheDocument();
      });
   });
});
