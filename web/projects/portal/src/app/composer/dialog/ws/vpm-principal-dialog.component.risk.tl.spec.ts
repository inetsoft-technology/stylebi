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
 * VPMPrincipalDialogComponent — Pass 2: Risk / Async
 *
 * Risk-first coverage:
 *   Group 1   getRawValue includes disabled controls — ok() sees full model values
 *   Group 2   sessionType → sessionIds reset boundary: unknown type leaves sessionIds unchanged
 *   Group 3   vpmEnabled toggle round-trip: false → true re-enables controls
 *   Group 4   ngOnInit with missing optional fields (no users/roles in model)
 */

import { makeComponent, makeModel } from "./vpm-principal-dialog.component.test-helpers";

// ---------------------------------------------------------------------------
// Group 1 — getRawValue includes disabled controls
// ---------------------------------------------------------------------------

describe("Group 1 — ok() uses getRawValue so disabled controls are captured", () => {
   // Risk: if ok() used form.value instead of getRawValue(), disabled fields would be missing.
   it("should include sessionType in emitted model even when sessionType is disabled (vpmEnabled=false)", () => {
      const model = makeModel({
         vpmEnabled: false,
         sessionType: "user",
         sessionId: null,
         vpmSelectable: true,
      });
      const { comp } = makeComponent({ model });
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok();

      const emitted = emitSpy.mock.calls[0][0];
      // getRawValue captures disabled fields; if it used form.value the key would be absent
      expect(Object.prototype.hasOwnProperty.call(emitted, "sessionType")).toBe(true);
   });

   it("should include sessionId in emitted model even when disabled", () => {
      const model = makeModel({ vpmEnabled: false, sessionId: null, vpmSelectable: true, sessionType: null });
      const { comp } = makeComponent({ model });
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok();

      const emitted = emitSpy.mock.calls[0][0];
      expect(Object.prototype.hasOwnProperty.call(emitted, "sessionId")).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 2 — sessionType boundary: unknown type does not crash
// ---------------------------------------------------------------------------

describe("Group 2 — sessionType change with unknown type does not break sessionIds", () => {
   it("should not throw when sessionType is set to an unknown value", () => {
      const model = makeModel({ vpmEnabled: true, sessionType: "user" });
      const { comp } = makeComponent({ model });

      expect(() => comp.form?.get("sessionType")?.setValue("group")).not.toThrow();
   });

   it("should set sessionId to null when sessionType is unknown (sessionIds stays empty/unchanged)", () => {
      const model = makeModel({ vpmEnabled: true, sessionType: "user", users: ["u1"] });
      const { comp } = makeComponent({ model });
      // Force sessionIds to empty to simulate no valid list
      comp.sessionIds = [];

      comp.form?.get("sessionType")?.setValue("group");

      // resetSessionId() sets to null when sessionIds is empty
      expect(comp.form?.get("sessionId")?.value).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 3 — vpmEnabled toggle round-trip
// ---------------------------------------------------------------------------

describe("Group 3 — vpmEnabled toggle: false → true re-enables controls", () => {
   it("should re-enable sessionType when vpmEnabled toggles from false to true", () => {
      const model = makeModel({ vpmEnabled: true, sessionType: "user" });
      const { comp } = makeComponent({ model });

      // Disable
      comp.form?.get("vpmEnabled")?.setValue(false);
      expect(comp.form?.get("sessionType")?.disabled).toBe(true);

      // Re-enable
      comp.form?.get("vpmEnabled")?.setValue(true);
      expect(comp.form?.get("sessionType")?.enabled).toBe(true);
   });

   it("should re-enable sessionId when vpmEnabled toggles from false to true", () => {
      const model = makeModel({ vpmEnabled: true });
      const { comp } = makeComponent({ model });

      comp.form?.get("vpmEnabled")?.setValue(false);
      comp.form?.get("vpmEnabled")?.setValue(true);

      expect(comp.form?.get("sessionId")?.enabled).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — missing optional fields
// ---------------------------------------------------------------------------

describe("Group 4 — model with empty users/roles does not crash", () => {
   it("should handle empty users array when sessionType changes to user", () => {
      const model = makeModel({ vpmEnabled: true, sessionType: "role", users: [] });
      const { comp } = makeComponent({ model });

      expect(() => comp.form?.get("sessionType")?.setValue("user")).not.toThrow();
      expect(comp.sessionIds).toEqual([]);
   });

   it("should handle empty roles array when sessionType changes to role", () => {
      const model = makeModel({ vpmEnabled: true, sessionType: "user", roles: [] });
      const { comp } = makeComponent({ model });

      expect(() => comp.form?.get("sessionType")?.setValue("role")).not.toThrow();
      expect(comp.sessionIds).toEqual([]);
   });

   it("should handle model with no sessionType (null) without crashing initForm", () => {
      const model = makeModel({ vpmEnabled: true, sessionType: null, sessionId: null, vpmSelectable: true });
      expect(() => makeComponent({ model })).not.toThrow();
   });
});
