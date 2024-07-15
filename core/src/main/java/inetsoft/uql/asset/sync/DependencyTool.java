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
package inetsoft.uql.asset.sync;

import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.IndexedStorage;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import javax.xml.xpath.*;
import java.util.*;

public class DependencyTool {
   public static List<AssetObject> getQueryDependencies(String qname) {
      return getDependencies(getAssetId(qname, AssetEntry.Type.QUERY));
   }

   public static List<AssetObject> getModelDependencies(String name) {
      return getDependencies(getAssetId(name, AssetEntry.Type.LOGIC_MODEL));
   }

   public static List<AssetObject> getPartitionDependencies(String name) {
      return getDependencies(getAssetId(name, AssetEntry.Type.PARTITION));
   }

   public static List<AssetObject> getVpmDependencies(String name) {
      return getDependencies(getAssetId(name, AssetEntry.Type.VPM));
   }

   public static List<AssetObject> getWsDependencies(String wsName) {
      return getDependencies(getAssetId(wsName, AssetEntry.Type.WORKSHEET));
   }

   public static String getAssetId(String name, AssetEntry.Type type) {
      return DependencyHandler.getAssetId(name, type);
   }

   public static List<AssetObject> getDependencies(String entryId) {
      DependencyStorageService service = DependencyStorageService.getInstance();

      try {
         AssetEntry entry = AssetEntry.createAssetEntry(entryId);
         String orgId = AssetEntry.createAssetEntry(entryId).getOrgID();
         RenameTransformObject obj = service.getWithOrg(entryId, orgId);
         List<AssetObject> dependencies = new ArrayList<>();

         if(obj instanceof DependenciesInfo) {
            List<AssetObject> dependencies0 = ((DependenciesInfo) obj).getDependencies();

            if(dependencies0 != null && dependencies0.size() > 0) {
               for(AssetObject assetObject: dependencies0) {
                  dependencies.add(assetObject);
               }
            }

            List<AssetObject> embedDependencies = ((DependenciesInfo) obj).getEmbedDependencies();

            if(embedDependencies != null &&  embedDependencies.size() > 0) {
               for(AssetObject assetObject: embedDependencies) {
                  dependencies.add(assetObject);
               }
            }

            return dependencies;
         }
         else if(entry.getType() == AssetEntry.Type.SCHEDULE_TASK) {
            return ScheduleManager.getScheduleManager().getDependentTasks(entryId, orgId);
         }
      }
      catch(Exception ignored) {
         // ignored
      }

      return Collections.emptyList();
   }

   public static List<AssetObject> getEmbedDependencies(String entryId) {
      DependencyStorageService service = DependencyStorageService.getInstance();

      try {
         RenameTransformObject obj = service.get(entryId);

         if(obj instanceof DependenciesInfo) {
            return ((DependenciesInfo) obj).getEmbedDependencies();
         }
      }
      catch(Exception ignored) {
         // ignored
      }

      return Collections.emptyList();
   }

   public static Element getAssetElement(AssetEntry asset) {
      try {
         AssetRepository repository = AssetUtil.getAssetRepository(false);
         IndexedStorage storage = repository.getStorage(asset);
         String identifier = asset.toIdentifier();
         Document doc = null;

         synchronized(storage) {
            doc = storage.getDocument(identifier);
         }

         return doc != null ? doc.getDocumentElement() : null;
      }
      catch(Exception e) {
         LOG.warn("Failed to rename dependency assets: ", e);
      }

      return null;
   }

   public static NodeList getChildNodes(XPath xpath, Element doc, String path) {
      NodeList nodeList = null;

      try {
         XPathExpression expr = xpath.compile(path);
         nodeList = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);

         if(nodeList != null) {
            return nodeList;
         }
      }
      catch(XPathExpressionException ignore) {
         // ignored
      }

