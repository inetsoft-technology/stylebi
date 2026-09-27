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
 * SaveWizVisualizationDialog — Bug #76714 regression
 *
 *   The name FormControl used to be built from the auto-generated "Untitled-N" name (valid)
 *   before model.name was cleared. On page 2 the OK button's [disabled] was evaluated with the
 *   stale valid state and then flipped false -> true when the nested name input wrote "" into
 *   the control, so dev-mode checkNoChanges threw NG0100.
 */

import { Component, NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { SaveWizVisualizationDialog, SaveWizVisualizationDialogModel } from "./save-wiz-visualization-dialog.component";
import { ModelService } from "../../../../widget/services/model.service";
import { ModalHeaderComponent } from "../../../../widget/modal-header/modal-header.component";
import { AssetTreeComponent } from "../../../../widget/asset-tree/asset-tree.component";

@Component({ selector: "asset-tree", template: "", standalone: true })
class AssetTreeComponentStub {}

@Component({ selector: "modal-header", template: "", standalone: true })
class ModalHeaderComponentStub {}

function makeModel(name: string): SaveWizVisualizationDialogModel {
   return {
      name, parentId: "", updateDepend: true, viewsheetOptionsPaneModel: null as any,
      visualizationScope: "shared"
   };
}

async function renderDialog(name: string, standaloneVisualization: boolean) {
   const { fixture } = await render(SaveWizVisualizationDialog, {
      schemas: [NO_ERRORS_SCHEMA],
      detectChangesOnRender: false,
      autoDetectChanges: false,
      importOverrides: [
         { replace: AssetTreeComponent, with: AssetTreeComponentStub },
         { replace: ModalHeaderComponent, with: ModalHeaderComponentStub },
      ],
      providers: [
         { provide: ModelService, useValue: { sendModel: vi.fn(), getModel: vi.fn() } },
         { provide: NgbModal, useValue: {} },
      ],
      componentProperties: { model: makeModel(name), runtimeId: "vs-123", standaloneVisualization },
   });

   return fixture;
}

afterEach(() => vi.restoreAllMocks());

describe("SaveWizVisualizationDialog — Bug #76714 NG0100 on the name page", () => {
   for(const standaloneVisualization of [true, false]) {
      it(`should not throw NG0100 and should disable OK after next() for an 'Untitled-' name (standalone=${standaloneVisualization})`, async () => {
         const fixture = await renderDialog("Untitled-1", standaloneVisualization);
         fixture.detectChanges(false);
         fixture.checkNoChanges();

         fixture.componentInstance.next();
         fixture.detectChanges(false);
         expect(() => fixture.checkNoChanges()).not.toThrow();

         const okButton: HTMLButtonElement =
            fixture.nativeElement.querySelector(".modal-footer .btn-primary");
         expect(fixture.componentInstance.model.name).toBe("");
         expect(fixture.componentInstance.form.valid).toBe(false);
         expect(okButton.disabled).toBe(true);
      });
   }

   it("should enable OK on the name page after typing a valid name into a cleared 'Untitled-' dialog", async () => {
      const fixture = await renderDialog("Untitled-1", true);
      fixture.detectChanges(false);
      fixture.componentInstance.next();
      fixture.detectChanges(false);
      fixture.checkNoChanges();

      const input: HTMLInputElement = fixture.nativeElement.querySelector("input#name");
      input.value = "Viz1";
      input.dispatchEvent(new Event("input"));
      fixture.detectChanges(false);
      expect(() => fixture.checkNoChanges()).not.toThrow();

      const okButton: HTMLButtonElement =
         fixture.nativeElement.querySelector(".modal-footer .btn-primary");
      expect(fixture.componentInstance.model.name).toBe("Viz1");
      expect(fixture.componentInstance.form.valid).toBe(true);
      expect(okButton.disabled).toBe(false);
   });

   it("should keep a real name and enable OK on the name page", async () => {
      const fixture = await renderDialog("Sales", true);
      fixture.detectChanges(false);

      fixture.componentInstance.next();
      fixture.detectChanges(false);
      expect(() => fixture.checkNoChanges()).not.toThrow();

      const okButton: HTMLButtonElement =
         fixture.nativeElement.querySelector(".modal-footer .btn-primary");
      expect(fixture.componentInstance.model.name).toBe("Sales");
      expect(okButton.disabled).toBe(false);
   });
});
