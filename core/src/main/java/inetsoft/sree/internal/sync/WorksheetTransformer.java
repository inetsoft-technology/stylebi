
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
package inetsoft.sree.internal.sync;

import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.sync.UpdateDependencyHandler;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.util.Tool;
import org.apache.commons.lang.StringUtils;
import org.w3c.dom.*;

import java.io.File;

/**
 * Tranform to change QueryBoundTableAssembly to SQLBoundTableAssembly.
 */
public class WorksheetTransformer implements ConvertTransformer {
   public WorksheetTransformer() {
      super();
   }

   /**
    * transform files when doing import.
    */
   public void process(File qfile, File wsFile, String qname, boolean tabular) {
      Document queryDoc = UpdateDependencyHandler.getXmlDocByFile(qfile);
      Document doc = UpdateDependencyHandler.getXmlDocByFile(wsFile);
      transform(doc, queryDoc, qname, null, tabular);
      updateFile(doc, wsFile);
   }

   /**
    * tranform when start server, transform worksheet since depended
    * queries were converted to worksheets.
    */
   public void processDocument(Document wsDoc, Document queryDoc, String qname,
                               String wsIdentifer, boolean tabular)
   {
      AutoDrillRequiredInfo info = new AutoDrillRequiredInfo(qname, wsIdentifer);
      transform(wsDoc, queryDoc, qname, info, tabular);
   }

   private void transform(Document wsDoc, Document queryDoc, String qname,
                          AutoDrillRequiredInfo info, boolean tabular)
   {
      updateMaxOffset(wsDoc.getDocumentElement());
      Element root = wsDoc.getDocumentElement();
      Element queryNode = queryDoc.getDocumentElement();
      NodeList list = UpdateDependencyHandler.getChildNodes(root,
         "//assemblies/oneAssembly/assembly[@class='inetsoft.uql.asset.QueryBoundTableAssembly']");

      for(int i = 0; i < list.getLength(); i++) {
         Element assembly = (Element) list.item(i);
         NodeList sub = UpdateDependencyHandler.getChildNodes(assembly, "./assemblyInfo");

         if(sub.getLength() == 0) {
            continue;
         }

         Element assemblyInfo = (Element) sub.item(0);

         if(!transformSource(assemblyInfo, queryNode, qname)) {
            continue;
         }

         assembly.setAttribute("class", getAssemblyClassName(tabular));
         removeUselessNodes(assembly);
         assemblyInfo.setAttribute("class", getAssemblyInfoClassName(tabular));

         sub = UpdateDependencyHandler.getChildNodes(assemblyInfo, "./class");

         if(sub.getLength() != 0) {
            Element classElem = (Element) sub.item(0);
            replaceElementCDATANode(classElem, getAssemblyClassName(tabular));
         }

         addNodes(queryNode, assemblyInfo);

         if(!tabular) {
            Element advanced = assembly.getOwnerDocument().createElement("advancedEditing");
            advanced.setAttribute("val", "true");
            assembly.appendChild(advanced);
         }

         updateProperties(assemblyInfo, queryNode);
         updateSqlEditValue(assembly, queryNode);
      }

      if(info != null) {
         transformAutoDrill(root, info);
      }

      convertVariables(root);
   }

   private void updateMaxOffset(Element root) {
      NodeList list = UpdateDependencyHandler.getChildNodes(root,
         "//assemblies/oneAssembly/assembly/assemblyInfo");

      for(int i = 0; i < list.getLength(); i++) {
         Element assemblyInfo = (Element) list.item(i);
         String pixelOffX = assemblyInfo.getAttribute("pixelOffX");
         String pixelOffY = assemblyInfo.getAttribute("pixelOffY");

         if(!StringUtils.isEmpty(pixelOffX)) {
            MAX_OFF_X = Math.max(MAX_OFF_X, Integer.parseInt(pixelOffX));
         }

         if(!StringUtils.isEmpty(pixelOffY)) {
            MAX_OFF_Y = Math.max(MAX_OFF_Y, Integer.parseInt(pixelOffY));
         }
      }
   }

