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
import inetsoft.web.composer.model.vs.VSObjectFormatInfoModel;
import inetsoft.web.composer.vs.controller.FormatPainterService;
import inetsoft.web.composer.vs.objects.event.FormatVSObjectEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.List;

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

   private static ViewsheetFormatService serviceWith(FormatPainterService painter) {
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);

      try {
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(null, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return new ViewsheetFormatService(sessions, painter);
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
