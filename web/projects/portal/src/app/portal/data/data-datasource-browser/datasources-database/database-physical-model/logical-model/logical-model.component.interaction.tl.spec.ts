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
 * LogicalModelComponent — Pass 1 (interaction / lifecycle / user flows)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — ngOnInit create mode (no parent): logicalModel initialized from
 *                        route params; isModified=true; initialized=true; editing=false
 *   Group 2 [Risk 3] — ngOnInit create mode with parent: createExtendedModel() POST fired;
 *                        logicalModel updated; loading=false after
 *   Group 3 [Risk 3] — save() editing=true: PUT → logicalModel/originalModel updated;
 *                        success notification; error → danger notification
 *   Group 4 [Risk 3] — save() editing=false: POST → success + folderChangeService called
 *                        + editing flips to true
 *   Group 5 [Risk 3] — dataModelNameChange subscription: rename originalName; rename
 *                        physicalModelName; null newName → router.navigate called
 *   Group 6 [Risk 2] — displayTitle getter: unmodified, modified, physicalModel, parent+connection
 *   Group 7 [Risk 2] — canDeactivate(): !isModified→true; ok→true; cancel→false;
 *                        parent→different confirm message
 *   Group 8 [Risk 2] — checkModified(): !initialized→no-op; same model→no change;
 *                        changed model→isModified=true
 *   Group 9 [Risk 1] — ngDoCheck, parent getter/setter, settings getter, lmContentHeight,
 *                        notify() all notification types
 *
 * Out of scope this pass:
 *   refreshModel() / getSettings() — HTTP loading, race conditions, param-change retrigger
 *     deferred to Pass 2 (async≥3).
 *
 * See also: logical-model.component.test-helpers.ts for stubs, route factories, renderComp.
 */

import { waitFor } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";

import { server } from "@test-mocks/server";
import { ComponentTool } from "../../../../../../common/util/component-tool";
import { Tool } from "../../../../../../../../../shared/util/tool";
import {
   makeCreateRoute, makeEditRoute, renderComp,
} from "./logical-model.component.test-helpers";

// ---------------------------------------------------------------------------
// Global lifecycle
// ---------------------------------------------------------------------------

afterEach(() => {
   vi.restoreAllMocks();
});

// ---------------------------------------------------------------------------
// Group 1 — ngOnInit: create mode without parent [Risk 3]
// ---------------------------------------------------------------------------

describe("LogicalModelComponent — ngOnInit: create mode (no parent)", () => {
   // 🔁 Regression-sensitive: if logicalModel is not initialized from params, the first
   // save() sends a null-name model to the server, causing a validation failure.

   it("should set logicalModel.name from the logicalModelName route param", async () => {
      const { comp } = await renderComp({
         route: makeCreateRoute({ logicalModelName: "MyNewLM" }),
      });
      expect(comp.logicalModel.name).toBe("MyNewLM");
   });

   it("should set logicalModel.partition from the physicalModelName route param", async () => {
      const { comp } = await renderComp({
         route: makeCreateRoute({ physicalModelName: "myPhysModel" }),
      });
      expect(comp.logicalModel.partition).toBe("myPhysModel");
   });

   it("should set isModified=true and initialized=true in create mode", async () => {
      const { comp } = await renderComp({ route: makeCreateRoute() });
      expect(comp.isModified).toBe(true);
      expect(comp.initialized).toBe(true);
      expect(comp.editing).toBe(false);
   });

   it("should set logicalModel.description and folder from route params when provided", async () => {
      const { comp } = await renderComp({
         route: makeCreateRoute({ desc: "My description", folder: "myFolder" }),
      });
      expect(comp.logicalModel.description).toBe("My description");
      expect(comp.logicalModel.folder).toBe("myFolder");
   });
});

// ---------------------------------------------------------------------------
// Group 2 — ngOnInit: create mode with parent → createExtendedModel [Risk 3]
// ---------------------------------------------------------------------------

describe("LogicalModelComponent — ngOnInit: create mode with parent", () => {
   // 🔁 Regression-sensitive: if createExtendedModel() is not called, the extended model
   // inherits no entities from the base model and the user starts with an empty model.

   it("should set logicalModel.connection from route params for extended models", async () => {
      const { comp } = await renderComp({
         route: makeCreateRoute({ parent: "parentLM", connection: "myConn" }),
      });
      // connection is set before createExtendedModel fires the POST
      expect(comp.logicalModel.connection).toBe("myConn");
   });

   it("should call POST /extended and update logicalModel with the response", async () => {
      const extendedModel = {
         name: "LM1", partition: "physModel", entities: [{ name: "ExtendedEntity", attributes: [], baseElement: false, errorMessage: null }],
         description: "", connection: "myConn", parent: "parentLM", folder: "",
      };
      server.use(
         http.post("*/api/data/logicalmodel/extended", () =>
            MswHttpResponse.json(extendedModel)
         )
      );
      const { comp } = await renderComp({
         route: makeCreateRoute({ parent: "parentLM", connection: "myConn" }),
      });
      await waitFor(() => expect(comp.loading).toBe(false));
      expect(comp.logicalModel.entities).toHaveLength(1);
      expect(comp.logicalModel.entities[0].name).toBe("ExtendedEntity");
   });
});

