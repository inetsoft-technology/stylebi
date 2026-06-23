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
 * PresenterPropertyDialog — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — isColorEnable(): ".BulletGraphPresenter" → always true; no descriptors
 *     at all → true (no hasImagePropertyEditor); ImagePropertyEditor with selectedImage==null
 *     → true; ImagePropertyEditor with selectedImage set → false; no ImagePropertyEditor
 *     descriptor → true
 *   Group 2 [Risk 2] — isImageOptionDisable(): presenter not ImagePresenter → false for any
 *     attr; ImagePresenter + "autoSize" + descriptors[1].value==true → true; ImagePresenter +
 *     "autoSize" + descriptors[1].value==false → false; ImagePresenter + "maintainAspectRatio" +
 *     descriptors[0].value==true → true; unknown attr → false
 *   Group 3 [Risk 2] — ok(): emits onCommit(model)
 *   Group 4 [Risk 1] — cancel(): emits onCancel("cancel")
 *
 * Confirmed bugs (it.fails):
 *   None.
 *
 * Out of scope:
 *   openEditImageDialog — requires @ViewChild TemplateRef and NgbModal.open() interaction;
 *     integration-level (modal lifecycle).
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { PresenterPropertyDialog } from "./presenter-property-dialog.component";
import { PresenterPropertyDialogModel } from "./data/presenter-property-dialog-model";

// ---------------------------------------------------------------------------
// Shared mocks
// ---------------------------------------------------------------------------

const MODAL_SERVICE_MOCK = { open: vi.fn() };

// ---------------------------------------------------------------------------
// Shared fixture helpers
// ---------------------------------------------------------------------------

function makeModel(overrides: Partial<PresenterPropertyDialogModel> = {}): PresenterPropertyDialogModel {
   return {
      presenter: "com.example.DefaultPresenter",
      descriptors: [],
      ...overrides,
   };
}

function makeImageDescriptor(selectedImage: string | null) {
   return {
      name: "image",
      displayName: "Image",
      editor: "ImagePropertyEditor",
      value: { selectedImage },
   } as any;
}

function makeBooleanDescriptor(value: boolean) {
   return {
      name: "flag",
      displayName: "Flag",
      editor: "BooleanPropertyEditor",
      value,
   } as any;
}

async function renderComponent(modelOverrides: Partial<PresenterPropertyDialogModel> = {}) {
   const model = makeModel(modelOverrides);
   const { fixture } = await render(PresenterPropertyDialog, {
      schemas: [NO_ERRORS_SCHEMA],
      componentImports: [],
      providers: [
         { provide: NgbModal, useValue: MODAL_SERVICE_MOCK },
      ],
      componentInputs: {
         model,
         runtimeId: "vs-test-1",
         animateGif: true,
      },
   });
   const comp = fixture.componentInstance as PresenterPropertyDialog;
   return { comp, fixture, model };
}

beforeEach(() => {
   MODAL_SERVICE_MOCK.open.mockReset();
});

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: isColorEnable [Risk 3]
// ---------------------------------------------------------------------------