      return new NodeList() {
         @Override
         public Node item(int index) {
            return null;
         }

         @Override
         public int getLength() {
            return 0;
         }
      };
   }

   public static Element getChildNode(XPath xpath, Element doc, String path) {
      try {
         XPathExpression expr = xpath.compile(path);
         return (Element) expr.evaluate(doc, XPathConstants.NODE);
      }
      catch(XPathExpressionException ignore) {
         // ignored
      }

      return null;
   }

   /**
    * Get the optimal pool size for keeping the processors at the desired utilization.
    * @param entriesCount the number of assets need to transform.
    */
   public static int getThreadNumber(int entriesCount) {
      if(entriesCount < 5) {
         return 1;
      }

      int Ncpu = Runtime.getRuntime().availableProcessors();

      return (int) (Ncpu * 0.5 * (1 + entriesCount / 100));
   }

   /**
    * Return the assets list to transform in the target thread.
    * @param threadNo  the thread no.
    * @param perCount  the assets number to tranform in a thread.
    * @param entries   the total assets need to transform.
    */
   public static List<AssetObject> getThreadAssets(int threadNo, int perCount,
                                                   AssetObject[] entries)
   {
      List<AssetObject> list = new ArrayList<>();

      if(threadNo * perCount >= entries.length) {
         return list;
      }

      for(int i = threadNo * perCount, n = 0; i < entries.length && n < perCount; i++, n++) {
         list.add(entries[i]);
      }

      return list;
   }

   public static String getString(Element doc, String path) {
      try {
         XPathExpression expr = xpath.compile(path);
         return (String) expr.evaluate(doc, XPathConstants.STRING);
      }
      catch(XPathExpressionException ignore) {
         // ignored
      }

      return null;
   }

   public static String getVSAssemblyType(Element elem) {
      if(elem == null) {
         return null;
      }

      String className = elem.getAttribute("class");

      if(className == null) {
         return null;
      }

      int lastIndex = className.lastIndexOf('.');

      if(lastIndex < 0 || lastIndex >= className.length() - 1) {
         return className;
      }
      else {
         return className.substring(lastIndex + 1, className.length());
      }
   }

   public static boolean isSameVSSource(Element elem, RenameInfo info, boolean isAllAggs) {
      // For rename column and entity, should check source is the same or not. Others no need.
      if(!info.isColumn() && !info.isEntity() && !(info.isWorksheet() && info.isTable())) {
         return true;
      }

      String assmblyType = getVSAssemblyType(elem);
      String source = info.isWorksheet() ?  info.getTable() : info.getSource();

      if("ChartVSAssembly".equals(assmblyType) || "CrosstabVSAssembly".equals(assmblyType) ||
              "CalcTableVSAssembly".equals(assmblyType) || "TableVSAssembly".equals(assmblyType))
      {
         return Tool.equals(source, getString(elem, "./assemblyInfo/sourceInfo/source/text()"));
      }
      else if("CheckBoxVSAssembly".equals(assmblyType) || "ComboBoxVSAssembly".equals(assmblyType)
              || "GaugeVSAssembly".equals(assmblyType) || "ImageVSAssembly".equals(assmblyType)
              || "RadioButtonVSAssembly".equals(assmblyType) || "TextVSAssembly".equals(assmblyType))
      {
         return Tool.equals(source, getString(elem, "./assemblyInfo/bindingInfo/table/text()"));
      }
      else if("SelectionListVSAssembly".equals(assmblyType) ||
              "CalendarVSAssembly".equals(assmblyType) || "SelectionTreeVSAssembly".equals(assmblyType)
              || "TimeSliderVSAssembly".equals(assmblyType) || "CurrentSelectionVSAssembly".equals(assmblyType))
      {
         return Tool.equals(source, getString(elem, "./assemblyInfo/firstTable/text()")) ||
                 Tool.equals(source, getString(elem, "./assemblyInfo/table/text()")) ||
                 Tool.equals(source, getString(elem, "./assemblyInfo/additionalTables/table/text()"));
      }
      else if(isAllAggs) {
         return Tool.equals(source, getString(elem, "./tname/text()"));
      }

      return false;
   }

   public static boolean isSameWorkSheetSource(Element elem, RenameInfo info) {
      // For rename column and entity, should check source is the same or not. Others no need.
      if(!info.isColumn() && !info.isEntity()) {
         return true;
      }

      NodeList list = getChildNodes(xpath, elem, "./assemblyInfo/source/sourceInfo | ./attachedAssembly/source/sourceInfo");

      if(list != null && list.getLength() > 0) {
         Element sourceElem = (Element)list.item(0);
         Element srcElem = Tool.getChildNodeByTagName(sourceElem, "source");

         if(Tool.equals(Tool.getValue(srcElem), info.getSource())) {
            return true;
         }
      }

      NodeList list1 = getChildNodes(xpath, elem, ".//mirrorAssembly");

      if(list1 == null) {
         return false;
      }

      for(int i = 0; i < list1.getLength(); i++) {
         Element mirror = (Element) list1.item(i);
         String src = Tool.getAttribute(mirror, "source");

         if(Tool.equals(src, info.getSource())) {
            return true;
         }
      }

      return false;
   }

   private static final XPath xpath = XPathFactory.newInstance().newXPath();
   private static final Logger LOG = LoggerFactory.getLogger(DependencyTool.class);
}