// ---------------------------------------------------------------------------
// Group 3 — save() editing=true: PUT [Risk 3]
// ---------------------------------------------------------------------------

describe("LogicalModelComponent — save() editing=true", () => {
   // 🔁 Regression-sensitive: after a successful PUT, originalModel must be updated to the
   // server's response so that subsequent checkModified() calls don't show stale changes.

   it("should PUT the model and update logicalModel/originalModel on success", async () => {
      const savedModel = {
         name: "LM1_saved", partition: "physModel", entities: [],
         description: "saved", connection: null, parent: null, folder: "",
      };
      server.use(
         http.put("*/api/data/logicalmodel/models", () => MswHttpResponse.json(savedModel))
      );
      const { comp } = await renderComp({ route: makeCreateRoute() });
      comp.editing = true;
      comp.isModified = true;

      comp.save();

      await waitFor(() => expect(comp.logicalModel.name).toBe("LM1_saved"));
      expect(comp.originalModel.name).toBe("LM1_saved");
      expect(comp.isModified).toBe(false);
   });

   it("should show success notification after a successful PUT", async () => {
      server.use(
         http.put("*/api/data/logicalmodel/models", () =>
            MswHttpResponse.json({ name: "LM1", partition: "physModel", entities: [], description: "", connection: null, parent: null, folder: "" })
         )
      );
      const { comp } = await renderComp({ route: makeCreateRoute() });
      comp.editing = true;

      comp.save();

      await waitFor(() =>
         expect((comp.notifications as any).success).toHaveBeenCalledWith(
            "_#(js:data.logicalmodel.saveModelSuccess)"
         )
      );
   });

   it("should show danger notification on PUT failure", async () => {
      server.use(
         http.put("*/api/data/logicalmodel/models", () =>
            MswHttpResponse.json({ error: "Internal error" }, { status: 500 })
         )
      );
      const { comp } = await renderComp({ route: makeCreateRoute() });
      comp.editing = true;

      comp.save();

      await waitFor(() =>
         expect((comp.notifications as any).danger).toHaveBeenCalledWith(
            "_#(js:data.logicalmodel.saveModelError)"
         )
      );
   });
});

// ---------------------------------------------------------------------------
// Group 4 — save() editing=false: POST [Risk 3]
// ---------------------------------------------------------------------------

describe("LogicalModelComponent — save() editing=false", () => {
   // 🔁 Regression-sensitive: after a successful POST, editing must flip to true and
   // folderChangeService.emitFolderChange must be called so the sidebar tree refreshes.

   it("should POST and flip editing to true on success", async () => {
      const { comp, folderChangeMock } = await renderComp({ route: makeCreateRoute() });
      // editing=false after create-mode render
      expect(comp.editing).toBe(false);

      comp.save();

      await waitFor(() => expect(comp.editing).toBe(true));
      expect(folderChangeMock.emitFolderChange).toHaveBeenCalledTimes(1);
   });

   it("should show extended-model success message when parent is set", async () => {
      server.use(
         http.post("*/api/data/logicalmodel/models", () =>
            MswHttpResponse.json({ name: "LM1", partition: "physModel", entities: [], description: "", connection: "c", parent: "parentLM", folder: "" })
         )
      );
      const { comp } = await renderComp({ route: makeCreateRoute({ parent: "parentLM", connection: "c" }) });
      await waitFor(() => expect(comp.loading).toBe(false)); // wait for createExtendedModel
      comp.editing = false; // override to test POST path
      comp.logicalModel = { ...comp.logicalModel, parent: "parentLM" };

      comp.save();

      await waitFor(() =>
         expect((comp.notifications as any).success).toHaveBeenCalledWith(
            "_#(js:data.logicalmodel.extended.saveModelSuccess)"
         )
      );
   });
});

// ---------------------------------------------------------------------------
// Group 5 — dataModelNameChange subscription [Risk 3]
// ---------------------------------------------------------------------------

