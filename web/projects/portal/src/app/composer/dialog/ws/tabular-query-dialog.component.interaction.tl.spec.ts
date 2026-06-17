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
 * TabularQueryDialog — Pass 1: Interaction
 *
 * Method coverage:
 *   Group 1   ngOnInit — model load, initView, conditional refreshView
 *   Group 2   ok — emits onCommit with model + socket URL
 *   Group 3   apply — emits onApply with collapse flag
 *   Group 4   cancel — emits onCancel
 *   Group 5   validChanged — updates valid, calls changeRef.detectChanges
 *   Group 6   buttonClicked — OAUTH delegates to authorize; others call refreshView
 *   Group 7   viewChanged — triggers refreshView when value in dependsOn
 *   Group 8   displayTitle getter — full label / truncation
 */

import { of } from "rxjs";
import {
   makeComponent,
   makeModel,
   makeTabularView,
   makeDataSourceType,
   makeHttp,
} from "./tabular-query-dialog.component.test-helpers";

// ---------------------------------------------------------------------------
// Group 1 — ngOnInit
// ---------------------------------------------------------------------------

describe("Group 1 — ngOnInit: loads model and initializes view", () => {
   it("should call modelService.getModel() during init", () => {
      const { modelSvc } = makeComponent();
      expect(modelSvc.getModel).toHaveBeenCalled();
   });

   it("should set comp.model from modelService.getModel() response", () => {
      const model = makeModel({ tableName: "custom_table" });
      const { comp } = makeComponent({ model });
      expect(comp.model?.tableName).toBe("custom_table");
   });

   it("should set model.dataSource from dataSourceType.dataSource", () => {
      const dst = makeDataSourceType({ dataSource: "special-src" });
      const { comp } = makeComponent({ dataSourceType: dst });
      expect(comp.model?.dataSource).toBe("special-src");
   });

   it("should NOT call http.post (refreshView) when model already has a tabularView", () => {
      const model = makeModel({ tabularView: makeTabularView() });
      const http = makeHttp();
      makeComponent({ model, http });

      const refreshCalls = http.post.mock.calls.filter((c: any[]) =>
         (c[0] as string).endsWith("/refreshView"),
      );
      expect(refreshCalls.length).toBe(0);
   });

   it("should call http.post (refreshView) when model has no tabularView", () => {
      const model = makeModel({ tabularView: null });
      const http = makeHttp();
      makeComponent({ model, http });

      const refreshCalls = http.post.mock.calls.filter((c: any[]) =>
         (c[0] as string).endsWith("/refreshView"),
      );
      expect(refreshCalls.length).toBeGreaterThan(0);
   });

   it("should set isLoading=false after model loads", () => {
      const { comp } = makeComponent();
      expect(comp.isLoading).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 2 — ok
// ---------------------------------------------------------------------------

describe("Group 2 — ok: emits onCommit with model and controller socket URL", () => {
   it("should emit onCommit once", () => {
      const { comp } = makeComponent();
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok();

      expect(emitSpy).toHaveBeenCalledOnce();
   });

   it("should emit payload containing model", () => {
      const { comp } = makeComponent();
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok();

      const payload = emitSpy.mock.calls[0][0];
      expect(payload.model).toBe(comp.model);
   });

   it("should emit payload with the CONTROLLER_SOCKET URL", () => {
      const { comp } = makeComponent();
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok();

      const payload = emitSpy.mock.calls[0][0];
      expect(typeof payload.controller).toBe("string");
      expect(payload.controller).toContain("tabular-query-dialog-model");
   });

   it("should update model.tableName from initTableName when tables are provided", () => {
      const { comp } = makeComponent({ tables: [{ name: "other" } as any], initTableName: "newName" });
      comp.initTableName = "newName";
      comp.ok();
      expect(comp.model?.tableName).toBe("newName");
   });
});

// ---------------------------------------------------------------------------
// Group 3 — apply
// ---------------------------------------------------------------------------

describe("Group 3 — apply: emits onApply with collapse flag", () => {
   it("should emit onApply with collapse=false", () => {
      const { comp } = makeComponent();
      const emitSpy = vi.spyOn(comp.onApply, "emit");

      comp.apply(false);

      expect(emitSpy).toHaveBeenCalledOnce();
      const payload = emitSpy.mock.calls[0][0];
      expect(payload.collapse).toBe(false);
   });

   it("should emit onApply with collapse=true", () => {
      const { comp } = makeComponent();
      const emitSpy = vi.spyOn(comp.onApply, "emit");

      comp.apply(true);

      const payload = emitSpy.mock.calls[0][0];
      expect(payload.collapse).toBe(true);
   });

   it("should include model in the apply result", () => {
      const { comp } = makeComponent();
      const emitSpy = vi.spyOn(comp.onApply, "emit");

      comp.apply(false);

      const payload = emitSpy.mock.calls[0][0];
      expect(payload.result.model).toBe(comp.model);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — cancel
// ---------------------------------------------------------------------------

describe("Group 4 — cancel: emits onCancel with 'cancel' string", () => {
   it("should emit onCancel once", () => {
      const { comp } = makeComponent();
      const emitSpy = vi.spyOn(comp.onCancel, "emit");

      comp.cancel();

      expect(emitSpy).toHaveBeenCalledOnce();
   });

   it("should emit 'cancel' string", () => {
      const { comp } = makeComponent();
      const emitSpy = vi.spyOn(comp.onCancel, "emit");

      comp.cancel();

      expect(emitSpy).toHaveBeenCalledWith("cancel");
   });
});

// ---------------------------------------------------------------------------
// Group 5 — validChanged
// ---------------------------------------------------------------------------

describe("Group 5 — validChanged: updates valid flag and triggers change detection", () => {
   it("should set comp.valid to the emitted value (true)", () => {
      const { comp } = makeComponent();
      comp.validChanged(true);
      expect(comp.valid).toBe(true);
   });

   it("should set comp.valid=false", () => {
      const { comp } = makeComponent();
      comp.valid = true;
      comp.validChanged(false);
      expect(comp.valid).toBe(false);
   });

   it("should call changeRef.detectChanges", () => {
      const { comp, changeRef } = makeComponent();
      comp.validChanged(true);
      expect(changeRef.detectChanges).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 6 — buttonClicked
// ---------------------------------------------------------------------------

describe("Group 6 — buttonClicked: routes OAUTH to authorize, others to refreshView", () => {
   it("should call http.post (for OAUTH params) when button.type=OAUTH", () => {
      const http = makeHttp();
      http.post.mockImplementation((url: string) => {
         if(url.includes("oauth-params")) return of({ code: "params" });
         if(url.includes("oauth-tokens")) return of(makeTabularView());
         return of(makeTabularView());
      });
      const { comp } = makeComponent({ http });
      http.post.mockClear();
      http.post.mockImplementation((url: string) => {
         if(url.includes("oauth-params")) return of({ code: "params" });
         if(url.includes("oauth-tokens")) return of(makeTabularView());
         return of(makeTabularView());
      });

      const button = {
         type: "OAUTH", clicked: true, loading: false,
         oauthClientId: "cid", oauthClientSecret: "secret",
         oauthScope: "read", oauthAuthorizationUri: "http://auth",
         oauthTokenUri: "http://token", oauthFlags: 0,
         oauthServiceName: "svc", method: "GET",
      } as any;

      comp.buttonClicked(button);

      // authorize calls http.post for oauth-params endpoint
      const oauthCalls = http.post.mock.calls.filter((c: any[]) =>
         (c[0] as string).includes("oauth"),
      );
      expect(oauthCalls.length).toBeGreaterThan(0);
   });

   it("should call http.post (refreshView) when button.type=REFRESH", () => {
      const http = makeHttp();
      const { comp } = makeComponent({ http });
      http.post.mockClear();

      const button = { type: "REFRESH", clicked: false, loading: false } as any;
      comp.buttonClicked(button);

      const refreshCalls = http.post.mock.calls.filter((c: any[]) =>
         (c[0] as string).endsWith("/refreshView"),
      );
      expect(refreshCalls.length).toBeGreaterThan(0);
   });

   it("should call refreshView with cancelClicked=true when button.type=CANCEL", () => {
      const http = makeHttp();
      const { comp } = makeComponent({ http });
      const refreshSpy = vi.spyOn(comp, "refreshView");
      http.post.mockClear();

      const button = { type: "CANCEL", clicked: false, loading: false } as any;
      comp.buttonClicked(button);

      expect(refreshSpy).toHaveBeenCalledWith(true);
   });
});

// ---------------------------------------------------------------------------
// Group 7 — viewChanged
// ---------------------------------------------------------------------------

describe("Group 7 — viewChanged: refreshes when changed view value is a dependsOn key", () => {
   it("should call http.post (refreshView) when view value is in dependsOn and no refresh button", () => {
      const model = makeModel({ tabularView: makeTabularView() });
      const http = makeHttp();
      const { comp } = makeComponent({ model, http });

      comp.refreshButtonExists = false;
      comp.dependsOn = new Set(["dept"]);
      http.post.mockClear();

      const changedView = makeTabularView({ value: "dept" });
      const parentView = makeTabularView({ views: [changedView] });
      comp.model.tabularView = makeTabularView({ views: [parentView] });

      comp.viewChanged([changedView, parentView]);

      const refreshCalls = http.post.mock.calls.filter((c: any[]) =>
         (c[0] as string).endsWith("/refreshView"),
      );
      expect(refreshCalls.length).toBeGreaterThan(0);
   });

   it("should NOT call refreshView when view value is not in dependsOn", () => {
      const http = makeHttp();
      const { comp } = makeComponent({ http });
      comp.refreshButtonExists = false;
      comp.dependsOn = new Set(["dept"]);
      http.post.mockClear();

      const changedView = makeTabularView({ value: "other" });
      comp.viewChanged([changedView, makeTabularView()]);

      const refreshCalls = http.post.mock.calls.filter((c: any[]) =>
         (c[0] as string).endsWith("/refreshView"),
      );
      expect(refreshCalls.length).toBe(0);
   });

   it("should NOT call refreshView when refreshButtonExists=true", () => {
      const http = makeHttp();
      const { comp } = makeComponent({ http });
      comp.refreshButtonExists = true;
      comp.dependsOn = new Set(["dept"]);
      http.post.mockClear();

      const changedView = makeTabularView({ value: "dept" });
      comp.viewChanged([changedView, makeTabularView()]);

      const refreshCalls = http.post.mock.calls.filter((c: any[]) =>
         (c[0] as string).endsWith("/refreshView"),
      );
      expect(refreshCalls.length).toBe(0);
   });
});

// ---------------------------------------------------------------------------
// Group 8 — displayTitle getter
// ---------------------------------------------------------------------------

describe("Group 8 — displayTitle: returns label, truncating at 30 chars", () => {
   it("should return short label unchanged", () => {
      const dst = makeDataSourceType({ label: "REST JSON" });
      const { comp } = makeComponent({ dataSourceType: dst });
      expect(comp.displayTitle).toBe("REST JSON");
   });

   it("should truncate labels longer than 30 chars with '...'", () => {
      const longLabel = "A".repeat(35);
      const dst = makeDataSourceType({ label: longLabel });
      const { comp } = makeComponent({ dataSourceType: dst });
      expect(comp.displayTitle).toBe("A".repeat(27) + "...");
      expect(comp.displayTitle.length).toBe(30);
   });

   it("should return exactly 30-char label unchanged (not truncated)", () => {
      const label = "B".repeat(30);
      const dst = makeDataSourceType({ label });
      const { comp } = makeComponent({ dataSourceType: dst });
      expect(comp.displayTitle).toBe(label);
   });
});
