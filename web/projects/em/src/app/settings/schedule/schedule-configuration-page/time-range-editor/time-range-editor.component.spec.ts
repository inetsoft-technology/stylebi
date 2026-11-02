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
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { MatDialog, MatDialogRef } from "@angular/material/dialog";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { NEVER } from "rxjs";
import { StompClientService } from "../../../../../../../shared/stomp/stomp-client.service";
import { pressEscape, settle, trackDialog } from "../../../../testing/escape-press";
import { SecurityTreeDialogComponent } from "../../../security/security-tree-dialog/security-tree-dialog.component";
import { TimeRangeEditorComponent } from "./time-range-editor.component";

/**
 * Bug #78722: Escape that closes Add Permission must not also cancel the Time Range editor.
 */
describe("TimeRangeEditorComponent Escape", () => {
   let dialog: MatDialog;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [NoopAnimationsModule, HttpClientTestingModule],
         providers: [{ provide: StompClientService, useValue: { connect: () => NEVER } }]
      });
      dialog = TestBed.inject(MatDialog);
   });

   afterEach(async () => {
      dialog.closeAll();
      await settle();
   });

   // opened as TimeRangesViewComponent opens it
   const openEditor = async (): Promise<MatDialogRef<TimeRangeEditorComponent>> => {
      const permissions: any = {
         permissions: [], displayActions: [], hasOrgEdited: false, securityEnabled: true,
         requiresBoth: false, derivePermissionLabel: "", grantReadToAllVisible: false, changed: false
      };
      const ref = dialog.open(TimeRangeEditorComponent, {
         disableClose: true,
         data: {
            range: { name: "r1", startTime: "01:00:00", endTime: "02:00:00", defaultRange: false, permissions },
            ranges: []
         }
      });
      await settle();
      return ref;
   };

   it("keeps the editor and its edits when Escape closes Add Permission", async () => {
      const editorRef = await openEditor();
      const editorState = trackDialog(editorRef);
      editorRef.componentInstance.form.get("name").setValue("Renamed");

      // the real Add button on the Security tab
      (Array.from(document.querySelectorAll("[role=tab]"))[1] as HTMLElement).click();
      await settle(10);
      const add = (Array.from(document.querySelectorAll("button")) as HTMLElement[])
         .find(b => b.textContent.trim() === "_#(Add)");
      add.click();
      await settle(10);
      const addRef = dialog.openDialogs[1];
      expect(addRef.componentInstance).toBeInstanceOf(SecurityTreeDialogComponent);
      const addState = trackDialog(addRef);

      await pressEscape();

      expect(addState.closed).toBe(true);
      expect(editorState.closed).toBe(false);
      expect(dialog.openDialogs).toEqual([editorRef]);

      editorRef.componentInstance.commit();
      await settle();
      expect(editorState.result.name).toBe("Renamed");
   });

   it("cancels on Escape when it is the only dialog", async () => {
      const state = trackDialog(await openEditor());

      await pressEscape();

      expect(state).toEqual({ closed: true, result: undefined });
   });
});
