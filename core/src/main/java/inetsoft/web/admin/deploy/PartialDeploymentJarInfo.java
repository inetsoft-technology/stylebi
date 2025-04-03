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
package inetsoft.web.admin.deploy;

import com.github.zafarkhaja.semver.Version;
import inetsoft.sree.security.IdentityID;
import inetsoft.util.dep.XAsset;
import inetsoft.util.*;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Jar File info.
 */
public class PartialDeploymentJarInfo implements XMLSerializable, Serializable {
   /**
    * Create a jar info.
    */
   public PartialDeploymentJarInfo() {
   }

   public PartialDeploymentJarInfo(String name, Timestamp deploymentDate, boolean overwriting,
                                   List<SelectedAsset> selectedEntries,
                                   List<RequiredAsset> dependentAssets)
   {
      this.deploymentDate = deploymentDate;
      this.overwriting = overwriting;
      this.name = name;
      this.selectedEntries = selectedEntries;
      this.dependentAssets = dependentAssets;
   }

   /**
    * Get the product version in which it is exported
    */
   public String getVersion() {
      return version;
   }

   /**
    * Set the product version in which it is exported
    */
   public void setVersion(String version) {
      this.version = version;
   }

   /**
    * Get the name of the info.
    */
   public String getName() {
      return name;
   }

   /**
    * Set the name of the info.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get the deployment date.
    */
   public Timestamp getDeploymentDate() {
      return deploymentDate;
   }

   /**
    * Set the deployment date.
    */
   public void setDeploymentDate(Timestamp deploymentDate) {
      this.deploymentDate = deploymentDate;
   }

   /**
    * Check if it can be overwritten.
    */
   public boolean isOverwriting() {
      return overwriting;
   }

   /**
    * Set if it can be overwritten.
    */
   public void setOverwriting(boolean overwriting) {
      this.overwriting = overwriting;
   }

   public List<XAsset> getSelectedEntryList() {
      return entryList;
   }

   public void setSelectedEntryList(List<XAsset> selectedEntryList) {
      this.entryList = selectedEntryList;
   }

   /**
    * Set the selected entries.
    */
   public void setSelectedEntries(List<SelectedAsset> selectedEntries) {
      this.selectedEntries = selectedEntries;
      this.entryList = null;
   }

   /**
    * Get the selected entries.
    */
   public List<SelectedAsset> getSelectedEntries() {
      if(selectedEntries == null && entryList != null) {
         return DeployUtil.getEntryData(entryList);
      }

      return selectedEntries;
   }

   /**
    * Set the dependent assets.
    */
   public void setDependentAssets(List<RequiredAsset> dependentAssets) {
      this.dependentAssets = dependentAssets;
   }

   /**
    * Get the dependent assets.
    */
   public List<RequiredAsset> getDependentAssets() {
      return dependentAssets;
   }

   /**
    * Save the info.
    */
   public void save(OutputStream out) throws IOException {
      PrintWriter writer =  new PrintWriter(
         new OutputStreamWriter(out, StandardCharsets.UTF_8));
      writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
      writeXML(writer);
      writer.close();
   }

