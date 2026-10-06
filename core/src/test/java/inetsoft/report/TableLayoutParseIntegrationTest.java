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
package inetsoft.report;

import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.IndexedStorage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.PrintWriter;
import java.io.StringWriter;

import static inetsoft.report.TableLayoutParseTest.*;

/**
 * Bug #77792, #77794: a calc-table layout with a forged region rows value, read back through
 * the real indexed storage, fails with an ordinary Exception, not an Error or a hang. Kept
 * apart from {@link TableLayoutParseTest} because it needs the integration storage beans.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TableLayoutParseIntegrationTest {
   @Test
   void storageReadFailsFastOnForgedRows() throws Exception {
      Element elem = parse(productViewsheetXml());
      firstViewsheetRegion(elem).setAttribute("rows", "2147483647");
      String key = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                  "/tableLayoutParseForgedRows", null).toIdentifier();
      IndexedStorage storage = IndexedStorage.getIndexedStorage();
      storage.putXMLSerializable(key, new RawViewsheet(elementXml(elem)));

      // a parse that hangs keeps holding the blob's lock, so the key is removed only once
      // the read has returned, and before the result is checked
      Throwable thrown = captureThrowable(() -> storage.getXMLSerializable(key, null));
      storage.remove(key);
      assertFailedWith(thrown, "invalid region rows");
   }

   /**
    * Writes the given viewsheet XML as is, so a forged layout can be stored. Read back, the
    * storage instantiates it with the no-arg constructor and parses it as a real Viewsheet.
    */
   public static class RawViewsheet extends Viewsheet {
      public RawViewsheet() {
         this(null);
      }

      RawViewsheet(String xml) {
         this.xml = xml;
      }

      @Override
      public void writeXML(PrintWriter writer) {
         writer.print(xml);
      }

      private final String xml;
   }

   private static String elementXml(Element elem) throws Exception {
      Transformer transformer = TransformerFactory.newInstance().newTransformer();
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
      StringWriter buf = new StringWriter();
      transformer.transform(new DOMSource(elem), new StreamResult(buf));
      return buf.toString();
   }
}
