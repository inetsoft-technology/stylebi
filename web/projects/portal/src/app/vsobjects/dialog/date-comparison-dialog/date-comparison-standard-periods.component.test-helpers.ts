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
 * Shared fixtures for
 * date-comparison-standard-periods.component.{interaction,display}.tl.spec.ts.
 *
 * Direct instantiation — the component has a single constructor dependency
 * (DateComparisonService) with no `inject()` calls anywhere in its chain, so no
 * TestBed wiring is needed.
 */

import { DateComparisonStandardPeriodsComponent } from "./date-comparison-standard-periods.component";
import { DateComparisonService } from "../../util/date-comparison.service";
import { StandardPeriodPaneModel } from "../../model/standard-period-pane-model";
import { DynamicValueModel, ValueTypes } from "../../model/dynamic-value-model";
import { XConstants } from "../../../common/util/xconstants";

export function makeDynamicValue(overrides: Partial<DynamicValueModel> = {}): DynamicValueModel {
   return Object.assign({ value: "1", type: ValueTypes.VALUE as string }, overrides);
}

export function makeStandardPeriodModel(
   overrides: Partial<StandardPeriodPaneModel> = {}
): StandardPeriodPaneModel {
   return Object.assign({
      preCount: makeDynamicValue({ value: "1" }),
      dateLevel: makeDynamicValue({ value: XConstants.YEAR_DATE_GROUP + "" }),
      toDate: false,
      endDay: makeDynamicValue({ value: "2024-03-15" }),
      toDayAsEndDay: false,
      inclusive: false,
   }, overrides);
}

export interface CreateComponentOpts {
   model?: StandardPeriodPaneModel;
}

export function createComponent(opts: CreateComponentOpts = {}) {
   const dateComparisonService = {
      getDateComparisonValueTypeStr: vi.fn((type: number) => ValueTypes.VALUE as string),
      isValidDate: vi.fn(() => true),
   };

   const comp = new DateComparisonStandardPeriodsComponent(
      dateComparisonService as unknown as DateComparisonService
   );
   comp.standardPeriodPaneModel = opts.model ?? makeStandardPeriodModel();

   return { comp, dateComparisonService };
}