   /**
    * Write xml.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<jarinfo>");
      writer.format("<version>%s</version>%n", FileVersions.JAR_VERSION);

      writer.println("<name>" + Tool.escape(name) + "</name>");

      if(folderAlias != null && !folderAlias.isEmpty()) {
         for(Map.Entry<String, String> e : folderAlias.entrySet()) {
            writer.println("<alias>");
            writer.format("<key>%s</key>%n", Tool.escape(e.getKey()));
            writer.format("<value>%s</value>%n", Tool.escape(e.getValue()));
            writer.println("</alias>");
         }
      }

      if(folderDescription != null && !folderDescription.isEmpty()) {
         for(Map.Entry<String, String> e : folderDescription.entrySet()) {
            writer.println("<description>");
            writer.format("<key>%s</key>%n", Tool.escape(e.getKey()));
            writer.format("<value>%s</value>%n", Tool.escape(e.getValue()));
            writer.println("</description>");
         }
      }

      writer.format("<deploymentDate>%s</deploymentDate>%n", Tool.getDataString(deploymentDate));
      writer.println("<entries>");

      List<SelectedAsset> selected = getSelectedEntries();

      if(selected != null) {
         for(SelectedAsset asset : selected) {
            asset.writeXML(writer);
         }
      }

      writer.println("</entries>");
      writer.println("<assets>");

      if(dependentAssets != null) {
         for(RequiredAsset asset : dependentAssets) {
            asset.writeXML(writer);
         }
      }

      writer.println("</assets>");
      writer.println("<overwriting>" + overwriting + "</overwriting>");
      writer.println("</jarinfo>");
      writer.flush();
   }

   /**
    * Parse xml.
    */
   @Override
   public void parseXML(Element node) throws Exception {
      Element elem = Tool.getChildNodeByTagName(node, "version");
      version = elem == null ? null : Tool.getValue(elem);
      elem = Tool.getChildNodeByTagName(node, "name");
      name = Tool.getValue(elem);

      NodeList list = Tool.getChildNodesByTagName(node, "alias");

      for(int i = 0; i < list.getLength(); i++) {
         parseKeyValuePair((Element) list.item(i), folderAlias);
      }

      list = Tool.getChildNodesByTagName(node, "description");

      for(int i = 0; i < list.getLength(); i++) {
         parseKeyValuePair((Element) list.item(i), folderDescription);
      }

      elem = Tool.getChildNodeByTagName(node, "overwriting");
      overwriting = "true".equalsIgnoreCase(Tool.getValue(elem));
      elem = Tool.getChildNodeByTagName(node, "deploymentDate");
      deploymentDate = (Timestamp) Tool.getData(Tool.TIME_INSTANT, Tool.getValue(elem));

      if(beforeSchemaChangeVersion()) {
         elem = Tool.getChildNodeByTagName(node, "entries");
         selectedEntries = Arrays.stream(Tool.parseArray2DXML(elem))
            .map(this::createSelectedAsset)
            .collect(Collectors.toList());

         elem = Tool.getChildNodeByTagName(node, "assets");
         dependentAssets = Arrays.stream(Tool.parseArray2DXML(elem))
            .map(this::createRequiredAsset)
            .collect(Collectors.toList());
      }
      else {
         elem = Tool.getChildNodeByTagName(node, "entries");
         list = Tool.getChildNodesByTagName(elem, "entry");
         selectedEntries = new ArrayList<>();

         for(int i = 0; i < list.getLength(); i++) {
            SelectedAsset asset = new SelectedAsset();
            asset.parseXML((Element) list.item(i));
            selectedEntries.add(asset);
         }

         elem = Tool.getChildNodeByTagName(node, "assets");
         list = Tool.getChildNodesByTagName(elem, "asset");
         dependentAssets = new ArrayList<>();

         for(int i = 0; i < list.getLength(); i++) {
            RequiredAsset asset = new RequiredAsset();
            asset.parseXML((Element) list.item(i));
            dependentAssets.add(asset);
         }
      }
   }

   public boolean beforeSchemaChangeVersion() {
      Version oldVersion = getSemanticVersion(version);
      Version curVersion = getSemanticVersion("13.1"); // schema changed

      return oldVersion == null || oldVersion.isLowerThan(curVersion);
   }

   private Version getSemanticVersion(String version) {
      if(version == null) {
         return null;
      }

      try {
         return Version.parse(version);
      }
      catch(Exception ignore) {
      }

      try {
         return Version.parse(version + ".0");
      }
      catch(Exception ignore) {
      }

      return null;
   }

   private void parseKeyValuePair(Element ptag, Map<String, String> map) {
      Element key = Tool.getChildNodeByTagName(ptag, "key");
      Element value = Tool.getChildNodeByTagName(ptag, "value");
      map.put(Tool.getValue(key), Tool.getValue(value));
   }

