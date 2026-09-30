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
package inetsoft.uql.asset.internal;

import inetsoft.sree.security.Resource;
import inetsoft.sree.security.ResourceType;
import inetsoft.test.*;
import inetsoft.util.dep.XLogicalModelAsset;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77428 - the deploy permission checks of a logical model must use the QUERY resource the
 * model permission is stored with ({@code model::ds} plus {@code ^__^folder} for a model in a
 * data model folder), so that the model and data model folder permissions are respected.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class LogicalModelSecurityResourceTest {
   @Test
   void parentSecurityResource_modelInFolder_usesStoredKey() {
      assertEquals(new Resource(ResourceType.QUERY, "LM::DS^__^F"),
                   AssetUtil.getParentSecurityResource("DS^__^F^LM", XLogicalModelAsset.XLOGICALMODEL));
   }

   @Test
   void parentSecurityResource_rootModel_usesStoredKey() {
      assertEquals(new Resource(ResourceType.QUERY, "LM::DS"),
                   AssetUtil.getParentSecurityResource("DS^LM", XLogicalModelAsset.XLOGICALMODEL));
   }

   // an extended model is governed by its base model
   @Test
   void parentSecurityResource_extendedModel_usesBaseModelKey() {
      assertEquals(new Resource(ResourceType.QUERY, "LM::DS^__^F"),
                   AssetUtil.getParentSecurityResource("DS^__^F^LM^Ext", XLogicalModelAsset.XLOGICALMODEL));
      assertEquals(new Resource(ResourceType.QUERY, "LM::DS"),
                   AssetUtil.getParentSecurityResource("DS^LM^Ext", XLogicalModelAsset.XLOGICALMODEL));
   }

   @Test
   void assetSecurityResource_modelInFolder_usesStoredKey() {
      assertEquals(new Resource(ResourceType.QUERY, "LM::DS^__^F"),
                   new XLogicalModelAsset("DS^__^F^LM").getSecurityResource());
   }

   @Test
   void assetSecurityResource_rootModel_usesStoredKey() {
      assertEquals(new Resource(ResourceType.QUERY, "LM::DS"),
                   new XLogicalModelAsset("DS^LM").getSecurityResource());
   }

   @Test
   void assetSecurityResource_extendedModel_usesBaseModelKey() {
      assertEquals(new Resource(ResourceType.QUERY, "LM::DS^__^F"),
                   new XLogicalModelAsset("DS^__^F^LM^Ext").getSecurityResource());
      assertEquals(new Resource(ResourceType.QUERY, "LM::DS"),
                   new XLogicalModelAsset("DS^LM^Ext").getSecurityResource());
   }
}
