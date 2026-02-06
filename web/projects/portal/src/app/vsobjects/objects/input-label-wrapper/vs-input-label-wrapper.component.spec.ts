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
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { CommonModule } from "@angular/common";
import { VSInputLabelWrapper } from "./vs-input-label-wrapper.component";
import { VSInputLabelModel } from "../../model/vs-input-label-model";

describe("VSInputLabelWrapper", () => {
   let component: VSInputLabelWrapper;
   let fixture: ComponentFixture<VSInputLabelWrapper>;

   beforeEach(async () => {
      await TestBed.configureTestingModule({
         imports: [CommonModule],
         declarations: [VSInputLabelWrapper]
      }).compileComponents();

      fixture = TestBed.createComponent(VSInputLabelWrapper);
      component = fixture.componentInstance;
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });

   it("should not show label when labelModel is null", () => {
      component.labelModel = null;
      fixture.detectChanges();

      expect(component.showLabel).toBeFalse();
   });

   it("should not show label when showLabel is false", () => {
      component.labelModel = {
         showLabel: false,
         labelText: "Test Label",
         labelPosition: "left"
      };
      fixture.detectChanges();

      expect(component.showLabel).toBeFalse();
   });

   it("should show label when showLabel is true", () => {
      component.labelModel = {
         showLabel: true,
         labelText: "Test Label",
         labelPosition: "left"
      };
      fixture.detectChanges();

      expect(component.showLabel).toBeTrue();
      expect(component.labelText).toBe("Test Label");
   });

   it("should return correct wrapper class for each position", () => {
      const positions: Array<"top" | "bottom" | "left" | "right"> = ["top", "bottom", "left", "right"];

      positions.forEach(position => {
         component.labelModel = {
            showLabel: true,
            labelText: "Test",
            labelPosition: position
         };

         expect(component.wrapperClass).toBe(`label-${position}`);
      });
   });

   it("should use default gap when not specified", () => {
      component.labelModel = {
         showLabel: true,
         labelText: "Test",
         labelPosition: "left"
      };

      expect(component.labelGap).toBe(5);
   });

   it("should use specified gap", () => {
      component.labelModel = {
         showLabel: true,
         labelText: "Test",
         labelPosition: "left",
         labelGap: 10
      };

      expect(component.labelGap).toBe(10);
   });

   it("should apply label format styles", () => {
      component.labelModel = {
         showLabel: true,
         labelText: "Test",
         labelPosition: "left",
         labelFormat: {
            foreground: "#ff0000",
            font: "12px Arial",
            hAlign: "center",
            decoration: "underline"
         } as any
      };

      const styles = component.labelStyles;

      expect(styles["color"]).toBe("#ff0000");
      expect(styles["font"]).toBe("12px Arial");
      expect(styles["text-align"]).toBe("center");
      expect(styles["text-decoration"]).toBe("underline");
   });
});
