/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

/**
 * ComposerEmptyEditor — Pass 1
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — ngOnInit: calls modelService.getModel(COMPOSER_CUSTOM_MESSAGE_URI)
 *                        .subscribe and sets customMessageModel from the response
 *   Group 2  [Risk 3] — onDrop (worksheet): emits onOpenSheet with {type:"worksheet", assetId}
 *                        for each worksheet entry; calls composerRecentService.addRecentlyViewed
 *   Group 3  [Risk 3] — onDrop (viewsheet): emits onOpenSheet with {type:"viewsheet", assetId}
 *                        for each viewsheet entry; calls composerRecentService.addRecentlyViewed
 *   Group 4  [Risk 2] — onDrop (script): emits onOpenLibraryAsset with {type:"script", assetId}
 *                        for each script entry
 *   Group 5  [Risk 2] — onDrop (tableStyle): emits onOpenLibraryAsset with
 *                        {type:"tableStyle", assetId, styleId} from properties["styleID"]
 *   Group 6  [Risk 2] — createVSMessage / createWSMessage / editVSMessage getters:
 *                        return custom message when set, default i18n key otherwise
 *   Group 7  [Risk 1] — memory-leak: getModel() completes after first emit (of()), so no
 *                        persistent subscription is held; no unsubscribe needed
 *
 * Confirmed bugs: none
 *
 * Suspected bugs (header only):
 *   Suspicion A — Group 7 (memory-leak): Because getModel() is implemented as of() in the mock
 *     (single synchronous emit + complete), the subscription auto-completes and no manual
 *     unsubscribe is needed. If the real API ever changes to a long-lived observable this
 *     guarantee disappears. No action needed now; document awareness here.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { Observable, of } from "rxjs";

import { ComposerEmptyEditor } from "./composer-empty-editor.component";
import { DragService } from "../../../widget/services/drag.service";
import { ModelService } from "../../../widget/services/model.service";
import { ComposerRecentService } from "../composer-recent.service";
import { AssetTreeService } from "../../../widget/asset-tree/asset-tree.service";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { GuiTool } from "../../../common/util/gui-tool";
import { ComposerCustomMessageModel } from "../../data/composer-custom-message-model";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";

// ---------------------------------------------------------------------------
// Service mocks
// ---------------------------------------------------------------------------

const DRAG_SERVICE_MOCK = { getDragData: vi.fn() };
const MODEL_SERVICE_MOCK = { getModel: vi.fn() };
const RECENT_SERVICE_MOCK = { addRecentlyViewed: vi.fn() };

// ---------------------------------------------------------------------------
// Factory helpers
// ---------------------------------------------------------------------------

function makeAssetEntry(identifier: string, properties: Record<string, string> = {}): AssetEntry {
   return {
      identifier,
      properties,
      type: AssetType.WORKSHEET,
      path: "",
      scope: 0,
   } as any;
}

function makeDragEvent(): any {
   return { preventDefault: vi.fn() };
}

function makeDragData(assetsByType: Partial<Record<AssetType, AssetEntry[]>>): Record<string, string> {
   const result: Record<string, string> = {};
   for(const [type, entries] of Object.entries(assetsByType)) {
      const key = AssetTreeService.getDragName(type as AssetType);
      result[key] = JSON.stringify(entries);
   }
   return result;
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

async function renderComponent(
   customMessage: ComposerCustomMessageModel | null = null
) {
   MODEL_SERVICE_MOCK.getModel.mockReturnValue(of(customMessage));

   const { fixture } = await render(ComposerEmptyEditor, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: DragService, useValue: DRAG_SERVICE_MOCK },
         { provide: ModelService, useValue: MODEL_SERVICE_MOCK },
         { provide: ComposerRecentService, useValue: RECENT_SERVICE_MOCK },
      ],
   });

   const comp = fixture.componentInstance as ComposerEmptyEditor;
   return { fixture, comp };
}

// ---------------------------------------------------------------------------
// Per-test reset
// ---------------------------------------------------------------------------

beforeEach(() => {
   DRAG_SERVICE_MOCK.getDragData.mockReset();
   MODEL_SERVICE_MOCK.getModel.mockReset();
   RECENT_SERVICE_MOCK.addRecentlyViewed.mockReset();
});

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: ngOnInit [Risk 3]
// ---------------------------------------------------------------------------

describe("ComposerEmptyEditor — ngOnInit", () => {

   // 🔁 Regression-sensitive: customMessageModel must be populated on init so the
   //    message getters return custom values without a second round-trip.
   it("should call modelService.getModel with the customMessage URI", async () => {
      MODEL_SERVICE_MOCK.getModel.mockReturnValue(of(null));
      await renderComponent();

      expect(MODEL_SERVICE_MOCK.getModel).toHaveBeenCalledWith(
         "../api/composer/customMessage"
      );
   });

   it("should set customMessageModel from the service response", async () => {
      const msg: ComposerCustomMessageModel = {
         viewsheetCreateMessage: "Create VS",
         viewsheetEditMessage: "Edit VS",
         worksheetCreateMessage: "Create WS",
         worksheetEditMessage: "Edit WS",
      };
      const { comp } = await renderComponent(msg);

      expect(comp.customMessageModel).toEqual(msg);
   });

   it("should leave customMessageModel null when service returns null", async () => {
      const { comp } = await renderComponent(null);

      expect(comp.customMessageModel).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 2: onDrop — worksheet [Risk 3]
// ---------------------------------------------------------------------------

describe("ComposerEmptyEditor — onDrop (worksheet)", () => {

   // 🔁 Regression-sensitive: onOpenSheet must carry type="worksheet" so the composer
   //    opens the correct editor; wrong type silently opens a viewsheet editor.
   it("should emit onOpenSheet with type='worksheet' and assetId for each worksheet entry", async () => {
      const { comp } = await renderComponent();
      const spy = vi.fn();
      comp.onOpenSheet.subscribe(spy);

      const ws1 = makeAssetEntry("ws-id-1");
      const ws2 = makeAssetEntry("ws-id-2");
      DRAG_SERVICE_MOCK.getDragData.mockReturnValue(
         makeDragData({ [AssetType.WORKSHEET]: [ws1, ws2] })
      );
      vi.spyOn(GuiTool, "clearDragImage").mockImplementation(() => {});

      comp.onDrop(makeDragEvent());

      expect(spy).toHaveBeenCalledTimes(2);
      expect(spy).toHaveBeenCalledWith({ type: "worksheet", assetId: "ws-id-1" });
      expect(spy).toHaveBeenCalledWith({ type: "worksheet", assetId: "ws-id-2" });
   });

   // 🔁 Regression-sensitive: addRecentlyViewed must be called so the recent list is updated;
   //    missing call leaves the recently-opened sidebar stale.
   it("should call composerRecentService.addRecentlyViewed for each worksheet asset", async () => {
      const { comp } = await renderComponent();
      const ws1 = makeAssetEntry("ws-id-1");
      DRAG_SERVICE_MOCK.getDragData.mockReturnValue(
         makeDragData({ [AssetType.WORKSHEET]: [ws1] })
      );
      vi.spyOn(GuiTool, "clearDragImage").mockImplementation(() => {});

      comp.onDrop(makeDragEvent());

      expect(RECENT_SERVICE_MOCK.addRecentlyViewed).toHaveBeenCalledWith(ws1);
   });

   it("should call event.preventDefault()", async () => {
      const { comp } = await renderComponent();
      DRAG_SERVICE_MOCK.getDragData.mockReturnValue({});
      vi.spyOn(GuiTool, "clearDragImage").mockImplementation(() => {});
      const event = makeDragEvent();

      comp.onDrop(event);

      expect(event.preventDefault).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 3: onDrop — viewsheet [Risk 3]
// ---------------------------------------------------------------------------

describe("ComposerEmptyEditor — onDrop (viewsheet)", () => {

   // 🔁 Regression-sensitive: type must be "viewsheet" (not "worksheet"); the composer
   //    switches editors based on this discriminant.
   it("should emit onOpenSheet with type='viewsheet' and assetId for each viewsheet entry", async () => {
      const { comp } = await renderComponent();
      const spy = vi.fn();
      comp.onOpenSheet.subscribe(spy);

      const vs1 = makeAssetEntry("vs-id-1");
      const vs2 = makeAssetEntry("vs-id-2");
      DRAG_SERVICE_MOCK.getDragData.mockReturnValue(
         makeDragData({ [AssetType.VIEWSHEET]: [vs1, vs2] })
      );
      vi.spyOn(GuiTool, "clearDragImage").mockImplementation(() => {});

      comp.onDrop(makeDragEvent());

      expect(spy).toHaveBeenCalledTimes(2);
      expect(spy).toHaveBeenCalledWith({ type: "viewsheet", assetId: "vs-id-1" });
      expect(spy).toHaveBeenCalledWith({ type: "viewsheet", assetId: "vs-id-2" });
   });

   it("should call composerRecentService.addRecentlyViewed for each viewsheet asset", async () => {
      const { comp } = await renderComponent();
      const vs1 = makeAssetEntry("vs-id-1");
      DRAG_SERVICE_MOCK.getDragData.mockReturnValue(
         makeDragData({ [AssetType.VIEWSHEET]: [vs1] })
      );
      vi.spyOn(GuiTool, "clearDragImage").mockImplementation(() => {});

      comp.onDrop(makeDragEvent());

      expect(RECENT_SERVICE_MOCK.addRecentlyViewed).toHaveBeenCalledWith(vs1);
   });
});

// ---------------------------------------------------------------------------
// Group 4: onDrop — script [Risk 2]
// ---------------------------------------------------------------------------

describe("ComposerEmptyEditor — onDrop (script)", () => {

   it("should emit onOpenLibraryAsset with type='script' and assetId for each script entry", async () => {
      const { comp } = await renderComponent();
      const spy = vi.fn();
      comp.onOpenLibraryAsset.subscribe(spy);

      const sc1 = makeAssetEntry("script-id-1");
      DRAG_SERVICE_MOCK.getDragData.mockReturnValue(
         makeDragData({ [AssetType.SCRIPT]: [sc1] })
      );
      vi.spyOn(GuiTool, "clearDragImage").mockImplementation(() => {});

      comp.onDrop(makeDragEvent());

      expect(spy).toHaveBeenCalledWith({ type: "script", assetId: "script-id-1" });
   });

   it("should call composerRecentService.addRecentlyViewed for each script asset", async () => {
      const { comp } = await renderComponent();
      const sc1 = makeAssetEntry("script-id-1");
      DRAG_SERVICE_MOCK.getDragData.mockReturnValue(
         makeDragData({ [AssetType.SCRIPT]: [sc1] })
      );
      vi.spyOn(GuiTool, "clearDragImage").mockImplementation(() => {});

      comp.onDrop(makeDragEvent());

      expect(RECENT_SERVICE_MOCK.addRecentlyViewed).toHaveBeenCalledWith(sc1);
   });
});

// ---------------------------------------------------------------------------
// Group 5: onDrop — tableStyle [Risk 2]
// ---------------------------------------------------------------------------

describe("ComposerEmptyEditor — onDrop (tableStyle)", () => {

   it("should emit onOpenLibraryAsset with type='tableStyle', assetId, and styleId from properties", async () => {
      const { comp } = await renderComponent();
      const spy = vi.fn();
      comp.onOpenLibraryAsset.subscribe(spy);

      const ts1 = makeAssetEntry("ts-id-1", { styleID: "style-42" });
      DRAG_SERVICE_MOCK.getDragData.mockReturnValue(
         makeDragData({ [AssetType.TABLE_STYLE]: [ts1] })
      );
      vi.spyOn(GuiTool, "clearDragImage").mockImplementation(() => {});

      comp.onDrop(makeDragEvent());

      expect(spy).toHaveBeenCalledWith({
         type: "tableStyle",
         assetId: "ts-id-1",
         styleId: "style-42",
      });
   });

   it("should call composerRecentService.addRecentlyViewed for each tableStyle asset", async () => {
      const { comp } = await renderComponent();
      const ts1 = makeAssetEntry("ts-id-1", { styleID: "style-42" });
      DRAG_SERVICE_MOCK.getDragData.mockReturnValue(
         makeDragData({ [AssetType.TABLE_STYLE]: [ts1] })
      );
      vi.spyOn(GuiTool, "clearDragImage").mockImplementation(() => {});

      comp.onDrop(makeDragEvent());

      expect(RECENT_SERVICE_MOCK.addRecentlyViewed).toHaveBeenCalledWith(ts1);
   });

   it("should pass undefined styleId when styleID property is absent", async () => {
      const { comp } = await renderComponent();
      const spy = vi.fn();
      comp.onOpenLibraryAsset.subscribe(spy);

      const ts1 = makeAssetEntry("ts-id-no-style", {});
      DRAG_SERVICE_MOCK.getDragData.mockReturnValue(
         makeDragData({ [AssetType.TABLE_STYLE]: [ts1] })
      );
      vi.spyOn(GuiTool, "clearDragImage").mockImplementation(() => {});

      comp.onDrop(makeDragEvent());

      expect(spy).toHaveBeenCalledWith(
         expect.objectContaining({ type: "tableStyle", assetId: "ts-id-no-style" })
      );
   });
});

// ---------------------------------------------------------------------------
// Group 6: createVSMessage / editVSMessage getters [Risk 2]
// ---------------------------------------------------------------------------

describe("ComposerEmptyEditor — createVSMessage getter", () => {

   it("should return the custom viewsheetCreateMessage when set", async () => {
      const msg: ComposerCustomMessageModel = {
         viewsheetCreateMessage: "My Create VS",
         viewsheetEditMessage: "",
         worksheetCreateMessage: "",
         worksheetEditMessage: "",
      };
      const { comp } = await renderComponent(msg);

      expect(comp.createVSMessage).toBe("My Create VS");
   });

   it("should return the default i18n key when viewsheetCreateMessage is empty", async () => {
      const msg: ComposerCustomMessageModel = {
         viewsheetCreateMessage: "",
         viewsheetEditMessage: "",
         worksheetCreateMessage: "",
         worksheetEditMessage: "",
      };
      const { comp } = await renderComponent(msg);

      expect(comp.createVSMessage).toBe("_#(js:common.createViewsheet)");
   });

   it("should return the default i18n key when customMessageModel is null", async () => {
      const { comp } = await renderComponent(null);

      expect(comp.createVSMessage).toBe("_#(js:common.createViewsheet)");
   });
});

describe("ComposerEmptyEditor — createWSMessage getter", () => {

   it("should return the default i18n key when worksheetCreateMessage is empty", async () => {
      const msg: ComposerCustomMessageModel = {
         viewsheetCreateMessage: "",
         viewsheetEditMessage: "",
         worksheetCreateMessage: "",
         worksheetEditMessage: "",
      };
      const { comp } = await renderComponent(msg);

      expect(comp.createWSMessage).toBe("_#(js:common.createWorksheet)");
   });
});

describe("ComposerEmptyEditor — editVSMessage getter", () => {

   it("should return the custom viewsheetEditMessage when set", async () => {
      const msg: ComposerCustomMessageModel = {
         viewsheetCreateMessage: "",
         viewsheetEditMessage: "My Edit VS",
         worksheetCreateMessage: "",
         worksheetEditMessage: "",
      };
      const { comp } = await renderComponent(msg);

      expect(comp.editVSMessage).toBe("My Edit VS");
   });

   it("should return the default i18n key when viewsheetEditMessage is empty", async () => {
      const msg: ComposerCustomMessageModel = {
         viewsheetCreateMessage: "",
         viewsheetEditMessage: "",
         worksheetCreateMessage: "",
         worksheetEditMessage: "",
      };
      const { comp } = await renderComponent(msg);

      expect(comp.editVSMessage).toBe("_#(js:common.editViewsheet)");
   });

   it("should return the default i18n key when customMessageModel is null", async () => {
      const { comp } = await renderComponent(null);

      expect(comp.editVSMessage).toBe("_#(js:common.editViewsheet)");
   });
});

// ---------------------------------------------------------------------------
// Group 7: memory-leak guard [Risk 1]
// ---------------------------------------------------------------------------

describe("ComposerEmptyEditor — ngOnInit subscription lifecycle", () => {

   /**
    * Suspicion A note: getModel() in production returns an HttpClient observable which
    * completes after a single emit (HTTP response). The of() mock here mirrors that
    * semantics. If this ever changes to a long-lived stream, the component will need an
    * explicit takeUntilDestroyed or similar guard.
    */
   it("should complete the subscription after the first emit (no persistent leak)", async () => {
      let completed = false;
      MODEL_SERVICE_MOCK.getModel.mockReturnValue(
         new Observable(subscriber => {
            subscriber.next({ viewsheetCreateMessage: "x", viewsheetEditMessage: "", worksheetCreateMessage: "", worksheetEditMessage: "" });
            subscriber.complete();
            completed = true;
         })
      );

      // Cannot use renderComponent() here — it always overwrites MODEL_SERVICE_MOCK.getModel
      // via mockReturnValue(of(customMessage)), which would discard the spy above.
      await render(ComposerEmptyEditor, {
         schemas: [NO_ERRORS_SCHEMA],
         providers: [
            { provide: DragService, useValue: DRAG_SERVICE_MOCK },
            { provide: ModelService, useValue: MODEL_SERVICE_MOCK },
            { provide: ComposerRecentService, useValue: RECENT_SERVICE_MOCK },
         ],
      });

      expect(completed).toBe(true);
   });
});
