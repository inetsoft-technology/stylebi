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
