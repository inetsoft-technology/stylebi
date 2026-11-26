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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { AiAssistantDialogService } from "../../common/services/ai-assistant-dialog.service";
import { AiAssistantService } from "../../../../../shared/ai-assistant/ai-assistant.service";
import { EditorTitleBar } from "./editor-title-bar.component";

async function renderTitleBar(inputs: Partial<EditorTitleBar> = {}) {
   const onClose = vi.fn();
   const onOpenWizardPane = vi.fn();
   const result = await render(EditorTitleBar, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: NgbModal, useValue: {} },
         { provide: AiAssistantDialogService, useValue: { aiAssistantVisible: false } },
         { provide: AiAssistantService, useValue: {} },
      ],
      componentInputs: { elementName: "Chart2", sourceName: "VALUE_SHAPES_TEST_DATA", ...inputs },
      on: { onClose, onOpenWizardPane },
   });

   return { ...result, onClose, onOpenWizardPane };
}

describe("EditorTitleBar — title band", () => {
   it("should read sheet > object > source, with the data source icon and no object type icon", async () => {
      const { container } = await renderTitleBar({ sheetName: "chart-date-drill", objectType: "VSChart" });
      const title = container.querySelector(".title") as HTMLElement;

      expect(title.querySelector(".editor-back-button")).not.toBeNull();
      expect(title.querySelector(".editor-sheet-name").textContent).toContain("chart-date-drill");
      expect(title.querySelector("h4").textContent).toContain("Chart2");
      expect(title.querySelector(".editor-source").textContent).toContain("VALUE_SHAPES_TEST_DATA");
      expect(title.querySelector(".editor-source .database-icon")).not.toBeNull();
      expect(title.querySelector(".editor-breadcrumb, .editor-breadcrumb-1, .chart-icon")).toBeNull();
      expect(title.textContent).not.toContain("->");
   });

   it("should leave the sheet name out when it is not known", async () => {
      const { container } = await renderTitleBar({});

      expect(container.querySelector(".editor-sheet-name")).toBeNull();
   });

   it("should show To Wizard as a text button only when the wizard is reachable", async () => {
      const without = await renderTitleBar({ goToWizardVisible: false });
      expect(without.container.querySelector(".wizard-button")).toBeNull();
      without.fixture.destroy();
   });

   it("should open the wizard from a text-only To Wizard button", async () => {
      const { container, onOpenWizardPane } = await renderTitleBar({ goToWizardVisible: true });
      const button = container.querySelector(".wizard-button") as HTMLElement;

      expect(button.textContent.trim()).toContain("To Wizard");
      expect(button.querySelector("i, svg")).toBeNull();

      button.click();
      expect(onOpenWizardPane).toHaveBeenCalledTimes(1);
   });

   it("should end with Cancel and Done buttons that keep their old classes", async () => {
      const { container, onClose } = await renderTitleBar({});

      (container.querySelector(".close-button") as HTMLElement).click();
      (container.querySelector(".finish-button") as HTMLElement).click();

      expect(onClose.mock.calls.map(c => c[0])).toEqual([false, true]);
   });
});
