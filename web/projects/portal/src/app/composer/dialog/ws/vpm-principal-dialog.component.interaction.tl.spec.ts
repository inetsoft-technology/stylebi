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
 * VPMPrincipalDialogComponent — Pass 1: Interaction
 *
 * Method coverage:
 *   Group 1   ngOnInit — loads model, conditionally calls initForm
 *   Group 2   initForm — creates form with correct initial values from model
 *   Group 3   initForm — vpmEnabled=true enables sessionType + sessionId
 *   Group 4   initForm — vpmEnabled=false disables sessionType + sessionId
 *   Group 5   sessionType valueChanges — updates sessionIds list
 *   Group 6   ok — with form: emits merged model; without form: emits null
 *   Group 7   cancel — emits onCancel
 */

import {
   makeComponent,
   makeModel,
   makeModelService,
} from "./vpm-principal-dialog.component.test-helpers";

// ---------------------------------------------------------------------------
// Group 1 — ngOnInit
// ---------------------------------------------------------------------------

describe("Group 1 — ngOnInit: loads model and conditionally initializes form", () => {
   it("should call modelService.getModel() containing the worksheet runtimeId", () => {
      const { modelSvc } = makeComponent();
      expect(modelSvc.getModel).toHaveBeenCalled();
      const url: string = modelSvc.getModel.mock.calls[0][0];
      expect(url).toContain("vpm-principal-dialog");
   });

   it("should set comp.model from getModel() response", () => {
      const model = makeModel({ users: ["alice", "bob"] });
      const { comp } = makeComponent({ model });
      expect(comp.model?.users).toEqual(["alice", "bob"]);
   });

   it("should create comp.form when model.vpmSelectable=true", () => {
      const model = makeModel({ vpmSelectable: true });
      const { comp } = makeComponent({ model });
      expect(comp.form).toBeTruthy();
   });

   it("should NOT create comp.form when model.vpmSelectable=false", () => {
      const model = makeModel({ vpmSelectable: false });
      const { comp } = makeComponent({ model });
      expect(comp.form).toBeFalsy();
   });
});

// ---------------------------------------------------------------------------
// Group 2 — initForm: initial values
// ---------------------------------------------------------------------------

describe("Group 2 — initForm: form controls initialized from model", () => {
   it("should create form with vpmEnabled, sessionType, and sessionId controls", () => {
      const { comp } = makeComponent();
      expect(comp.form?.get("vpmEnabled")).toBeTruthy();
      expect(comp.form?.get("sessionType")).toBeTruthy();
      expect(comp.form?.get("sessionId")).toBeTruthy();
   });

   it("should set vpmEnabled from model.vpmEnabled", () => {
      const model = makeModel({ vpmEnabled: false, vpmSelectable: true, sessionType: null, sessionId: null });
      const { comp } = makeComponent({ model });
      expect(comp.form?.get("vpmEnabled")?.value).toBe(false);
   });

   it("should set sessionType from model.sessionType", () => {
      const model = makeModel({ vpmEnabled: true, sessionType: "role" });
      const { comp } = makeComponent({ model });
      expect(comp.form?.get("sessionType")?.value).toBe("role");
   });
});

// ---------------------------------------------------------------------------
// Group 3 — vpmEnabled=true enables dependent controls
// ---------------------------------------------------------------------------

