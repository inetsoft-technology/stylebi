/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { ComputationDialog } from "./computation-dialog.component";
import { TestUtils } from "../../../common/test/test-utils";

describe("ComputationDialog Unit Test", () => {
   let computationDialog: ComputationDialog;

   beforeEach(() => {
      computationDialog = new ComputationDialog();
   });

   // Bug #10196 Percentage and Percentile should allow multiple values
   it("should allow multiple number values", () => {
      computationDialog.model = TestUtils.createMockTargetStrategyInfo();
      computationDialog.model.standardIsSample = false;
      computationDialog.model.name = "Percentiles";
      let value: string = "24, 56";

      let isValid: boolean = computationDialog.isValueValid(value);
      expect(isValid).toBeTruthy();

      computationDialog.model.name = "Percentage";
      isValid = computationDialog.isValueValid(value);
      expect(isValid).toBeTruthy();
   });
});
