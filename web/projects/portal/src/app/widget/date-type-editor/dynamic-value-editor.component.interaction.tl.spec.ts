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
 * DynamicValueEditorComponent - Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - ngOnInit date fallback and interval seeding
 *   Group 2 [Risk 3] - ngOnChanges today/forceToDefault/defaultValue branches
 *   Group 3 [Risk 3] - updateValue/updateType/dateChange emit contracts
 *   Group 4 [Risk 2] - calendar visibility/disable and getType delegation
 *
 * Direct instantiation - child combo box and date picker are not rendered.
 */

import { DateTypeFormatter } from "../../../../../shared/util/date-type-formatter";
import { XSchema } from "../../common/data/xschema";
import { ValueTypes } from "../../vsobjects/model/dynamic-value-model";
import { ComboMode } from "../dynamic-combo-box/dynamic-combo-box-model";
import {
   createDynamicValueEditor,
   makeValueModel
} from "./dynamic-value-editor.test-helpers";

afterEach(() => vi.restoreAllMocks());

describe("DynamicValueEditorComponent - ngOnInit date fallback [Group 1, Risk 3]", () => {
   it("should keep a valid valueModel date for dateTime initialization", () => {
      const { comp } = createDynamicValueEditor({
         valueModel: makeValueModel({ value: "2024-03-20" })
      });

      comp.ngOnInit();

      expect(comp.dateTime).toEqual(DateTypeFormatter.toTimeInstant("2024-03-20", comp.format));
      expect(comp.valueModel.value).toBe("2024-03-20");
   });

   it("should fall back to defaultValue when the current value is invalid", () => {
      const { comp } = createDynamicValueEditor({
         valueModel: makeValueModel({ value: "not-a-date" }),
         defaultValue: "2024-06-01"
      });

      comp.ngOnInit();

      expect(comp.valueModel.value).toBe("not-a-date");
      expect(comp.dateTime).toEqual(DateTypeFormatter.toTimeInstant("2024-06-01", comp.format));
   });

   it("should fall back to current time when value and defaultValue are invalid", () => {
      vi.spyOn(DateTypeFormatter, "currentTimeInstantInFormat").mockReturnValue("2024-07-04");
      const { comp } = createDynamicValueEditor({
         valueModel: makeValueModel({ value: "" }),
         defaultValue: "bad-default"
      });

      comp.ngOnInit();

      expect(comp.dateTime).toEqual(DateTypeFormatter.toTimeInstant("2024-07-04", comp.format));
   });

   it("should seed interval value models when the date is invalid and type is VALUE", () => {
      vi.spyOn(DateTypeFormatter, "currentTimeInstantInFormat").mockReturnValue("2024-08-09");
      const { comp } = createDynamicValueEditor({
         isInterval: true,
         valueModel: makeValueModel({ value: "", type: ValueTypes.VALUE })
      });

      comp.ngOnInit();

      expect(comp.valueModel.value).toBe("2024-08-09");
      expect(comp.dateTime).toEqual(DateTypeFormatter.toTimeInstant("2024-08-09", comp.format));
   });
});

describe("DynamicValueEditorComponent - ngOnChanges input branches [Group 2, Risk 3]", () => {
   it("should replace the value with today when today becomes true", () => {
      vi.spyOn(DateTypeFormatter, "currentTimeInstantInFormat").mockReturnValue("2024-09-10");
      const { comp } = createDynamicValueEditor({
         valueModel: makeValueModel({ value: "2024-01-01", type: ValueTypes.VALUE })
      });
      comp.ngOnInit();

      comp.ngOnChanges({
         today: { currentValue: true, previousValue: false, firstChange: false, isFirstChange: () => false }
      });

      expect(comp.valueModel.value).toBe("2024-09-10");
      expect(comp.dateTime).toEqual(DateTypeFormatter.toTimeInstant("2024-09-10", comp.format));
   });

   it("should apply valid defaultValue when forceToDefault becomes true", () => {
      const { comp } = createDynamicValueEditor({
         forceToDefault: true,
         defaultValue: "2024-10-11",
         valueModel: makeValueModel({ value: "2024-01-01", type: ValueTypes.VALUE })
      });
      comp.ngOnInit();

      comp.ngOnChanges({
         forceToDefault: {
            currentValue: true,
            previousValue: false,
            firstChange: false,
            isFirstChange: () => false
         }
      });

      expect(comp.valueModel.value).toBe("2024-10-11");
      expect(comp.dateTime).toEqual(DateTypeFormatter.toTimeInstant("2024-10-11", comp.format));
   });

   it("should apply current time when forceToDefault is set with an invalid defaultValue", () => {
      vi.spyOn(DateTypeFormatter, "currentTimeInstantInFormat").mockReturnValue("2024-11-12");
      const { comp } = createDynamicValueEditor({
         forceToDefault: true,
         defaultValue: "invalid",
         valueModel: makeValueModel({ value: "2024-01-01", type: ValueTypes.VALUE })
      });
      comp.ngOnInit();

      comp.ngOnChanges({
         defaultValue: {
            currentValue: "invalid",
            previousValue: undefined,
            firstChange: true,
            isFirstChange: () => true
         }
      });

      expect(comp.valueModel.value).toBe("2024-11-12");
      expect(comp.dateTime).toEqual(DateTypeFormatter.toTimeInstant("2024-11-12", comp.format));
   });

   it("should ignore today and forceToDefault when value type is not VALUE", () => {
      vi.spyOn(DateTypeFormatter, "currentTimeInstantInFormat").mockReturnValue("2024-12-13");
      const { comp } = createDynamicValueEditor({
         forceToDefault: true,
         defaultValue: "2024-10-11",
         valueModel: makeValueModel({ value: "$()", type: ValueTypes.VARIABLE })
      });
      comp.ngOnInit();

      comp.ngOnChanges({
         today: { currentValue: true, previousValue: false, firstChange: false, isFirstChange: () => false },
         forceToDefault: {
            currentValue: true,
            previousValue: false,
            firstChange: false,
            isFirstChange: () => false
         }
      });

      expect(comp.valueModel.value).toBe("$()");
   });
});

