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
 * VariableAssemblyDialog — Pass 2: Risk / Async
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — cancelChanges: emits onCancel with "cancel" string
 *
 * Out of scope this pass (covered in variable-assembly-dialog.component.interaction.tl.spec.ts):
 *   init, initDefaultValueType, initForm, checkOuterMirror, getDefaultStrValue,
 *   showVariableListDialog, showVariableTableListDialog, valid, embeddedValid,
 *   queryValid, selectDefaultValueType, isExpressionDefaultValue, okDisabled,
 *   saveChanges, validateVariableList, defaultExpressionValueChange,
 *   getVariableTree, getVariableTreeModel
 */

import { makeComponent, makeModel } from "./variable-assembly-dialog.component.test-helpers";

// ---------------------------------------------------------------------------
// Group 1 — cancelChanges
// ---------------------------------------------------------------------------

describe("Group 1 — cancelChanges: emits onCancel", () => {
   // 🔁 Regression-sensitive: cancel must emit the exact "cancel" string the parent listens for
   it("should emit onCancel with 'cancel' string when cancelChanges is called", () => {
      const { comp } = makeComponent({ presetModel: makeModel() });
      const emitSpy = vi.spyOn(comp.onCancel, "emit");

      comp.cancelChanges();

      expect(emitSpy).toHaveBeenCalledOnce();
      expect(emitSpy).toHaveBeenCalledWith("cancel");
   });
});
