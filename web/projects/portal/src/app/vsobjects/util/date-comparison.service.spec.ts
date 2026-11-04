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

import { DatePeriodModel } from "../model/date-period-model";
import { ValueTypes } from "../model/dynamic-value-model";
import { DateComparisonService } from "./date-comparison.service";

describe("DateComparisonService.hasOverlappingFixedPeriods", () => {
   let service: DateComparisonService;

   const fixed = (start: string, end: string): DatePeriodModel => ({
      start: { value: start, type: ValueTypes.VALUE },
      end: { value: end, type: ValueTypes.VALUE }
   });

   const variable = (start: string, end: string): DatePeriodModel => ({
      start: { value: start, type: ValueTypes.VARIABLE },
      end: { value: end, type: ValueTypes.VARIABLE }
   });

   beforeEach(() => {
      service = new DateComparisonService();
   });

   it("should accept disjoint ranges", () => {
      expect(service.hasOverlappingFixedPeriods([
         fixed("2020-01-01", "2020-03-31"), fixed("2020-04-01", "2020-06-30")])).toBe(false);
   });

   it("should reject partially overlapping ranges", () => {
      expect(service.hasOverlappingFixedPeriods([
         fixed("2020-01-01", "2020-03-31"), fixed("2020-02-15", "2020-05-15")])).toBe(true);
   });

   it("should treat end of one equal to start of another as an overlap", () => {
      expect(service.hasOverlappingFixedPeriods([
         fixed("2020-01-01", "2020-03-31"), fixed("2020-03-31", "2020-05-15")])).toBe(true);
   });

   it("should reject a range nested inside another", () => {
      expect(service.hasOverlappingFixedPeriods([
         fixed("2020-01-01", "2020-12-31"), fixed("2020-05-01", "2020-05-31")])).toBe(true);
   });

   it("should be independent of list order", () => {
      expect(service.hasOverlappingFixedPeriods([
         fixed("2020-02-15", "2020-05-15"), fixed("2020-01-01", "2020-03-31")])).toBe(true);
      expect(service.hasOverlappingFixedPeriods([
         fixed("2020-04-01", "2020-06-30"), fixed("2020-01-01", "2020-03-31")])).toBe(false);
   });

   it("should check non-adjacent pairs", () => {
      expect(service.hasOverlappingFixedPeriods([
         fixed("2020-01-01", "2020-12-31"), fixed("2021-01-01", "2021-03-31"),
         fixed("2021-04-01", "2021-06-30"), fixed("2020-06-01", "2020-06-02")])).toBe(true);
   });

   it("should compare dates across formats and unpadded values", () => {
      expect(service.hasOverlappingFixedPeriods([
         fixed("20200101", "20200331"), fixed("2020-3-31", "2020-05-15")])).toBe(true);
      expect(service.hasOverlappingFixedPeriods([
         fixed("2020-1-9", "2020-1-10"), fixed("2020-01-11", "2020-01-12")])).toBe(false);
   });

   it("should not check variable ranges or incomplete dates", () => {
      expect(service.hasOverlappingFixedPeriods([
         variable("$(a)", "$(b)"), variable("$(a)", "$(b)")])).toBe(false);
      expect(service.hasOverlappingFixedPeriods([
         fixed("2020-01-01", "2020-03-31"), variable("$(a)", "$(b)")])).toBe(false);
      expect(service.hasOverlappingFixedPeriods([
         fixed("2020-01-01", "2020-03-31"), fixed("2020-02-01", null)])).toBe(false);
      expect(service.hasOverlappingFixedPeriods([
         fixed("2020-01-01", "2020-03-31"), fixed("2020-02-01", "garbage")])).toBe(false);
   });

   it("should handle empty input", () => {
      expect(service.hasOverlappingFixedPeriods([])).toBe(false);
      expect(service.hasOverlappingFixedPeriods(null)).toBe(false);
   });
});