describe("DynamicValueEditorComponent - value mutation emits [Group 3, Risk 3]", () => {
   it("should update the model value and emit onValueModelChange", () => {
      const { comp } = createDynamicValueEditor();
      const emitSpy = vi.spyOn(comp.onValueModelChange, "emit");

      comp.updateValue("2024-05-05");

      expect(comp.valueModel.value).toBe("2024-05-05");
      expect(emitSpy).toHaveBeenCalled();
   });

   it("should map combo type numbers to value model types and emit", () => {
      const { comp } = createDynamicValueEditor();
      const emitSpy = vi.spyOn(comp.onValueModelChange, "emit");

      comp.updateType(ComboMode.VARIABLE);

      expect(comp.valueModel.type).toBe(ValueTypes.VARIABLE);
      expect(emitSpy).toHaveBeenCalled();
   });

   it("should commit calendar dates through dateChange", () => {
      const { comp } = createDynamicValueEditor();
      const emitSpy = vi.spyOn(comp.onValueModelChange, "emit");

      comp.dateChange("2024-06-06");

      expect(comp.valueModel.value).toBe("2024-06-06");
      expect(emitSpy).toHaveBeenCalled();
   });
});

describe("DynamicValueEditorComponent - calendar state [Group 4, Risk 2]", () => {
   it("should hide the calendar for expression and variable types", () => {
      const { comp: expressionEditor } = createDynamicValueEditor({
         valueModel: makeValueModel({ type: ValueTypes.EXPRESSION, value: "1=1" })
      });
      const { comp: variableEditor } = createDynamicValueEditor({
         valueModel: makeValueModel({ type: ValueTypes.VARIABLE, value: "$()" })
      });

      expect(expressionEditor.isCalendarVisible()).toBe(false);
      expect(variableEditor.isCalendarVisible()).toBe(false);
   });

   it("should show the calendar for date schema types with VALUE type", () => {
      const { comp } = createDynamicValueEditor({
         type: XSchema.DATE,
         valueModel: makeValueModel({ type: ValueTypes.VALUE })
      });

      expect(comp.isCalendarVisible()).toBe(true);
   });

   it("should disable the calendar when disabled or non-literal types are selected", () => {
      const { comp: disabledEditor } = createDynamicValueEditor({ disable: true });
      const { comp: variableEditor } = createDynamicValueEditor({
         valueModel: makeValueModel({ type: ValueTypes.VARIABLE, value: "$()" })
      });
      const { comp: expressionEditor } = createDynamicValueEditor({
         valueModel: makeValueModel({ type: ValueTypes.EXPRESSION, value: "1=1" })
      });

      expect(disabledEditor.isCalendarDisable).toBe(true);
      expect(variableEditor.isCalendarDisable).toBe(true);
      expect(expressionEditor.isCalendarDisable).toBe(true);
   });

   it("should expose combo mode through getType", () => {
      const { comp } = createDynamicValueEditor({
         valueModel: makeValueModel({ type: ValueTypes.EXPRESSION, value: "1=1" })
      });

      expect(comp.getType()).toBe(ComboMode.EXPRESSION);
   });
});
