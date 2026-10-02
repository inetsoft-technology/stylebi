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
package inetsoft.web.composer.ws;

import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XLogicalModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77428 - the open worksheet check of a model bound table must read the permission of the
 * logical model under the QUERY resource it is stored with, using the folder of the stored model.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class OpenWorksheetControllerModelResourceTest {
   @BeforeEach
   void setUp() throws Exception {
      dataModel = mock(XDataModel.class);
      repository = mock(XRepository.class);
      when(repository.getDataModel("DS")).thenReturn(dataModel);
   }

   @Test
   void modelInFolder_usesStoredKey() throws Exception {
      storedModel("F");
      assertEquals("LM::DS^__^F",
                   OpenWorksheetController.getLogicalModelResource(repository, "DS", "LM"));
   }

   @Test
   void rootModel_usesStoredKey() throws Exception {
      storedModel(null);
      assertEquals("LM::DS",
                   OpenWorksheetController.getLogicalModelResource(repository, "DS", "LM"));
   }

   // a missing model keeps the old key, which inherits from the data source
   @Test
   void missingModel_usesRootKey() throws Exception {
      assertEquals("LM::DS",
                   OpenWorksheetController.getLogicalModelResource(repository, "DS", "LM"));
      assertEquals("LM::DS_X",
                   OpenWorksheetController.getLogicalModelResource(repository, "DS_X", "LM"));
   }

   private void storedModel(String folder) {
      XLogicalModel model = new XLogicalModel("LM");
      model.setFolder(folder);
      when(dataModel.getLogicalModel("LM")).thenReturn(model);
   }

   private XRepository repository;
   private XDataModel dataModel;
}
