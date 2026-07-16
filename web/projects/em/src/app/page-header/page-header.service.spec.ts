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
 * PageHeaderService — orgVisible allow-list
 *
 * This is the mechanism PageHeaderComponent.showOrgs() reads (page-header.component.ts) to decide
 * whether the site-admin org switcher renders on the current EM page: `orgVisible` is recomputed
 * every time `title` is set, as `orgPages.includes(this.title)`. Each EM page component sets
 * `pageTitle.title = "..."` in its own ngOnInit(); whether that exact string is in the
 * `orgPages` allow-list is what determines per-page visibility (page-header.component.tl.spec.ts
 * only covers the generic "given orgVisible, does the switcher render" mechanism with orgVisible
 * stubbed — this file covers the real per-title allow-list decision instead).
 *
 * The "shows" cases parameterize over the service's own `orgPages` array (not a hand-copied
 * duplicate), so a future addition to the allow-list is automatically covered without editing
 * this file. Every real EM page whose own title is itself an `orgPages` member — all 12 Auditing
 * sub-pages, Monitoring's Viewsheet/Query/Cache/Cluster/User pages, Content's Repository/
 * Materialized-Views, Schedule's task-list/cycle-list, Presentation's Themes and the
 * "Organization Presentation Settings" (orgSettings=true) branch of presentation-settings-view —
 * is therefore already exercised by that same parameterization; none of them need a separate
 * "shows" case here.
 *
 * The "hides" cases below are every other real title actually set by an EM page component today
 * (cross-referenced by grepping `pageTitle.title =` across projects/em/src), grouped by the area
 * the user asked to double check. This is what makes the list a genuine regression guard rather
 * than a few illustrative examples: if a future edit moves any of these titles into (or a
 * currently-listed title out of) `orgPages`, this file breaks.
 *
 * Two components set their title conditionally rather than once in ngOnInit:
 * - presentation-settings-view.component.ts:116-122 sets "_#(js:Organization Presentation
 *   Settings)" when `@Input() orgSettings` is true (allow-listed — the "shows" side, already
 *   covered generically above) and "_#(js:Presentation Settings)" when false (the Global
 *   Settings tab — not allow-listed, listed below).
 * - security-settings-page.component.ts:88 sets the parent "_#(js:Security Settings)" title in
 *   its own ngOnInit(); the Users/Actions/Provider/Google-sign-in child tabs each override it in
 *   their own ngOnInit(), but sso-settings-page.component.ts never sets its own title, so it's the
 *   one child tab where the parent's (non-allow-listed) title is what's actually visible.
 *
 * A few parent/wrapper components (content-settings-view.component.ts:61, schedule-settings-
 * page.component.ts:64) set a plain, non-i18n-wrapped literal ("Content Settings"/"Schedule
 * Settings" — no `_#(js:...)` placeholder) as a transient title before their routed child tab
 * overrides it; included here as-is since it's the real value briefly present in production.
 */

import { TestBed } from "@angular/core/testing";
import { Title } from "@angular/platform-browser";
import { PageHeaderService } from "./page-header.service";

// Read from the class itself (not re-typed by hand) so new allow-list entries are covered
// automatically. The constructor has no side effects beyond storing the injected Title, so a
// throwaway instance is safe to use purely to enumerate the list at module-collection time.
const ORG_PAGES = new PageHeaderService({ setTitle: () => {}, getTitle: () => "" } as unknown as Title).orgPages;

// Real titles set by today's non-org-scoped EM pages, grouped by area (see file header for the
// two conditional-title components and the two non-i18n-wrapped literals).
const NON_ORG_PAGE_TITLES: Array<[label: string, title: string]> = [
   // Settings — General
   ["General Settings", "_#(js:General Settings)"],

   // Settings — Security
   ["Security Settings Provider", "_#(js:Security Settings Provider)"],
   ["Security Settings (SSO's inherited parent title)", "_#(js:Security Settings)"],
   ["Security Settings: Sign In With Google", "_#(js:Security Settings:Sign In With Google)"],

   // Settings — Content
   ["Content: Data Space", "_#(js:Data Space)"],
   ["Content: Drivers and Plugins", "_#(js:Drivers and Plugins)"],
   ["Content Settings (parent nav wrapper, non-i18n-wrapped literal)", "Content Settings"],

   // Settings — Schedule
   ["Schedule: Edit Schedule Task", "_#(js:Edit Schedule Task)"],
   ["Schedule: Edit Data Cycle", "_#(js:Edit Data Cycle)"],
   ["Schedule: Scheduler Status", "_#(js:Scheduler Status)"],
   ["Schedule: Scheduler Settings", "_#(js:Scheduler Settings)"],
   ["Schedule Settings (parent nav wrapper, non-i18n-wrapped literal)", "Schedule Settings"],

   // Settings — Presentation
   ["Presentation: Global Settings (orgSettings=false branch)", "_#(js:Presentation Settings)"],
   ["Presentation (parent nav wrapper)", "_#(js:Presentation)"],

   // Settings — Logging
   ["Logging Settings", "_#(js:Logging Settings)"],

   // Settings — Properties
   ["All Properties", "_#(js:All Properties)"],

   // Monitoring (Auditing has none: every one of its 12 sub-pages is allow-listed — see header)
   ["Monitoring (summary parent wrapper)", "_#(js:Monitoring)"],
   ["Monitoring: Logs", "_#(js:Logs)"],

   // Misc
   ["Manage Favorites", "_#(js:Manage Favorites)"],
   ["Change Password", "_#(js:Change Password)"],

   // Edge cases
   ["initial/empty title", ""],
   ["an unrecognized title", "_#(js:Some Unknown Page)"],
];

describe("PageHeaderService — orgVisible (per-page org-switcher allow-list)", () => {
   let service: PageHeaderService;

   beforeEach(() => {
      TestBed.configureTestingModule({
         providers: [
            PageHeaderService,
            { provide: Title, useValue: { setTitle: vi.fn(), getTitle: () => "" } },
         ],
      });
      service = TestBed.inject(PageHeaderService);
   });

   it("orgPages allow-list is non-empty (sanity check that ORG_PAGES was read correctly)", () => {
      expect(ORG_PAGES.length).toBeGreaterThan(0);
   });

   it.each(ORG_PAGES)("shows the org switcher for allow-listed title %s", (title) => {
      service.title = title;
      expect(service.orgVisible).toBe(true);
   });

   it.each(NON_ORG_PAGE_TITLES)("hides the org switcher for %s (title=%s, not in the allow-list)", (_label, title) => {
      service.title = title;
      expect(service.orgVisible).toBe(false);
   });

   it("recomputes orgVisible on every title change (navigating from an org page to a non-org page hides it again)", () => {
      service.title = "_#(js:Repository)";
      expect(service.orgVisible).toBe(true);

      service.title = "_#(js:General Settings)";
      expect(service.orgVisible).toBe(false);
   });

   it("recomputes orgVisible on every title change (navigating from a non-org page to an org page shows it)", () => {
      service.title = "_#(js:General Settings)";
      expect(service.orgVisible).toBe(false);

      service.title = "_#(js:Security Settings Users)";
      expect(service.orgVisible).toBe(true);
   });
});
