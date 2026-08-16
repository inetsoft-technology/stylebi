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

import inetsoft.report.Hyperlink;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.composer.model.vs.HyperlinkDialogModel;
import inetsoft.web.composer.vs.dialog.HyperlinkDialogService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class AssemblyHyperlinkServiceTest {
   private static Map<String, Object> link(Object... pairs) {
      Map<String, Object> link = new LinkedHashMap<>();

      for(int i = 0; i < pairs.length; i += 2) {
         link.put((String) pairs[i], pairs[i + 1]);
      }

      return link;
   }

   @Test
   void setsAWebLink() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());

      h.service.set("tok", principal(), "Chart1", null,
                    link("linkType", "web", "webLink", "https://example.com"), "");

      HyperlinkDialogModel posted = capture(h.links);
      assertEquals(Hyperlink.WEB_LINK, posted.getLinkType());
      assertEquals("https://example.com", posted.getWebLink());
   }

   @Test
   void setsAViewsheetLink() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());

      h.service.set("tok", principal(), "Chart1", null,
                    link("linkType", "viewsheet", "assetLinkPath", "Reports/Detail"), "");

      assertEquals(Hyperlink.VIEWSHEET_LINK, capture(h.links).getLinkType());
   }

   @Test
   void eachWriteIsExactlyOneCheckpoint() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());

      h.service.set("tok", principal(), "Chart1", null,
                    link("linkType", "web", "webLink", "https://example.com"), "");

      verify(h.sessions, times(1)).mutate(anyString(), any(Principal.class), any());
   }

   /** Clearing is its own type rather than an empty value, so intent is never inferred. */
   @Test
   void clearingALinkNeedsNoDestination() throws Exception {
      HyperlinkDialogModel model = new HyperlinkDialogModel();
      model.setWebLink("https://old.example.com");
      Harness h = harness(model);

      h.service.set("tok", principal(), "Chart1", null, link("linkType", "none"), "");

      HyperlinkDialogModel posted = capture(h.links);
      assertEquals(0, posted.getLinkType());
      assertNull(posted.getWebLink(), "clearing the type must clear the destination with it");
   }

   /**
    * A link with a type but no matching destination is accepted by the dialog and then does
    * nothing when clicked, which reads as a broken report rather than a bad call.
    */
   @Test
   void refusesAWebLinkWithNoUrl() {
      Harness h = harness(new HyperlinkDialogModel());

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.set("tok", principal(), "Chart1", null, link("linkType", "web"), ""));

      assertTrue(thrown.getMessage().contains("webLink"));
   }

   @Test
   void refusesAViewsheetLinkWithNoPath() {
      Harness h = harness(new HyperlinkDialogModel());

      assertThrows(Exception.class,
                   () -> h.service.set("tok", principal(), "Chart1", null,
                                       link("linkType", "viewsheet"), ""));
   }

   @Test
   void validatesBeforeTouchingTheRuntime() {
      Harness h = harness(new HyperlinkDialogModel());

      assertThrows(Exception.class,
                   () -> h.service.set("tok", principal(), "Chart1", null,
                                       link("linkType", "web"), ""));

      verifyNoInteractions(h.sessions);
   }

   @Test
   void refusesAnIntegerLinkTypeListingTheTokens() {
      Harness h = harness(new HyperlinkDialogModel());

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.set("tok", principal(), "Chart1", null,
                             link("linkType", 1, "webLink", "https://x"), ""));

      assertTrue(thrown.getMessage().contains("web"));
   }

   @Test
   void refusesAMissingLinkType() {
      Harness h = harness(new HyperlinkDialogModel());

      assertThrows(Exception.class,
                   () -> h.service.set("tok", principal(), "Chart1", null,
                                       link("webLink", "https://x"), ""));
   }

   // ── region addressing ─────────────────────────────────────────────────────

   /**
    * Addressing the whole assembly means row/col <b>0</b>, not null.
    *
    * <p>{@code HyperlinkDialogService.getHyperlinkDialogModel} dereferences row as an int
    * (via {@code getFields}), so nulls threw
    * {@code NullPointerException: Cannot invoke "java.lang.Integer.intValue()" because "row" is
    * null} for every assembly type — set_hyperlink was unusable at assembly level. The Composer
    * never sends null: its controller declares
    * {@code @RequestParam(value = "row", required = false, defaultValue = "0")}.
    *
    * <p>This test previously asserted {@code isNull(), isNull()} and so certified the crash.
    */
   @Test
   void addressesTheWholeAssemblyWithZerosBecauseNullRowNPEs() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());

      h.service.read("tok", principal(), "Chart1", null);

      verify(h.links).getHyperlinkDialogModel(eq("rt1"), eq("Chart1"), eq(0), eq(0),
                                              isNull(), eq(false), eq(false), eq(false),
                                              eq(false), any(Principal.class));
   }

   /**
    * The controller builds a Region straight from its nullable {@code @RequestParam}s rather than
    * calling {@code Region.whole()}, so normalizing only in the factory left the live path still
    * passing nulls — and still NPEing. The record itself must normalize.
    */
   @Test
   void aRegionBuiltDirectlyWithNullsStillAddressesRowAndColZero() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());

      h.service.read("tok", principal(), "Chart1",
                     new AssemblyHyperlinkService.Region(null, null, null, false, false, false,
                                                         false));

      verify(h.links).getHyperlinkDialogModel(eq("rt1"), eq("Chart1"), eq(0), eq(0),
                                              isNull(), eq(false), eq(false), eq(false),
                                              eq(false), any(Principal.class));
   }

   @Test
   void passesARegionThrough() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());

      h.service.read("tok", principal(), "Table1",
                     new AssemblyHyperlinkService.Region(2, 1, "Sales", false, false, false,
                                                         false));

      verify(h.links).getHyperlinkDialogModel(eq("rt1"), eq("Table1"), eq(2), eq(1),
                                              eq("Sales"), eq(false), eq(false), eq(false),
                                              eq(false), any(Principal.class));
   }

   @Test
   void addressesATitleLink() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());

      h.service.read("tok", principal(), "Chart1",
                     new AssemblyHyperlinkService.Region(null, null, null, false, false, true,
                                                         false));

      // Row/col normalize to 0 here too: a title link addresses the assembly, and the dialog
      // service dereferences them as ints whatever the region flags say. This previously
      // asserted isNull(), isNull() — the values that NPE live.
      verify(h.links).getHyperlinkDialogModel(anyString(), anyString(), eq(0), eq(0),
                                              isNull(), eq(false), eq(false), eq(true),
                                              eq(false), any(Principal.class));
   }

   // ── the read direction ────────────────────────────────────────────────────

   @Test
   void readsBackATokenNeverAnInteger() throws Exception {
      HyperlinkDialogModel model = new HyperlinkDialogModel();
      model.setLinkType(Hyperlink.WEB_LINK);
      model.setWebLink("https://example.com");

      Map<String, Object> read = harness(model).service.read("tok", principal(), "Chart1", null);

      assertEquals("web", read.get("linkType"));
      assertEquals("https://example.com", read.get("webLink"));
   }

   @Test
   void reportsAnUnrecognizedConstantAsItself() throws Exception {
      HyperlinkDialogModel model = new HyperlinkDialogModel();
      model.setLinkType(99);

      Map<String, Object> read = harness(model).service.read("tok", principal(), "Chart1", null);

      assertEquals("unknown(99)", read.get("linkType"));
   }

   @Test
   void listsTheLinkTypesForDiscovery() {
      Map<String, Object> types = harness(new HyperlinkDialogModel()).service.linkTypes();

      assertTrue(String.valueOf(types.get("linkTypes")).contains("viewsheet"));
   }

   // ── harness ───────────────────────────────────────────────────────────────

   private record Harness(AssemblyHyperlinkService service, ViewsheetSessionService sessions,
                          HyperlinkDialogService links) {}

   private static HyperlinkDialogModel capture(HyperlinkDialogService links) throws Exception {
      ArgumentCaptor<HyperlinkDialogModel> captor =
         ArgumentCaptor.forClass(HyperlinkDialogModel.class);
      verify(links).setHyperlinkDialogModel(eq("rt1"), anyString(), captor.capture(),
                                            anyString(), any(Principal.class), any());
      return captor.getValue();
   }

   private static Harness harness(HyperlinkDialogModel model) {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(mock(Viewsheet.class));
      when(rvs.getID()).thenReturn("rt1");

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      HyperlinkDialogService links = mock(HyperlinkDialogService.class);

      try {
         when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
         when(links.getHyperlinkDialogModel(anyString(), anyString(), any(), any(), any(),
                                            anyBoolean(), anyBoolean(), anyBoolean(),
                                            anyBoolean(), any(Principal.class)))
            .thenReturn(model);
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return new Harness(new AssemblyHyperlinkService(sessions, links), sessions, links);
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
