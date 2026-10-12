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
import { HttpClient } from "@angular/common/http";
import { TestBed } from "@angular/core/testing";
import { of } from "rxjs";
import { describe, expect, it, vi } from "vitest";
import { AiAssistantDialogService } from "../../../../common/services/ai-assistant-dialog.service";
import { EditTaskFolderDialog } from "./edit-task-folder-dialog.component";

// Bug #77856, a task folder name made only of whitespace is refused like an empty one
describe("EditTaskFolderDialog (portal) folder name", () => {
   function create(folderName: string = "") {
      const http = { post: vi.fn(() => of({ duplicate: false })) };
      const dialog = new EditTaskFolderDialog(http as unknown as HttpClient);
      dialog.model = {
         folderName,
         oldPath: "x",
         securityEnabled: true,
         owner: { name: "null", orgID: null }
      };
      dialog.ngOnInit();
      const commit = vi.fn();
      dialog.onCommit.subscribe(commit);
      return { dialog, http, commit };
   }

   it.each(["", " ", "   "])("rejects %j as required", (name) => {
      const { dialog } = create();
      const control = dialog.form.get("folderName");
      control.setValue(name);

      expect(control.errors).toEqual({ required: true });
      expect(dialog.form.invalid).toBe(true);
   });

   it.each(["Folder", " a", "a "])("accepts %j", (name) => {
      const { dialog } = create();
      dialog.form.get("folderName").setValue(name);

      expect(dialog.form.valid).toBe(true);
   });

   it("does not submit a whitespace-only name", () => {
      const { dialog, http, commit } = create();
      dialog.form.get("folderName").setValue(" ");

      dialog.ok();

      expect(http.post).not.toHaveBeenCalled();
      expect(commit).not.toHaveBeenCalled();
   });

   it("submits a valid name", () => {
      const { dialog, http, commit } = create();
      dialog.form.get("folderName").setValue("Folder");

      dialog.ok();

      expect(http.post).toHaveBeenCalledTimes(1);
      expect(commit).toHaveBeenCalledWith(expect.objectContaining({ folderName: "Folder" }));
   });

   // a folder stored with a blank name before the fix can be renamed to a valid name
   it("lets an existing blank-named folder be renamed", () => {
      const { dialog, commit } = create(" ");
      expect(dialog.form.invalid).toBe(true);

      dialog.form.get("folderName").setValue("Renamed");
      dialog.ok();

      expect(commit).toHaveBeenCalledWith(expect.objectContaining({ folderName: "Renamed" }));
   });
});

// Bug #77856, the rendered dialog shows the "name required" message and disables OK for a
// whitespace-only name, as it does for an empty one
describe("EditTaskFolderDialog (portal) rendered folder name", () => {
   function render(folderName: string = "") {
      TestBed.configureTestingModule({
         imports: [EditTaskFolderDialog],
         providers: [
            { provide: HttpClient, useValue: { post: vi.fn(() => of({ duplicate: false })) } },
            // the modal header's AI assistant chain loads the current user
            { provide: AiAssistantDialogService, useValue: {} }
         ]
      });
      const fixture = TestBed.createComponent(EditTaskFolderDialog);
      fixture.componentInstance.model = {
         folderName,
         oldPath: "x",
         securityEnabled: true,
         owner: { name: "null", orgID: null }
      };
      fixture.detectChanges();
      return fixture;
   }

   function type(fixture: any, value: string) {
      const input: HTMLInputElement = fixture.nativeElement.querySelector("#inputNameField");
      input.value = value;
      input.dispatchEvent(new Event("input"));
      fixture.detectChanges();
      return input;
   }

   function okButton(fixture: any): HTMLButtonElement {
      return fixture.nativeElement.querySelector(".modal-footer .btn-primary");
   }

   function feedback(fixture: any): string[] {
      return Array.from(fixture.nativeElement.querySelectorAll(".invalid-feedback") as NodeListOf<HTMLElement>)
         .map(e => e.textContent.trim());
   }

   it.each([" ", "   "])("shows the required message and disables OK for %j", (name) => {
      const fixture = render();
      const input = type(fixture, name);

      expect(input.classList.contains("is-invalid")).toBe(true);
      expect(feedback(fixture)).toEqual([expect.stringContaining("viewer.nameValid")]);
      expect(okButton(fixture).disabled).toBe(true);
   });

   it.each(["a", " a", "a "])("shows no message and enables OK for %j", (name) => {
      const fixture = render();
      const input = type(fixture, name);

      expect(input.classList.contains("is-invalid")).toBe(false);
      expect(feedback(fixture)).toEqual([]);
      expect(okButton(fixture).disabled).toBe(false);
   });
});
