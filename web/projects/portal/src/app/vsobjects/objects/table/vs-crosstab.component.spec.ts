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
import { CommonModule } from "@angular/common";
import { HttpClient, HttpClientModule } from "@angular/common/http";
import { NO_ERRORS_SCHEMA, Optional } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { Router } from "@angular/router";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { of as observableOf, Subject } from "rxjs";
import { DownloadService } from "../../../../../../shared/download/download.service";
import { AppInfoService } from "../../../../../../shared/util/app-info.service";
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
   ComposerContextProviderFactory,
   ComposerToken,
   ContextProvider,
   EmbedToken,
   ViewerContextProviderFactory
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
import { InteractService } from "../../../widget/interact/interact.service";

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
      viewsheetClient = { sendEvent: vi.fn() };
      dropdownService = { open: vi.fn() };
      downloadService = { download: vi.fn() };
      modalService = { open: vi.fn() };
      dataTipService = {
         showDataTip: vi.fn(),
         isDataTip: vi.fn(),
         isDataTipVisible: vi.fn(),
         isDataTipSource: vi.fn(),
         isCurrentDataTip: vi.fn(),
         isFrozen: vi.fn(),
         hideDataTip: vi.fn(),
         getVSObjectId: vi.fn(),
         dataTipName: null,
         dataTipY: 0,
         dataTipX: 0,
         dataTipAlpha: 1,
         viewerOffset: {width: 0, height: 0}
      };
      viewDataService = {};
      dataTipService.isDataTip.mockImplementation(() => false);
      popComponentService = {
         toggle: vi.fn(),
         isPopComponent: vi.fn(),
         getPopComponent: vi.fn(),
         isCurrentPopComponent: vi.fn(),
         registerPopComponentChild: vi.fn(),
         clearPopViewerOffset: vi.fn(),
         getTriggerPopInfo: vi.fn(),
         getPopInfo: vi.fn(),
         getPopLocation: vi.fn(),
         popY: 0,
         popX: 0,
         popAlpha: 1,
         viewerOffset: {width: 0, height: 0},
         componentRegistered: new Subject<{name: string, parent: string}>()
      };
      popComponentService.isPopComponent.mockImplementation(() => false);
      modelService = { getModel: vi.fn() };
      modelService.getModel.mockImplementation(() => observableOf([]));
      debounceService = { debounce: vi.fn() };
      const formDataService: any = {
         checkFormData: vi.fn(),
         removeObject: vi.fn(),
         addObject: vi.fn(),
         replaceObject: vi.fn()
      };
      dndService = { setDragStartStype: vi.fn() };
      dialogService = { open: vi.fn() };
      adhocFilterService = { showFilter: vi.fn(), adhocFilterShowing: false };
      router = {
         navigate: vi.fn(),
         events: new Subject<any>()
      };
      richTextService = {
         showAnnotationDialog: vi.fn()
      };
      pagingControlService = {
         scrollTop: vi.fn(() => observableOf({})),
         scrollLeft: vi.fn(() => observableOf({}))
      };
      vsTabService = { };
      vsTabService.tabDeselected = observableOf(null);

      TestBed.configureTestingModule({
         imports: [
            CommonModule,
            FormsModule,
            ReactiveFormsModule,
            NgbModule,
            HttpClientModule,
            VSCrosstab,
            VSTableCell,
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
               deps: [[new Optional(), ComposerToken], [new Optional(), EmbedToken]]
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
            { provide: VSTabService, useValue: vsTabService },
            { provide: InteractService, useValue: { addInteractable: vi.fn(), removeInteractable: vi.fn(), notify: vi.fn() } },
            AppInfoService
         ]
      });
      TestBed.compileComponents();
      fixture = TestBed.createComponent(VSCrosstab);
      vsCrosstab = <VSCrosstab>fixture.componentInstance;
   });

   // Bug #17211
   it("should fire event when condition action is triggered", () => new Promise<void>((done) => {
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
   }));
});
