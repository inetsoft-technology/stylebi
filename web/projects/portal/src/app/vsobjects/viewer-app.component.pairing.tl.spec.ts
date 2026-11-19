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
 * ViewerAppComponent — "Connect to Claude" toolbar control (Feature #80547, lane L2)
 *
 *   Group 1  — A1: shown in the portal viewer, wired with runtimeId / VIEWSHEET / viewsheetClient / viewerMode
 *   Group 2  — C4: hidden when the feature flag is off
 *   Group 3  — Q3: hidden outside the portal viewer toolbar (embed, preview, mobile, link view,
 *              iframe, dashboard tab, no runtime id yet, hidden toolbar)
 */

import { Component, Input } from "@angular/core";
import { By } from "@angular/platform-browser";
import { ConnectToClaudeComponent } from "../composer/gui/wiz/connect-to-claude.component";
import {
   renderComponent, resetMocks, VS_CLIENT_MOCK, WIZ_SERVICE_MOCK
} from "./viewer-app.test-fixtures";

// Stand-in for the real component, which has its own spec. Records what the viewer binds.
@Component({ selector: "wiz-connect-to-claude", template: "", standalone: true })
class ConnectToClaudeStub {
   @Input() runtimeId: string;
   @Input() sheetType: string;
   @Input() socketConnection: any;
   @Input() viewerMode = false;
   @Input() sheetAgentConnected: boolean;
}

const PORTAL_VIEWER = { inPortal: true, runtimeId: "rt-viewer-1" };

async function render(opts: { inputs?: Record<string, any> } = {}) {
   const result = await renderComponent({
      inputs: { ...PORTAL_VIEWER, ...(opts.inputs ?? {}) },
      importOverrides: [{ replace: ConnectToClaudeComponent, with: ConnectToClaudeStub }]
   } as any);
   return result;
}

function control(fixture: any): HTMLElement | null {
   return (fixture.nativeElement as HTMLElement).querySelector(".wiz-viewer-connect");
}

function hosted(fixture: any): ConnectToClaudeStub | null {
   const de = fixture.debugElement.query(By.directive(ConnectToClaudeStub));
   return de ? de.componentInstance : null;
}

beforeEach(() => {
   resetMocks();
   WIZ_SERVICE_MOCK.sheetAgentPairingEnabled = true;
});

describe("ViewerAppComponent — Connect to Claude [A1]", () => {
   it("shows the control in the portal viewer when the flag is on and a runtime id exists", async () => {
      const { comp, fixture } = await render();
      fixture.detectChanges();

      expect(comp.isConnectToClaudeVisible()).toBe(true);
      expect(control(fixture)).not.toBeNull();
      expect(control(fixture).querySelector("button[ngbDropdownToggle]")).not.toBeNull();
   });

   it("hosts the component with the viewer's runtime, VIEWSHEET, its own socket and viewerMode", async () => {
      const { fixture } = await render();
      fixture.detectChanges();

      const child = hosted(fixture);
      expect(child).not.toBeNull();
      expect(child.runtimeId).toBe("rt-viewer-1");
      expect(child.sheetType).toBe("VIEWSHEET");
      expect(child.socketConnection).toBe(VS_CLIENT_MOCK);
      expect(child.viewerMode).toBe(true);
      // The sheet-level agent flag is a Composer-only input; the viewer does not pass it.
      expect(child.sheetAgentConnected).toBeUndefined();
   });

   it("is placed in the left toolbar group, after Export", async () => {
      const { fixture } = await render();
      fixture.detectChanges();

      const group = (fixture.nativeElement as HTMLElement).querySelector(".viewer-left-toolbar");
      expect(group.querySelector(".wiz-viewer-connect")).not.toBeNull();
   });

   it("keeps the child's runtimeId in step with the viewer's runtime", async () => {
      const { comp, fixture } = await render();
      fixture.detectChanges();

      comp.runtimeId = "rt-viewer-2";
      fixture.detectChanges();

      expect(hosted(fixture).runtimeId).toBe("rt-viewer-2");
   });
});

describe("ViewerAppComponent — Connect to Claude [C4: feature flag off]", () => {
   it("hides the control when the feature flag is off", async () => {
      WIZ_SERVICE_MOCK.sheetAgentPairingEnabled = false;
      const { comp, fixture } = await render();
      fixture.detectChanges();

      expect(comp.isConnectToClaudeVisible()).toBe(false);
      expect(control(fixture)).toBeNull();
      expect(hosted(fixture)).toBeNull();
   });

   it("follows the flag live (it can turn on after the page rendered)", async () => {
      WIZ_SERVICE_MOCK.sheetAgentPairingEnabled = false;
      const { fixture } = await render();
      fixture.detectChanges();
      expect(control(fixture)).toBeNull();

      WIZ_SERVICE_MOCK.sheetAgentPairingEnabled = true;
      fixture.detectChanges();
      expect(control(fixture)).not.toBeNull();
   });
});

describe("ViewerAppComponent — Connect to Claude [Q3: portal viewer toolbar only]", () => {
   const hiddenCases: [string, Record<string, any>][] = [
      ["not opened in the portal (inPortal false)", { inPortal: false }],
      ["the dashboard tab (inDashboard)", { inDashboard: true }],
      ["Composer Preview (preview)", { preview: true }],
      ["a link view (linkView)", { linkView: true }],
      ["an iframe (isIframe)", { isIframe: true }],
      ["before the runtime id arrives", { runtimeId: null }],
      ["a hidden toolbar (hideToolbar)", { hideToolbar: true }],
   ];

   it.each(hiddenCases)("hides the control in %s", async (_name, inputs) => {
      const { comp, fixture } = await render({ inputs });
      fixture.detectChanges();

      expect(control(fixture)).toBeNull();

      if(!inputs.hideToolbar) {
         expect(comp.isConnectToClaudeVisible()).toBe(false);
      }
   });

   it("hides the control in the embed viewer", async () => {
      // embed is read from ContextProvider once at init; the other viewer specs flip the field the same way.
      const { comp, fixture } = await render();
      comp.embed = true;
      fixture.detectChanges();

      expect(comp.embed).toBe(true);
      expect(comp.isConnectToClaudeVisible()).toBe(false);
      expect(control(fixture)).toBeNull();
   });

   it("hides the control on a mobile device", async () => {
      const { comp, fixture } = await render();
      comp.mobileDevice = true;
      fixture.detectChanges();

      expect(comp.isConnectToClaudeVisible()).toBe(false);
      expect(control(fixture)).toBeNull();
   });
});
