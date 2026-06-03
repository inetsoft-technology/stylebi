/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import { TipCustomizeDialog } from "./tip-customize-dialog.component";
import { TipCustomizeDialogModel } from "./tip-customize-dialog-model";

describe("TipCustomizeDialog snap/combined state machine", () => {
   let dialog: TipCustomizeDialog;

   const createModel = (overrides: Partial<TipCustomizeDialogModel> = {}): TipCustomizeDialogModel => ({
      customRB: "DEFAULT",
      combinedTip: false,
      combinedSupported: true,
      customTip: "",
      dataRefList: [],
      availableTipValues: [],
      snapTooltip: false,
      snapSupported: true,
      ...overrides
   });

   const initWith = (model: TipCustomizeDialogModel) => {
      dialog = new TipCustomizeDialog(null as any, null as any);
      dialog.model = model;
      dialog.ngOnChanges(<any> { model: {} });
   };

   it("keeps snap enabled when Combined is turned off", () => {
      initWith(createModel({ combinedTip: true, snapTooltip: true }));

      dialog.form.get("combinedTip").setValue(false);

      expect(dialog.form.get("snapTooltip").enabled).toBe(true);
   });

   it("enables and checks snap when Combined is turned on", () => {
      initWith(createModel());

      dialog.form.get("combinedTip").setValue(true);

      const snap = dialog.form.get("snapTooltip");
      expect(snap.enabled).toBe(true);
      expect(snap.value).toBe(true);
   });

   it("re-enables snap when switching from NONE to CUSTOM", () => {
      initWith(createModel({ customRB: "NONE" }));
      expect(dialog.form.get("snapTooltip").disabled).toBe(true);

      dialog.form.get("customRB").setValue("CUSTOM");

      expect(dialog.form.get("snapTooltip").enabled).toBe(true);
   });

   it("disables and clears snap when switching to NONE", () => {
      initWith(createModel({ snapTooltip: true }));

      dialog.form.get("customRB").setValue("NONE");

      const snap = dialog.form.get("snapTooltip");
      expect(snap.disabled).toBe(true);
      expect(snap.value).toBe(false);
   });

   it("keeps snap disabled when snap is not supported", () => {
      initWith(createModel({ snapSupported: false }));
      expect(dialog.form.get("snapTooltip").disabled).toBe(true);

      dialog.form.get("combinedTip").setValue(true);

      expect(dialog.form.get("snapTooltip").disabled).toBe(true);
   });
});
