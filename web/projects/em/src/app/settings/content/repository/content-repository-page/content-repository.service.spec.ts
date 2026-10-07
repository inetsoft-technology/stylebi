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
import { provideHttpClient } from "@angular/common/http";
import { HttpTestingController, provideHttpClientTesting } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { MatDialog } from "@angular/material/dialog";
import { MatSnackBar } from "@angular/material/snack-bar";
import { of } from "rxjs";
import { RepositoryEntryType } from "../../../../../../../shared/data/repository-entry-type.enum";
import { MessageDialogType } from "../../../../common/util/message-dialog";
import { ContentRepositoryService } from "./content-repository.service";

const DELETE_URI = "../api/em/content/repository/tree/delete";

/**
 * Bug #77939, the status returned by the repository tree delete. A "warning:" status is shown
 * once, the other statuses ask to confirm the delete and send it again.
 */
describe("ContentRepositoryService delete status", () => {
   let service: ContentRepositoryService;
   let http: HttpTestingController;
   let dialog: { open: ReturnType<typeof vi.fn> };

   beforeEach(() => {
      dialog = { open: vi.fn(() => ({ afterClosed: () => of(true) })) };

      // a failed verify() in afterEach must not leave the module instantiated for the next test
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
         providers: [
            provideHttpClient(),
            provideHttpClientTesting(),
            { provide: MatDialog, useValue: dialog },
            { provide: MatSnackBar, useValue: { open: vi.fn() } }
         ]
      });

      service = TestBed.inject(ContentRepositoryService);
      http = TestBed.inject(HttpTestingController);
   });

   afterEach(() => {
      http.verify();
   });

   function deleteNode(type: number): void {
      service.selectedNodes = [<any> {
         data: { label: "vs1", path: "F/vs1", type: type, owner: null }
      }];
      service.deleteSelectedNodes();
   }

   it("shows a warning status without sending the delete again", () => {
      deleteNode(RepositoryEntryType.VIEWSHEET);

      const req = http.expectOne(DELETE_URI);
      expect(req.request.body.force).toBe(false);
      req.flush({ status: "warning:Some permissions may not have been removed.", connected: false });

      // the delete confirmation, then the warning
      expect(dialog.open).toHaveBeenCalledTimes(2);
      const data = dialog.open.mock.calls[1][1].data;
      expect(data.type).toBe(MessageDialogType.WARNING);
      expect(data.content).toBe("Some permissions may not have been removed.");
      http.expectNone(DELETE_URI);
   });

   it("refreshes the tree after a warning as after a delete without status", () => {
      const refreshed = vi.fn();
      service.needRefreshAfterDelete().subscribe(refreshed);
      deleteNode(RepositoryEntryType.SCRIPT);

      http.expectOne(DELETE_URI).flush({ status: "warning:left", connected: false });

      expect(refreshed).toHaveBeenCalledTimes(1);
   });

   it("still asks to confirm another status and sends the delete again with force", () => {
      deleteNode(RepositoryEntryType.VIEWSHEET);

      http.expectOne(DELETE_URI).flush({ status: "The item has dependencies.", connected: false });

      const data = dialog.open.mock.calls[1][1].data;
      expect(data.type).toBe(MessageDialogType.CONFIRMATION);
      expect(data.content).toBe("The item has dependencies.");
      const retry = http.expectOne(DELETE_URI);
      expect(retry.request.body.force).toBe(true);
      expect(retry.request.body.permanent).toBe(false);
      retry.flush(null);
   });

   it("still strips the corrupt prefix and sends a permanent delete", () => {
      deleteNode(RepositoryEntryType.VIEWSHEET);

      http.expectOne(DELETE_URI).flush({ status: "corrupt:Delete permanently?", connected: false });

      const data = dialog.open.mock.calls[1][1].data;
      expect(data.type).toBe(MessageDialogType.CONFIRMATION);
      expect(data.content).toBe("Delete permanently?");
      const retry = http.expectOne(DELETE_URI);
      expect(retry.request.body.force).toBe(true);
      expect(retry.request.body.permanent).toBe(true);
      retry.flush(null);
   });
});
