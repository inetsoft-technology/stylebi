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
package inetsoft.web.wiz.viewsheet;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.report.StyleConstants;
import inetsoft.report.TableDataDescriptor;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.web.composer.model.vs.VSObjectFormatInfoModel;
import inetsoft.web.composer.vs.controller.FormatPainterService;
import inetsoft.web.composer.vs.objects.command.SetCurrentFormatCommand;
import inetsoft.web.composer.vs.objects.event.FormatVSObjectEvent;
import inetsoft.web.composer.vs.objects.event.GetVSObjectFormatEvent;
import inetsoft.web.wiz.binding.CalcTableService;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class ViewsheetFormatServiceTest {
   @Test
   void appliesTheFormatToTheNamedAssemblies() throws Exception {
      FormatPainterService painter = mock(FormatPainterService.class);
      VSObjectFormatInfoModel format = new VSObjectFormatInfoModel();
      format.setColor("#333333");

      serviceWith(painter).setFormat(
         "tok", principal(),
         new ViewsheetFormatService.FormatRequest(List.of("Gauge1"), format, false), "");

      ArgumentCaptor<FormatVSObjectEvent> captor =
         ArgumentCaptor.forClass(FormatVSObjectEvent.class);
      verify(painter).setFormat(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                anyString());
      assertArrayEquals(new String[]{ "Gauge1" }, captor.getValue().getObjects());
      assertEquals("#333333", captor.getValue().getFormat().getColor());
   }

   /**
    * {@code FormatPainterService.setFormat} dereferences {@code event.getCharts().length}, so
    * leaving the array null made every set_format call fail with
    * "Cannot read the array length because the return value of
    * FormatVSObjectEvent.getCharts() is null" — a 500 for both a format and a reset.
    */
   @Test
   void populatesTheChartsArraySoThePainterDoesNotDereferenceNull() throws Exception {
      FormatPainterService painter = mock(FormatPainterService.class);
      VSObjectFormatInfoModel format = new VSObjectFormatInfoModel();

      serviceWith(painter).setFormat(
         "tok", principal(),
         new ViewsheetFormatService.FormatRequest(List.of("Gauge1"), format, false), "");

      ArgumentCaptor<FormatVSObjectEvent> captor =
         ArgumentCaptor.forClass(FormatVSObjectEvent.class);
      verify(painter).setFormat(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                anyString());
      assertNotNull(captor.getValue().getCharts(), "charts must not be null");
   }

   /**
    * {@code FormatInfoModel} is annotated {@code @JsonTypeInfo(use = Id.CLASS, property = "type")},
    * so Jackson refused any format object that did not carry
    * {@code "type": "inetsoft.web.composer.model.vs.VSObjectFormatInfoModel"} — a 400 for every
    * documented usage, including an empty {@code {}}. Requiring a caller to name a Java class is
    * exactly the internals leak this API is meant to avoid, so the endpoint must accept a plain
    * format object.
    */
   @Test
   void acceptsAPlainFormatObjectWithoutAJavaClassDiscriminator() throws Exception {
      ObjectMapper mapper = new ObjectMapper();

      ViewsheetFormatService.FormatRequest request = mapper.readValue(
         "{\"assemblies\":[\"Text1\"],\"format\":{\"color\":\"#CC0000\"," +
         "\"backgroundColor\":\"#FFEEAA\"},\"reset\":false}",
         ViewsheetFormatService.FormatRequest.class);

      assertNotNull(request.format(), "format must deserialize without a 'type' discriminator");
      assertEquals("#CC0000", request.format().getColor());
      assertEquals("#FFEEAA", request.format().getBackgroundColor());
   }

   /**
    * The tool documents the format as CSS-shaped and lists `align` beside `color` and
    * `backgroundColor`, so a caller writes {@code align: "center"}. But align is an
    * {@code AlignmentInfo} object with {@code halign}/{@code valign}, so Jackson threw during
    * body conversion — and Spring wraps that in {@code HttpMessageNotReadableException}, which
    * returns a **bodyless 400**. The caller saw "Request failed with status code 400" and nothing
    * else. Found live on local-1201 running case 7.
    *
    * <p>Accepting the word is the fix rather than documenting the object: the documented usage
    * should work, and "center" has exactly one sensible meaning here.
    */
   @Test
   void acceptsAlignAsAWordBecauseThatIsWhatTheToolDocuments() throws Exception {
      ObjectMapper mapper = new ObjectMapper();

      ViewsheetFormatService.FormatRequest request = mapper.readValue(
         "{\"assemblies\":[\"Text1\"],\"format\":{\"align\":\"center\"},\"reset\":false}",
         ViewsheetFormatService.FormatRequest.class);

      assertNotNull(request.format().getAlign(), "align must survive as an AlignmentInfo");
      assertEquals("Center", request.format().getAlign().getHalign());
   }

   @Test
   void acceptsAVerticalAlignWordToo() throws Exception {
      ObjectMapper mapper = new ObjectMapper();

      ViewsheetFormatService.FormatRequest request = mapper.readValue(
         "{\"assemblies\":[\"Text1\"],\"format\":{\"align\":\"middle\"},\"reset\":false}",
         ViewsheetFormatService.FormatRequest.class);

      assertEquals("Middle", request.format().getAlign().getValign());
   }

   /** Both axes at once, since a caller wanting one often wants the other. */
   @Test
   void acceptsBothAlignmentsInOneValue() throws Exception {
      ObjectMapper mapper = new ObjectMapper();

      ViewsheetFormatService.FormatRequest request = mapper.readValue(
         "{\"assemblies\":[\"Text1\"],\"format\":{\"align\":\"center middle\"}," +
         "\"reset\":false}",
         ViewsheetFormatService.FormatRequest.class);

      assertEquals("Center", request.format().getAlign().getHalign());
      assertEquals("Middle", request.format().getAlign().getValign());
   }

   /** The object form still works — this widens the contract rather than replacing it. */
   @Test
   void stillAcceptsTheObjectForm() throws Exception {
      ObjectMapper mapper = new ObjectMapper();

      ViewsheetFormatService.FormatRequest request = mapper.readValue(
         "{\"assemblies\":[\"Text1\"],\"format\":{\"align\":{\"halign\":\"Right\"}}," +
         "\"reset\":false}",
         ViewsheetFormatService.FormatRequest.class);

      assertEquals("Right", request.format().getAlign().getHalign());
   }

   @Test
   void refusesAnAlignWordItCannotResolve() {
      ObjectMapper mapper = new ObjectMapper();

      Exception thrown = assertThrows(
         Exception.class,
         () -> mapper.readValue(
            "{\"assemblies\":[\"Text1\"],\"format\":{\"align\":\"sideways\"}," +
            "\"reset\":false}",
            ViewsheetFormatService.FormatRequest.class));

      assertTrue(thrown.getMessage().contains("sideways"), thrown.getMessage());
   }

   /**
    * {@code coerceAlign}/{@code coerceBorderStyles}/{@code toLineConstant}/
    * {@code coerceBorderWidth} are shared between {@code FormatRequest} and
    * {@code CellFormatRequest}, so a bad payload sent to {@code set_calc_cell_format} must name
    * that tool, not the sibling {@code set_format} that first defined this parsing (a caller
    * debugging via the reported name would otherwise be pointed at the wrong tool entirely).
    */
   @Test
   void refusesABadAlignWordThroughCellFormatRequestNamingTheRightTool() {
      ObjectMapper mapper = new ObjectMapper();

      Exception thrown = assertThrows(
         Exception.class,
         () -> mapper.readValue(
            "{\"assembly\":\"FreehandTable1\",\"row\":0,\"col\":0," +
            "\"format\":{\"align\":\"sideways\"},\"reset\":false}",
            ViewsheetFormatService.CellFormatRequest.class));

      assertTrue(thrown.getMessage().contains("set_calc_cell_format"), thrown.getMessage());
      assertFalse(thrown.getMessage().contains("set_format could not"), thrown.getMessage());
   }

   /**
    * The border style is asymmetric in the underlying model: reading emits CSS words
    * ({@code FormatInfoModel.getBorderStyle} returns "solid"/"dashed"/"dotted"/"double"), while
    * writing goes through {@code FormatPainterService}, which does
    * {@code Integer.parseInt(topBorder)}. So the CSS word this API documents could never be
    * written, and produced a raw {@code For input string: "solid"} naming no field. Found live on
    * local-1203 running case 7.
    */
   @Test
   void acceptsBorderStylesAsCssWordsBecauseThatIsWhatReadsBack() throws Exception {
      ObjectMapper mapper = new ObjectMapper();

      ViewsheetFormatService.FormatRequest request = mapper.readValue(
         "{\"assemblies\":[\"Text1\"],\"format\":{\"borderTopStyle\":\"solid\"," +
         "\"borderLeftStyle\":\"dashed\",\"borderBottomStyle\":\"none\"},\"reset\":false}",
         ViewsheetFormatService.FormatRequest.class);

      assertEquals(String.valueOf(inetsoft.report.StyleConstants.THIN_LINE),
                   request.format().getBorderTopStyle());
      assertEquals(String.valueOf(inetsoft.report.StyleConstants.DASH_LINE),
                   request.format().getBorderLeftStyle());
      assertEquals("0", request.format().getBorderBottomStyle());
   }

   /**
    * A border width has to reach the line constant, because that is the only place StyleBI keeps
    * weight: {@code FormatPainterService} builds its Insets from the four style fields alone and
    * never reads {@code borderTopWidth}. The field is in {@code FormatInfoModel} and in this tool's
    * documented schema, so asking for 3px silently produced a thin border.
    */
   @Test
   void foldsABorderWidthIntoTheLineConstant() throws Exception {
      ObjectMapper mapper = new ObjectMapper();

      ViewsheetFormatService.FormatRequest request = mapper.readValue(
         "{\"assemblies\":[\"Text1\"],\"format\":{" +
         "\"borderTopStyle\":\"solid\",\"borderTopWidth\":3," +
         "\"borderLeftStyle\":\"solid\",\"borderLeftWidth\":\"2px\"," +
         "\"borderBottomStyle\":\"dashed\",\"borderBottomWidth\":2," +
         "\"borderRightStyle\":\"solid\",\"borderRightWidth\":0},\"reset\":false}",
         ViewsheetFormatService.FormatRequest.class);

      assertEquals(String.valueOf(StyleConstants.THICK_LINE),
                   request.format().getBorderTopStyle(), "3px solid is a thick line");
      assertEquals(String.valueOf(StyleConstants.MEDIUM_LINE),
                   request.format().getBorderLeftStyle(), "\"2px\" is accepted like 2");
      assertEquals(String.valueOf(StyleConstants.MEDIUM_DASH),
                   request.format().getBorderBottomStyle(), "weight applies to the dash family too");
      assertEquals(String.valueOf(StyleConstants.NO_BORDER),
                   request.format().getBorderRightStyle(), "a zero width is no border");
   }

   /** A width with no style at all reads as a solid border of that weight. */
   @Test
   void aBorderWidthAloneImpliesSolid() throws Exception {
      ObjectMapper mapper = new ObjectMapper();

      ViewsheetFormatService.FormatRequest request = mapper.readValue(
         "{\"assemblies\":[\"Text1\"],\"format\":{\"borderTopWidth\":2},\"reset\":false}",
         ViewsheetFormatService.FormatRequest.class);

      assertEquals(String.valueOf(StyleConstants.MEDIUM_LINE),
                   request.format().getBorderTopStyle());
   }

   /**
    * StyleBI has no weighted dotted or double line, so the combination fails loud instead of
    * rendering a thin one — which would be the original silent drop wearing a different hat.
    */
   @Test
   void refusesAWidthOnABorderFamilyThatHasNoWeight() {
      ObjectMapper mapper = new ObjectMapper();

      Exception thrown = assertThrows(
         Exception.class,
         () -> mapper.readValue(
            "{\"assemblies\":[\"Text1\"],\"format\":{\"borderTopStyle\":\"dotted\"," +
            "\"borderTopWidth\":3},\"reset\":false}",
            ViewsheetFormatService.FormatRequest.class));

      assertTrue(thrown.getMessage().contains("borderTopWidth"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("dotted"), thrown.getMessage());
   }

   /** A weight word plus a width is a contradiction, not extra detail. */
   @Test
   void refusesAWidthAlongsideAWeightWord() {
      ObjectMapper mapper = new ObjectMapper();

      Exception thrown = assertThrows(
         Exception.class,
         () -> mapper.readValue(
            "{\"assemblies\":[\"Text1\"],\"format\":{\"borderTopStyle\":\"thick\"," +
            "\"borderTopWidth\":1},\"reset\":false}",
            ViewsheetFormatService.FormatRequest.class));

      assertTrue(thrown.getMessage().contains("borderTopWidth"), thrown.getMessage());
   }

   /** A numeric constant already encodes the weight, so a width beside it is ambiguous. */
   @Test
   void refusesAWidthAlongsideANumericConstant() {
      ObjectMapper mapper = new ObjectMapper();

      Exception thrown = assertThrows(
         Exception.class,
         () -> mapper.readValue(
            "{\"assemblies\":[\"Text1\"],\"format\":{\"borderTopStyle\":\"4097\"," +
            "\"borderTopWidth\":2},\"reset\":false}",
            ViewsheetFormatService.FormatRequest.class));

      assertTrue(thrown.getMessage().contains("borderTopWidth"), thrown.getMessage());
   }

   /** A number still passes through, for anyone who already knows the constant. */
   @Test
   void leavesANumericBorderStyleAlone() throws Exception {
      ObjectMapper mapper = new ObjectMapper();

      ViewsheetFormatService.FormatRequest request = mapper.readValue(
         "{\"assemblies\":[\"Text1\"],\"format\":{\"borderTopStyle\":\"4097\"}," +
         "\"reset\":false}",
         ViewsheetFormatService.FormatRequest.class);

      assertEquals("4097", request.format().getBorderTopStyle());
   }

   @Test
   void refusesABorderStyleItCannotResolve() {
      ObjectMapper mapper = new ObjectMapper();

      Exception thrown = assertThrows(
         Exception.class,
         () -> mapper.readValue(
            "{\"assemblies\":[\"Text1\"],\"format\":{\"borderTopStyle\":\"wiggly\"}," +
            "\"reset\":false}",
            ViewsheetFormatService.FormatRequest.class));

      assertTrue(thrown.getMessage().contains("wiggly"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("borderTopStyle"), thrown.getMessage());
   }

   @Test
   void requiresAtLeastOneAssembly() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> serviceWith(mock(FormatPainterService.class)).setFormat(
            "tok", principal(),
            new ViewsheetFormatService.FormatRequest(List.of(), new VSObjectFormatInfoModel(),
                                                     false), ""));
      assertTrue(thrown.getMessage().contains("assemblies"));
   }

   @Test
   void requiresAFormatUnlessResetting() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> serviceWith(mock(FormatPainterService.class)).setFormat(
            "tok", principal(),
            new ViewsheetFormatService.FormatRequest(List.of("Gauge1"), null, false), ""));
      assertTrue(thrown.getMessage().contains("format"));
   }

   @Test
   void resetNeedsNoFormat() throws Exception {
      FormatPainterService painter = mock(FormatPainterService.class);

      serviceWith(painter).setFormat(
         "tok", principal(),
         new ViewsheetFormatService.FormatRequest(List.of("Gauge1"), null, true), "");

      ArgumentCaptor<FormatVSObjectEvent> captor =
         ArgumentCaptor.forClass(FormatVSObjectEvent.class);
      verify(painter).setFormat(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                anyString());
      assertTrue(captor.getValue().isReset());
   }

   /**
    * Bug 76325 item 3: no existing tool could format a chart's own title without also bleeding
    * onto its axis titles and tick labels, because the only write path was the whole-OBJECT
    * format. {@code target: "title"} routes the write through {@code event.getData()} instead —
    * the same per-assembly {@code TableDataPath[]} mechanism a table already uses for its own
    * title/header/cell formats — landing on {@code VSAssemblyInfo.TITLEPATH} specifically rather
    * than {@code TableDataPath.OBJECT}.
    */
   @Test
   void targetTitleRoutesThroughTheTitlePathInsteadOfTheWholeObject() throws Exception {
      FormatPainterService painter = mock(FormatPainterService.class);
      VSObjectFormatInfoModel format = new VSObjectFormatInfoModel();
      format.setColor("#000080");

      serviceWith(painter).setFormat(
         "tok", principal(),
         new ViewsheetFormatService.FormatRequest(
            List.of("Chart1"), format, false, "title"), "");

      ArgumentCaptor<FormatVSObjectEvent> captor =
         ArgumentCaptor.forClass(FormatVSObjectEvent.class);
      verify(painter).setFormat(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                anyString());
      ArrayList<TableDataPath[]> data = captor.getValue().getData();
      assertNotNull(data, "target:title must populate event.getData()");
      assertEquals(1, data.size());
      assertArrayEquals(new TableDataPath[]{ VSAssemblyInfo.TITLEPATH }, data.get(0));
   }

   /** Default behaviour (no target, or target:"object") must be unchanged — a whole-object write. */
   @Test
   void defaultTargetLeavesDataNullForAWholeObjectWrite() throws Exception {
      FormatPainterService painter = mock(FormatPainterService.class);
      VSObjectFormatInfoModel format = new VSObjectFormatInfoModel();

      serviceWith(painter).setFormat(
         "tok", principal(),
         new ViewsheetFormatService.FormatRequest(List.of("Chart1"), format, false), "");

      ArgumentCaptor<FormatVSObjectEvent> captor =
         ArgumentCaptor.forClass(FormatVSObjectEvent.class);
      verify(painter).setFormat(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                anyString());
      assertNull(captor.getValue().getData(), "no target must not touch event.getData()");
   }

   @Test
   void targetTitleAlignsOneTitlePathPerAssembly() throws Exception {
      FormatPainterService painter = mock(FormatPainterService.class);
      VSObjectFormatInfoModel format = new VSObjectFormatInfoModel();

      serviceWith(painter).setFormat(
         "tok", principal(),
         new ViewsheetFormatService.FormatRequest(
            List.of("Chart1", "Table1"), format, false, "title"), "");

      ArgumentCaptor<FormatVSObjectEvent> captor =
         ArgumentCaptor.forClass(FormatVSObjectEvent.class);
      verify(painter).setFormat(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                anyString());
      ArrayList<TableDataPath[]> data = captor.getValue().getData();
      assertEquals(2, data.size(), "one TableDataPath[] per assembly, same order as objects[]");
      assertArrayEquals(new TableDataPath[]{ VSAssemblyInfo.TITLEPATH }, data.get(1));
   }

   @Test
   void refusesATargetThatIsNeitherObjectNorTitleNorText() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> serviceWith(mock(FormatPainterService.class)).setFormat(
            "tok", principal(),
            new ViewsheetFormatService.FormatRequest(
               List.of("Chart1"), new VSObjectFormatInfoModel(), false, "axis"), ""));
      assertTrue(thrown.getMessage().contains("target"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("axis"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("text"), thrown.getMessage());
   }

   /**
    * `target: "text"` routes a chart's aesthetic-bound field format through the same
    * `event.getCharts()`/`getRegions()`/`getColumnNames()`/`getIndexes()` mechanism the
    * interactive Composer's own "aggregate text format" editor already drives
    * ({@code vs-binding-pane.component.ts}'s {@code createUpdateFormatEvent()}) — previously
    * dead for the wiz-agent path because {@code event.setCharts(new String[0])} was hardcoded.
    */
   @Test
   void targetTextRoutesThroughTheChartsArrayWithTheNamedField() throws Exception {
      FormatPainterService painter = mock(FormatPainterService.class);
      VSObjectFormatInfoModel format = new VSObjectFormatInfoModel();
      format.setFormat("currency");
      format.setFormatSpec("$#,##0");

      serviceWith(painter).setFormat(
         "tok", principal(),
         new ViewsheetFormatService.FormatRequest(
            List.of("Chart1"), format, false, "text", "NET_REVENUE_SUM"), "");

      ArgumentCaptor<FormatVSObjectEvent> captor =
         ArgumentCaptor.forClass(FormatVSObjectEvent.class);
      verify(painter).setFormat(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                anyString());
      FormatVSObjectEvent event = captor.getValue();
      assertArrayEquals(new String[0], event.getObjects());
      assertArrayEquals(new String[]{ "Chart1" }, event.getCharts());
      assertArrayEquals(new String[]{ "text" }, event.getRegions());
      assertArrayEquals(new String[][]{ { "NET_REVENUE_SUM" } }, event.getColumnNames());
      assertArrayEquals(new int[][]{ { -1 } }, event.getIndexes());
   }

   @Test
   void targetTextRequiresExactlyOneAssembly() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> serviceWith(mock(FormatPainterService.class)).setFormat(
            "tok", principal(),
            new ViewsheetFormatService.FormatRequest(
               List.of("Chart1", "Chart2"), new VSObjectFormatInfoModel(), false, "text",
               "NET_REVENUE_SUM"), ""));
      assertTrue(thrown.getMessage().contains("2"), thrown.getMessage());
   }

   @Test
   void targetTextRequiresField() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> serviceWith(mock(FormatPainterService.class)).setFormat(
            "tok", principal(),
            new ViewsheetFormatService.FormatRequest(
               List.of("Chart1"), new VSObjectFormatInfoModel(), false, "text", null), ""));
      assertTrue(thrown.getMessage().contains("field"), thrown.getMessage());
   }

   // ── target: "data" (bug 76754) ──────────────────────────────────────────────────────────

   /**
    * A Crosstab's per-cell rendering discards an OBJECT-level `color` write once a table style
    * applies (essentially always -- see {@code VSFormatTableLens}'s "styled" gate), so `target:
    * "data"` writes directly to each body cell's own {@code TableDataPath} instead -- the same
    * per-path mechanism `target: "title"` already uses. HEADER-type cells (the header
    * row/column's own label text) are excluded -- only the body region is "data" -- and a
    * repeated path is written once, not once per cell.
    */
   @Test
   void targetDataRoutesThroughTheAssemblysComputedBodyCellPaths() throws Exception {
      FormatPainterService painter = mock(FormatPainterService.class);
      VSObjectFormatInfoModel format = new VSObjectFormatInfoModel();
      format.setColor("#D32F2F");

      TableDataPath headerPath = new TableDataPath(-1, TableDataPath.HEADER, XSchema.STRING,
         new String[]{ "state" });
      TableDataPath groupHeaderPath = new TableDataPath(-1, TableDataPath.GROUP_HEADER,
         XSchema.STRING, new String[]{ "state" });
      TableDataPath summaryPath = new TableDataPath(-1, TableDataPath.SUMMARY, XSchema.INTEGER,
         new String[]{ "Count(customer_id)" });

      RuntimeViewsheet rvs = crosstabRvs("Crosstab1", lens -> {
         when(lens.getColCount()).thenReturn(2);
         when(lens.moreRows(0)).thenReturn(true);
         when(lens.moreRows(1)).thenReturn(true);
         when(lens.moreRows(2)).thenReturn(false);

         TableDataDescriptor desc = lens.getDescriptor();
         // Header row (row 0): excluded from the result.
         when(desc.getCellDataPath(0, 0)).thenReturn(headerPath);
         when(desc.getCellDataPath(0, 1)).thenReturn(headerPath);
         // Body row (row 1): the row-dimension label cell and the aggregate cell.
         when(desc.getCellDataPath(1, 0)).thenReturn(groupHeaderPath);
         when(desc.getCellDataPath(1, 1)).thenReturn(summaryPath);
      });

      serviceWith(painter, rvs).setFormat(
         "tok", principal(),
         new ViewsheetFormatService.FormatRequest(
            List.of("Crosstab1"), format, false, "data"), "");

      ArgumentCaptor<FormatVSObjectEvent> captor =
         ArgumentCaptor.forClass(FormatVSObjectEvent.class);
      verify(painter).setFormat(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                anyString());
      ArrayList<TableDataPath[]> data = captor.getValue().getData();
      assertNotNull(data, "target:data must populate event.getData()");
      assertEquals(1, data.size());
      List<TableDataPath> paths = Arrays.asList(data.get(0));
      assertEquals(2, paths.size(), "the HEADER cell must be excluded: " + paths);
      assertTrue(paths.contains(groupHeaderPath), paths.toString());
      assertTrue(paths.contains(summaryPath), paths.toString());
      assertFalse(paths.contains(headerPath), paths.toString());
   }

   /** A path repeated across several rows/cells is written once, not once per occurrence. */
   @Test
   void targetDataDedupesARepeatedPathAcrossRows() throws Exception {
      FormatPainterService painter = mock(FormatPainterService.class);
      VSObjectFormatInfoModel format = new VSObjectFormatInfoModel();
      format.setColor("#D32F2F");

      TableDataPath detailPath = new TableDataPath(-1, TableDataPath.DETAIL, XSchema.STRING,
         new String[]{ "NAME" });

      RuntimeViewsheet rvs = tableRvs("Table1", lens -> {
         when(lens.getColCount()).thenReturn(1);
         when(lens.moreRows(0)).thenReturn(true);
         when(lens.moreRows(1)).thenReturn(true);
         when(lens.moreRows(2)).thenReturn(true);
         when(lens.moreRows(3)).thenReturn(false);

         TableDataDescriptor desc = lens.getDescriptor();
         // Same DETAIL path on every one of three data rows, as a plain Table's own descriptor
         // returns regardless of row (DefaultTableDataDescriptor.getCellDataPath).
         when(desc.getCellDataPath(anyInt(), eq(0))).thenReturn(detailPath);
      });

      serviceWith(painter, rvs).setFormat(
         "tok", principal(),
         new ViewsheetFormatService.FormatRequest(
            List.of("Table1"), format, false, "data"), "");

      ArgumentCaptor<FormatVSObjectEvent> captor =
         ArgumentCaptor.forClass(FormatVSObjectEvent.class);
      verify(painter).setFormat(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                anyString());
      TableDataPath[] paths = captor.getValue().getData().get(0);
      assertArrayEquals(new TableDataPath[]{ detailPath }, paths);
   }

   @Test
   void targetDataAlignsOnePathListPerAssembly() throws Exception {
      FormatPainterService painter = mock(FormatPainterService.class);
      VSObjectFormatInfoModel format = new VSObjectFormatInfoModel();

      TableDataPath path1 = new TableDataPath(-1, TableDataPath.DETAIL, XSchema.STRING,
         new String[]{ "A" });
      TableDataPath path2 = new TableDataPath(-1, TableDataPath.DETAIL, XSchema.STRING,
         new String[]{ "B" });

      Viewsheet viewsheet = mock(Viewsheet.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(viewsheet);

      TableVSAssembly table1 = mock(TableVSAssembly.class);
      ViewsheetSandbox box1 = mock(ViewsheetSandbox.class);
      VSTableLens lens1 = mock(VSTableLens.class);
      TableDataDescriptor desc1 = mock(TableDataDescriptor.class);
      when(viewsheet.getAssembly("Table1")).thenReturn(table1);
      when(lens1.getDescriptor()).thenReturn(desc1);
      when(lens1.getColCount()).thenReturn(1);
      when(lens1.moreRows(0)).thenReturn(true);
      when(lens1.moreRows(1)).thenReturn(false);
      when(desc1.getCellDataPath(0, 0)).thenReturn(path1);

      TableVSAssembly table2 = mock(TableVSAssembly.class);
      ViewsheetSandbox box2 = mock(ViewsheetSandbox.class);
      VSTableLens lens2 = mock(VSTableLens.class);
      TableDataDescriptor desc2 = mock(TableDataDescriptor.class);
      when(viewsheet.getAssembly("Table2")).thenReturn(table2);
      when(lens2.getDescriptor()).thenReturn(desc2);
      when(lens2.getColCount()).thenReturn(1);
      when(lens2.moreRows(0)).thenReturn(true);
      when(lens2.moreRows(1)).thenReturn(false);
      when(desc2.getCellDataPath(0, 0)).thenReturn(path2);

      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box1), Optional.of(box2));
      when(box1.getVSTableLens("Table1", false)).thenReturn(lens1);
      when(box2.getVSTableLens("Table2", false)).thenReturn(lens2);

      serviceWith(painter, rvs).setFormat(
         "tok", principal(),
         new ViewsheetFormatService.FormatRequest(
            List.of("Table1", "Table2"), format, false, "data"), "");

      ArgumentCaptor<FormatVSObjectEvent> captor =
         ArgumentCaptor.forClass(FormatVSObjectEvent.class);
      verify(painter).setFormat(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                anyString());
      ArrayList<TableDataPath[]> data = captor.getValue().getData();
      assertEquals(2, data.size(), "one TableDataPath[] per assembly, same order as objects[]");
      assertArrayEquals(new TableDataPath[]{ path1 }, data.get(0));
      assertArrayEquals(new TableDataPath[]{ path2 }, data.get(1));
   }

   @Test
   void targetDataRefusesAnAssemblyThatIsNeitherCrosstabNorTable() {
      Viewsheet viewsheet = mock(Viewsheet.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly("Text1")).thenReturn(mock(TextVSAssembly.class));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> serviceWith(mock(FormatPainterService.class), rvs).setFormat(
            "tok", principal(),
            new ViewsheetFormatService.FormatRequest(
               List.of("Text1"), new VSObjectFormatInfoModel(), false, "data"), ""));
      assertTrue(thrown.getMessage().contains("data"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("Text1"), thrown.getMessage());
   }

   /** No sandbox (e.g. an unloaded/disposed viewsheet) degrades to an empty path list. */
   @Test
   void targetDataIsEmptyWhenNoSandboxIsAvailable() throws Exception {
      FormatPainterService painter = mock(FormatPainterService.class);
      Viewsheet viewsheet = mock(Viewsheet.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly("Crosstab1")).thenReturn(mock(CrosstabVSAssembly.class));
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.empty());

      serviceWith(painter, rvs).setFormat(
         "tok", principal(),
         new ViewsheetFormatService.FormatRequest(
            List.of("Crosstab1"), new VSObjectFormatInfoModel(), false, "data"), "");

      ArgumentCaptor<FormatVSObjectEvent> captor =
         ArgumentCaptor.forClass(FormatVSObjectEvent.class);
      verify(painter).setFormat(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                anyString());
      assertArrayEquals(new TableDataPath[0], captor.getValue().getData().get(0));
   }

   // ── set_calc_cell_format / get_calc_cell_format (bug 76679) ────────────────────────────

   /**
    * The write side routes through {@code event.getData()} with the cell's own
    * {@code TableDataPath} -- the same per-path mechanism {@code target: "title"} already uses,
    * just with a computed cell path ({@code CalcTableService.cellFormatPath}) instead of
    * {@code VSAssemblyInfo.TITLEPATH}.
    */
   @Test
   void setCellFormatAppliesAtTheCellsOwnDataPath() throws Exception {
      FormatPainterService painter = mock(FormatPainterService.class);
      CalcTableService calcService = mock(CalcTableService.class);
      TableDataPath cellPath = new TableDataPath(-1, TableDataPath.DETAIL,
         inetsoft.uql.schema.XSchema.STRING, new String[]{ "Cell [1,0]" });
      when(calcService.cellFormatPath(any(), eq("FreehandTable1"), eq(1), eq(0)))
         .thenReturn(cellPath);
      VSObjectFormatInfoModel format = new VSObjectFormatInfoModel();
      format.setFormat("PercentFormat");

      serviceWith(painter, calcService).setCellFormat(
         "tok", principal(),
         new ViewsheetFormatService.CellFormatRequest("FreehandTable1", 1, 0, format, false), "");

      ArgumentCaptor<FormatVSObjectEvent> captor =
         ArgumentCaptor.forClass(FormatVSObjectEvent.class);
      verify(painter).setFormat(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                anyString());
      assertArrayEquals(new String[]{ "FreehandTable1" }, captor.getValue().getObjects());
      ArrayList<TableDataPath[]> data = captor.getValue().getData();
      assertNotNull(data, "the cell's own path must be set, not a whole-object write");
      assertEquals(1, data.size());
      assertArrayEquals(new TableDataPath[]{ cellPath }, data.get(0));
      assertEquals("PercentFormat", captor.getValue().getFormat().getFormat());
   }

   @Test
   void setCellFormatRequiresAssembly() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> serviceWith(mock(FormatPainterService.class)).setCellFormat(
            "tok", principal(),
            new ViewsheetFormatService.CellFormatRequest(
               null, 0, 0, new VSObjectFormatInfoModel(), false), ""));
      assertTrue(thrown.getMessage().contains("assembly"), thrown.getMessage());
   }

   @Test
   void setCellFormatRequiresRowAndCol() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> serviceWith(mock(FormatPainterService.class)).setCellFormat(
            "tok", principal(),
            new ViewsheetFormatService.CellFormatRequest(
               "FreehandTable1", null, 0, new VSObjectFormatInfoModel(), false), ""));
      assertTrue(thrown.getMessage().contains("row"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("col"), thrown.getMessage());
   }

   @Test
   void setCellFormatRequiresAFormatUnlessResetting() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> serviceWith(mock(FormatPainterService.class)).setCellFormat(
            "tok", principal(),
            new ViewsheetFormatService.CellFormatRequest(
               "FreehandTable1", 0, 0, null, false), ""));
      assertTrue(thrown.getMessage().contains("format"), thrown.getMessage());
   }

   @Test
   void setCellFormatResetNeedsNoFormat() throws Exception {
      FormatPainterService painter = mock(FormatPainterService.class);
      CalcTableService calcService = mock(CalcTableService.class);
      when(calcService.cellFormatPath(any(), anyString(), anyInt(), anyInt()))
         .thenReturn(new TableDataPath(-1, TableDataPath.DETAIL));

      serviceWith(painter, calcService).setCellFormat(
         "tok", principal(),
         new ViewsheetFormatService.CellFormatRequest("FreehandTable1", 0, 0, null, true), "");

      ArgumentCaptor<FormatVSObjectEvent> captor =
         ArgumentCaptor.forClass(FormatVSObjectEvent.class);
      verify(painter).setFormat(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                anyString());
      assertTrue(captor.getValue().isReset());
   }

   /**
    * The read side of {@link ViewsheetFormatService#setCellFormat} -- resolves the identical
    * cell path and reads it back through {@code FormatPainterService.getFormat}, which answers
    * by dispatching a {@code SetCurrentFormatCommand} rather than returning a value (the same
    * shape {@code get_calc_cell_script} already reads {@code GetCellScriptCommand} out of).
    */
   @Test
   void getCellFormatReadsBackTheModelAtTheCellsDataPath() throws Exception {
      FormatPainterService painter = mock(FormatPainterService.class);
      CalcTableService calcService = mock(CalcTableService.class);
      TableDataPath cellPath = new TableDataPath(-1, TableDataPath.DETAIL,
         inetsoft.uql.schema.XSchema.STRING, new String[]{ "Cell [0,0]" });
      when(calcService.cellFormatPath(any(), eq("FreehandTable1"), eq(0), eq(0)))
         .thenReturn(cellPath);

      VSObjectFormatInfoModel model = new VSObjectFormatInfoModel();
      model.setFormat("PercentFormat");
      model.setColor("#333333");

      doAnswer(invocation -> {
         CapturingCommandDispatcher dispatcher = invocation.getArgument(3);
         dispatcher.sendCommand("FreehandTable1", new SetCurrentFormatCommand(model));
         return null;
      }).when(painter).getFormat(eq("rt1"), any(GetVSObjectFormatEvent.class),
                                 any(Principal.class), any());

      Map<String, Object> read =
         serviceWith(painter, calcService).getCellFormat("tok", principal(), "FreehandTable1", 0, 0);

      assertEquals("FreehandTable1", read.get("assembly"));
      assertEquals(0, read.get("row"));
      assertEquals(0, read.get("col"));
      @SuppressWarnings("unchecked")
      Map<String, Object> format = (Map<String, Object>) read.get("format");
      assertEquals("PercentFormat", format.get("format"));
      assertEquals("#333333", format.get("color"));
   }

   /** A6/A3's "no override" baseline: no format captured means the cell has none of its own. */
   @Test
   void getCellFormatReturnsNullFormatWhenTheCellHasNoOverride() throws Exception {
      FormatPainterService painter = mock(FormatPainterService.class);
      CalcTableService calcService = mock(CalcTableService.class);
      when(calcService.cellFormatPath(any(), anyString(), anyInt(), anyInt()))
         .thenReturn(new TableDataPath(-1, TableDataPath.DETAIL));

      Map<String, Object> read =
         serviceWith(painter, calcService).getCellFormat("tok", principal(), "FreehandTable1", 1, 0);

      assertNull(read.get("format"));
   }

   @Test
   void getCellFormatRequiresAssembly() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> serviceWith(mock(FormatPainterService.class)).getCellFormat(
            "tok", principal(), null, 0, 0));
      assertTrue(thrown.getMessage().contains("assembly"), thrown.getMessage());
   }

   private static ViewsheetFormatService serviceWith(FormatPainterService painter) {
      return serviceWith(painter, mock(CalcTableService.class));
   }

   /** A real (mocked) {@code RuntimeViewsheet} in place of {@code null} -- for target:"data",
    *  which needs to resolve the named assembly and its live table lens. */
   private static ViewsheetFormatService serviceWith(FormatPainterService painter,
                                                      RuntimeViewsheet rvs)
   {
      return serviceWith(painter, mock(CalcTableService.class), rvs);
   }

   /** A Crosstab assembly whose {@code VSTableLens}/descriptor {@code configure} sets up. */
   private static RuntimeViewsheet crosstabRvs(String name,
                                               java.util.function.Consumer<VSTableLens> configure)
      throws Exception
   {
      return dataAssemblyRvs(name, mock(CrosstabVSAssembly.class), configure);
   }

   /** A plain Table assembly whose {@code VSTableLens}/descriptor {@code configure} sets up. */
   private static RuntimeViewsheet tableRvs(String name,
                                            java.util.function.Consumer<VSTableLens> configure)
      throws Exception
   {
      return dataAssemblyRvs(name, mock(TableVSAssembly.class), configure);
   }

   private static RuntimeViewsheet dataAssemblyRvs(
      String name, inetsoft.uql.viewsheet.VSAssembly assembly,
      java.util.function.Consumer<VSTableLens> configure)
      throws Exception
   {
      Viewsheet viewsheet = mock(Viewsheet.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      VSTableLens lens = mock(VSTableLens.class);
      TableDataDescriptor desc = mock(TableDataDescriptor.class);

      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(name)).thenReturn(assembly);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      when(box.getVSTableLens(name, false)).thenReturn(lens);
      when(lens.getDescriptor()).thenReturn(desc);

      configure.accept(lens);

      return rvs;
   }

   private static ViewsheetFormatService serviceWith(FormatPainterService painter,
                                                      CalcTableService calcService)
   {
      return serviceWith(painter, calcService, null);
   }

   private static ViewsheetFormatService serviceWith(FormatPainterService painter,
                                                      CalcTableService calcService,
                                                      RuntimeViewsheet rvs)
   {
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);

      try {
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());

         // A real CapturingCommandDispatcher, not a bare null: getCellFormat answers by
         // dispatching a SetCurrentFormatCommand (see FormatPainterService.getFormat), which
         // needs somewhere real to land, the same reason CalcTableService.cellScript's own tests
         // use one for GetCellScriptCommand.
         doAnswer(invocation -> {
            ViewsheetSessionService.Read<?> read = invocation.getArgument(2);
            return CapturingCommandDispatcher.withCapturingDispatcher(
               principal(), dispatcher -> read.run(null, "rt1", dispatcher));
         }).when(sessions).read(anyString(), any(Principal.class), any());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return new ViewsheetFormatService(sessions, painter, calcService);
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
