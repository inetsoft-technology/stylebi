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
package inetsoft.util.dep;

import inetsoft.sree.security.OrganizationManager;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77850: the deployment export of a viewsheet wrote the entry alias raw into CDATA, so
 * an alias holding ]]> made the exported viewsheet unreadable on import.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class ViewsheetAssetAliasCdataTest {
   @Test
   void exportedAliasHoldingCdataEndIsReadBack() throws Exception {
      String alias = "Q1]]>Q2 ]]>";
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        "vs77850", null,
                                        OrganizationManager.getInstance().getCurrentOrgID());
      entry.setAlias(alias);
      ViewsheetAsset asset = new ViewsheetAsset(entry);

      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      // the writer AbstractSheetAsset.writeContent uses for the export JAR entry
      asset.writeContent0(new Viewsheet(), writer);
      writer.flush();

      // the read AbstractSheetAsset.parseContent makes
      Document doc = Tool.parseXML(
         new ByteArrayInputStream(buf.toString().getBytes(StandardCharsets.UTF_8)));
      assertEquals(alias, Tool.getChildValueByTagName(doc.getDocumentElement(), "entryAlias"));

      AssetEntry imported = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                           AssetEntry.Type.VIEWSHEET, "vs77850", null,
                                           entry.getOrgID());
      new ViewsheetAsset(imported).parseContent0(doc.getDocumentElement());
      assertEquals(alias, imported.getAlias());
   }
}
