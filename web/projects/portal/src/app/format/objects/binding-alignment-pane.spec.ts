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
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { BindingAlignmentPane } from "./binding-alignment-pane.component";
import { AlignmentInfo } from "../../common/data/format-info-model";

describe("Binding Alignment Pane Unit Test", () => {
   let mockAlignmentInfo: () => AlignmentInfo = () => {
      return {
         valign: "Left",
         halign: "Middle"
      };
   };

   let fixture: ComponentFixture<BindingAlignmentPane>;
   let alignmentPane: BindingAlignmentPane;
   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [ReactiveFormsModule, FormsModule, NgbModule],
         declarations: [BindingAlignmentPane],
      }).compileComponents();

      fixture = TestBed.createComponent(BindingAlignmentPane);
      alignmentPane = <BindingAlignmentPane>fixture.componentInstance;
   });

   //for Bug #19910
   it("alignment can not change auto", () => {
      alignmentPane.alignmentInfo = mockAlignmentInfo();
      alignmentPane.enableHAlign = true;
      alignmentPane.enableVAlign = true;
      fixture.detectChanges();

      let autoButton: Element = fixture.nativeElement.querySelector("button.auto_id");
      expect(autoButton).not.toBeNull();

      alignmentPane.setAuto();
      expect(alignmentPane.alignmentInfo.halign).toBeNull();
      expect(alignmentPane.alignmentInfo.valign).toBeNull();
   });
});