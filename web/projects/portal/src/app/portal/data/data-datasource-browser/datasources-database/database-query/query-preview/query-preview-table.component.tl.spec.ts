import { ChangeDetectorRef, ElementRef, Renderer2, SimpleChange } from "@angular/core";
import { GuiTool } from "../../../../../../common/util/gui-tool";
import { QueryPreviewTableComponent } from "./query-preview-table.component";

interface MockNativeElement {
   clientHeight: number;
   clientWidth: number;
   offsetHeight: number;
   scrollHeight: number;
   scrollLeft: number;
   scrollTop: number;
}

interface QueryPreviewComponentContext {
   changeRef: ChangeDetectorRef;
   comp: QueryPreviewTableComponent;
   headerNative: MockNativeElement;
   previewNative: MockNativeElement;
   renderer: Renderer2;
   tableContainerNative: MockNativeElement;
   tableNative: MockNativeElement;
   verticalScrollNative: MockNativeElement;
}

function flushMicrotasks(): Promise<void> {
   return Promise.resolve();
}

function createElementRef(nativeElement: MockNativeElement): ElementRef {
   return { nativeElement } as ElementRef;
}

function createComponent(): QueryPreviewComponentContext {
   const renderer = {
      setProperty: vi.fn((target: Record<string, unknown>, key: string, value: unknown) => {
         target[key] = value;
      })
   } as unknown as Renderer2;
   const changeRef = {
      detectChanges: vi.fn()
   } as unknown as ChangeDetectorRef;

   const previewNative: MockNativeElement = {
      clientHeight: 180,
      clientWidth: 300,
      offsetHeight: 0,
      scrollHeight: 0,
      scrollLeft: 0,
      scrollTop: 0
   };
   const tableContainerNative: MockNativeElement = {
      clientHeight: 120,
      clientWidth: 0,
      offsetHeight: 0,
      scrollHeight: 420,
      scrollLeft: 0,
      scrollTop: 0
   };
   const tableNative: MockNativeElement = {
      clientHeight: 0,
      clientWidth: 0,
      offsetHeight: 224,
      scrollHeight: 0,
      scrollLeft: 0,
      scrollTop: 0
   };
   const headerNative: MockNativeElement = {
      clientHeight: 0,
      clientWidth: 0,
      offsetHeight: 35,
      scrollHeight: 0,
      scrollLeft: 0,
      scrollTop: 0
   };
   const verticalScrollNative: MockNativeElement = {
      clientHeight: 0,
      clientWidth: 0,
      offsetHeight: 0,
      scrollHeight: 0,
      scrollLeft: 0,
      scrollTop: 0
   };

   const comp = new QueryPreviewTableComponent(renderer, changeRef);
   comp.previewContainer = createElementRef(previewNative);
   comp.tableContainer = createElementRef(tableContainerNative);
   comp.table = createElementRef(tableNative);
   comp.headerTable = createElementRef(headerNative);
   comp.verticalScrollWrapper = createElementRef(verticalScrollNative);

   return {
      changeRef,
      comp,
      headerNative,
      previewNative,
      renderer,
      tableContainerNative,
      tableNative,
      verticalScrollNative
   };
}

