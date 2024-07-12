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
import { CommonModule } from "@angular/common";
import { HttpClient, HttpClientModule } from "@angular/common/http";
import { NO_ERRORS_SCHEMA, Optional } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { Router } from "@angular/router";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { of as observableOf, Subject } from "rxjs";
import { DownloadService } from "../../../../../../shared/download/download.service";
import { DndService } from "../../../common/dnd/dnd.service";
import { TestUtils } from "../../../common/test/test-utils";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { ViewDataService } from "../../../viewer/services/view-data.service";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DebounceService } from "../../../widget/services/debounce.service";
import { ModelService } from "../../../widget/services/model.service";
import { DefaultScaleService } from "../../../widget/services/scale/default-scale-service";
import { ScaleService } from "../../../widget/services/scale/scale-service";
import { DialogService } from "../../../widget/slide-out/dialog-service.service";
import { CrosstabActions } from "../../action/crosstab-actions";
import {
   ComposerContextProviderFactory, ComposerToken, ContextProvider, ViewerContextProviderFactory
} from "../../context-provider.service";
import { RichTextService } from "../../dialog/rich-text-dialog/rich-text.service";
import { ShowHyperlinkService } from "../../show-hyperlink.service";
import { CheckFormDataService } from "../../util/check-form-data.service";
import { AdhocFilterService } from "../data-tip/adhoc-filter.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { PopComponentService } from "../data-tip/pop-component.service";
import { VSCrosstab } from "./vs-crosstab.component";
import { VSTableCell } from "./vs-table-cell.component";
import { PagingControlService } from "../../../common/services/paging-control.service";
import { VSTabService } from "../../util/vs-tab.service";

describe("VSCrosstab", () => {
   const createModel = () => {
      return TestUtils.createMockVSCrosstabModel("Table1");
   };

   let viewsheetClient: any;
   let dropdownService: any;
   let downloadService: any;
   let modalService: any;
   let dataTipService: any;
   let viewDataService: any;
   let popComponentService: any;
   let dndService: any;
   let fixture: ComponentFixture<VSCrosstab>;
   let vsCrosstab: VSCrosstab;
   let modelService: any;
   let debounceService: any;
   let dialogService: any;
   let adhocFilterService: any;
   let router: any;
   let richTextService: any;
   let pagingControlService: any;
   let vsTabService: any;

   beforeEach(() => {
      viewsheetClient = { sendEvent: jest.fn() };
      dropdownService = { open: jest.fn() };
      downloadService = { download: jest.fn() };
      modalService = { open: jest.fn() };
      dataTipService = {
         showDataTip: jest.fn(),
         isDataTip: jest.fn(),
         isDataTipVisible: jest.fn(),
         isDataTipSource: jest.fn()
      };
      viewDataService = {};
      dataTipService.isDataTip.mockImplementation(() => false);
      popComponentService = { toggle: jest.fn(), isPopComponent: jest.fn() };
      popComponentService.isPopComponent.mockImplementation(() => false);
      modelService = { getModel: jest.fn() };
      modelService.getModel.mockImplementation(() => observableOf([]));
      debounceService = { debounce: jest.fn() };
      const formDataService: any = {
         checkFormData: jest.fn(),
         removeObject: jest.fn(),
         addObject: jest.fn(),
         replaceObject: jest.fn()
      };
      dndService = { setDragStartStype: jest.fn() };
      dialogService = { open: jest.fn() };
      adhocFilterService = { showFilter: jest.fn(), adhocFilterShowing: false };
      router = {
         navigate: jest.fn(),
         events: new Subject<any>()
      };
      richTextService = {
         showAnnotationDialog: jest.fn()
      };
      pagingControlService = {
         scrollTop: jest.fn(() => observableOf({})),
         scrollLeft: jest.fn(() => observableOf({}))
      };
      vsTabService = { };
      vsTabService.tabDeselected = observableOf(null);

      TestBed.configureTestingModule({
         imports: [
            CommonModule,
            FormsModule,
            ReactiveFormsModule,
            NgbModule,
            HttpClientModule
         ],
         declarations: [
            VSCrosstab, VSTableCell
         ],
         schemas: [NO_ERRORS_SCHEMA],
         providers: [
            { provide: ViewsheetClientService, useValue: viewsheetClient },
            { provide: FixedDropdownService, useValue: dropdownService },
            { provide: DownloadService, useValue: downloadService },
            { provide: NgbModal, useValue: modalService },
            { provide: DataTipService, useValue: dataTipService },
            { provide: ViewDataService, useValue: viewDataService },
            { provide: PopComponentService, useValue: popComponentService },
            { provide: ModelService, useValue: modelService },
            { provide: CheckFormDataService, useValue: formDataService },
            {
               provide: ContextProvider,
               useFactory: ViewerContextProviderFactory,
               deps: [[new Optional(), ComposerToken]]
            },
            { provide: DebounceService, useValue: debounceService },
            { provide: ScaleService, useClass: DefaultScaleService},
            { provide: DndService, useValue: dndService },
            ShowHyperlinkService,
            HttpClient,
            { provide: DialogService, useValue: dialogService },
            { provide: AdhocFilterService, useValue: adhocFilterService },
            { provide: Router, useValue: router },
            { provide: RichTextService, useValue: richTextService },
            { provide: PagingControlService, useValue: pagingControlService },
            { provide: VSTabService, useValue: vsTabService }
         ]
      });
      TestBed.compileComponents();
      fixture = TestBed.createComponent(VSCrosstab);
      vsCrosstab = <VSCrosstab>fixture.componentInstance;
   });

   // Bug #17211
   it("should fire event when condition action is triggered", (done) => {
      const model = createModel();
      const actions = new CrosstabActions(model, ComposerContextProviderFactory(),
                                          false, null, dataTipService);
      vsCrosstab.model = model;
      vsCrosstab.actions = actions;
      fixture.detectChanges();

      vsCrosstab.onOpenConditionDialog.subscribe((event) => {
         expect(event).toBe(model);
         done();
      });

      actions.menuActions[3].actions[0].action(null);
   });
});
