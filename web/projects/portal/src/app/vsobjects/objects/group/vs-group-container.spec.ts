/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { By } from "@angular/platform-browser";
import { TestUtils } from "../../../common/test/test-utils";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { ContextProvider } from "../../context-provider.service";
import { VSGroupContainerModel } from "../../model/vs-group-container-model";
import { DataTipService } from "../data-tip/data-tip.service";
import { VSGroupContainer } from "./vs-group-container.component";

describe("VS Group Container Unit Test", () => {
   let fixture: ComponentFixture<VSGroupContainer>;
   let model: VSGroupContainerModel;
   let viewsheetClient: any;
   let dataTipService: any;

   beforeEach(() => {
      model = TestUtils.createMockVSGroupContainerModel("Group1");
      viewsheetClient = { sendEvent: jest.fn() };
      viewsheetClient.runtimeId = "Viewsheet1";
      dataTipService = { isDataTip: jest.fn() };
      const contextProvider = {};

      TestBed.configureTestingModule({
         imports: [],
         declarations: [ VSGroupContainer ],
         schemas: [NO_ERRORS_SCHEMA],
         providers: [
            { provide: ContextProvider, useValue: contextProvider },
            { provide: ViewsheetClientService, useValue: viewsheetClient },
            { provide: DataTipService, useValue: dataTipService }
         ]
      });
      TestBed.compileComponents();
   });

   // Bug #20760 should apply alpha to background image
   it("should apply alpha to background image", () => {
      model.objectFormat.alpha = 0.8;
      fixture = TestBed.createComponent(VSGroupContainer);
      fixture.componentInstance.model = model;
      fixture.detectChanges();

      expect(fixture.debugElement.query(By.css("img"))).toBe(null);

      model.noImageFlag = false;
      model.imageAlpha = "30";
      fixture.detectChanges();

      let image = fixture.debugElement.query(By.css("img")).nativeElement;

      expect(image.style["opacity"]).toBe("0.3");
   });
});
