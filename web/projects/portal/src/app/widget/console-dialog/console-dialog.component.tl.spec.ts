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
 * ConsoleDialogComponent — single pass (+内存泄漏)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — ok(): HTTP sendModel → if res.body → clones selectedLevels to
 *     messageLevels + emits onCommit(cloned); if !res.body → emits onCommit(original
 *     messageLevels unchanged)
 *   Group 2 [Risk 3] — visibleMessages getter: filters messages by selectedLevels using
 *     case-insensitive match
 *   Group 3 [Risk 2] — levelChanged(): type=="all" + checked → fills all; type=="all" + unchecked
 *     → clears; specific type + checked → adds; specific type + unchecked → removes; calls
 *     updateLevelButtonLabel after each change
 *   Group 4 [Risk 2] — ngOnInit(): clones messageLevels → selectedLevels; null messageLevels
 *     → defaults to levelOptions (i18n strings); null messages → []; calls updateLevelButtonLabel
 *   Group 5 [Risk 2] — getLevelButtonLabel(): all 3 selected → "All levels"; fewer → "Custom levels"
 *   Group 6 [Risk 1] — getLevelCounter(): counts messages matching given type (case-insensitive)
 *   Group 7 [Risk 1] — getLevelIcon(): Error→error icon; Warning→warning icon; other→annotation icon
 *   Group 8 [Risk 1] — isSelected(): true when level is in selectedLevels; false otherwise
 *   Group 9 [Risk 1] — closeDialog(): emits onClose(messages)
 *   Group 10 [Risk 1] — clearMessages(): emits messagesChange([])
 *
 * Confirmed bugs (it.fails):
 *   Bug — ok() subscribe leak: modelService.sendModel().subscribe() stores no Subscription.
 *     After component destruction the callback still fires and emits onCommit on a destroyed
 *     component. Fix: store the subscription and unsubscribe in ngOnDestroy.
 *
 * Out of scope:
 *   Template checkbox binding — FixedDropdownDirective + FormsModule template interaction
 *     is integration-level; levelChanged() is tested by direct call.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { Subject, of } from "rxjs";
import { ConsoleDialogComponent } from "./console-dialog.component";
import { ConsoleMessage } from "./console-message";
import { ModelService } from "../services/model.service";

// ---------------------------------------------------------------------------
// Shared mocks
// ---------------------------------------------------------------------------

const MODEL_SERVICE_MOCK = { sendModel: vi.fn() };

// ---------------------------------------------------------------------------
// Shared fixture helpers
// ---------------------------------------------------------------------------

function makeMessage(type: string, message: string = "msg"): ConsoleMessage {
   return { type, message } as any;
}

async function renderComponent(overrides: {
   runtimeId?: string;
   messageLevels?: string[];
   messages?: ConsoleMessage[];
} = {}) {
   const {
      runtimeId = "vs-test-1",
      messageLevels = ["_#(js:Error)", "_#(js:Warning)", "_#(js:Info)"],
      messages = [],
   } = overrides;

   const { fixture } = await render(ConsoleDialogComponent, {
      schemas: [NO_ERRORS_SCHEMA],
      componentImports: [],
      providers: [
         { provide: ModelService, useValue: MODEL_SERVICE_MOCK },
      ],
      componentInputs: { runtimeId, messageLevels, messages },
   });
   const comp = fixture.componentInstance as ConsoleDialogComponent;
   return { comp, fixture };
}

beforeEach(() => {
   MODEL_SERVICE_MOCK.sendModel.mockReset();
});

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: ok() [Risk 3]
// ---------------------------------------------------------------------------

