/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.asset.sync;

import inetsoft.uql.asset.AssetObject;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.*;
import java.util.*;

/**
 * This class keeps information to update the dependencies of binding source.
 *
 * @version 13.2
 * @author InetSoft Technology Corp
 */
public class RenameDependencyInfo implements Serializable, XMLSerializable {
   /**
    * Constructor.
    */
   public RenameDependencyInfo() {
   }

   /**
    * Check if the rename transformation need to recursive.
    *
    * If an opened asset be transformed (caused by rename action of its binding source),
    * when user save the asset and choose yes for "The dependency has changed, whether to update?",
    * only need to do transformation for the asset itself, because other assets depends on it have
    * already be transformated.
    *
    * @return
    */
   public boolean isRecursive() {
      return recursive;
   }

   /**
    * Set if need to do recursive transform.
    * @param recursive
    */
   public void setRecursive(boolean recursive) {
      this.recursive = recursive;
   }

   public boolean isRuntime() {
      return runtime;
   }

   public void setRuntime(boolean run) {
      this.runtime = run;
   }

   /**
    * Return the rename info for the rename action.
    *
    * If a source have be renamed, the key of dependencies map (config\assetSyncData\dependencies)
    * should be updated to the new source identifer.
    */
   public List<RenameInfo> getRenameInfos() {
      return this.rinfos;
   }

   /**
    * Set the all rename info.
    */
   public void setRenameInfos(List<RenameInfo> infos) {
      this.rinfos = infos;
   }

   public Map<AssetObject, List<RenameInfo>> getDependencyMap() {
      return map;
   }

   public void setDependencyMap(Map<AssetObject, List<RenameInfo>> map) {
      this.map = map;
   }

   /**
    * Add rename information to the map.
    * @param entry    the asset object which depends on the renamed source.
    * @param info     the renamed information of the renamed source,
    *                 which will be used to transform the target asset object.
    */
   public void addRenameInfo(AssetObject entry, RenameInfo info) {
      List<RenameInfo> list = map.computeIfAbsent(entry, k -> new ArrayList<>());
      list.add(info);
   }

   /**
    * Add rename information to the map.
    * @param entry    the asset object which depends on the renamed source.
    * @param infos     the renamed information of the renamed source,
    *                 which will be used to transform the target asset object.
    */
   public void addRenameInfos(AssetObject entry, List<RenameInfo> infos) {
      List<RenameInfo> list = map.computeIfAbsent(entry, k -> new ArrayList<>());
      list.addAll(infos);
   }

   public void setRenameInfo(AssetObject entry, List<RenameInfo> infos) {
      map.put(entry, infos);
   }

   public void removeRenameInfo(AssetObject entry) {
      map.remove(entry);
   }

   public List<RenameInfo> getRenameInfo(AssetObject entry) {
      return map.getOrDefault(entry, new ArrayList<>());
   }

   public AssetObject[] getAssetObjects() {
      Set<AssetObject> keys = map.keySet();
      return keys.toArray(new AssetObject[0]);
   }

   public void setAssetFile(AssetObject asset, File file) {
      assetFileMap.put(asset, file);
   }

   public File getAssetFile(AssetObject asset) {
      return assetFileMap.get(asset);
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   public void writeXML(PrintWriter writer) {
      writer.print("<renameDependencyInfo class=\"" + getClass().getName()
         + "\" recursive=\"" + recursive + "\" id=\"" + id + "\">");

      if(rinfos != null) {
         writer.print("<renameInfos>");

         for(int i = 0; i < rinfos.size(); i++) {
            RenameInfo rinfo = rinfos.get(i);

            if(rinfo != null) {
               rinfo.writeXML(writer);
            }
         }

         writer.print("</renameInfos>");
      }

      Set<AssetObject> keys = map.keySet();
      Iterator it = keys.iterator();

      while(it.hasNext()) {
         AssetObject obj = (AssetObject) it.next();
         List<RenameInfo> list = getRenameInfo(obj);

         writer.print("<item>");
         writer.print("<assetObject class=\"" + obj.getClass().getName() + "\">");
         obj.writeXML(writer);
         writer.print("</assetObject>");

         for(int i = 0; i < list.size(); i++) {
            RenameInfo rinfo = list.get(i);

            if(rinfo != null) {
               rinfo.writeXML(writer);
            }
         }

         writer.print("</item>");
      }

      writer.print("</renameDependencyInfo>");
   }

   /**
    * Method to parse an xml segment.
    */
   public void parseXML(Element elem) throws Exception {
      recursive = "true".equalsIgnoreCase(Tool.getAttribute(elem, "class"));
      id = Tool.getAttribute(elem, "id");

      if(id == null) {
         id = UUID.randomUUID().toString();
      }

      Element rnode = Tool.getChildNodeByTagName(elem, "renameInfos");

      if(rnode != null) {
         NodeList infos = Tool.getChildNodesByTagName(rnode, "renameInfo");
         List<RenameInfo> rinfos = new ArrayList<>();

         for(int j = 0; j < infos.getLength(); j++) {
            Element infoElem = (Element) infos.item(j);
            RenameInfo info = new RenameInfo();
            info.parseXML(infoElem);
            rinfos.add(info);
         }
      }

      map = new HashMap<>();
      NodeList items = Tool.getChildNodesByTagName(elem, "item");

      for(int i = 0; i < items.getLength(); i++) {
         Element item = (Element) items.item(i);
         Element assetNode = Tool.getChildNodeByTagName(item, "assetObject");

         if(assetNode == null) {
            continue;
         }

         String cls = Tool.getAttribute(assetNode, "class");
         AssetObject assetObj = (AssetObject) Class.forName(cls).newInstance();

         NodeList list = assetNode.getChildNodes();

         for(int k = 0; k < list.getLength(); k++) {
            if(list.item(k) instanceof Element) {
               assetObj.parseXML((Element) list.item(k));
               break;
            }
         }

         NodeList infos = Tool.getChildNodesByTagName(item, "renameInfo");
         List<RenameInfo> renameInfoList = new ArrayList<>();

         for(int j = 0; j < infos.getLength(); j++) {
            Element ielem = (Element) infos.item(j);
            RenameInfo rinfo = new RenameInfo();
            rinfo.parseXML(ielem);
            renameInfoList.add(rinfo);
         }

         map.put(assetObj, renameInfoList);
      }
   }

   public boolean isUpdateStorage() {
      return updateStorage;
   }

   public void setUpdateStorage(boolean update) {
      this.updateStorage = update;
   }

   public String getTaskId() {
      return id;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      RenameDependencyInfo that = (RenameDependencyInfo) o;
      return Objects.equals(id, that.id);
   }

   @Override
   public int hashCode() {
      return Objects.hash(id);
   }

   private String id = UUID.randomUUID().toString();
   private boolean recursive = true;
   private boolean updateStorage = true;
   private boolean runtime = false;
   private List<RenameInfo> rinfos = new ArrayList<>();
   private Map<AssetObject, List<RenameInfo>> map = new HashMap<>();
   private Map<AssetObject, File> assetFileMap = new HashMap<>();
}
