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
package inetsoft.mv;

import inetsoft.report.composition.WorksheetWrapper;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.storage.BlobStorage;
import inetsoft.storage.BlobTransaction;
import inetsoft.uql.asset.Worksheet;
import inetsoft.util.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class MVWorksheetStorage implements AutoCloseable {
   public MVWorksheetStorage() {
      transformListener = new SheetTransformListener();
   }

   public static MVWorksheetStorage getInstance() {
      return SingletonManager.getInstance(MVWorksheetStorage.class);
   }

   public Worksheet getWorksheet(String path, String orgID) throws Exception {
      return getWorksheet(getStorage(orgID), path);
   }

   public Worksheet getWorksheet(BlobStorage<Metadata> storage, String path) throws Exception {
      Worksheet worksheet = null;

      if(storage.exists(path)) {
         try(InputStream input = storage.getInputStream(path)) {
            Document doc = Tool.parseXML(input);
            transformListener.transform(doc, Worksheet.class.getName(), path, null);
            Element root = doc.getDocumentElement();
            worksheet = new WorksheetWrapper(null);
            worksheet.parseXML(root);
         }
      }

      return worksheet;
   }

   public void putWorksheet(String path, Worksheet worksheet, String orgID) throws IOException {
      putWorksheet(getStorage(orgID), path, worksheet);
   }

   public void putWorksheet(BlobStorage<Metadata> storage, String path, Worksheet worksheet) throws IOException {
      try(BlobTransaction<Metadata> tx = storage.beginTransaction();
          OutputStream output = tx.newStream(path, new Metadata()))
      {
         PrintWriter writer =
            new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
         worksheet.writeXML(writer);
         writer.flush();
         tx.commit();
      }
   }

   public void removeWorksheet(String path) throws IOException {
      getStorage().delete(path);
   }

   @Override
   public void close() throws Exception {
      getStorage().close();
   }

   private BlobStorage<Metadata> getStorage() {
      String storeID = OrganizationManager.getInstance().getCurrentOrgID().toLowerCase() + "__" + "mvws";
      return SingletonManager.getInstance(BlobStorage.class, storeID, false);
   }

   private BlobStorage<Metadata> getStorage(String orgID) {
      String storeID = orgID.toLowerCase() + "__" + "mvws";
      return SingletonManager.getInstance(BlobStorage.class, storeID, false);
   }

   private final TransformListener transformListener;

   public static final class Metadata implements Serializable {
   }
}
