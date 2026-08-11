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
import { AssemblyAction } from "../common/action/assembly-action";
import { AssemblyActionGroup } from "../common/action/assembly-action-group";
import { TestUtils } from "../common/test/test-utils";
import { EmbedChartComponent } from "./chart/embed-chart.component";
import { EmbedCrosstabComponent } from "./crosstab/embed-crosstab.component";
import { EmbedGaugeComponent } from "./gauge/embed-gauge.component";
import { EmbedImageComponent } from "./image/embed-image.component";
import { EmbedTableComponent } from "./table/embed-table.component";
import { EmbedTextComponent } from "./text/embed-text.component";

/**
 * Bug #75951: the guard lives in EmbedContextMenu.open() and each embed component's
 * onOpenContextMenu() is a one-line delegation to it. Driving the components rather than the
 * namespace directly is the point - it covers both the guard and the wiring, so an embed that is
 * added later and hands over the wrong actions object, service or assembly name fails as soon as
 * it is listed below.
 *
 * embed-table.component.spec.ts additionally pins the guard against the real EmbedTableActions;
 * here the actions object is stubbed so the cases that matter to the guard (nothing at all, a
 * group whose actions are all hidden, something visible) can be stated directly. Components are
 * built off their prototype for the reason given there: these are Angular Elements custom
 * elements wired to a websocket client, and TestBed would exercise the DI setup, not the guard.
 */
type EmbedComponent = EmbedTableComponent | EmbedCrosstabComponent | EmbedChartComponent |
   EmbedTextComponent | EmbedGaugeComponent | EmbedImageComponent;

const EMBED_COMPONENTS: [string, { prototype: EmbedComponent }][] = [
   ["EmbedTableComponent", EmbedTableComponent],
   ["EmbedCrosstabComponent", EmbedCrosstabComponent],
   ["EmbedChartComponent", EmbedChartComponent],
   ["EmbedTextComponent", EmbedTextComponent],
   ["EmbedGaugeComponent", EmbedGaugeComponent],
   ["EmbedImageComponent", EmbedImageComponent],
];

describe.each(EMBED_COMPONENTS)("%s.onOpenContextMenu", (name, componentClass) => {
   let dropdownRef: any;
   let dropdownService: any;
   let miniToolbarService: any;
   let component: TestUtils.ContextMenuHost;

   const rightClick: () => any = () => ({
      type: "contextmenu", clientX: 10, clientY: 20, preventDefault: vi.fn()
   });

   const group: (visible: boolean) => AssemblyActionGroup = (visible) =>
      new AssemblyActionGroup([{
         id: () => "something",
         label: () => "Something",
         icon: () => "icon",
         enabled: () => true,
         visible: () => visible
      } as AssemblyAction]);

   beforeEach(() => {
      dropdownRef = {
         componentInstance: {},
         closeEvent: { subscribe: () => ({ unsubscribe: () => {} }) }
      };
      dropdownService = { open: vi.fn(() => dropdownRef) };
      miniToolbarService = { hiddenFreeze: vi.fn(), hiddenUnfreeze: vi.fn() };

      component = Object.create(componentClass.prototype);
      component.vsObject = { absoluteName: "Assembly1" };
      component.dropdownService = dropdownService;
      component.miniToolbarService = miniToolbarService;
   });

   // The table, crosstab, text, gauge and image embeds have no menu actions at all - everything
   // they offer is a toolbar button.
   it("opens nothing when the assembly has no menu actions", () => {
      component.vsObjectActions = { menuActions: [], clickAction: null };
      const event = rightClick();

      component.onOpenContextMenu(event);

      expect(dropdownService.open).not.toHaveBeenCalled();
      expect(miniToolbarService.hiddenFreeze).not.toHaveBeenCalled();
      // The browser's own context menu stays suppressed either way - having nothing of our own
      // to show is not a reason to offer the embedding page's users Reload/Save As.
      expect(event.preventDefault).toHaveBeenCalled();
   });

   // The chart embed does have menu actions, but each is tied to a selection (a title, an axis,
   // a legend, a zoom), so a right click on plain plot area leaves every one of them hidden.
   it("opens nothing when every menu action is hidden", () => {
      component.vsObjectActions = { menuActions: [group(false)], clickAction: null };
      const event = rightClick();

      component.onOpenContextMenu(event);

      expect(dropdownService.open).not.toHaveBeenCalled();
      expect(miniToolbarService.hiddenFreeze).not.toHaveBeenCalled();
      expect(event.preventDefault).toHaveBeenCalled();
   });

   it("still opens the menu when there is something to show", () => {
      const menuActions = [group(false), group(true)];
      component.vsObjectActions = { menuActions, clickAction: null };
      const event = rightClick();

      component.onOpenContextMenu(event);

      expect(dropdownService.open).toHaveBeenCalled();
      expect(dropdownRef.componentInstance.actions).toBe(menuActions);
      expect(miniToolbarService.hiddenFreeze).toHaveBeenCalledWith("Assembly1");
      expect(event.preventDefault).toHaveBeenCalled();
   });

   // A left click routed through the same method builds its group from clickAction, which is
   // null unless the assembly has an on-click command - the guard catches that too.
   it("opens nothing for a click with no click action", () => {
      component.vsObjectActions = { menuActions: [group(true)], clickAction: null };
      const event: any = { ...rightClick(), type: "click" };

      component.onOpenContextMenu(event);

      expect(dropdownService.open).not.toHaveBeenCalled();
      expect(event.preventDefault).toHaveBeenCalled();
   });

   // This bail-out predates the guard and deliberately leaves the event alone: the component is
   // not wired up yet, which is not the same as having decided there is nothing to show.
   it("opens nothing when the actions object is not set up yet", () => {
      component.vsObjectActions = null;
      const event = rightClick();

      component.onOpenContextMenu(event);

      expect(dropdownService.open).not.toHaveBeenCalled();
      expect(event.preventDefault).not.toHaveBeenCalled();
   });
});
