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
package inetsoft.web.wiz.controller;

import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.XRepository;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XPartition;
import inetsoft.web.portal.controller.database.*;
import inetsoft.web.portal.model.database.*;
import inetsoft.web.portal.service.database.PhysicalGraphService;
import inetsoft.web.wiz.controller.WizPhysicalViewWire.AutoJoinResponse;
import inetsoft.web.wiz.controller.WizPhysicalViewWire.ProposedJoin;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * The session half of Auto Join: a stored view opens no runtime, a draft's runtime never outlives
 * the request, and the candidates of the service reach the assembly.
 */
@Tag("core")
class WizPhysicalViewAutoJoinSessionTest {
   private final Principal bob = () -> "bob";
   private final PhysicalModelManagerService manager = mock(PhysicalModelManagerService.class);
   private final PhysicalModelService modelService = mock(PhysicalModelService.class);
   private final RuntimePartitionService runtimes = mock(RuntimePartitionService.class);
   private final WizPhysicalViewSession session = new WizPhysicalViewSession(
      manager, modelService, runtimes, new PhysicalGraphService(), mock(DataSourceService.class),
      mock(XRepository.class), mock(SecurityEngine.class));

   @Test
   void aStoredViewIsReadWithoutOpeningARuntimeAndTheCandidatesBecomeJoins() throws Exception {
      XDataModel dataModel = mock(XDataModel.class);
      when(dataModel.getPartition("pv")).thenReturn(new XPartition("pv"));
      PhysicalModelDefinition definition = new PhysicalModelDefinition();
      definition.setName("pv");
      definition.setTables(List.of(table("ORDERS"), table("ORDER_DETAILS")));
      when(modelService.createModel(anyString(), any(XDataModel.class), any(XPartition.class)))
         .thenReturn(definition);
      AutoJoinColumnsModel candidates = new AutoJoinColumnsModel();
      AutoJoinNameColumnModel name = new AutoJoinNameColumnModel();
      name.setColumn("ORDER_ID");
      name.setTables(List.of("ORDER_DETAILS", "ORDERS"));
      candidates.setNameColumns(List.of(name));
      candidates.setMetaAvailable(false);
      when(manager.getAutoJoinColumns("db", definition, bob)).thenReturn(candidates);

      AutoJoinResponse response = session.autoJoin(
         "db", dataModel, null, "pv", null, null, null, bob);

      assertEquals(List.of(
         new ProposedJoin("ORDERS", "ORDER_ID", "ORDER_DETAILS", "ORDER_ID", "EQUAL")),
                   response.joins());
      verifyNoInteractions(runtimes);
   }

   @Test
   void aDraftRuntimeIsDestroyedEvenWhenBuildingTheDraftFails() throws Exception {
      XDataModel dataModel = new XDataModel("db");
      when(modelService.createPartition(any())).thenAnswer(
         i -> new XPartition(((PhysicalModelDefinition) i.getArgument(0)).getName()));
      when(runtimes.createModel(any(XPartition.class), anyString(), any())).thenAnswer(
         i -> new RuntimePartitionService.RuntimeXPartition(i.getArgument(0), "rid", "db"));
      // the runtime of the mocked services never holds the table: adding it fails
      when(runtimes.getPartition("rid")).thenReturn(null);
      Map<String, Object> draft = new LinkedHashMap<>();
      draft.put("name", "autojoin-draft");
      draft.put("tables", List.of(new LinkedHashMap<>(Map.of("name", "T1")),
                                  new LinkedHashMap<>(Map.of("name", "T2"))));

      assertThrows(Exception.class, () -> session.autoJoin(
         "db", dataModel, null, null, draft, null, null, bob));

      verify(manager).closeModel("rid");
      verify(manager, never()).getAutoJoinColumns(anyString(), any(), any());
   }

   private static PhysicalTableModel table(String name) {
      PhysicalTableModel table = new PhysicalTableModel();
      table.setName(name);
      table.setQualifiedName(name);
      table.setType(TableType.PHYSICAL);
      table.setJoins(new ArrayList<>());
      return table;
   }
}
