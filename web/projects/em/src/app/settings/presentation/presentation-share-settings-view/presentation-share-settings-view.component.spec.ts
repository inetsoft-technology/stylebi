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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { ReactiveFormsModule } from "@angular/forms";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatInputModule } from "@angular/material/input";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { PresentationShareSettingsViewComponent } from "./presentation-share-settings-view.component";

describe("PresentationShareSettingsViewComponent", () => {
   let component: PresentationShareSettingsViewComponent;
   let fixture: ComponentFixture<PresentationShareSettingsViewComponent>;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            NoopAnimationsModule,
            ReactiveFormsModule,
            MatCheckboxModule,
            MatInputModule
         ],
         declarations: [
            PresentationShareSettingsViewComponent
         ],
         schemas: [
            NO_ERRORS_SCHEMA
         ]
      })
      .compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(PresentationShareSettingsViewComponent);
      component = fixture.componentInstance;
      component.model = {
         emailEnabled: true,
         facebookEnabled: true,
         googleChatEnabled: true,
         googleChatUrl: "",
         linkedinEnabled: true,
         slackEnabled: true,
         slackUrl: "true",
         twitterEnabled: true,
         linkEnabled: true,
         openGraphSiteName: "",
         openGraphTitle: "",
         openGraphDescription: "",
         openGraphImageUrl: ""
      };
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
