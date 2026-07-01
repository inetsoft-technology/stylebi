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
 * DynamicValueEditorComponent - Pass 3: Display
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - ComboMode and ValueTypes bidirectional mapping
 *   Group 2 [Risk 3] - getPromptString dispatch by schema and combo type
 *   Group 3 [Risk 2] - format/mode/isDate computed getters
 *
 * Direct instantiation - pure branch coverage only.
 */

import { DateTypeFormatter } from "../../../../../shared/util/date-type-formatter";
import { XSchema } from "../../common/data/xschema";
import { ValueTypes } from "../../vsobjects/model/dynamic-value-model";
import { ComboMode, ValueMode } from "../dynamic-combo-box/dynamic-combo-box-model";
import {
   createDynamicValueEditor,
   makeValueModel
} from "./dynamic-value-editor.test-helpers";

afterEach(() => vi.restoreAllMocks());

describe("DynamicValueEditorComponent - type mapping dispatch [Group 1, Risk 3]", () => {
   it("should map known ValueTypes strings to ComboMode numbers", () => {
      const { comp } = createDynamicValueEditor();

      expect(comp.getDateValueTypeNumber(ValueTypes.VALUE)).toBe(ComboMode.VALUE);
      expect(comp.getDateValueTypeNumber(ValueTypes.VARIABLE)).toBe(ComboMode.VARIABLE);
      expect(comp.getDateValueTypeNumber(ValueTypes.EXPRESSION)).toBe(ComboMode.EXPRESSION);
   });

   it("should default unknown ValueTypes strings to ComboMode.VALUE", () => {
      const { comp } = createDynamicValueEditor();

      expect(comp.getDateValueTypeNumber("UNKNOWN")).toBe(ComboMode.VALUE);
   });

   it("should map ComboMode numbers back to ValueTypes strings", () => {
      const { comp } = createDynamicValueEditor();

      expect(comp.getDateValueTypeStr(ComboMode.VALUE)).toBe(ValueTypes.VALUE);
      expect(comp.getDateValueTypeStr(ComboMode.VARIABLE)).toBe(ValueTypes.VARIABLE);
      expect(comp.getDateValueTypeStr(ComboMode.EXPRESSION)).toBe(ValueTypes.EXPRESSION);
   });

   it("should default unknown ComboMode numbers to ValueTypes.VALUE", () => {
      const { comp } = createDynamicValueEditor();

      expect(comp.getDateValueTypeStr(99)).toBe(ValueTypes.VALUE);
   });
});

describe("DynamicValueEditorComponent - prompt string dispatch [Group 2, Risk 3]", () => {
   it("should return the generic value prompt when combo type is not VALUE", () => {
      const { comp } = createDynamicValueEditor({
         valueModel: makeValueModel({ type: ValueTypes.VARIABLE, value: "$()" })
      });

      expect(comp.getPromptString()).toBe("_#(js:Value)");
   });

   it("should return schema-specific prompts for literal date types", () => {
      const { comp: dateEditor } = createDynamicValueEditor({ type: XSchema.DATE });
      const { comp: timeEditor } = createDynamicValueEditor({ type: XSchema.TIME });
      const { comp: instantEditor } = createDynamicValueEditor({ type: XSchema.TIME_INSTANT });

      expect(dateEditor.getPromptString()).toBe("yyyy-mm-dd");
      expect(timeEditor.getPromptString()).toBe("hh:mm:ss");
      expect(instantEditor.getPromptString()).toBe("yyyy-mm-dd hh:mm:ss");
   });
});

describe("DynamicValueEditorComponent - computed getters [Group 3, Risk 2]", () => {
   it("should select format strings by schema type", () => {
      const { comp: dateEditor } = createDynamicValueEditor({ type: XSchema.DATE });
      const { comp: timeEditor } = createDynamicValueEditor({ type: XSchema.TIME });
      const { comp: instantEditor } = createDynamicValueEditor({ type: XSchema.TIME_INSTANT });

      expect(dateEditor.format).toBe(DateTypeFormatter.ISO_8601_DATE_FORMAT);
      expect(timeEditor.format).toBe(DateTypeFormatter.ISO_8601_TIME_FORMAT);
      expect(instantEditor.format).toBe("YYYY-MM-DD HH:mm:ss");
   });

   it("should expose numeric mode for numeric schema types and text mode otherwise", () => {
      const { comp: numericEditor } = createDynamicValueEditor({ type: XSchema.INTEGER });
      const { comp: textEditor } = createDynamicValueEditor({ type: XSchema.STRING });

      expect(numericEditor.mode).toBe(ValueMode.NUMBER);
      expect(textEditor.mode).toBe(ValueMode.TEXT);
   });

   it("should report isDate only for date schema types", () => {
      const { comp: dateEditor } = createDynamicValueEditor({ type: XSchema.DATE });
      const { comp: stringEditor } = createDynamicValueEditor({ type: XSchema.STRING });

      expect(dateEditor.isDate).toBe(true);
      expect(stringEditor.isDate).toBe(false);
   });
});
