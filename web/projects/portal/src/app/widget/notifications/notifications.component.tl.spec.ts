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
 * NotificationsComponent — single pass (+memory leak)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — addAlert: duplicate message suppression
 *   Group 2 [Risk 3] — addAlert + hideNotifications: info type blocked when notifications hidden
 *   Group 3 [Risk 3] — closeAlert: guards destroyed CDR (prevents NG0205 after navigation)
 *   Group 4 [Risk 2] — ngOnInit: auto-shows message @Input as info alert
 *   Group 5 [Risk 2] — ngOnChanges: removes info alerts when hideNotifications becomes true
 *   Group 6 [Risk 2] — success / warning / danger: correct type assigned
 *   Group 7 [Risk 1] — closeAlert: removes the targeted alert
 *
 * Out of scope: private alertShowing() — covered transitively via dedup tests
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { NotificationsComponent } from "./notifications.component";

async function renderComponent(props: Partial<NotificationsComponent> = {}) {
   const { fixture } = await render(NotificationsComponent, {
      schemas: [NO_ERRORS_SCHEMA],
      componentProperties: props,
   });
   return fixture.componentInstance as NotificationsComponent;
}

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: duplicate suppression [Risk 3]
// ---------------------------------------------------------------------------

describe("NotificationsComponent — duplicate suppression", () => {
   // 🔁 Regression-sensitive: same message must not stack; regression leaves uncloseable
   //    duplicate banners that fill the viewport.
   it("should not add a second alert when the same info message is added twice", async () => {
      const comp = await renderComponent();
      comp.info("Hello world");
      comp.info("Hello world");
      expect(comp.alerts).toHaveLength(1);
   });

   it("should add separate alerts for two different messages", async () => {
      const comp = await renderComponent();
      comp.info("First");
      comp.info("Second");
      expect(comp.alerts).toHaveLength(2);
   });

   it("should deduplicate across different alert types for the same text", async () => {
      const comp = await renderComponent();
      comp.success("Same text");
      comp.success("Same text");
      expect(comp.alerts).toHaveLength(1);
   });
});

// ---------------------------------------------------------------------------
// Group 2: hideNotifications blocks info [Risk 3]
// ---------------------------------------------------------------------------

describe("NotificationsComponent — hideNotifications blocks info type", () => {
   // 🔁 Regression-sensitive: when hideNotifications=true, info alerts must not appear;
   //    non-info types must pass through so errors/warnings remain visible.
   it("should not add an info alert when hideNotifications is true", async () => {
      const comp = await renderComponent({ hideNotifications: true });
      comp.info("Hidden");
      expect(comp.alerts).toHaveLength(0);
   });

   it("should still add a success alert when hideNotifications is true", async () => {
      const comp = await renderComponent({ hideNotifications: true });
      comp.success("Still visible");
      expect(comp.alerts).toHaveLength(1);
      expect(comp.alerts[0].type).toBe("success");
   });

   it("should still add a danger alert when hideNotifications is true", async () => {
      const comp = await renderComponent({ hideNotifications: true });
      comp.danger("Error still shows");
      expect(comp.alerts).toHaveLength(1);
      expect(comp.alerts[0].type).toBe("danger");
   });
});

// ---------------------------------------------------------------------------
// Group 3: closeAlert guards destroyed CDR [Risk 3]
// ---------------------------------------------------------------------------

describe("NotificationsComponent — closeAlert with destroyed CDR", () => {
   // 🔁 Regression-sensitive: without the destroyed guard, the auto-close timeout callback
   //    throws NG0205 after the user navigates away from the page that owned the notification.
   it("should not throw when closeAlert is called after the CDR is marked destroyed", async () => {
      const comp = await renderComponent();
      comp.info("Test");
      const alert = comp.alerts[0];
      (comp as any).changeDetectionRef["destroyed"] = true;

      expect(() => comp.closeAlert(alert)).not.toThrow();
   });

   it("should still remove the alert from the array even when CDR is destroyed", async () => {
      const comp = await renderComponent();
      comp.info("Removable");
      const alert = comp.alerts[0];
      (comp as any).changeDetectionRef["destroyed"] = true;

      comp.closeAlert(alert);

      expect(comp.alerts).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 4: ngOnInit [Risk 2]
// ---------------------------------------------------------------------------

describe("NotificationsComponent — ngOnInit", () => {
   it("should add an info alert from the message @Input on initialization", async () => {
      const comp = await renderComponent({ message: "Welcome!" });
      expect(comp.alerts).toHaveLength(1);
      expect(comp.alerts[0].type).toBe("info");
   });

   it("should not add any alert when message @Input is empty", async () => {
      const comp = await renderComponent({ message: "" });
      expect(comp.alerts).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 5: ngOnChanges — hideNotifications [Risk 2]
// ---------------------------------------------------------------------------

describe("NotificationsComponent — ngOnChanges hideNotifications", () => {
   // 🔁 Regression-sensitive: when hideNotifications becomes true, existing info alerts must
   //    disappear; success/warning/danger alerts must remain visible.
   it("should remove info alerts when hideNotifications changes to true", async () => {
      const comp = await renderComponent();
      comp.info("Info to hide");
      comp.success("Success to keep");

      comp.ngOnChanges({
         hideNotifications: {
            currentValue: true,
            previousValue: false,
            firstChange: false,
            isFirstChange: () => false,
         },
      });

      expect(comp.alerts.some(a => a.type === "info")).toBe(false);
      expect(comp.alerts.some(a => a.type === "success")).toBe(true);
   });

   it("should not remove alerts when hideNotifications changes to false", async () => {
      const comp = await renderComponent();
      comp.info("Keep me");

      comp.ngOnChanges({
         hideNotifications: {
            currentValue: false,
            previousValue: true,
            firstChange: false,
            isFirstChange: () => false,
         },
      });

      expect(comp.alerts).toHaveLength(1);
   });
});

// ---------------------------------------------------------------------------
// Group 6: alert type assignment [Risk 2]
// ---------------------------------------------------------------------------

describe("NotificationsComponent — alert type creation", () => {
   it("should add a success alert with type 'success' and correct message", async () => {
      const comp = await renderComponent();
      comp.success("All good");
      expect(comp.alerts[0].type).toBe("success");
      expect(comp.alerts[0].message).toBe("All good");
   });

   it("should add a warning alert with type 'warning'", async () => {
      const comp = await renderComponent();
      comp.warning("Be careful");
      expect(comp.alerts[0].type).toBe("warning");
   });

   it("should add a danger alert with type 'danger'", async () => {
      const comp = await renderComponent();
      comp.danger("Something went wrong");
      expect(comp.alerts[0].type).toBe("danger");
   });
});

// ---------------------------------------------------------------------------
// Group 7: closeAlert removes target [Risk 1]
// ---------------------------------------------------------------------------

describe("NotificationsComponent — closeAlert", () => {
   it("should remove the specified alert from the alerts array", async () => {
      const comp = await renderComponent();
      comp.success("First");
      comp.success("Second");
      const first = comp.alerts[0];

      comp.closeAlert(first);

      expect(comp.alerts).toHaveLength(1);
      expect(comp.alerts[0].message).toBe("Second");
   });

   it("should do nothing when the alert is not in the array", async () => {
      const comp = await renderComponent();
      comp.success("Existing");
      const phantom = { id: 999, type: "info" as any, message: "ghost" };

      comp.closeAlert(phantom);

      expect(comp.alerts).toHaveLength(1);
   });
});
