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
import { Component, NO_ERRORS_SCHEMA, Optional } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { By } from "@angular/platform-browser";
import { TestUtils } from "../../../common/test/test-utils";
import {
   ComposerToken, ContextProvider, EmbedToken,
   ViewerContextProviderFactory
} from "../../context-provider.service";
import { VSTitle } from "./vs-title.component";

@Component({
   selector: "test-app",
   template: "<vs-title [titleFormat]='titleFormat' [titleVisible]='titleVisible'></vs-title>"
})
class TestApp {
   public titleFormat = TestUtils.createMockVSFormatModel();
   public titleVisible: boolean = true;
}

describe("VSTitle", () => {
   beforeEach(() => {
      TestBed.configureTestingModule({
         declarations: [
            TestApp,
            VSTitle
         ],
         providers: [
            {
               provide: ContextProvider,
               useFactory: ViewerContextProviderFactory,
               deps: [[new Optional(), ComposerToken], [new Optional(), EmbedToken]]
            }
         ],
         schemas: [NO_ERRORS_SCHEMA]
      });
   });

   // Bug #18906 should not display title row when title not visible
   it("should hide title in viewer when title is not visible", () => {
      const fixture = TestBed.createComponent(TestApp);
      const testApp = fixture.componentInstance;
      testApp.titleVisible = true;
      fixture.detectChanges();

      const titleDiv = fixture.debugElement.query(By.css(".vs-title")).nativeElement;
      expect(titleDiv.style.display).toBe("flex");

      testApp.titleVisible = false;
      fixture.detectChanges();
      expect(titleDiv.style["display"]).toBe("none");
   });
});
