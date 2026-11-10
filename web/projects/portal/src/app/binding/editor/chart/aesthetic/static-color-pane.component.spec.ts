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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { TestBed, waitForAsync } from "@angular/core/testing";
import { StaticColorModel } from "../../../../common/data/visual-frame-model";
import { StaticColorPane } from "./static-color-pane.component";

describe("Static Color Pane Unit Test", () => {
   beforeEach(waitForAsync(() => {
      TestBed.configureTestingModule({
         imports: [StaticColorPane],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();
   }));

   it("clear sends the default colour", () => {
      const fixture = TestBed.createComponent(StaticColorPane);
      const pane = fixture.componentInstance;
      pane.frame = <StaticColorModel> {
         clazz: "inetsoft.web.binding.model.graph.aesthetic.StaticColorModel",
         name: null,
         field: null,
         summary: false,
         changed: true,
         color: "#d84d3f",
         cssColor: "",
         defaultColor: "#518db9",
      };

      pane.clearColor("#0490ff");
      expect(pane.frame.color).toEqual("#518db9");
      expect(pane.frame.changed).toBe(false);
   });
});