describe("PresenterPropertyDialog — isColorEnable", () => {
   // 🔁 Regression-sensitive: BulletGraphPresenter must always enable color regardless of
   //    descriptors, so custom bullet colors can be set.
   it("should return true when presenter ends with '.BulletGraphPresenter'", async () => {
      const { comp } = await renderComponent({
         presenter: "com.example.BulletGraphPresenter",
         descriptors: [],
      });
      expect(comp.isColorEnable()).toBe(true);
   });

   it("should return true when there are no ImagePropertyEditor descriptors", async () => {
      const { comp } = await renderComponent({
         presenter: "com.example.SomePresenter",
         descriptors: [{ name: "x", displayName: "X", editor: "TextPropertyEditor" }],
      });
      expect(comp.isColorEnable()).toBe(true);
   });

   it("should return true when ImagePropertyEditor descriptor has selectedImage==null", async () => {
      const { comp } = await renderComponent({
         presenter: "com.example.SomePresenter",
         descriptors: [makeImageDescriptor(null)],
      });
      expect(comp.isColorEnable()).toBe(true);
   });

   it("should return false when ImagePropertyEditor has a selectedImage", async () => {
      const { comp } = await renderComponent({
         presenter: "com.example.SomePresenter",
         descriptors: [makeImageDescriptor("image.png")],
      });
      expect(comp.isColorEnable()).toBe(false);
   });

   it("should return true when descriptors array is empty", async () => {
      const { comp } = await renderComponent({ descriptors: [] });
      expect(comp.isColorEnable()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 2: isImageOptionDisable [Risk 2]
// ---------------------------------------------------------------------------

describe("PresenterPropertyDialog — isImageOptionDisable", () => {
   it("should return false when presenter is not ImagePresenter", async () => {
      const { comp } = await renderComponent({
         presenter: "com.example.SomePresenter",
         descriptors: [makeBooleanDescriptor(true), makeBooleanDescriptor(true)],
      });
      expect(comp.isImageOptionDisable("autoSize")).toBe(false);
      expect(comp.isImageOptionDisable("maintainAspectRatio")).toBe(false);
   });

   it("should return true for 'autoSize' when ImagePresenter and descriptors[1].value==true", async () => {
      const { comp } = await renderComponent({
         presenter: "com.example.ImagePresenter",
         descriptors: [makeBooleanDescriptor(false), makeBooleanDescriptor(true)],
      });
      expect(comp.isImageOptionDisable("autoSize")).toBe(true);
   });

   it("should return false for 'autoSize' when descriptors[1].value==false", async () => {
      const { comp } = await renderComponent({
         presenter: "com.example.ImagePresenter",
         descriptors: [makeBooleanDescriptor(false), makeBooleanDescriptor(false)],
      });
      expect(comp.isImageOptionDisable("autoSize")).toBe(false);
   });

   it("should return true for 'maintainAspectRatio' when ImagePresenter and descriptors[0].value==true", async () => {
      const { comp } = await renderComponent({
         presenter: "com.example.ImagePresenter",
         descriptors: [makeBooleanDescriptor(true), makeBooleanDescriptor(false)],
      });
      expect(comp.isImageOptionDisable("maintainAspectRatio")).toBe(true);
   });

   it("should return false for 'maintainAspectRatio' when descriptors[0].value==false", async () => {
      const { comp } = await renderComponent({
         presenter: "com.example.ImagePresenter",
         descriptors: [makeBooleanDescriptor(false), makeBooleanDescriptor(false)],
      });
      expect(comp.isImageOptionDisable("maintainAspectRatio")).toBe(false);
   });

   it("should return false for an unknown attr", async () => {
      const { comp } = await renderComponent({
         presenter: "com.example.ImagePresenter",
         descriptors: [makeBooleanDescriptor(true), makeBooleanDescriptor(true)],
      });
      expect(comp.isImageOptionDisable("unknownAttr")).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3: ok [Risk 2]
// ---------------------------------------------------------------------------

describe("PresenterPropertyDialog — ok", () => {
   it("should emit onCommit with the current model", async () => {
      const { comp, model } = await renderComponent();
      const emitSpy = vi.spyOn(comp.onCommit, "emit");
      comp.ok();
      expect(emitSpy).toHaveBeenCalledWith(model);
   });
});

// ---------------------------------------------------------------------------
// Group 4: cancel [Risk 1]
// ---------------------------------------------------------------------------

describe("PresenterPropertyDialog — cancel", () => {
   it("should emit onCancel with 'cancel'", async () => {
      const { comp } = await renderComponent();
      const emitSpy = vi.spyOn(comp.onCancel, "emit");
      comp.cancel();
      expect(emitSpy).toHaveBeenCalledWith("cancel");
   });
});
