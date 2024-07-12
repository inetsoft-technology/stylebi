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
package inetsoft.web.portal.model;

import com.fasterxml.jackson.annotation.*;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.util.XUtil;
import org.apache.ignite.internal.processors.affinity.IdealAffinityAssignment;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
   include = JsonTypeInfo.As.EXISTING_PROPERTY,
   property = "classType")
@JsonSubTypes({
   @JsonSubTypes.Type(value = DefaultFolderEntryModel.class, name = "DefaultFolderEntry"),
   @JsonSubTypes.Type(value = RepletEntryModel.class, name = "RepletEntry"),
   @JsonSubTypes.Type(value = RepletFolderEntryModel.class, name = "RepletFolderEntry"),
   @JsonSubTypes.Type(value = ViewsheetEntryModel.class, name = "ViewsheetEntry"),
   @JsonSubTypes.Type(value = WorksheetEntryModel.class, name = "WorksheetEntry"),
   @JsonSubTypes.Type(value = RepositoryEntryModel.class, name = "RepositoryEntry")
})
@JsonIgnoreProperties(ignoreUnknown = true)
public class RepositoryEntryModel<T extends RepositoryEntry> {
   public RepositoryEntryModel() {
   }

   public RepositoryEntryModel(RepositoryEntry entry) {
      name = entry.getName();
      type = entry.getType();
      path = entry.getPath();
      label = entry.getLabel();
      owner = entry.getOwner();
      favoritesUser = entry.getFavoritesUser();
      this.entry = entry.getAssetEntry();
      htmlType = entry.getHtmlType();
      classType = "RepositoryEntry";
   }

   public RepositoryEntry createRepositoryEntry() {
      RepositoryEntry entry = new RepositoryEntry();
      setProperties(entry);
      return entry;
   }

   protected void setProperties(RepositoryEntry entry) {
      entry.setType(type);
      entry.setPath(path);
      entry.setType(type);
      entry.setOwner(owner);
      entry.setAssetEntry(this.entry);
   }

   public String getName() {
      return name;
   }

   public int getType() {
      return type;
   }

   public String getPath() {
      return path;
   }

   public String getLabel() {
      return label;
   }

   public IdentityID getOwner() {
      return owner;
   }

   public AssetEntry getEntry() {
      return entry;
   }

   public int getHtmlType() {
      return htmlType;
   }

   public String getClassType() {
      return classType;
   }

   public void setClassType(String classType) {
      this.classType = classType;
   }

   public EnumSet<RepositoryTreeAction> getOp() {
      return op;
   }

   public void addOp(RepositoryTreeAction action) {
      this.op.add(action);
   }

   public void setOp(EnumSet<RepositoryTreeAction> op) {
      this.op = op;
   }

   public boolean getFavoritesUser() {
      return favoritesUser;
   }

   public void setFavoritesUser(boolean favoritesUser) {
      this.favoritesUser = favoritesUser;
   }

   private String name;
   private int type;
   private String path;
   private String label;
   private IdentityID owner;
   private AssetEntry entry;
   private int htmlType;
   private String classType;
   private EnumSet<RepositoryTreeAction> op = EnumSet.noneOf(RepositoryTreeAction.class);
   private boolean favoritesUser; // Be collect in Favorites.(Add/Remove for Favorites)

   @Component
   public static final class RepositoryEntryModelFactory0
      extends RepositoryEntryModelFactory<RepositoryEntry, RepositoryEntryModel<RepositoryEntry>>
   {
      public RepositoryEntryModelFactory0() {
         super(RepositoryEntry.class);
      }

      @Override
      public RepositoryEntryModel createModel(RepositoryEntry entry) {
         return new RepositoryEntryModel(entry);
      }
   }
}
