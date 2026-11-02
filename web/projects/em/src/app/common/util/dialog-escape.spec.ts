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
import { Component } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { MatDialog, MatDialogRef } from "@angular/material/dialog";
import { MatOption } from "@angular/material/core";
import { MatSelect } from "@angular/material/select";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { Subscription } from "rxjs";
import { focused, pressEscape, sendKey, settle } from "../../testing/escape-press";
import { onDialogEscape } from "./dialog-escape";

@Component({
   selector: "em-escape-test-dialog",
   imports: [MatSelect, MatOption],
   template: `
     <mat-select aria-label="list">
       <mat-option value="a">A</mat-option>
       <mat-option value="b">B</mat-option>
     </mat-select>`
})
class EscapeTestDialogComponent {
}

/**
 * Bug #78722: onDialogEscape sees an Escape press only while its dialog is the topmost overlay.
 */
describe("onDialogEscape", () => {
   let dialog: MatDialog;
   let ref: MatDialogRef<EscapeTestDialogComponent>;
   let escapes: number;
   let subscription: Subscription;

   beforeEach(async () => {
      TestBed.configureTestingModule({ imports: [NoopAnimationsModule] });
      dialog = TestBed.inject(MatDialog);
      ref = dialog.open(EscapeTestDialogComponent, { disableClose: true });
      escapes = 0;
      subscription = onDialogEscape(ref, () => escapes++);
      await settle();
   });

   afterEach(async () => {
      subscription.unsubscribe();
      dialog.closeAll();
      await settle();
   });

   const openSelect = async () => {
      const select = document.querySelector("mat-select") as HTMLElement;
      select.focus();
      (select.querySelector(".mat-mdc-select-trigger") as HTMLElement).click();
      await settle();
      expect(document.querySelector(".mat-mdc-select-panel")).not.toBeNull();
   };

   it("calls the handler once for an Escape press in the dialog", async () => {
      await pressEscape();

      expect(escapes).toBe(1);
      // the dialog is disableClose, so only the handler decides whether it closes
      expect(dialog.openDialogs.length).toBe(1);
   });

   it("does not call the handler while an open select list is on top", async () => {
      await openSelect();

      await pressEscape();

      expect(document.querySelector(".mat-mdc-select-panel")).toBeNull();
      expect(escapes).toBe(0);

      await pressEscape();
      expect(escapes).toBe(1);
   });

   it("does not call the handler while an inner dialog is on top", async () => {
      dialog.open(EscapeTestDialogComponent);
      await settle();

      await pressEscape();

      expect(dialog.openDialogs.length).toBe(1);
      expect(escapes).toBe(0);

      await pressEscape();
      expect(escapes).toBe(1);
   });

   it("ignores Escape with a modifier key and Escape while composing", async () => {
      await pressEscape({ shiftKey: true });
      await pressEscape({ altKey: true });
      await pressEscape({ ctrlKey: true });
      await pressEscape({ metaKey: true });
      await pressEscape({ isComposing: true });

      expect(escapes).toBe(0);
   });

   it("ignores other keys and a keyup on its own", async () => {
      sendKey(focused(), "keydown", { key: "Enter", code: "Enter", keyCode: 13 });
      sendKey(focused(), "keyup");
      await settle();

      expect(escapes).toBe(0);
   });

   it("stops calling the handler once unsubscribed", async () => {
      subscription.unsubscribe();

      await pressEscape();

      expect(escapes).toBe(0);
   });
});
