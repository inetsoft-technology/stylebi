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
import { Subject } from "rxjs";
import { ComponentTool } from "./component-tool";

function fakeModal() {
   const onApply = new Subject<any>();
   const onCommit = new Subject<any>();
   const onCancel = new Subject<any>();
   const componentInstance: any = { onApply, onCommit, onCancel };
   let resolveResult: (v: any) => void;
   const modalRef: any = {
      componentInstance,
      result: new Promise<any>((resolve) => { resolveResult = resolve; }),
      close: (v: any) => resolveResult(v)
   };
   const modalService: any = { open: () => modalRef, objectChange: () => {} };

   return { onApply, onCommit, modalService };
}

// Flushes the "wait for next cycle" setTimeout(0) in showDialog's commit path, and the
// microtask it schedules by resolving modal.result.
function flush(): Promise<void> {
   return new Promise(resolve => setTimeout(resolve, 0));
}

describe("ComponentTool.showDialog", () => {
   // Write coordination (2026-08-17-write-coordination-design.md): Apply never closes the
   // dialog, so a dialog that forwards its held model unmodified on Apply would otherwise carry
   // a since-bumped revision into its own next apply/commit and conflict with itself. Found on
   // stylebi#4640 review -- most property dialogs emit their model as-is on apply, so the fix
   // belongs here, once, rather than in each dialog's own payload-construction code.
   it("strips revision from an apply payload before it reaches the caller", () => {
      const { onApply, modalService } = fakeModal();
      let received: any;

      ComponentTool.showDialog(modalService, class {} as any,
         (v: any) => { received = v; }, {});

      onApply.next({ collapse: false, result: { revision: 7, other: "x" } });

      expect(received.revision).toBeUndefined();
      expect(received.other).toBe("x");
   });

   it("leaves an apply payload with no revision untouched", () => {
      const { onApply, modalService } = fakeModal();
      let received: any;

      ComponentTool.showDialog(modalService, class {} as any,
         (v: any) => { received = v; }, {});

      onApply.next({ collapse: false, result: { other: "x" } });

      expect(received).toEqual({ other: "x" });
   });

   // stylebi#4637: "Apply then OK discards your edits". An Apply bumps the server's revision
   // but nothing refreshes the dialog's held copy, so the FINAL commit (OK) that follows still
   // carries the pre-apply revision and would otherwise be wrongly refused as a conflict with
   // the dialog's own prior apply.
   it("strips a stale revision from the final commit once an apply has already happened", async () => {
      const { onApply, onCommit, modalService } = fakeModal();
      let received: any;

      ComponentTool.showDialog(modalService, class {} as any,
         (v: any) => { received = v; }, {});

      onApply.next({ collapse: false, result: { revision: 7, other: "x" } });
      onCommit.next({ revision: 7, other: "y" });
      await flush();
      await flush();

      expect(received.revision).toBeUndefined();
      expect(received.other).toBe("y");
   });

   it("does not touch the commit's revision when no apply happened first", async () => {
      const { onCommit, modalService } = fakeModal();
      let received: any;

      ComponentTool.showDialog(modalService, class {} as any,
         (v: any) => { received = v; }, {});

      onCommit.next({ revision: 7, other: "y" });
      await flush();
      await flush();

      expect(received.revision).toBe(7);
   });
});
