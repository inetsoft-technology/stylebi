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

import { PresentationTimeSettingsViewComponent } from "./presentation-time-settings-view.component";
import { ReactiveFormsModule } from "@angular/forms";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatSelectModule } from "@angular/material/select";
import { MatCardModule } from "@angular/material/card";

describe("PresentationTimeSettingsViewComponent", () => {
  let component: PresentationTimeSettingsViewComponent;
  let fixture: ComponentFixture<PresentationTimeSettingsViewComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        ReactiveFormsModule,
        NoopAnimationsModule,
        MatFormFieldModule,
        MatSelectModule,
        MatCardModule,
            PresentationTimeSettingsViewComponent]
         })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(PresentationTimeSettingsViewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it("should create", () => {
    expect(component).toBeTruthy();
  });

  // week.start only accepts the seven day names; anything else silently falls back to
  // Sunday on the server, so the control must not accept free text.
  it("should offer only the valid week start values", () => {
    expect(component.weekStartOptions.map(o => o.value))
      .toEqual(["", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday",
                "Saturday"]);
  });

  // an existing lower-case value must still select its option rather than reading as
  // "default" and being cleared on the next save.
  it("should normalize an existing week start onto the option list", () => {
    component.model = {weekStart: "monday", scheduleTime12Hours: false};
    expect(component.model.weekStart).toBe("Monday");

    // a value matching nothing is preserved, not silently blanked on the next save
    component.model = {weekStart: "bogus", scheduleTime12Hours: false};
    expect(component.model.weekStart).toBe("bogus");
  });
});