   private SelectedAsset createSelectedAsset(String[] oldEntry) {
      SelectedAsset asset = new SelectedAsset();

      if(oldEntry != null && oldEntry.length > 0) {
         asset.setType(oldEntry[0]);

         if(oldEntry.length > 1) {
            asset.setPath(oldEntry[1]);
         }

         // oldEntry[2] was always set to "null" as of v13.0, so ignore it

         if(oldEntry.length > 3) {
            asset.setUser(IdentityID.getIdentityIDFromKey(oldEntry[3]));
         }

         if(oldEntry.length > 4) {
            asset.setIcon(fixIconPath(oldEntry[4]));
         }
      }

      return asset;
   }

   private RequiredAsset createRequiredAsset(String[] oldAsset) {
      RequiredAsset asset = new RequiredAsset();

      if(oldAsset != null && oldAsset.length > 0) {
         asset.setPath(oldAsset[0]);

         if(oldAsset.length > 1) {
            asset.setType(oldAsset[1]);
         }

         if(oldAsset.length > 2) {
            asset.setRequiredBy(oldAsset[2]);
         }

         if(oldAsset.length > 3) {
            asset.setUser(IdentityID.getIdentityIDFromKey(oldAsset[3]));
         }

         if(oldAsset.length > 4) {
            asset.setDetailDescription(oldAsset[4]);
         }

         if(oldAsset.length > 5) {
            asset.setDetailDescription(oldAsset[5]);
         }
      }

      return asset;
   }

   /**
    * Backward compatibility for icon path.
    */
   private String fixIconPath(String iconPath) {
      String oldDir = "/inetsoft/report/design/";

      if(iconPath != null && iconPath.startsWith(oldDir)) {
         return "/inetsoft/report/gui/" + iconPath.substring(oldDir.length());
      }

      return iconPath;
   }

   public boolean isJarFileTransformed() {
      return jarFileTransformed;
   }

   public void setJarFileTransformed(boolean jarFileTransformed) {
      this.jarFileTransformed = jarFileTransformed;
   }

   public Map<String, String> getQueryFolderMap() {
      return queryFolderMap;
   }

   public void setQueryFolderMap(Map<String, String> queryFolderMap) {
      this.queryFolderMap = queryFolderMap;
   }

   public Map<String, List<String>> getDependeciesMap() {
      return dependeciesMap;
   }

   public void setDependenciesMap(Map<String, List<String>> dependeciesMap) {
      this.dependeciesMap = dependeciesMap;
   }

   public void setFolderAlias(HashMap<String, String> m) {
      folderAlias = m;
   }

   public HashMap<String, String> getFolderAlias(){
      return folderAlias;
   }

   public void setFolderDescription(HashMap<String, String> m) {
      folderDescription = m;
   }

   public HashMap<String, String> getFolderDescription() {
      return folderDescription;
   }

   private HashMap<String, String> folderAlias = new HashMap<>();
   private HashMap<String, String> folderDescription = new HashMap<>();
   private Timestamp deploymentDate;
   private boolean overwriting;
   private boolean jarFileTransformed;
   private Map<String, String> queryFolderMap = new HashMap<>(); // key -> query name, value -> folder
   private Map<String, List<String>> dependeciesMap = new HashMap<>(); // key -> query name, value-> file name
   private String version;
   private String name;
   private List<XAsset> entryList;
   private List<SelectedAsset> selectedEntries;
   private List<RequiredAsset> dependentAssets;

   public static final class SelectedAsset implements XMLSerializable, Serializable {
      public String getType() {
         return type;
      }

      public void setType(String type) {
         this.type = type;
      }

      public String getPath() {
         return path;
      }

      public void setPath(String path) {
         this.path = path;
      }

      public IdentityID getUser() {
         return user;
      }

      public void setUser(IdentityID user) {
         this.user = user;
      }

      public String getIcon() {
         return icon;
      }

      public void setIcon(String icon) {
         this.icon = icon;
      }

      public XAsset getAsset() {
         return asset;
      }

      public void setAsset(XAsset asset) {
         this.asset = asset;
      }