describe("ConsoleDialogComponent — ok", () => {
   // 🔁 Regression-sensitive: when res.body is truthy, messageLevels must be updated to the
   //    currently selected levels so re-opening the dialog shows the saved state.
   it("should update messageLevels and emit onCommit when res.body is truthy", async () => {
      const { comp } = await renderComponent({ messageLevels: ["_#(js:Error)"] });
      comp.selectedLevels = ["_#(js:Error)", "_#(js:Warning)"];
      MODEL_SERVICE_MOCK.sendModel.mockReturnValue(of({ body: true }));
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok();
      await Promise.resolve();

      expect(comp.messageLevels).toEqual(["_#(js:Error)", "_#(js:Warning)"]);
      expect(emitSpy).toHaveBeenCalledWith(comp.messageLevels);
   });

   it("should emit onCommit with original messageLevels when res.body is falsy", async () => {
      const { comp } = await renderComponent({ messageLevels: ["_#(js:Error)"] });
      comp.selectedLevels = ["_#(js:Error)", "_#(js:Warning)"];
      MODEL_SERVICE_MOCK.sendModel.mockReturnValue(of({ body: null }));
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok();
      await Promise.resolve();

      // messageLevels should NOT be updated
      expect(comp.messageLevels).toEqual(["_#(js:Error)"]);
      expect(emitSpy).toHaveBeenCalledWith(["_#(js:Error)"]);
   });

   // Bug: ok() calls modelService.sendModel().subscribe() without storing the Subscription.
   // After component destruction the callback still fires and emits onCommit. Fix: store and
   // unsubscribe in ngOnDestroy.
   it.fails("should not emit after component is destroyed (subscribe leak)", async () => {
      const subject = new Subject<any>();
      MODEL_SERVICE_MOCK.sendModel.mockReturnValue(subject.asObservable());
      const { comp, fixture } = await renderComponent();
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok();
      fixture.destroy();

      subject.next({ body: true });
      await Promise.resolve();

      // With fix: emit should NOT have been called after destroy
      // Currently: FAILS — callback still runs
      expect(emitSpy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 2: visibleMessages [Risk 3]
// ---------------------------------------------------------------------------

describe("ConsoleDialogComponent — visibleMessages", () => {
   // 🔁 Regression-sensitive: equalsIgnoreCase compares selectedLevels values against
   //    message.type — selectedLevels comes from messageLevels (backend), so both must use
   //    the same format. ConsoleMessageType is "INFO"|"WARNING"|"ERROR" (uppercase).
   it("should return only messages matching selectedLevels (case-insensitive)", async () => {
      const msgs = [
         makeMessage("ERROR", "err1"),
         makeMessage("WARNING", "warn1"),
         makeMessage("INFO", "info1"),
      ];
      const { comp } = await renderComponent({
         messages: msgs,
         messageLevels: ["Error", "Warning", "Info"],
      });
      comp.selectedLevels = ["Error"];
      comp.messages = msgs;

      const visible = comp.visibleMessages;
      expect(visible).toHaveLength(1);
      expect(visible[0].message).toBe("err1");
   });

   it("should return all messages when all levels are selected", async () => {
      const msgs = [
         makeMessage("ERROR"),
         makeMessage("WARNING"),
         makeMessage("INFO"),
      ];
      const { comp } = await renderComponent({
         messages: msgs,
         messageLevels: ["Error", "Warning", "Info"],
      });
      comp.messages = msgs;
      // selectedLevels is initialized from messageLevels in ngOnInit
      expect(comp.visibleMessages).toHaveLength(3);
   });

   it("should return empty array when no levels are selected", async () => {
      const msgs = [makeMessage("error"), makeMessage("warning")];
      const { comp } = await renderComponent({ messages: msgs });
      comp.selectedLevels = [];
      comp.messages = msgs;
      expect(comp.visibleMessages).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 3: levelChanged [Risk 2]
// ---------------------------------------------------------------------------

describe("ConsoleDialogComponent — levelChanged", () => {
   it("should fill all levels when type is 'all' and checked", async () => {
      const { comp } = await renderComponent({ messageLevels: [] });
      comp.selectedLevels = [];
      comp.levelChanged({ target: { checked: true } }, "all");
      expect(comp.selectedLevels).toEqual(comp.levelOptions);
   });

   it("should clear all levels when type is 'all' and unchecked", async () => {
      const { comp } = await renderComponent();
      comp.levelChanged({ target: { checked: false } }, "all");
      expect(comp.selectedLevels).toEqual([]);
   });

   it("should add a specific level when checked", async () => {
      const { comp } = await renderComponent({ messageLevels: [] });
      comp.selectedLevels = [];
      comp.levelChanged({ target: { checked: true, value: "_#(js:Warning)" } }, "_#(js:Warning)");
      expect(comp.selectedLevels).toContain("_#(js:Warning)");
   });

   it("should remove a specific level when unchecked", async () => {
      const { comp } = await renderComponent();
      comp.levelChanged({ target: { checked: false, value: "_#(js:Warning)" } }, "_#(js:Warning)");
      expect(comp.selectedLevels).not.toContain("_#(js:Warning)");
   });

   it("should update levelButtonLabel after change", async () => {
      const { comp } = await renderComponent();
      comp.levelChanged({ target: { checked: false } }, "all");
      expect(comp.levelButtonLabel).toBe("_#(js:Custom levels)");
   });
});

// ---------------------------------------------------------------------------
// Group 4: ngOnInit [Risk 2]
// ---------------------------------------------------------------------------

describe("ConsoleDialogComponent — ngOnInit", () => {
   it("should clone messageLevels into selectedLevels", async () => {
      const levels = ["_#(js:Error)"];
      const { comp } = await renderComponent({ messageLevels: levels });
      // cloned — not the same reference
      expect(comp.selectedLevels).toEqual(levels);
      expect(comp.selectedLevels).not.toBe(levels);
   });

   it("should default selectedLevels to levelOptions (i18n strings) when messageLevels is null", async () => {
      const { comp } = await renderComponent({ messageLevels: null });
      expect(comp.selectedLevels).toEqual(["_#(js:Error)", "_#(js:Warning)", "_#(js:Info)"]);
   });

   it("should initialize messages to [] when messages input is null", async () => {
      const { comp } = await renderComponent({ messages: null });
      expect(comp.messages).toEqual([]);
   });

   it("should set levelButtonLabel to 'All levels' when all 3 levels are selected", async () => {
      const { comp } = await renderComponent();
      expect(comp.levelButtonLabel).toBe("_#(js:All levels)");
   });
});

// ---------------------------------------------------------------------------
// Group 5: getLevelButtonLabel [Risk 2]
// ---------------------------------------------------------------------------

describe("ConsoleDialogComponent — getLevelButtonLabel", () => {
   it("should return 'All levels' when all options are selected", async () => {
      const { comp } = await renderComponent();
      expect(comp.getLevelButtonLabel()).toBe("_#(js:All levels)");
   });

   it("should return 'Custom levels' when not all options are selected", async () => {
      const { comp } = await renderComponent({ messageLevels: ["_#(js:Error)"] });
      comp.selectedLevels = ["_#(js:Error)"];
      expect(comp.getLevelButtonLabel()).toBe("_#(js:Custom levels)");
   });
});

// ---------------------------------------------------------------------------
// Group 6: getLevelCounter [Risk 1]
// ---------------------------------------------------------------------------

describe("ConsoleDialogComponent — getLevelCounter", () => {
   it("should count messages matching the given level (case-insensitive)", async () => {
      const msgs = [
         makeMessage("ERROR"),
         makeMessage("ERROR"),
         makeMessage("WARNING"),
      ];
      const { comp } = await renderComponent({
         messages: msgs,
         messageLevels: ["Error", "Warning", "Info"],
      });
      comp.messages = msgs;
      // Pass title-case level values (matching messageLevels format); equalsIgnoreCase
      // compares against ConsoleMessageType uppercase values from the backend.
      expect(comp.getLevelCounter("Error")).toBe(2);
      expect(comp.getLevelCounter("Warning")).toBe(1);
      expect(comp.getLevelCounter("Info")).toBe(0);
   });
});

// ---------------------------------------------------------------------------
// Group 7: getLevelIcon [Risk 1]
// ---------------------------------------------------------------------------

describe("ConsoleDialogComponent — getLevelIcon", () => {
   it("should return error icon for the Error level option", async () => {
      const { comp } = await renderComponent();
      expect(comp.getLevelIcon(comp.levelOptions[0])).toBe("message-error-icon");
   });

   it("should return warning icon for the Warning level option", async () => {
      const { comp } = await renderComponent();
      expect(comp.getLevelIcon(comp.levelOptions[1])).toBe("message-warning-icon");
   });

   it("should return annotation icon for other levels", async () => {
      const { comp } = await renderComponent();
      expect(comp.getLevelIcon(comp.levelOptions[2])).toBe("annotation-icon");
   });
});

// ---------------------------------------------------------------------------
// Group 8: isSelected [Risk 1]
// ---------------------------------------------------------------------------

describe("ConsoleDialogComponent — isSelected", () => {
   it("should return true when level is in selectedLevels", async () => {
      const { comp } = await renderComponent();
      expect(comp.isSelected("_#(js:Error)")).toBe(true);
   });

   it("should return false when level is not in selectedLevels", async () => {
      const { comp } = await renderComponent({ messageLevels: [] });
      comp.selectedLevels = [];
      expect(comp.isSelected("_#(js:Error)")).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 9: closeDialog [Risk 1]
// ---------------------------------------------------------------------------

describe("ConsoleDialogComponent — closeDialog", () => {
   it("should emit onClose with current messages", async () => {
      const msgs = [makeMessage("error")];
      const { comp } = await renderComponent({ messages: msgs });
      comp.messages = msgs;
      const emitSpy = vi.spyOn(comp.onClose, "emit");
      comp.closeDialog();
      expect(emitSpy).toHaveBeenCalledWith(msgs);
   });
});

// ---------------------------------------------------------------------------
// Group 10: clearMessages [Risk 1]
// ---------------------------------------------------------------------------

describe("ConsoleDialogComponent — clearMessages", () => {
   it("should emit messagesChange with empty array", async () => {
      const { comp } = await renderComponent();
      const emitSpy = vi.spyOn(comp.messagesChange, "emit");
      comp.clearMessages();
      expect(emitSpy).toHaveBeenCalledWith([]);
   });
});