describe("Group 3 — initForm: vpmEnabled=true enables sessionType and sessionId", () => {
   it("should have sessionType enabled when model.vpmEnabled=true", () => {
      const model = makeModel({ vpmEnabled: true });
      const { comp } = makeComponent({ model });
      expect(comp.form?.get("sessionType")?.enabled).toBe(true);
   });

   it("should have sessionId enabled when model.vpmEnabled=true", () => {
      const model = makeModel({ vpmEnabled: true });
      const { comp } = makeComponent({ model });
      expect(comp.form?.get("sessionId")?.enabled).toBe(true);
   });

   it("should enable controls when vpmEnabled changes to true after init", () => {
      const model = makeModel({ vpmEnabled: false, vpmSelectable: true, sessionType: null, sessionId: null });
      const { comp } = makeComponent({ model });
      comp.form?.get("vpmEnabled")?.setValue(true);
      expect(comp.form?.get("sessionType")?.enabled).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — vpmEnabled=false disables dependent controls
// ---------------------------------------------------------------------------

describe("Group 4 — initForm: vpmEnabled=false disables sessionType and sessionId", () => {
   it("should have sessionType disabled when model.vpmEnabled=false", () => {
      const model = makeModel({ vpmEnabled: false, vpmSelectable: true, sessionType: null, sessionId: null });
      const { comp } = makeComponent({ model });
      expect(comp.form?.get("sessionType")?.disabled).toBe(true);
   });

   it("should have sessionId disabled when model.vpmEnabled=false", () => {
      const model = makeModel({ vpmEnabled: false, vpmSelectable: true, sessionType: null, sessionId: null });
      const { comp } = makeComponent({ model });
      expect(comp.form?.get("sessionId")?.disabled).toBe(true);
   });

   it("should disable controls when vpmEnabled changes to false at runtime", () => {
      const model = makeModel({ vpmEnabled: true });
      const { comp } = makeComponent({ model });
      comp.form?.get("vpmEnabled")?.setValue(false);
      expect(comp.form?.get("sessionType")?.disabled).toBe(true);
      expect(comp.form?.get("sessionId")?.disabled).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — sessionType valueChanges updates sessionIds
// ---------------------------------------------------------------------------

describe("Group 5 — sessionType valueChanges: resets sessionIds list", () => {
   it("should set sessionIds to model.users when sessionType changes to 'user'", () => {
      const model = makeModel({ vpmEnabled: true, sessionType: "role", users: ["u1", "u2"] });
      const { comp } = makeComponent({ model });

      comp.form?.get("sessionType")?.setValue("user");

      expect(comp.sessionIds).toEqual(["u1", "u2"]);
   });

   it("should set sessionIds to model.roles when sessionType changes to 'role'", () => {
      const model = makeModel({ vpmEnabled: true, sessionType: "user", roles: ["admin", "analyst"] });
      const { comp } = makeComponent({ model });

      comp.form?.get("sessionType")?.setValue("role");

      expect(comp.sessionIds).toEqual(["admin", "analyst"]);
   });

   it("should set sessionId form control to the first item in the new sessionIds list", () => {
      const model = makeModel({ vpmEnabled: true, sessionType: "role", users: ["first", "second"] });
      const { comp } = makeComponent({ model });

      comp.form?.get("sessionType")?.setValue("user");

      expect(comp.form?.get("sessionId")?.value).toBe("first");
   });

   it("should set sessionId to null when the new sessionIds list is empty", () => {
      const model = makeModel({ vpmEnabled: true, sessionType: "role", users: [] });
      const { comp } = makeComponent({ model });

      comp.form?.get("sessionType")?.setValue("user");

      expect(comp.form?.get("sessionId")?.value).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 6 — ok
// ---------------------------------------------------------------------------

describe("Group 6 — ok: emits merged model or null", () => {
   it("should emit onCommit with merged model when form exists", () => {
      const model = makeModel({ vpmEnabled: true, sessionType: "user", sessionId: "admin" });
      const { comp } = makeComponent({ model });
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok();

      expect(emitSpy).toHaveBeenCalledOnce();
      const emitted = emitSpy.mock.calls[0][0];
      expect(emitted).not.toBeNull();
   });

   it("should merge form values into the emitted model (spread overwrites)", () => {
      const model = makeModel({ vpmEnabled: true, sessionType: "user", sessionId: "admin" });
      const { comp } = makeComponent({ model });
      comp.form?.get("sessionId")?.setValue("user1");
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok();

      const emitted = emitSpy.mock.calls[0][0];
      expect(emitted?.sessionId).toBe("user1");
   });

   it("should preserve non-form model fields (e.g. users, roles) in emitted model", () => {
      const model = makeModel({ users: ["a", "b"], roles: ["r1"] });
      const { comp } = makeComponent({ model });
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok();

      const emitted = emitSpy.mock.calls[0][0];
      expect(emitted?.users).toEqual(["a", "b"]);
      expect(emitted?.roles).toEqual(["r1"]);
   });

   it("should emit null when form does not exist (vpmSelectable=false)", () => {
      const model = makeModel({ vpmSelectable: false });
      const { comp } = makeComponent({ model });
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok();

      expect(emitSpy).toHaveBeenCalledWith(null);
   });
});

// ---------------------------------------------------------------------------
// Group 7 — cancel
// ---------------------------------------------------------------------------

describe("Group 7 — cancel: emits onCancel", () => {
   it("should emit onCancel when cancel() is called", () => {
      const { comp } = makeComponent();
      const emitSpy = vi.spyOn(comp.onCancel, "emit");

      comp.cancel();

      expect(emitSpy).toHaveBeenCalledOnce();
   });
});
