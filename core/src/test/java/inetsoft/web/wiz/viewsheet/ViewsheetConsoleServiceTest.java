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

import inetsoft.util.Tool;
import inetsoft.web.composer.ConsoleDialogMessageServiceProxy;
import inetsoft.web.composer.ws.event.SaveConsoleMessageLevelsEvent;
import inetsoft.web.wiz.dispatch.ConsoleMessageCapture;
import inetsoft.web.wiz.pairing.PairingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Bug #82316: the read / clear / set-levels behaviour behind the three wiz console routes. */
@Tag("core")
class ViewsheetConsoleServiceTest {
   private final String runtimeId = "rt-" + UUID.randomUUID();
   private final Principal agent = () -> "agent";
   private ViewsheetSessionService sessions;
   private ConsoleDialogMessageServiceProxy levelService;
   private ViewsheetConsoleService service;

   @BeforeEach
   void setUp() throws Exception {
      sessions = mock(ViewsheetSessionService.class);
      when(sessions.runtimeId("tok", agent)).thenReturn(runtimeId);
      levelService = mock(ConsoleDialogMessageServiceProxy.class);
      when(levelService.getConsoleMessageLevels(anyString(), any()))
         .thenReturn(new String[] { "Error", "Warning", "Info" });
      service = new ViewsheetConsoleService(sessions, levelService);

      var buffer = ConsoleMessageCapture.buffer(runtimeId);
      buffer.add("INFO", "Script execution error in assembly: B5Chart\nScript failed:\nboom",
                 "B5Chart");
      buffer.add("ERROR", "an alert error", null);
      buffer.add("WARNING", "a warning", null);
   }

   @SuppressWarnings("unchecked")
   private static List<Map<String, Object>> messages(Map<String, Object> result) {
      return (List<Map<String, Object>>) result.get("messages");
   }

   @Test
   void readReturnsMessagesCountsAndTheSavedLevels() throws Exception {
      Map<String, Object> result = service.read("tok", agent, null, null, null);

      assertEquals(3, messages(result).size());
      assertEquals(List.of("Error", "Warning", "Info"), result.get("levels"));
      assertEquals(Map.of("error", 1, "warning", 1, "info", 1, "total", 3), result.get("counts"));
      assertEquals("INFO", messages(result).get(0).get("type"));
      assertEquals("B5Chart", messages(result).get(0).get("assembly"));
   }

   /** The dialog hides rows outside the saved levels but its counters still count them. */
   @Test
   void theSavedLevelsFilterTheListButNotTheCounters() throws Exception {
      when(levelService.getConsoleMessageLevels(anyString(), any()))
         .thenReturn(new String[] { "Error" });

      Map<String, Object> result = service.read("tok", agent, null, null, null);

      assertEquals(1, messages(result).size());
      assertEquals("ERROR", messages(result).get(0).get("type"));
      assertEquals(3, ((Map<?, ?>) result.get("counts")).get("total"));
      assertEquals(List.of("Error"), result.get("levels"));
   }

   @Test
   void aLevelParameterOverridesTheSavedLevelsCaseInsensitively() throws Exception {
      Map<String, Object> result =
         service.read("tok", agent, List.of("INFO, warning"), null, null);

      assertEquals(2, messages(result).size());
      assertEquals(List.of("Error", "Warning", "Info"), result.get("levels"), "saved is unchanged");
   }

   @Test
   void limitAndSinceSeqSelectTheLatestAndTheNew() throws Exception {
      assertEquals(1, messages(service.read("tok", agent, null, 1, null)).size());
      assertEquals("a warning", messages(service.read("tok", agent, null, 1, null)).get(0).get("message"));
      assertEquals(1, messages(service.read("tok", agent, null, null, 2L)).size());
   }

   @Test
   void badReadArgumentsFailLoudly() {
      assertThrows(PairingException.class, () -> service.read("tok", agent, List.of("fatal"), null, null));
      assertThrows(PairingException.class, () -> service.read("tok", agent, null, 0, null));
      assertThrows(PairingException.class, () -> service.read("tok", agent, null, 201, null));
      assertThrows(PairingException.class, () -> service.read("tok", agent, null, null, -1L));
   }

   @Test
   void aSheetSavedWithNoLevelReadsAsAnEmptyList() throws Exception {
      when(levelService.getConsoleMessageLevels(anyString(), any()))
         .thenReturn(new String[] { "" });

      Map<String, Object> result = service.read("tok", agent, null, null, null);

      assertEquals(List.of(), result.get("levels"));
      assertEquals(0, messages(result).size());
   }

   @Test
   void clearEmptiesTheBufferAndReportsHowManyWereRemoved() throws Exception {
      assertEquals(Map.of("cleared", 3), service.clear("tok", agent));

      Map<String, Object> result = service.read("tok", agent, null, null, null);
      assertEquals(0, messages(result).size());
      assertEquals(0, ((Map<?, ?>) result.get("counts")).get("total"));
   }

   @Test
   void setLevelsSavesTheCanonicalListThroughTheComposerService() throws Exception {
      when(levelService.saveConsoleMessageLevels(anyString(), any(), any())).thenReturn(true);

      Map<String, Object> result = service.setLevels("tok", agent, List.of("info", " ERROR ", "info"));

      assertEquals(Map.of("levels", List.of("Error", "Info")), result);
      ArgumentCaptor<SaveConsoleMessageLevelsEvent> event =
         ArgumentCaptor.forClass(SaveConsoleMessageLevelsEvent.class);
      verify(levelService).saveConsoleMessageLevels(eq(Tool.byteEncode(runtimeId)), event.capture(),
                                                    eq(agent));
      assertArrayEquals(new String[] { "Error", "Info" }, event.getValue().getMessageLevels());
   }

   @Test
   void setLevelsAcceptsAnEmptyList() throws Exception {
      when(levelService.saveConsoleMessageLevels(anyString(), any(), any())).thenReturn(true);

      assertEquals(Map.of("levels", List.of()), service.setLevels("tok", agent, List.of()));
   }

   @Test
   void badLevelsAreRefusedAndNothingIsSaved() {
      assertThrows(PairingException.class, () -> service.setLevels("tok", agent, null));
      assertThrows(PairingException.class, () -> service.setLevels("tok", agent, "error"));
      assertThrows(PairingException.class, () -> service.setLevels("tok", agent, List.of(1)));
      PairingException unknown = assertThrows(PairingException.class,
         () -> service.setLevels("tok", agent, List.of("error", "debug")));
      assertTrue(unknown.getMessage().contains("debug"), unknown.getMessage());

      verify(levelService, never()).saveConsoleMessageLevels(anyString(), any(), any());
   }

   @Test
   void aSheetWithNoInfoReportsAnInternalError() {
      when(levelService.saveConsoleMessageLevels(anyString(), any(), any())).thenReturn(false);

      PairingException e = assertThrows(PairingException.class,
         () -> service.setLevels("tok", agent, List.of("error")));
      assertEquals(PairingException.Kind.INTERNAL, e.getKind());
   }
}
