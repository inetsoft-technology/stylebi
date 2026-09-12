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
 * ViewerAppComponent — Pass 3: Display
 *
 * Covers template rendering, CSS classes, visibility predicates, and the
 * `isPermissionForbidden()` gate used across all toolbar button methods.
 *
 *   Group 1  — getReloadMessage(): expired / transformFinished / editBookmark text
 *   Group 2  — expired-vs-banner: *ngIf conditions + reload / close button visibility
 *   Group 3  — viewer-container CSS classes: preview / fullscreen / server-refresh / runtime-id
 *   Group 4  — cancelled overlay: cancels replaces viewer-container
 *   Group 5  — toolbar structural: hideToolbar / toolbarVisible / mobileDevice CSS class
 *   Group 6  — isPermissionForbidden(): toolbarPermissions gate + ExportVS + Schedule edge cases
 *   Group 7  — isPreviousPageVisible / isNextPageVisible / isEditVisible / isZoomVisible
 *   Group 8  — bookmarksVisible(): snapshot / embed short-circuit / permission guard
 *   Group 9  — isToggleFullScreenVisible / isCloseViewsheetVisible
 *   Group 10 — profilingVisible banner / waiting backdrop
 *   Group 11 — viewer-root CSS class: overflow-hide / mobile-viewer-root
 */

import { resetMocks, renderComponent } from "./viewer-app.test-fixtures";

beforeEach(() => {
   resetMocks();
});

// ---------------------------------------------------------------------------
// Group 1 — getReloadMessage() [baseline]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — getReloadMessage()", () => {
   it("should return expired message when expired=true", async () => {
      const { comp } = await renderComponent();
      comp.expired = true;

      expect(comp.getReloadMessage()).toContain("viewer.expiration");
   });

   it("should return renameTransformFinished message when transformFinished=true", async () => {
      const { comp } = await renderComponent();
      comp.transformFinished = true;

      const msg = comp.getReloadMessage();
      expect(msg).toContain("viewer.expiration.renameTransformFinished");
   });

   it("should return editBookmarkFinished message when editBookmarkFinished=true", async () => {
      const { comp } = await renderComponent();
      comp.editBookmarkFinished = true;

      const msg = comp.getReloadMessage();
      expect(msg).toContain("viewer.expiration.editBookmarkFinished");
   });

   it("should check expired first (priority over transformFinished)", async () => {
      const { comp } = await renderComponent();
      comp.expired = true;
      comp.transformFinished = true;

      const msg = comp.getReloadMessage();
      // expired branch is checked first
      expect(msg).toContain("viewer.expiration");
      expect(msg).not.toContain("renameTransformFinished");
   });
});