describe("QueryPreviewTableComponent", () => {
   afterEach(() => {
      vi.restoreAllMocks();
      vi.useRealTimers();
   });

   describe("Group 1 - table data and width calculation", () => {
      it("should limit rows and recalculate widths when columns change", async () => {
         vi.useFakeTimers();

         const { comp, previewNative, renderer } = createComponent();
         const measureSpy = vi.spyOn(QueryPreviewTableComponent.prototype, "getCellLabel");

         previewNative.scrollLeft = 48;
         vi.spyOn(GuiTool, "measureScrollbars").mockReturnValue(16);

         const rows = Array.from({ length: 5003 }, (_value, index) =>
            index === 0 ? ["Name", "Value"] : [`row-${index}`, `value-${index}`]
         );

         comp.tableData = rows;
         await flushMicrotasks();
         vi.runAllTimers();

         expect(comp.limited).toBe(true);
         expect(comp.tableData).toHaveLength(5001);
         expect(comp.tableHeight).toBe(5001 * comp.cellHeight);
         expect(comp.scrollXPos).toBe(0);
         expect(previewNative.scrollLeft).toBe(0);
         // _columnWidths is private internal state — cast needed to verify column-width recalculation
         expect(comp["_columnWidths"]).toHaveLength(2);
         expect(comp.columnIndexRange.start).toBe(0);
         expect(comp.columnIndexRange.end).toBe(1);
         expect(renderer.setProperty).toHaveBeenCalledWith(previewNative, "scrollLeft", 0);
         expect(measureSpy).toHaveBeenCalled();
      });

      it("should recompute and restore scroll position when column visibility changes with the same column count", async () => {
         vi.useFakeTimers();

         const { comp, previewNative } = createComponent();
         vi.spyOn(GuiTool, "measureScrollbars").mockReturnValue(12);

         comp.tableData = [
            ["Visible", "Hidden"],
            ["A", "B"]
         ];
         await flushMicrotasks();
         vi.runAllTimers();

         previewNative.scrollLeft = 32;
         comp.tableData = [
            ["Visible", null],
            ["A", null]
         ];
         await flushMicrotasks();
         vi.runAllTimers();

         expect(comp.scrollXPos).toBe(32);
         expect(previewNative.scrollLeft).toBe(32);
      });

      it("should skip width recalculation and leave scroll position untouched when columns stay aligned", async () => {
         vi.useFakeTimers();

         const { comp, previewNative, renderer } = createComponent();
         vi.spyOn(GuiTool, "measureScrollbars").mockReturnValue(12);

         comp.tableData = [
            ["Visible", "Hidden"],
            ["A", "B"]
         ];
         await flushMicrotasks();
         vi.runAllTimers();

         // sentinel deliberately differs from previewNative.scrollLeft: if the skip
         // branch were incorrectly entered, scrollXPos would be overwritten to 32
         comp.scrollXPos = 99;
         previewNative.scrollLeft = 32;
         vi.mocked(renderer.setProperty).mockClear();
         comp.tableData = [
            ["Visible", "Hidden"],
            ["C", "D"]
         ];
         await flushMicrotasks();
         vi.runAllTimers();

         expect(comp.scrollXPos).toBe(99);
         expect(renderer.setProperty).not.toHaveBeenCalled();
      });

      it("should normalize hidden column widths and restore scrollLeft after colWidths updates", async () => {
         vi.useFakeTimers();

         const { comp, previewNative } = createComponent();
         vi.spyOn(GuiTool, "measureScrollbars").mockReturnValue(10);

         comp.tableData = [
            ["ID", null, "Name"],
            ["1", null, "Alice"]
         ];
         await flushMicrotasks();
         vi.runAllTimers();

         previewNative.scrollLeft = 21;
         comp.colWidths = [50, 60, 70];
         await flushMicrotasks();
         vi.runAllTimers();

         expect(comp.colWidths).toEqual([50, 0, 70]);
         // _columnWidths is private internal state — cast needed to verify zero-width normalization
         expect(comp["_columnWidths"]).toEqual([50, 0, 70]);
         expect(previewNative.scrollLeft).toBe(21);
      });
   });

   describe("Group 2 - layout lifecycle", () => {
      it("should update container bounds from ngOnChanges", () => {
         const { comp } = createComponent();

         comp.ngOnChanges({
            containerSize: new SimpleChange(null, { height: 240, width: 360 }, true)
         });

         expect(comp.containerHeight).toBe(238);
         expect(comp.containerWidth).toBe(358);
      });

      it("should detect header and table height changes after view checks", () => {
         const { changeRef, comp, headerNative, tableNative } = createComponent();

         headerNative.offsetHeight = 40;
         tableNative.offsetHeight = 300;
         comp.ngAfterViewChecked();

         expect(comp.headerHeight).toBe(40);
         expect(comp.tableHeight).toBe(300);
         expect(changeRef.detectChanges).toHaveBeenCalledTimes(2);
      });

      it("should restore horizontal scroll when content check sees a browser reset", () => {
         const { comp, previewNative } = createComponent();

         comp.scrollXPos = 30;
         previewNative.scrollLeft = 0;
         previewNative.clientWidth = 160;
         comp.ngAfterContentChecked();

         expect(previewNative.scrollLeft).toBe(30);
         expect(comp.horizontalDist).toBe(190);
      });
   });

   describe("Group 3 - scrolling and visibility", () => {
      it("should update scroll positions for touch and horizontal scroll events", () => {
         const { comp, previewNative, tableContainerNative } = createComponent();

         tableContainerNative.scrollHeight = 380;
         tableContainerNative.clientHeight = 100;
         comp.touchVScroll(-40);
         comp.touchVScroll(-500);
         comp.touchVScroll(1000);

         previewNative.scrollLeft = 75;
         comp.touchHScroll(30);
         previewNative.clientWidth = 150;
         previewNative.scrollLeft = 120;
         comp["_columnWidths"] = [100, 100, 100];
         comp.columnRightPositions = [100, 200, 300];
         comp.horizontalScroll();

         expect(comp.scrollY).toBe(0);
         expect(previewNative.scrollLeft).toBe(120);
         expect(comp.horizontalDist).toBe(270);
         expect(comp.columnIndexRange.start).toBe(1);
         expect(comp.columnIndexRange.end).toBe(2);
         expect(comp.leftOfColRangeWidth).toBe(100);
         expect(comp.rightOfColRangeWidth).toBe(0);
      });

      it("should update currentRow and wheel wrapper scroll state from scroll handlers", () => {
         const { comp, verticalScrollNative } = createComponent();
         const preventDefault = vi.fn();

         comp.verticalScrollHandler({
            target: {
               scrollTop: 95
            }
         });
         comp.wheelScrollHandler({
            deltaY: 25,
            preventDefault
         });

         expect(comp.scrollY).toBe(95);
         expect(comp.currentRow).toBe(3);
         expect(verticalScrollNative.scrollTop).toBe(25);
         expect(preventDefault).toHaveBeenCalled();
      });
   });

   describe("Group 4 - display helpers", () => {
      it("should report tableBodyWidth as tableWidth plus a 2px border allowance", () => {
         const { comp } = createComponent();
         comp.tableWidth = 420;

         expect(comp.tableBodyWidth).toBe(422);
      });

      it("should mark rows visible inside the 100-row render window and invisible beyond it", () => {
         const { comp } = createComponent();
         comp.currentRow = 4;

         expect(comp.isRowVisible(4)).toBe(true);
         expect(comp.isRowVisible(103)).toBe(true);
         expect(comp.isRowVisible(104)).toBe(false);
      });

      it("should escape newlines and return an empty string for null labels", () => {
         const { comp } = createComponent();

         expect(comp.getCellLabel("a\nb")).toBe("a\\nb");
         expect(comp.getCellLabel(null)).toBe("");
      });
   });
});
