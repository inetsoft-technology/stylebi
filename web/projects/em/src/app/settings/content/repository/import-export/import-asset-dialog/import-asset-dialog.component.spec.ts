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

import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { UntypedFormBuilder } from "@angular/forms";
import { MatDialog, MatDialogRef } from "@angular/material/dialog";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { NEVER, of } from "rxjs";
import { MessageDialogType } from "../../../../../common/util/message-dialog";
import { RepositoryEntryType } from "../../../../../../../../shared/data/repository-entry-type.enum";
import { StompClientService } from "../../../../../../../../shared/stomp/stomp-client.service";
import { pressEscape, settle, trackDialog } from "../../../../../testing/escape-press";
import { ImportAssetResponse } from "../../model/import-asset-response";
import { RepositoryTreeNode } from "../../repository-tree-node";
import { SelectAssetFolderDialogComponent } from "../select-asset-folder-dialog/select-asset-folder-dialog.component";
import { ImportAssetDialogComponent } from "./import-asset-dialog.component";

/**
 * Bug #77628: warnings about imported assets (e.g. secrets that could not be decrypted) are shown
 * as a warning, never as "Failed to import".
 */
describe("ImportAssetDialogComponent import result", () => {
   let dialog: { open: ReturnType<typeof vi.fn> };
   let component: ImportAssetDialogComponent;

   beforeEach(() => {
      dialog = { open: vi.fn(() => ({ afterClosed: () => of(null) })) };
      const http: any = { get: vi.fn(() => of([])) };
      const dialogRef: any = { close: vi.fn(), keydownEvents: () => NEVER };
      component = new ImportAssetDialogComponent(
         http, dialog as any, dialogRef, {}, new UntypedFormBuilder());
   });

   function complete(response: Partial<ImportAssetResponse>): any {
      (component as any).onImportComplete({
         failedAssets: [], ignoreUserAssets: [], complete: true, failed: false, ...response
      });
      return dialog.open.mock.calls[0][1].data;
   }

   it("shows warnings of a successful import as a warning", () => {
      const data = complete({ warnings: ["secrets could not be decrypted: ds1"] });

      expect(data.type).toBe(MessageDialogType.WARNING);
      expect(data.content).toContain("secrets could not be decrypted: ds1");
      expect(data.content).not.toContain("em.import.fail");
   });

   it("shows a successful import without warnings as success", () => {
      const data = complete({});

      expect(data.type).toBe(MessageDialogType.INFO);
   });

   it("keeps failed assets and adds the warnings", () => {
      const data = complete({ failedAssets: ["vs1"], failed: true, warnings: ["w1"] });

      expect(data.type).toBe(MessageDialogType.WARNING);
      expect(data.content).toContain("vs1");
      expect(data.content).toContain("w1");
   });
});

/**
 * Bug #78722: Escape that closes Select Target Location, or the node menu in its tree, must not
 * also cancel Import Assets behind it.
 */
describe("ImportAssetDialogComponent Escape", () => {
   // the repository tree loads once its socket is connected
   const connection = { subscribe: () => NEVER.subscribe(), disconnect: () => {} };
   let dialog: MatDialog;
   let httpMock: HttpTestingController;

   beforeEach(() => {
      dialog = null;
      TestBed.configureTestingModule({
         imports: [NoopAnimationsModule, HttpClientTestingModule],
         providers: [{ provide: StompClientService, useValue: { connect: () => of(connection) } }]
      });
   });

   afterEach(async () => {
      dialog?.closeAll();
      await settle();
   });

   // opened as ContentRepositoryPageComponent opens it
   const openImport = async (): Promise<MatDialogRef<ImportAssetDialogComponent>> => {
      dialog = TestBed.inject(MatDialog);
      httpMock = TestBed.inject(HttpTestingController);
      const ref = dialog.open(ImportAssetDialogComponent, { disableClose: true, data: {} });
      await settle();
      return ref;
   };

   const openTargetLocation = async (importRef: MatDialogRef<ImportAssetDialogComponent>) => {
      importRef.componentInstance.openSelectLocation();
      await settle();
      const ref = dialog.openDialogs[1];
      expect(ref.componentInstance).toBeInstanceOf(SelectAssetFolderDialogComponent);
      return ref;
   };

   it("closes only Select Target Location when Escape is pressed in it", async () => {
      const importRef = await openImport();
      const importState = trackDialog(importRef);
      const locationState = trackDialog(await openTargetLocation(importRef));

      await pressEscape();

      expect(locationState.closed).toBe(true);
      expect(importState.closed).toBe(false);
      expect(dialog.openDialogs).toEqual([importRef]);

      await pressEscape();
      expect(importState).toEqual({ closed: true, result: null });
   });

   it("closes only the tree's node menu when Escape is pressed in it", async () => {
      const importRef = await openImport();
      const importState = trackDialog(importRef);
      // an uploaded file with one viewsheet, so the tree shows the repository folder
      importRef.componentInstance.model = <any> {
         importId: "1", selectedEntities: [{ type: RepositoryEntryType.VIEWSHEET, path: "vs1" }],
         dependentAssets: []
      };
      importRef.componentInstance.openSelectLocation();
      const locationRef = dialog.openDialogs[1];
      const locationState = trackDialog(locationRef);
      // the tree is answered before the folder dialog is first checked
      httpMock.expectOne(req => req.url.endsWith("/content/repository/tree")).flush({
         nodes: [<RepositoryTreeNode> {
            label: "Repository", path: "/", type: RepositoryEntryType.REPOSITORY_FOLDER,
            owner: null, readOnly: false, builtIn: false, description: "", icon: null,
            visible: true, children: []
         }]
      });
      // the virtual scroll viewport renders its rows on the next animation frame
      await new Promise(resolve => setTimeout(resolve, 50));
      await settle();
      const menuButton = document.querySelector(
         "em-select-asset-folder-dialog [aria-label=\"_#(Menu)\"]") as HTMLElement;
      menuButton.focus();
      menuButton.click();
      await settle();
      expect(document.querySelector(".mat-mdc-menu-panel")).not.toBeNull();

      await pressEscape();

      expect(document.querySelector(".mat-mdc-menu-panel")).toBeNull();
      expect(locationState.closed).toBe(false);
      expect(importState.closed).toBe(false);
      expect(dialog.openDialogs).toEqual([importRef, locationRef]);
   });

   it("cancels on Escape when it is the only dialog", async () => {
      const state = trackDialog(await openImport());

      await pressEscape();

      expect(state).toEqual({ closed: true, result: null });
   });
});