// ---------------------------------------------------------------------------
// Group 2 — expired-vs-banner [display]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — expired-vs-banner", () => {
   // 🔁 Regression-sensitive: the banner is the ONLY user-visible cue that the
   // viewsheet data has expired and needs reloading. If the *ngIf is inverted or
   // the class name changes, users silently receive stale data.
   it("should show expired banner when expired=true and embed=false", async () => {
      const { comp, fixture } = await renderComponent();
      comp.expired = true;
      comp.embed = false;
      fixture.detectChanges();

      const banner = (fixture.nativeElement as HTMLElement).querySelector(".expired-vs-banner");
      expect(banner).not.toBeNull();
   });

   it("should show banner when transformFinished=true", async () => {
      const { comp, fixture } = await renderComponent();
      comp.transformFinished = true;
      fixture.detectChanges();

      const banner = (fixture.nativeElement as HTMLElement).querySelector(".expired-vs-banner");
      expect(banner).not.toBeNull();
   });

   it("should NOT show expired banner when all flags are false", async () => {
      const { comp, fixture } = await renderComponent();
      comp.expired = false;
      comp.transformFinished = false;
      comp.editBookmarkFinished = false;
      fixture.detectChanges();

      const banner = (fixture.nativeElement as HTMLElement).querySelector(".expired-vs-banner");
      expect(banner).toBeNull();
   });

   it("should NOT show expired banner when embed=true even if expired=true", async () => {
      const { comp, fixture } = await renderComponent();
      comp.expired = true;
      comp.embed = true;
      fixture.detectChanges();

      const banner = (fixture.nativeElement as HTMLElement).querySelector(".expired-vs-banner");
      expect(banner).toBeNull();
   });

   it("should show Reload Now button when assetId is set and preview=false", async () => {
      const { comp, fixture } = await renderComponent();
      comp.expired = true;
      // assetId is set by default from renderComponent()
      comp.preview = false;
      fixture.detectChanges();

      const reloadBtn = (fixture.nativeElement as HTMLElement).querySelector(".btn-danger");
      expect(reloadBtn).not.toBeNull();
   });

   it("should NOT show Reload Now button when assetId is null", async () => {
      const { comp, fixture } = await renderComponent({ assetId: null });
      comp.expired = true;
      fixture.detectChanges();

      // The *ngIf="!!assetId && !preview" guard hides the button
      const reloadBtn = (fixture.nativeElement as HTMLElement).querySelector(
         ".expired-vs-banner .btn-danger",
      );
      expect(reloadBtn).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 3 — viewer-container CSS classes [display]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — viewer-container CSS classes", () => {
   // 🔁 Regression-sensitive: the viewer-container-preview class controls the
   // CSS height/position of the entire canvas in preview mode. If missing,
   // the preview canvas overflows or clips unexpectedly.
   it("should add viewer-container-preview when preview=true", async () => {
      const { fixture } = await renderComponent({ preview: true });

      const container = (fixture.nativeElement as HTMLElement).querySelector(".viewer-container");
      expect(container?.classList.contains("viewer-container-preview")).toBe(true);
   });

   it("should NOT add viewer-container-preview when preview=false", async () => {
      const { fixture } = await renderComponent({ preview: false });

      const container = (fixture.nativeElement as HTMLElement).querySelector(".viewer-container");
      expect(container?.classList.contains("viewer-container-preview")).toBe(false);
   });

   // 🔁 Regression-sensitive: viewer-fullscreen removes the toolbar and forces
   // the canvas to full viewport. Missing this class breaks the full-screen UX.
   it("should add viewer-fullscreen when fullScreen=true and preview=false", async () => {
      const { comp, fixture } = await renderComponent({ preview: false });
      comp.fullScreen = true;
      fixture.detectChanges();

      const container = (fixture.nativeElement as HTMLElement).querySelector(".viewer-container");
      expect(container?.classList.contains("viewer-fullscreen")).toBe(true);
   });

   it("should NOT add viewer-fullscreen when preview=true even if fullScreen=true", async () => {
      const { comp, fixture } = await renderComponent({ preview: true });
      comp.fullScreen = true;
      fixture.detectChanges();

      const container = (fixture.nativeElement as HTMLElement).querySelector(".viewer-container");
      expect(container?.classList.contains("viewer-fullscreen")).toBe(false);
   });

   it("should add server-refresh when updateEnabled=true and exporting=false", async () => {
      const { comp, fixture } = await renderComponent();
      comp.updateEnabled = true;
      comp.exporting = false;
      fixture.detectChanges();

      const container = (fixture.nativeElement as HTMLElement).querySelector(".viewer-container");
      expect(container?.classList.contains("server-refresh")).toBe(true);
   });

   it("should NOT add server-refresh when exporting=true", async () => {
      const { comp, fixture } = await renderComponent();
      comp.updateEnabled = true;
      comp.exporting = true;
      fixture.detectChanges();

      const container = (fixture.nativeElement as HTMLElement).querySelector(".viewer-container");
      expect(container?.classList.contains("server-refresh")).toBe(false);
   });

   it("should bind [attr.runtime-id] to runtimeId", async () => {
      const { comp, fixture } = await renderComponent();
      comp.runtimeId = "rt-display-test";
      fixture.detectChanges();

      const container = (fixture.nativeElement as HTMLElement).querySelector(".viewer-container");
      expect(container?.getAttribute("runtime-id")).toBe("rt-display-test");
   });
});