describe("LogicalModelComponent — dataModelNameChange subscription", () => {
   // 🔁 Regression-sensitive: if the rename handler doesn't update originalModel.name,
   // the next checkModified() call falsely marks the model as modified (name mismatch).

   it("should update logicalModel.name and originalName when originalName is renamed", async () => {
      const { comp, nameChangeSubject } = await renderComp({ route: makeCreateRoute({ logicalModelName: "OldName" }) });
      comp.originalModel = Tool.clone(comp.logicalModel);

      nameChangeSubject.next({ oldName: "OldName", newName: "NewName" } as any);

      expect(comp.originalName).toBe("NewName");
      expect(comp.logicalModel.name).toBe("NewName");
      expect(comp.originalModel.name).toBe("NewName");
   });

   it("should navigate to datasources when newName=null (model deleted)", async () => {
      const { comp, nameChangeSubject, routerMock } = await renderComp({ route: makeCreateRoute({ logicalModelName: "ToDelete" }) });

      nameChangeSubject.next({ oldName: "ToDelete", newName: null } as any);

      expect(routerMock.navigate).toHaveBeenCalledWith(
         ["/portal/tab/data/datasources"],
         expect.objectContaining({ queryParams: expect.objectContaining({ path: "/" }) })
      );
   });

   it("should update physicalModelName and model.partition when physicalModel is renamed", async () => {
      const { comp, nameChangeSubject } = await renderComp({ route: makeCreateRoute({ physicalModelName: "OldPhys" }) });
      comp.originalModel = Tool.clone(comp.logicalModel);
      // oldName != originalName but == physicalModelName
      nameChangeSubject.next({ oldName: "OldPhys", newName: "NewPhys" } as any);

      expect(comp.physicalModelName).toBe("NewPhys");
      expect(comp.logicalModel.partition).toBe("NewPhys");
      expect(comp.originalModel.partition).toBe("NewPhys");
   });
});

// ---------------------------------------------------------------------------
// Group 6 — displayTitle getter [Risk 2]
// ---------------------------------------------------------------------------

describe("LogicalModelComponent — displayTitle getter", () => {
   it("should return empty string when logicalModel is null", async () => {
      const { comp } = await renderComp();
      (comp as any).logicalModel = null;
      expect(comp.displayTitle).toBe("");
   });

   it("should return name only when not modified and no physicalModelName", async () => {
      const { comp } = await renderComp();
      comp.logicalModel.name = "MyLM";
      comp.isModified = false;
      comp.physicalModelName = null;
      expect(comp.displayTitle).toBe("MyLM");
   });

   it("should append * when isModified=true", async () => {
      const { comp } = await renderComp();
      comp.logicalModel.name = "MyLM";
      comp.isModified = true;
      comp.physicalModelName = null;
      expect(comp.displayTitle).toBe("MyLM*");
   });

   it("should append ' -> physicalModelName' when physicalModel is set and no parent", async () => {
      const { comp } = await renderComp();
      comp.logicalModel.name = "MyLM";
      comp.isModified = false;
      comp.physicalModelName = "physModel";
      comp.logicalModel.parent = null;
      expect(comp.displayTitle).toBe("MyLM -> physModel");
   });

   it("should include connection in title for extended models with connection", async () => {
      const { comp } = await renderComp();
      comp.logicalModel.name = "MyLM";
      comp.isModified = false;
      comp.physicalModelName = "physModel";
      comp.logicalModel.parent = "parentLM";
      comp.logicalModel.connection = "myConn";
      expect(comp.displayTitle).toBe("MyLM -> physModel/myConn");
   });

   it("should show '(Default Connection)' when parent is set but connection is falsy", async () => {
      const { comp } = await renderComp();
      comp.logicalModel.name = "MyLM";
      comp.isModified = false;
      comp.physicalModelName = "physModel";
      comp.logicalModel.parent = "parentLM";
      comp.logicalModel.connection = null;
      expect(comp.displayTitle).toBe("MyLM -> physModel/(Default Connection)");
   });
});

// ---------------------------------------------------------------------------
// Group 7 — canDeactivate() [Risk 2]
// ---------------------------------------------------------------------------

