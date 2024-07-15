/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util.dep;

import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Resource;
import inetsoft.util.Tool;
import inetsoft.util.TransformerManager;
import org.owasp.encoder.Encode;
import org.w3c.dom.*;

import java.io.*;
import java.util.Vector;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;


/**
 * DataCycleAsset represents a data cycle type asset.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class DataCycleAsset extends AbstractXAsset {
   /**
    * Data cycle type XAsset.
    */
   public static final String DATACYCLE = "DATACYCLE";

   /**
    * Constructor.
    */
   public DataCycleAsset() {
      super();
   }

   /**
    * Constructor.
    * @param cycleId the data cycle asset name and orgId.
    */
   public DataCycleAsset(DataCycleManager.DataCycleId cycleId) {
      this();
      this.cycle = cycleId.name();
      this.orgId = cycleId.orgId();
   }

   /**
    * Get all dependencies of this asset.
    * @return an array of XAssetDependency.
    */
   @Override
   public XAssetDependency[] getDependencies() {
      return new XAssetDependency[0];
   }

   /**
    * Get the path of this asset.
    * @return the path of this asset.
    */
   @Override
   public String getPath() {
      return cycle;
   }

   /**
    * Get the type of this asset.
    * @return the type of this asset.
    */
   @Override
   public String getType() {
      return DATACYCLE;
   }

   /**
    * Get the owner of this asset if any.
    *
    * @return the owner of this asset if any.
    */
   @Override
   public IdentityID getUser() {
      return null;
   }

   /**
    * Parse an identifier to a real asset.
    * @param identifier the specified identifier, usually with the format of
    * ClassName^path.
    */
   @Override
   public void parseIdentifier(String identifier) {
      int idx = identifier.indexOf("^");
      String className = identifier.substring(0, idx);

      if(!className.equals(getClass().getName())) {
         return;
      }

      cycle = identifier.substring(idx + 1);
   }

   /**
    * Create an asset by its path and owner if any.
    *
    * @param path         the specified asset path.
    * @param userIdentity the specified asset owner if any.
    */
   @Override
   public void parseIdentifier(String path, IdentityID userIdentity) {
      this.cycle = path;
   }

   /**
    * Convert this asset to an identifier.
    * @return an identifier.
    */
   @Override
   public String toIdentifier() {
      return getClass().getName() + "^" + cycle;
   }

   /**
    * Parse content of the specified asset from input stream.
    */
   @Override
   public synchronized void parseContent(InputStream input, XAssetConfig config, boolean isImport)
      throws Exception
   {
      if(cycle == null) {
         return;
      }

      DataCycleManager manager = DataCycleManager.getDataCycleManager();
      boolean overwriting = config != null && config.isOverwriting();

      if(manager.getConditions(cycle, orgId) != null && !overwriting) {
         return;
      }

      Document doc = Tool.parseXML(input);

      if(doc == null) {
         return;
      }

      TransformerManager transf =
         TransformerManager.getManager(TransformerManager.SCHEDULE);
      transf.transform(doc);

      Element elem = (Element) doc.getElementsByTagName("DataCycle").item(0);
      NodeList cnodes = Tool.getChildNodesByTagName(elem, "Condition");

      if(cnodes.getLength() == 0) {
         return;
      }

      Vector conds = new Vector();

      for(int i = 0; i < cnodes.getLength(); i++) {
         Element cond = (Element) cnodes.item(i);

         if(Tool.equals("TimeCondition", cond.getAttribute("type"))) {
            TimeCondition tc = new TimeCondition();
            tc.parseXML(cond);
            conds.add(tc);
         }
         else {
            return;
         }
      }

      manager.setConditions(cycle, orgId, conds);

      Element cinfo = Tool.getChildNodeByTagName(elem, "CycleInfo");

      if(cinfo != null) {
         DataCycleManager.CycleInfo cycleInfo = new DataCycleManager.CycleInfo();
         cycleInfo.parseXML(cinfo);
         cycleInfo.setName(cycle);
         cycleInfo.setName(orgId);
         manager.setCycleInfo(cycle, orgId, cycleInfo);
      }


      manager.save();
   }

   /**
    * Write content of the specified asset to an output stream.
    */
   @Override
   public synchronized boolean writeContent(OutputStream output) throws Exception {
      DataCycleManager manager = DataCycleManager.getDataCycleManager();

      int count = manager.getConditionCount(cycle, orgId);

      if(count == 0) {
         return false;
      }

      JarOutputStream out = getJarOutputStream(output);
      ZipEntry zipEntry = new ZipEntry(getType() + "_" + replaceFilePath(toIdentifier()));
      out.putNextEntry(zipEntry);
      PrintWriter writer = new PrintWriter(out);
      String encodedCycle = Encode.forXmlAttribute(cycle);
      writer.println("<DataCycle name=\"" + encodedCycle + "\">");

      for(int i = 0; i < count; i++) {
         ScheduleCondition cond = manager.getCondition(cycle, orgId, i);

         if(cond instanceof TimeCondition) {
            ((TimeCondition) cond).writeXML(writer);
         }
      }

      DataCycleManager.CycleInfo cycleInfo = manager.getCycleInfo(cycle, orgId);

      if(cycleInfo != null) {
         cycleInfo.writeXML(writer);
      }

      writer.println("</DataCycle>");
      writer.flush();
      return true;
   }

   @Override
   public boolean exists() {
      return DataCycleManager.getDataCycleManager().getConditions(cycle, orgId) != null;
   }

   @Override
   public long getLastModifiedTime() {
      if(lastModifiedTime != 0) {
         return lastModifiedTime;
      }

      DataCycleManager.CycleInfo cycleInfo = DataCycleManager.getDataCycleManager().getCycleInfo(cycle, orgId);

      return cycleInfo == null ? 0 : cycleInfo.getLastModified();
   }

   @Override
   public Resource getSecurityResource() {
      return null;
   }

   private String cycle;
   private String orgId;
}