// ---------------------------------------------------------------------------
// Group 4 — cancelled overlay [display]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — cancelled overlay", () => {
   // 🔁 Regression-sensitive: when a viewsheet load is cancelled, the cancelled
   // overlay is the sole UI indicator. If the *ngIf switches are reversed,
   // the user sees a blank canvas or the loading spinner forever.
   it("should show cancelled overlay and hide viewer-container when cancelled=true", async () => {
      const { comp, fixture } = await renderComponent();
      comp.cancelled = true;
      fixture.detectChanges();

      const el = fixture.nativeElement as HTMLElement;
      expect(el.querySelector(".cancelled-sheet-overlay")).not.toBeNull();
      expect(el.querySelector(".viewer-container")).toBeNull();
   });

   it("should show viewer-container and hide cancelled overlay when cancelled=false", async () => {
      const { comp, fixture } = await renderComponent();
      comp.cancelled = false;
      fixture.detectChanges();

      const el = fixture.nativeElement as HTMLElement;
      expect(el.querySelector(".viewer-container")).not.toBeNull();
      expect(el.querySelector(".cancelled-sheet-overlay")).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 5 — toolbar structural [display]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — toolbar structural", () => {
   it("should NOT render toolbar when hideToolbar=true", async () => {
      const { comp, fixture } = await renderComponent();
      comp.hideToolbar = true;
      fixture.detectChanges();

      const toolbar = (fixture.nativeElement as HTMLElement).querySelector(".viewer-toolbar");
      expect(toolbar).toBeNull();
   });

   it("should render toolbar when hideToolbar=false", async () => {
      const { comp, fixture } = await renderComponent();
      comp.hideToolbar = false;
      fixture.detectChanges();

      const toolbar = (fixture.nativeElement as HTMLElement).querySelector(".viewer-toolbar");
      expect(toolbar).not.toBeNull();
   });

   // 🔁 Regression-sensitive: viewer-toolbar-visible controls the toolbar's CSS
   // transition animation. If the flag is not applied, the toolbar jump-appears
   // instead of sliding in.
   it("should add viewer-toolbar-visible class when toolbarVisible=true", async () => {
      const { comp, fixture } = await renderComponent();
      comp.hideToolbar = false;
      comp.toolbarVisible = true;
      fixture.detectChanges();

      const toolbar = (fixture.nativeElement as HTMLElement).querySelector(".viewer-toolbar");
      expect(toolbar?.classList.contains("viewer-toolbar-visible")).toBe(true);
   });

   it("should NOT add viewer-toolbar-visible class when toolbarVisible=false", async () => {
      const { comp, fixture } = await renderComponent();
      comp.hideToolbar = false;
      comp.toolbarVisible = false;
      fixture.detectChanges();

      const toolbar = (fixture.nativeElement as HTMLElement).querySelector(".viewer-toolbar");
      expect(toolbar?.classList.contains("viewer-toolbar-visible")).toBe(false);
   });

   it("should add mobile class to toolbar when mobileDevice=true", async () => {
      const { comp, fixture } = await renderComponent();
      comp.hideToolbar = false;
      comp.mobileDevice = true;
      fixture.detectChanges();

      const toolbar = (fixture.nativeElement as HTMLElement).querySelector(".viewer-toolbar");
      expect(toolbar?.classList.contains("mobile")).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 6 — isPermissionForbidden() [baseline]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — isPermissionForbidden()", () => {
   it("should return false when toolbarPermissions is empty", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: [] });

      expect(comp.isPermissionForbidden("Edit")).toBe(false);
   });

   it("should return false when the permission is not in toolbarPermissions", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: ["Email"] });

      expect(comp.isPermissionForbidden("Edit")).toBe(false);
   });

   it("should return true when the permission IS in toolbarPermissions", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: ["Edit"] });

      expect(comp.isPermissionForbidden("Edit")).toBe(true);
   });

   it("should return true when any of the candidate permissions is in toolbarPermissions", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: ["Undo"] });

      expect(comp.isPermissionForbidden("PageNavigation", "Undo")).toBe(true);
   });

   // 🔁 Regression-sensitive: ExportVS is forbidden when the export type list is
   // empty (e.g. user has no export format permissions). Ignoring this case
   // shows an empty export dialog.
   it("should return true for ExportVS when exportTypes is empty", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: [] });
      comp.exportTypes = [];

      expect(comp.isPermissionForbidden("ExportVS")).toBe(true);
   });

   it("should return false for ExportVS when exportTypes has entries", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: [] });
      comp.exportTypes = [{ label: "Excel", value: "Excel" }];

      expect(comp.isPermissionForbidden("ExportVS")).toBe(false);
   });

   // 🔁 Regression-sensitive: Schedule is forbidden when the only export type is
   // "snapshot". Schedulers need at least one non-snapshot format to actually send.
   it("should return true for Schedule when exportTypes contains only snapshot", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: [] });
      comp.exportTypes = [{ label: "Snapshot", value: "snapshot" }];

      expect(comp.isPermissionForbidden("Schedule")).toBe(true);
   });

   it("should return false for Schedule when exportTypes has non-snapshot entries", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: [] });
      comp.exportTypes = [{ label: "Excel", value: "Excel" }, { label: "PDF", value: "PDF" }];

      expect(comp.isPermissionForbidden("Schedule")).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 7 — isPreviousPageVisible / isNextPageVisible / isEditVisible / isZoomVisible
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — toolbar button visibility predicates", () => {
   it("isPreviousPageVisible() should return true when no undo permission is denied", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: [] });

      expect(comp.isPreviousPageVisible()).toBe(true);
   });

   it("isPreviousPageVisible() should return false when Undo is in toolbarPermissions", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: ["Undo"] });

      expect(comp.isPreviousPageVisible()).toBe(false);
   });

   it("isNextPageVisible() should return false when Redo is in toolbarPermissions", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: ["Redo"] });

      expect(comp.isNextPageVisible()).toBe(false);
   });

   // 🔁 Regression-sensitive: isEditVisible has 6 guards — any one is enough to
   // hide the button. If any guard is accidentally dropped, the Edit button
   // appears in contexts where editing is forbidden (embed, preview, etc.).
   it("isEditVisible() should return true when all guards allow edit", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: [] });
      comp.embed = false;
      comp.mobileDevice = false;
      comp.preview = false;
      comp.linkView = false;
      comp.editable = true;
      comp.fullScreen = false;

      expect(comp.isEditVisible()).toBe(true);
   });

   it("isEditVisible() should return false when embed=true", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: [] });
      comp.embed = true;
      comp.mobileDevice = false;
      comp.preview = false;
      comp.editable = true;
      comp.fullScreen = false;

      expect(comp.isEditVisible()).toBe(false);
   });

   it("isEditVisible() should return false when editable=false", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: [] });
      comp.embed = false;
      comp.mobileDevice = false;
      comp.preview = false;
      comp.editable = false;

      expect(comp.isEditVisible()).toBe(false);
   });

   it("isEditVisible() should return false when fullScreen=true", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: [] });
      comp.embed = false;
      comp.mobileDevice = false;
      comp.preview = false;
      comp.editable = true;
      comp.fullScreen = true;

      expect(comp.isEditVisible()).toBe(false);
   });

   it("isZoomVisible() should return true on desktop without Zoom permission", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: [] });
      comp.mobileDevice = false;

      expect(comp.isZoomVisible()).toBe(true);
   });

   it("isZoomVisible() should return false when Zoom is in toolbarPermissions", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: ["Zoom"] });

      expect(comp.isZoomVisible()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 8 — bookmarksVisible() [display]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — bookmarksVisible()", () => {
   // 🔁 Regression-sensitive: snapshot viewsheets must NEVER show the bookmarks
   // button — snapshots are read-only archive files and saving bookmarks to them
   // is not supported by the server.
   it("should return false when snapshot=true", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: [] });
      comp.snapshot = true;

      expect(comp.bookmarksVisible()).toBe(false);
   });

   it("should return false when Bookmark is in toolbarPermissions", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: ["Bookmark"] });

      expect(comp.bookmarksVisible()).toBe(false);
   });

   // 🔁 Regression-sensitive: in embed mode, the bookmarks button is hidden when
   // there is only one bookmark (the home bookmark). Showing it would open an
   // empty dropdown.
   it("should return false when embed=true and vsBookmarkList has only 1 entry", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: [] });
      comp.embed = true;
      comp.vsBookmarkList = [{ name: "(Home)" } as any];

      expect(comp.bookmarksVisible()).toBe(false);
   });

   it("should return true when embed=true and vsBookmarkList has 2+ entries", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: [] });
      comp.embed = true;
      comp.snapshot = false;
      comp.vsBookmarkList = [{ name: "(Home)" } as any, { name: "BK1" } as any];

      // AllBookmark not forbidden → visible
      expect(comp.bookmarksVisible()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 9 — isToggleFullScreenVisible / isCloseViewsheetVisible [baseline]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — isToggleFullScreenVisible() / isCloseViewsheetVisible()", () => {
   it("isToggleFullScreenVisible() should return true on desktop non-preview", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: [], preview: false });
      comp.mobileDevice = false;
      comp.linkView = false;

      expect(comp.isToggleFullScreenVisible()).toBe(true);
   });

   it("isToggleFullScreenVisible() should return false when preview=true", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: [], preview: true });
      comp.mobileDevice = false;

      expect(comp.isToggleFullScreenVisible()).toBe(false);
   });

   it("isToggleFullScreenVisible() should return false when mobileDevice=true", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: [], preview: false });
      comp.mobileDevice = true;

      expect(comp.isToggleFullScreenVisible()).toBe(false);
   });

   it("isToggleFullScreenVisible() should return false when Full Screen is in toolbarPermissions", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: ["Full Screen"] });
      comp.mobileDevice = false;
      comp.preview = false;

      expect(comp.isToggleFullScreenVisible()).toBe(false);
   });

   // 🔁 Regression-sensitive: the close button must NOT appear in full-screen
   // mode. Full-screen exit is handled by the full-screen button; two close paths
   // at once confuses users and may trigger double-close server errors.
   it("isCloseViewsheetVisible() should return false when fullScreen=true", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: [], preview: true });
      comp.fullScreen = true;

      expect(comp.isCloseViewsheetVisible()).toBe(false);
   });

   it("isCloseViewsheetVisible() should return true when preview=true and fullScreen=false", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: [], preview: true });
      comp.fullScreen = false;

      expect(comp.isCloseViewsheetVisible()).toBe(true);
   });

   it("isCloseViewsheetVisible() should return false when not inPortal, not preview, not linkView", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: [], preview: false });
      comp.inPortal = false;
      comp.linkView = false;
      comp.fullScreen = false;

      expect(comp.isCloseViewsheetVisible()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 10 — profilingVisible banner / waiting backdrop [display]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — profilingVisible banner & waiting backdrop", () => {
   // 🔁 Regression-sensitive: the profiling banner is shown only when the
   // profiling session is active AND we're not in maxMode or embed. In maxMode
   // the banner would overlap assembly content.
   it("should show profile-report-banner when profilingVisible=true and !maxMode and !embed", async () => {
      const { comp, fixture } = await renderComponent();
      comp.profilingVisible = true;
      comp.maxMode = false;
      comp.embed = false;
      fixture.detectChanges();

      const banner = (fixture.nativeElement as HTMLElement).querySelector(
         ".profile-report-banner",
      );
      expect(banner).not.toBeNull();
   });

   it("should NOT show profile-report-banner when maxMode=true", async () => {
      const { comp, fixture } = await renderComponent();
      comp.profilingVisible = true;
      comp.maxMode = true;
      fixture.detectChanges();

      const banner = (fixture.nativeElement as HTMLElement).querySelector(
         ".profile-report-banner",
      );
      expect(banner).toBeNull();
   });

   it("should NOT show profile-report-banner when embed=true", async () => {
      const { comp, fixture } = await renderComponent();
      comp.profilingVisible = true;
      comp.maxMode = false;
      comp.embed = true;
      fixture.detectChanges();

      const banner = (fixture.nativeElement as HTMLElement).querySelector(
         ".profile-report-banner",
      );
      expect(banner).toBeNull();
   });

   // 🔁 Regression-sensitive: the waiting backdrop prevents user interaction
   // while a server operation completes. If it's not rendered when waiting=true,
   // users can double-submit operations.
   it("should show modal-backdrop when waiting=true", async () => {
      const { comp, fixture } = await renderComponent();
      comp.waiting = true;
      fixture.detectChanges();

      const backdrop = (fixture.nativeElement as HTMLElement).querySelector(".modal-backdrop");
      expect(backdrop).not.toBeNull();
   });

   it("should NOT show modal-backdrop when waiting=false", async () => {
      const { comp, fixture } = await renderComponent();
      comp.waiting = false;
      fixture.detectChanges();

      const backdrop = (fixture.nativeElement as HTMLElement).querySelector(".modal-backdrop");
      expect(backdrop).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 11 — viewer-root CSS classes [display]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — viewer-root CSS classes", () => {
   it("should add mobile-viewer-root when mobileDevice=true and drillTabsTop=false", async () => {
      const { comp, fixture } = await renderComponent();
      comp.mobileDevice = true;
      comp.drillTabsTop = false;
      fixture.detectChanges();

      const root = (fixture.nativeElement as HTMLElement).querySelector(".viewer-root");
      expect(root?.classList.contains("mobile-viewer-root")).toBe(true);
   });

   it("should NOT add mobile-viewer-root when mobileDevice=false", async () => {
      const { comp, fixture } = await renderComponent();
      comp.mobileDevice = false;
      fixture.detectChanges();

      const root = (fixture.nativeElement as HTMLElement).querySelector(".viewer-root");
      expect(root?.classList.contains("mobile-viewer-root")).toBe(false);
   });

   // 🔁 Regression-sensitive: overflow-hide is required for scaleToScreen without
   // scrolling. Without it the scaled canvas overflows the viewport and creates
   // unwanted scrollbars.
   it("should add overflow-hide when scaleToScreen=true, fitToWidth=false, showScroll=false, embed=false", async () => {
      const { comp, fixture } = await renderComponent();
      comp.scaleToScreen = true;
      comp.fitToWidth = false;
      comp.showScroll = false;
      comp.embed = false;
      fixture.detectChanges();

      const root = (fixture.nativeElement as HTMLElement).querySelector(".viewer-root");
      expect(root?.classList.contains("overflow-hide")).toBe(true);
   });

   it("should NOT add overflow-hide when embed=true", async () => {
      const { comp, fixture } = await renderComponent();
      comp.scaleToScreen = true;
      comp.fitToWidth = false;
      comp.showScroll = false;
      comp.embed = true;
      fixture.detectChanges();

      const root = (fixture.nativeElement as HTMLElement).querySelector(".viewer-root");
      expect(root?.classList.contains("overflow-hide")).toBe(false);
   });
});