describe("LogicalModelComponent — canDeactivate()", () => {
   it("should return true immediately when there are no unsaved changes", async () => {
      const { comp } = await renderComp();
      comp.isModified = false;
      const result = await comp.canDeactivate();
      expect(result).toBe(true);
   });

   it("should show confirm dialog and return true when user clicks ok", async () => {
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const { comp } = await renderComp();
      comp.isModified = true;
      const result = await comp.canDeactivate();
      expect(result).toBe(true);
   });

   it("should return false when user cancels the confirm dialog", async () => {
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("cancel");
      const { comp } = await renderComp();
      comp.isModified = true;
      const result = await comp.canDeactivate();
      expect(result).toBe(false);
   });

   it("should use the extended-model confirm message when parent is set", async () => {
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const { comp } = await renderComp();
      comp.isModified = true;
      comp.logicalModel.parent = "parentLM";

      await comp.canDeactivate();

      expect(confirmSpy).toHaveBeenCalledWith(
         expect.anything(),
         "_#(js:dialog.changedTitle)",
         "_#(js:data.extended.logicalmodel.confirmLeaving)"
      );
   });

   it("should use the standard confirm message when no parent", async () => {
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const { comp } = await renderComp();
      comp.isModified = true;
      comp.logicalModel.parent = null;

      await comp.canDeactivate();

      expect(confirmSpy).toHaveBeenCalledWith(
         expect.anything(),
         "_#(js:dialog.changedTitle)",
         "_#(js:data.logicalmodel.confirmLeaving)"
      );
   });
});

// ---------------------------------------------------------------------------
// Group 8 — checkModified() [Risk 2]
// ---------------------------------------------------------------------------

describe("LogicalModelComponent — checkModified()", () => {
   it("should not set isModified when not yet initialized", async () => {
      const { comp } = await renderComp();
      comp.initialized = false;
      comp.isModified = false;
      comp.originalModel = Tool.clone(comp.logicalModel);
      comp.logicalModel = { ...comp.logicalModel, name: "Changed" }; // would set isModified=true when initialized
      comp.checkModified();
      expect(comp.isModified).toBe(false); // early-return prevents the update
   });

   it("should not change isModified when logicalModel equals originalModel", async () => {
      const { comp } = await renderComp();
      comp.initialized = true;
      comp.isModified = false;
      comp.originalModel = Tool.clone(comp.logicalModel);
      comp.checkModified();
      expect(comp.isModified).toBe(false);
   });

   it("should set isModified=true when logicalModel differs from originalModel", async () => {
      const { comp } = await renderComp();
      comp.initialized = true;
      comp.isModified = false;
      comp.originalModel = Tool.clone(comp.logicalModel);
      comp.logicalModel = { ...comp.logicalModel, name: "Changed" };
      comp.checkModified();
      expect(comp.isModified).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 9 — ngDoCheck, parent, settings, lmContentHeight, notify [Risk 1]
// ---------------------------------------------------------------------------

describe("LogicalModelComponent — ngDoCheck, parent, settings, lmContentHeight", () => {
   it("ngDoCheck should call checkModified when both logicalModel and originalModel are set", async () => {
      const { comp } = await renderComp();
      comp.initialized = true;
      comp.isModified = false;
      comp.originalModel = Tool.clone(comp.logicalModel);
      comp.logicalModel = { ...comp.logicalModel, name: "Changed" };
      comp.ngDoCheck();
      expect(comp.isModified).toBe(true);
   });

   it("ngDoCheck should skip checkModified when originalModel is null", async () => {
      const { comp } = await renderComp();
      comp.initialized = true;
      (comp as any).originalModel = null;
      comp.isModified = false;
      comp.ngDoCheck(); // should not throw
      expect(comp.isModified).toBe(false);
   });

   it("parent getter should return logicalModel.parent", async () => {
      const { comp } = await renderComp();
      comp.logicalModel.parent = "parentLM";
      expect(comp.parent).toBe("parentLM");
   });

   it("parent setter should update logicalModel.parent", async () => {
      const { comp } = await renderComp();
      comp.parent = "newParent";
      expect(comp.logicalModel.parent).toBe("newParent");
   });

   it("settings getter should delegate to logicalModelService.settings", async () => {
      const { comp } = await renderComp();
      const mockSettings = { fullDateSupport: true } as any;
      comp.lmService.settings = mockSettings;
      expect(comp.settings).toBe(mockSettings);
   });

   it("lmContentHeight should return the expected CSS calc string", async () => {
      const { comp } = await renderComp();
      expect(comp.lmContentHeight).toBe("calc(100% - 40px)");
   });
});

describe("LogicalModelComponent — notify() routes types to notification methods", () => {
   const cases: [string, string][] = [
      ["success", "success"],
      ["info", "info"],
      ["warning", "warning"],
      ["danger", "danger"],
      ["error", "danger"],   // "error" maps to danger
      ["unknown", "warning"], // default maps to warning
   ];

   for(const [type, method] of cases) {
      it(`should call notifications.${method} for type="${type}"`, async () => {
         const { comp } = await renderComp();
         comp.lmService.onNotification.next({ type, content: `test-${type}` } as any);
         expect((comp.notifications as any)[method]).toHaveBeenCalledWith(`test-${type}`);
      });
   }
});