   private String getAssemblyClassName(boolean tabular) {
      return tabular ? "inetsoft.uql.asset.TabularTableAssembly" :
         "inetsoft.uql.asset.SQLBoundTableAssembly";
   }

   private String getAssemblyInfoClassName(boolean tabular) {
      return tabular ? "inetsoft.uql.asset.internal.TabularTableAssemblyInfo" :
         "inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo";
   }

   private boolean transformSource(Element assemblyInfo, Element queryRoot, String qname) {
      NodeList nodeList = UpdateDependencyHandler.getChildNodes(assemblyInfo, "./source/sourceInfo");
      String dataSource = getQueryDataSource(queryRoot);

      if(dataSource == null) {
         return false;
      }

      if(nodeList.getLength() != 0) {
         Element sourceInfo = (Element) nodeList.item(0);
         NodeList sourceList = UpdateDependencyHandler.getChildNodes(sourceInfo, "./source");

         if(sourceList.getLength() != 0) {
            Element sourceElem = (Element) sourceList.item(0);

            if(!Tool.equals(qname, Tool.getValue(sourceElem))) {
               return false;
            }

            replaceElementCDATANode(sourceElem, dataSource);
            sourceInfo.setAttribute("type", XSourceInfo.DATASOURCE + "");
            return true;
         }
      }

      return false;
   }

   private void removeUselessNodes(Element assembly) {
      NodeList list = UpdateDependencyHandler.getChildNodes(assembly, "./queryColumnSelection");

      if(list.getLength() != 0) {
         Element querySelection = (Element) list.item(0);
         assembly.removeChild(querySelection);
      }
   }

   private void addNodes(Element queryRoot, Element assemblyInfo) {
      NodeList nodeList = UpdateDependencyHandler.getChildNodes(queryRoot, "//query");

      if(nodeList.getLength() != 0) {
         Element queryNode = (Element) nodeList.item(0);
         queryNode = (Element) assemblyInfo.getOwnerDocument().importNode(queryNode, true);
         assemblyInfo.appendChild(queryNode);

         String dsName = queryNode.getAttribute("datasource");
         Element datasource = assemblyInfo.getOwnerDocument().createElement("datasource");
         datasource.setAttribute("name", dsName);
         assemblyInfo.appendChild(datasource);
      }
   }

   private String getQueryDataSource(Element queryRoot) {
      NodeList nodeList = UpdateDependencyHandler.getChildNodes(queryRoot, "//query");
      String dsName = null;

      if(nodeList.getLength() != 0) {
         Element queryNode = (Element) nodeList.item(0);
         dsName = queryNode.getAttribute("datasource");
      }

      return dsName;
   }

   private void transformAutoDrill(Element root, AutoDrillRequiredInfo info) {
      NodeList list = UpdateDependencyHandler.getChildNodes(root, "//XDrillInfo/drillPath/subquery");

      for(int i = 0; i < list.getLength(); i++) {
         Element elem = (Element) list.item(i);

         if(Tool.equals(info.qname, elem.getAttribute("qname"))) {
            elem.removeAttribute("qname");
            AssetEntry entry = AssetEntry.createAssetEntry(info.wsIdentifier);
            Element wsNode = createAssetEntryElement(elem.getOwnerDocument(), entry);

            if(wsNode != null) {
               elem.appendChild(wsNode);
            }
         }
      }
   }

   @Override
   public int getStartPixelOffX() {
      return MAX_OFF_X;
   }

   @Override
   public int getStartPixelOffY() {
      return MAX_OFF_X;
   }

   class AutoDrillRequiredInfo {
      public AutoDrillRequiredInfo(String qname, String wsIdentifier) {
         super();

         this.qname = qname;
         this.wsIdentifier = wsIdentifier;
      }

      protected String qname;
      protected String wsIdentifier;
   }

   private int MAX_OFF_X = 0;
   private int MAX_OFF_Y = 0;
}