      public long getLastModifiedTime() {
         return lastModifiedTime;
      }

      public void setLastModifiedTime(long lastModifiedTime) {
         this.lastModifiedTime = lastModifiedTime;
      }

      @Override
      public void writeXML(PrintWriter writer) {
         writer.println("<entry>");

         if(type != null) {
            writer.format("<type>%s</type>%n", Tool.escape(type));
         }

         if(path != null) {
            writer.format("<path>%s</path>%n", Tool.escape(path));
         }

         if(user != null) {
            writer.format("<user>%s</user>%n", Tool.escape(user.convertToKey()));
         }

         if(icon != null) {
            writer.format("<icon>%s</icon>%n", Tool.escape(icon));
         }

         if(lastModifiedTime != 0) {
            writer.format("<lastModifiedTime>%s</lastModifiedTime>%n", lastModifiedTime);
         }

         writer.println("</entry>");
      }

      @Override
      public void parseXML(Element tag) {
         Element element;

         if((element = Tool.getChildNodeByTagName(tag, "type")) != null) {
            type = Tool.getValue(element);
         }
         else {
            type = null;
         }

         if((element = Tool.getChildNodeByTagName(tag, "path")) != null) {
            path = Tool.getValue(element);
         }
         else {
            path = null;
         }

         if((element = Tool.getChildNodeByTagName(tag, "user")) != null) {
            user = IdentityID.getIdentityIDFromKey(Tool.getValue(element));
         }
         else {
            user = null;
         }

         if((element = Tool.getChildNodeByTagName(tag, "icon")) != null) {
            icon = Tool.getValue(element);
         }
         else {
            icon = null;
         }

         if((element = Tool.getChildNodeByTagName(tag, "lastModifiedTime")) != null) {
            lastModifiedTime = Tool.getLongData(Tool.getValue(element));
         }
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }

         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         SelectedAsset that = (SelectedAsset) o;
         return Objects.equals(type, that.type) &&
            Objects.equals(path, that.path) &&
            Objects.equals(user, that.user) &&
            Objects.equals(icon, that.icon) &&
            Objects.equals(lastModifiedTime, that.lastModifiedTime);
      }

      @Override
      public int hashCode() {
         return Objects.hash(type, path, user, icon, lastModifiedTime);
      }

      @Override
      public String toString() {
         return "SelectedAsset{" +
            "type='" + type + '\'' +
            ", path='" + path + '\'' +
            ", user='" + user + '\'' +
            ", icon='" + icon + '\'' +
            ", lastModifiedTime='" + lastModifiedTime + '\'' +
            '}';
      }

