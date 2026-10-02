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
package inetsoft.web.viewsheet.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.uql.viewsheet.SelectionListVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.util.CancelledException;
import inetsoft.web.viewsheet.command.MessageCommand;
import org.junit.jupiter.api.*;

import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * A selection whose query is cancelled by a newer request on the same viewsheet (rapid
 * selection toggling) must be dropped quietly instead of surfacing "Query cancelled" to the
 * user, while other failures still propagate.
 */
@Tag("core")
class VSSelectionServiceCancelTest {
   @BeforeEach
   void setup() {
      coreLifecycleService = mock(CoreLifecycleService.class);
      service = new VSSelectionService(coreLifecycleService, mock(ViewsheetService.class),
                                       mock(MaxModeAssemblyService.class),
                                       mock(SharedFilterService.class));

      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getScope()).thenReturn(mock(ViewsheetScope.class));

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));

      dispatcher = mock(CommandDispatcher.class);
      context = Context.builder()
         .rvs(rvs)
         .principal(mock(Principal.class))
         .dispatcher(dispatcher)
         .linkUri("")
         .build();

      assembly = mock(SelectionListVSAssembly.class);
      when(assembly.getName()).thenReturn("SelectionList1");
      when(assembly.getAbsoluteName()).thenReturn("SelectionList1");
   }

   @Test
   void supersededQueryIsDroppedQuietly() throws Exception {
      doThrow(new CancelledException("Query cancelled")).when(coreLifecycleService)
         .execute(any(), anyString(), any(), any(), any(), anyBoolean());

      assertDoesNotThrow(() -> service.executeSelection(assembly, VSAssembly.OUTPUT_DATA_CHANGED,
                                                        context, null));
      verify(dispatcher, never()).sendCommand(any(MessageCommand.class));
      verify(dispatcher, never()).sendCommand(any(), any(MessageCommand.class));
   }

   @Test
   void otherFailuresStillPropagate() throws Exception {
      doThrow(new IllegalStateException("boom")).when(coreLifecycleService)
         .execute(any(), anyString(), any(), any(), any(), anyBoolean());

      assertThrows(IllegalStateException.class,
                   () -> service.executeSelection(assembly, VSAssembly.OUTPUT_DATA_CHANGED,
                                                  context, null));
   }

   private CoreLifecycleService coreLifecycleService;
   private VSSelectionService service;
   private CommandDispatcher dispatcher;
   private Context context;
   private SelectionListVSAssembly assembly;
}
