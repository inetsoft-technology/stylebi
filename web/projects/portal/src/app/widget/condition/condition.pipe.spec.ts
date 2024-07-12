/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Component } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { By } from "@angular/platform-browser";
import { Condition } from "../../common/data/condition/condition";
import { ConditionOperation } from "../../common/data/condition/condition-operation";
import { ConditionValueType } from "../../common/data/condition/condition-value-type";
import { ConditionPipe } from "./condition.pipe";

@Component({
   selector: "condition-pipe-test",
   template: `<div>{{condition | conditionToString}}</div>`
})
class ConditionPipeTest {
   condition: Condition;
}

describe(("Condition Pipe Test"), () => {
   beforeEach(() => {
      TestBed.configureTestingModule({
         declarations: [
            ConditionPipeTest, ConditionPipe
         ]
      });
      TestBed.compileComponents();
   });

   it("should contain 'or equal to' in the condition string", () => {
      const fixture: ComponentFixture<ConditionPipeTest> =
         TestBed.createComponent(ConditionPipeTest);

      const condition: Condition = {
         jsonType: "condition",
         field: null,
         operation: ConditionOperation.GREATER_THAN,
         values: [{type: ConditionValueType.VALUE, value: 2}],
         level: 0,
         equal: true,
         negated: false
      };

      fixture.componentInstance.condition = condition;
      fixture.detectChanges(true);
      let conditionString = fixture.debugElement.query(By.css("div")).nativeElement.textContent;
      expect(conditionString).toContain("or equal to");
   });
});