      private String type;
      private String path;
      private IdentityID user;
      private String icon;
      private XAsset asset;
      private long lastModifiedTime;
   }

   public static final class RequiredAsset implements XMLSerializable, Serializable {
      public String getType() {
         return type;
      }

      public void setType(String type) {
         this.type = type;
      }

      public String getPath() {
         return path;
      }

      public void setPath(String path) {
         this.path = path;
      }

      public IdentityID getUser() {
         return user;
      }

      public void setUser(IdentityID user) {
         this.user = user;
      }

      public String getTypeDescription() {
         return typeDescription;
      }

      public void setTypeDescription(String typeDescription) {
         this.typeDescription = typeDescription;
      }

      public String getRequiredBy() {
         return requiredBy;
      }

      public void setRequiredBy(String requiredBy) {
         this.requiredBy = requiredBy;
      }

      public String getDetailDescription() {
         return detailDescription;
      }

      public void setDetailDescription(String detailDescription) {
         this.detailDescription = detailDescription;
      }

      public long getLastModifiedTime() {
         return lastModifiedTime;
      }

      public void setLastModifiedTime(long lastModifiedTime) {
         this.lastModifiedTime = lastModifiedTime;
      }

      public String getAssetDescription() {
         return assetDescription;
      }

      public void setAssetDescription(String assetDescription) {
         this.assetDescription = assetDescription;
      }

      @Override
      public void writeXML(PrintWriter writer) {
         writer.println("<asset>");

         if(type != null) {
            writer.format("<type>%s</type>%n", Tool.escape(type));
         }

         if(path != null) {
            writer.format("<path>%s</path>%n", Tool.escape(path));
         }

         if(user != null) {
            writer.format("<user>%s</user>%n", Tool.escape(user.convertToKey()));
         }

         if(typeDescription != null) {
            writer.format("<typeDescription>%s</typeDescription>%n", Tool.escape(typeDescription));
         }

         if(requiredBy != null) {
            writer.format("<requiredBy>%s</requiredBy>%n", Tool.escape(requiredBy));
         }

         if(detailDescription != null) {
            writer.format(
               "<detailDescription>%s</detailDescription>%n", Tool.escape(detailDescription));
         }

         if(lastModifiedTime != 0) {
            writer.format("<lastModifiedTime>%s</lastModifiedTime>%n", lastModifiedTime);
         }

         if(assetDescription != null) {
            writer.format(
               "<assetDescription>%s</assetDescription>%n", Tool.escape(assetDescription));
         }

         writer.println("</asset>");
      }

      @Override
      public void parseXML(Element tag) {
         Element element;

         if((element = Tool.getChildNodeByTagName(tag, "type")) != null) {
            type = Tool.getValue(element);
         }
         else {
            type = null;
         }

         if((element = Tool.getChildNodeByTagName(tag, "path")) != null) {
            path = Tool.getValue(element);
         }
         else {
            path = null;
         }

         if((element = Tool.getChildNodeByTagName(tag, "user")) != null) {
            user = IdentityID.getIdentityIDFromKey(Tool.getValue(element));
         }
         else {
            user = null;
         }

         if((element = Tool.getChildNodeByTagName(tag, "typeDescription")) != null) {
            typeDescription = Tool.getValue(element);
         }
         else {
            typeDescription = null;
         }

         if((element = Tool.getChildNodeByTagName(tag, "requiredBy")) != null) {
            requiredBy = Tool.getValue(element);
         }
         else {
            requiredBy = null;
         }

         if((element = Tool.getChildNodeByTagName(tag, "detailDescription")) != null) {
            detailDescription = Tool.getValue(element);
         }
         else {
            detailDescription = null;
         }

         if((element = Tool.getChildNodeByTagName(tag, "lastModifiedTime")) != null) {
            lastModifiedTime = Tool.getLongData(Tool.getValue(element));
         }

         if((element = Tool.getChildNodeByTagName(tag, "assetDescription")) != null) {
            assetDescription = Tool.getValue(element);
         }
         else {
            assetDescription = null;
         }
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }

         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         RequiredAsset that = (RequiredAsset) o;
         return Objects.equals(type, that.type) &&
            Objects.equals(path, that.path) &&
            Objects.equals(user, that.user) &&
            Objects.equals(typeDescription, that.typeDescription) &&
            Objects.equals(requiredBy, that.requiredBy) &&
            Objects.equals(detailDescription, that.detailDescription) &&
            Objects.equals(lastModifiedTime, that.lastModifiedTime) &&
            Objects.equals(assetDescription, that.assetDescription);
      }

      @Override
      public int hashCode() {
         return Objects.hash(type, path, user, typeDescription, requiredBy, detailDescription,
                             lastModifiedTime, assetDescription);
      }

      @Override
      public String toString() {
         return "RequiredAsset{" +
            "type='" + type + '\'' +
            ", path='" + path + '\'' +
            ", user='" + user + '\'' +
            ", typeDescription='" + typeDescription + '\'' +
            ", requiredBy='" + requiredBy + '\'' +
            ", detailDescription='" + detailDescription + '\'' +
            ", lastModifiedTime='" + lastModifiedTime + '\'' +
            ", assetDescription='" + assetDescription + '\'' +
            '}';
      }

      private String type;
      private String path;
      private IdentityID user;
      private String typeDescription;
      private String requiredBy;
      private String detailDescription;
      private long lastModifiedTime;
      private String assetDescription;
   }
}
