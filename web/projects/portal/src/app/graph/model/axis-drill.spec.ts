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
import { Axis } from "./axis";
import { axisFieldLabel, drillLevelLabel, getAxisDrillFields } from "./axis-drill";

const axis = (overrides: Partial<Axis>): Axis => ({
   axisType: "x",
   sortOp: "",
   axisOps: [],
   axisFields: [],
   axisSizes: [],
   secondary: false,
   ...overrides
} as Axis);

describe("getAxisDrillFields", () => {
   it("reads the fields that can be drilled, with the levels the server sent", () => {
      const fields = getAxisDrillFields([axis({
         axisFields: ["Month(OrderDate)"],
         axisFieldOps: ["-+"],
         axisLevels: ["Month"],
         axisUpLevels: ["Quarter"],
         axisDownLevels: ["Day"]
      })]);

      expect(fields).toEqual([{
         axisType: "x", field: "Month(OrderDate)", label: "OrderDate", op: "-+",
         level: "Month", up: "Quarter", down: "Day"
      }]);
   });

   it("leaves out a field that has no op", () => {
      const fields = getAxisDrillFields([axis({
         axisFields: ["Region", "Month(OrderDate)"],
         axisFieldOps: ["", "+"]
      })]);

      expect(fields.map(f => f.field)).toEqual(["Month(OrderDate)"]);
   });

   it("pairs the ops with the fields even after the axis reversed its own axisOps", () => {
      // an x axis reverses axisOps in place to draw its labels; axisFieldOps stays with the fields
      const fields = getAxisDrillFields([axis({
         axisFields: ["Region", "Month(OrderDate)"],
         axisOps: ["+", ""],
         axisFieldOps: ["", "+"]
      })]);

      expect(fields.map(f => f.field)).toEqual(["Month(OrderDate)"]);
   });

   it("lists a field once however many axes carry it", () => {
      const a = axis({ axisFields: ["Month(OrderDate)"], axisFieldOps: ["+"] });
      const b = axis({ axisFields: ["Month(OrderDate)"], axisFieldOps: ["+"], secondary: true });
      const y = axis({ axisType: "y", axisFields: ["Month(OrderDate)"], axisFieldOps: ["+"] });

      expect(getAxisDrillFields([a, b, y]).map(f => f.axisType)).toEqual(["x", "y"]);
   });

   it("tolerates axes without the drill data", () => {
      expect(getAxisDrillFields(undefined)).toEqual([]);
      expect(getAxisDrillFields([axis({ axisFields: ["A"] })])).toEqual([]);
      expect(getAxisDrillFields([axis({ axisFields: ["A"], axisFieldOps: ["+"] })])[0].level)
         .toBe("");
   });
});

describe("axisFieldLabel", () => {
   it("strips the date level", () => {
      expect(axisFieldLabel("Month(OrderDate)")).toBe("OrderDate");
      expect(axisFieldLabel("QuarterOfYear(Order Date)")).toBe("Order Date");
      expect(axisFieldLabel("Region")).toBe("Region");
      expect(axisFieldLabel(null)).toBe("");
   });
});

describe("drillLevelLabel", () => {
   it("names the continuous levels and nothing else", () => {
      for(const level of ["Year", "Quarter", "Month", "Week", "Day", "Hour", "Minute", "Second"]) {
         expect(drillLevelLabel(level)).not.toBe("");
      }

      expect(drillLevelLabel("QuarterOfYear")).toBe("");
      expect(drillLevelLabel("")).toBe("");
   });
});
