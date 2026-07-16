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
 * Timing / lifecycle coverage for loadTable → setTimeout → selectColumns.
 * Kept out of *.tl.spec.ts (Zone + macrotask flushes flake under the full TL suite).
 *
 * Bug #75599: ngOnDestroy must unsubscribe the POST and clear the selectColumns timer.
 */

import { provideHttpClient } from "@angular/common/http";
import { HttpTestingController, provideHttpClientTesting } from "@angular/common/http/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { TestBed } from "@angular/core/testing";
import { TreeNodeModel } from "../../../../../../../widget/tree/tree-node-model";
import { LogicalModelAttributeDialog } from "./logical-model-attribute-dialog.component";

const TABLES_URI = "../api/data/logicalModel/tables/nodes";

function makeTreeRoot(): TreeNodeModel {
   return { label: "root", leaf: false, children: [] };
}

describe("LogicalModelAttributeDialog — loadTable timer (#75599)", () => {
   let httpMock: HttpTestingController;

   beforeEach(() => {
      vi.useFakeTimers();
      TestBed.configureTestingModule({
         providers: [provideHttpClient(), provideHttpClientTesting()],
      });
      TestBed.overrideComponent(LogicalModelAttributeDialog, {
         set: {
            imports: [FormsModule, ReactiveFormsModule],
            template: `<input #selectFocus />`,
         },
      });
      httpMock = TestBed.inject(HttpTestingController);
   });

   afterEach(() => {
      httpMock.verify();
      vi.useRealTimers();
      TestBed.resetTestingModule();
   });

   function createDialog() {
      const fixture = TestBed.createComponent(LogicalModelAttributeDialog);
      const comp = fixture.componentInstance;
      comp.databaseName = "db";
      comp.physicalModelName = "pm";
      comp.logicalModelName = "lm";
      comp.entities = [];
      comp.parent = -1;
      return { fixture, comp };
   }

   it("should invoke selectColumns after the successful POST schedules the timer", async () => {
      const { fixture, comp } = createDialog();
      const selectSpy = vi.spyOn(comp, "selectColumns").mockImplementation(() => {});

      fixture.detectChanges();

      httpMock.expectOne(TABLES_URI).flush(makeTreeRoot());
      expect(selectSpy).not.toHaveBeenCalled();

      await vi.advanceTimersByTimeAsync(0);

      expect(selectSpy).toHaveBeenCalledTimes(1);
   });

   it("should cancel the pending POST on destroy so selectColumns is never scheduled", () => {
      const { fixture, comp } = createDialog();
      const selectSpy = vi.spyOn(comp, "selectColumns").mockImplementation(() => {});

      fixture.detectChanges();
      const req = httpMock.expectOne(TABLES_URI);
      expect(req.cancelled).toBe(false);

      fixture.destroy();

      expect(req.cancelled).toBe(true);
      expect(selectSpy).not.toHaveBeenCalled();
   });

   it("should clear a pending selectColumns timer on destroy", async () => {
      const { fixture, comp } = createDialog();
      const selectSpy = vi.spyOn(comp, "selectColumns").mockImplementation(() => {});

      fixture.detectChanges();
      httpMock.expectOne(TABLES_URI).flush(makeTreeRoot());
      // Timer scheduled; destroy clears it before it fires.
      fixture.destroy();
      await vi.advanceTimersByTimeAsync(0);

      expect(selectSpy).not.toHaveBeenCalled();
   });
});
