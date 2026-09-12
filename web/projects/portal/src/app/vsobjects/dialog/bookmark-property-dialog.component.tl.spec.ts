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
 * BookmarkPropertyDialog — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — ngOnInit(): sharedOption derivation from model.type + shareToAllDisabled
 *   Group 2 [Risk 2] — saveChanges(): name must be trimmed before onCommit fires
 *   Group 3 [Risk 1] — cancelChanges, selectType, isGlobalScope, formValid
 *
 * Confirmed bugs: none
 *
 * Out of scope:
 *   initForm() — called transitively by ngOnInit; form control tested via formValid getter
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ReactiveFormsModule } from "@angular/forms";
import { render } from "@testing-library/angular";

import { BookmarkPropertyDialog } from "./bookmark-property-dialog.component";
import { VSBookmarkInfoModel, VSBookmarkType } from "../model/vs-bookmark-info-model";

function makeModel(overrides: Partial<VSBookmarkInfoModel> = {}): VSBookmarkInfoModel {
   return {
      name: "My Bookmark",
      type: VSBookmarkType.PRIVATE,
      owner: { name: "alice", orgID: "org1" },
      defaultBookmark: false,
      ...overrides,
   };
}

async function renderComp(opts: {
   model?: VSBookmarkInfoModel;
   shareToAllDisabled?: boolean;
   assetId?: string;
} = {}): Promise<BookmarkPropertyDialog> {
   const { fixture } = await render(BookmarkPropertyDialog, {
      imports: [ReactiveFormsModule],
      schemas: [NO_ERRORS_SCHEMA],
      componentInputs: {
         model: opts.model ?? makeModel(),
         runtimeId: "vs1",
         assetId: opts.assetId ?? "2^user^alice",
         shareToAllDisabled: opts.shareToAllDisabled ?? false,
      },
   });
   return fixture.componentInstance;
}

// ---------------------------------------------------------------------------
// Group 1: ngOnInit() — sharedOption derivation
// ---------------------------------------------------------------------------

describe("BookmarkPropertyDialog — ngOnInit sharedOption", () => {

   // 🔁 Regression-sensitive: sharedOption controls which share-type radio is pre-selected;
   // wrong initial value silently persists into the saved model.type.
   it("should default sharedOption to ALLSHARE when type is PRIVATE and shareToAllDisabled is false", async () => {
      const comp = await renderComp({ model: makeModel({ type: VSBookmarkType.PRIVATE }), shareToAllDisabled: false });
      expect(comp.sharedOption).toBe(VSBookmarkType.ALLSHARE);
   });

   it("should set sharedOption to GROUPSHARE when model.type is GROUPSHARE", async () => {
      const comp = await renderComp({ model: makeModel({ type: VSBookmarkType.GROUPSHARE }) });
      expect(comp.sharedOption).toBe(VSBookmarkType.GROUPSHARE);
   });

   it("should set sharedOption to GROUPSHARE when shareToAllDisabled is true regardless of type", async () => {
      const comp = await renderComp({ model: makeModel({ type: VSBookmarkType.ALLSHARE }), shareToAllDisabled: true });
      expect(comp.sharedOption).toBe(VSBookmarkType.GROUPSHARE);
   });

   it("should initialize the form name control with the model name", async () => {
      const comp = await renderComp({ model: makeModel({ name: "Q3 Report" }) });
      expect(comp.form.get("name")?.value).toBe("Q3 Report");
   });
});

// ---------------------------------------------------------------------------
// Group 2: saveChanges() — name trim contract
// ---------------------------------------------------------------------------

describe("BookmarkPropertyDialog — saveChanges", () => {

   // 🔁 Regression-sensitive: untrimmed bookmark names create entries with leading/trailing
   // spaces that cannot be found by server-side exact-name lookup.
   // Note: saveChanges() reads model.name directly (not the form control). In production,
   // InputTrimDirective keeps them in sync; here NO_ERRORS_SCHEMA stubs the directive, so
   // model.name is set via componentInputs — which is what this test exercises by design.
   it("should trim model.name before emitting onCommit", async () => {
      const comp = await renderComp({ model: makeModel({ name: "  My Report  " }) });
      const spy = vi.fn();
      comp.onCommit.subscribe(spy);
      comp.saveChanges();
      expect(spy).toHaveBeenCalledTimes(1);
      expect(spy.mock.calls[0][0].name).toBe("My Report");
   });
});

// ---------------------------------------------------------------------------
// Group 3: cancelChanges, selectType, isGlobalScope, formValid
// ---------------------------------------------------------------------------

describe("BookmarkPropertyDialog — cancelChanges / selectType / isGlobalScope / formValid", () => {

   it("cancelChanges: should emit 'cancel' via onCancel", async () => {
      const comp = await renderComp();
      const spy = vi.fn();
      comp.onCancel.subscribe(spy);
      comp.cancelChanges();
      expect(spy).toHaveBeenCalledWith("cancel");
   });

   it("selectType: should update model.type to the selected type", async () => {
      const comp = await renderComp();
      comp.selectType(VSBookmarkType.GROUPSHARE);
      expect(comp.model.type).toBe(VSBookmarkType.GROUPSHARE);
   });

   it("isGlobalScope: should reflect assetId scope across all three cases", async () => {
      const comp = await renderComp({ assetId: "2^user^alice" });
      expect(comp.isGlobalScope()).toBe(false);

      comp.assetId = "1^user^alice";
      expect(comp.isGlobalScope()).toBe(true);

      comp.assetId = "";
      expect(comp.isGlobalScope()).toBe(true);
   });

   it("formValid: should be false when name is empty and true after setting a valid name", async () => {
      const comp = await renderComp({ model: makeModel({ name: "" }) });
      expect(comp.formValid()).toBe(false);

      comp.form.get("name")?.setValue("Dashboard");
      expect(comp.formValid()).toBe(true);
   });
});
