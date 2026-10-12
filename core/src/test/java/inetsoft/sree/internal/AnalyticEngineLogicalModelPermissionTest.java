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
package inetsoft.sree.internal;

import inetsoft.report.LibManagerProvider;
import inetsoft.report.internal.DesignSession;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XLogicalModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77428 - {@link AnalyticEngine#getLogicalModel(String, Principal)} must check the READ
 * permission of the logical model under the QUERY resource it is stored with, which includes the
 * data model folder of the model.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AnalyticEngineLogicalModelPermissionTest {
   @BeforeEach
   void setUp() throws Exception {
      securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(Principal.class), eq(ResourceType.QUERY),
                                          anyString(), eq(ResourceAction.READ)))
         .thenAnswer(i -> grants.contains((String) i.getArgument(2)));
      securityMock = mockStatic(SecurityEngine.class);
      securityMock.when(SecurityEngine::getSecurity).thenReturn(securityEngine);

      dataModel = mock(XDataModel.class);
      XRepository repository = mock(XRepository.class);
      when(repository.getDataModel("DS")).thenReturn(dataModel);
      DesignSession session = mock(DesignSession.class);
      when(session.getDataService()).thenReturn(repository);
      engine = new AnalyticEngine(
         mock(DeployManagerService.class), session, mock(LibManagerProvider.class),
         mock(DataCycleManager.class), mock(Cluster.class), mock(RepletRegistryManager.class));
   }

   @AfterEach
   void tearDown() {
      securityMock.close();
   }

   @Test
   void modelInFolder_checksStoredKey() {
      XLogicalModel model = storedModel("F");

      grants.add("LM::DS");
      assertNull(engine.getLogicalModel("LM::DS", principal));

      grants.add("LM::DS^__^F");
      assertSame(model, engine.getLogicalModel("LM::DS", principal));
   }

   @Test
   void rootModel_checksStoredKey() {
      XLogicalModel model = storedModel(null);

      assertNull(engine.getLogicalModel("LM::DS", principal));

      grants.add("LM::DS");
      assertSame(model, engine.getLogicalModel("LM::DS", principal));
   }

   private XLogicalModel storedModel(String folder) {
      XLogicalModel model = new XLogicalModel("LM");
      model.setFolder(folder);
      when(dataModel.getLogicalModel("LM")).thenReturn(model);
      return model;
   }

   private final Principal principal = () -> "bob";
   private final Set<String> grants = new HashSet<>();
   private SecurityEngine securityEngine;
   private MockedStatic<SecurityEngine> securityMock;
   private XDataModel dataModel;
   private AnalyticEngine engine;
}
