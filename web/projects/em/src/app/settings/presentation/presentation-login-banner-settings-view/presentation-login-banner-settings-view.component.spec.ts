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
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { ReactiveFormsModule } from "@angular/forms";
import { MatCardModule } from "@angular/material/card";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatInputModule } from "@angular/material/input";
import { MatSelectModule } from "@angular/material/select";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { PresentationLoginBannerSettingsViewComponent } from "./presentation-login-banner-settings-view.component";

describe("PresentationLoginBannerSettingsViewComponent", () => {
  let component: PresentationLoginBannerSettingsViewComponent;
  let fixture: ComponentFixture<PresentationLoginBannerSettingsViewComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
       imports: [
          ReactiveFormsModule,
          NoopAnimationsModule,
          MatFormFieldModule,
          MatInputModule,
          MatCardModule,
          MatSelectModule
       ],
      declarations: [ PresentationLoginBannerSettingsViewComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(PresentationLoginBannerSettingsViewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it("should create", () => {
    expect(component).toBeTruthy();
  });
});
