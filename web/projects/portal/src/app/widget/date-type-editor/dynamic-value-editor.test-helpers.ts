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

import { XSchema } from "../../common/data/xschema";
import { DynamicValueModel, ValueTypes } from "../../vsobjects/model/dynamic-value-model";
import { DynamicValueEditorComponent } from "./dynamic-value-editor.component";

export interface DynamicValueEditorOptions {
   type?: string;
   valueModel?: DynamicValueModel;
   disable?: boolean;
   isInterval?: boolean;
   today?: boolean;
   defaultValue?: string;
   forceToDefault?: boolean;
}

export function makeValueModel(overrides: Partial<DynamicValueModel> = {}): DynamicValueModel {
   return {
      value: "2024-01-15",
      type: ValueTypes.VALUE,
      ...overrides
   };
}

export function createDynamicValueEditor(options: DynamicValueEditorOptions = {}) {
   const comp = new DynamicValueEditorComponent();
   comp.type = options.type ?? XSchema.DATE;
   comp.valueModel = options.valueModel ?? makeValueModel();
   comp.disable = options.disable ?? false;
   comp.isInterval = options.isInterval ?? false;
   comp.today = options.today ?? false;
   comp.defaultValue = options.defaultValue;
   comp.forceToDefault = options.forceToDefault ?? false;
   return { comp };
}
