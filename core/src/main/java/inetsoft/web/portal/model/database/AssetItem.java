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
package inetsoft.web.portal.model.database;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base class for beans that represent an asset in the repository.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
   @JsonSubTypes.Type(value = LogicalModel.class, name = "logical_model"),
   @JsonSubTypes.Type(value = PhysicalModel.class, name = "physical_model"),
   @JsonSubTypes.Type(value = Vpm.class, name = "vpm"),
   @JsonSubTypes.Type(value = Folder.class, name = "folder"),
   @JsonSubTypes.Type(value = DatabaseAsset.class, name = "database_asset"),
   @JsonSubTypes.Type(value = DataModelFolder.class, name = "data_model_folder")
})
public abstract class AssetItem {
   /**
    * Creates a new instance of <tt>AssetItem</tt>.
    *
    * @param type the type of the asset.
    */
   protected AssetItem(String type) {
      this.type = type;
   }

   /**
    * Gets the type of this asset.
    *
    * @return the asset type.
    */
   public String getType() {
      return type;
   }

   /**
    * Gets the asset identifier for this asset.
    *
    * @return the identifier.
    */
   public String getId() {
      return id;
   }

   /**
    * Sets the asset identifier for this asset.
    *
    * @param id the identifier.
    */
   public void setId(String id) {
      this.id = id;
   }

   /**
    * Gets the full path to this asset.
    *
    * @return the path.
    */
   public String getPath() {
      return path;
   }

   /**
    * Sets the full path to this asset.
    *
    * @param path the path.
    */
   public void setPath(String path) {
      this.path = path;
   }

   /**
    * Gets the full path to this asset, encoded for use in a URL.
    *
    * @return the URL-encoded path.
    */
   public String getUrlPath() {
      return urlPath;
   }

   /**
    * Sets the full path to this asset, encoded for use in a URL.
    *
    * @param urlPath the URL-encoded path.
    */
   public void setUrlPath(String urlPath) {
      this.urlPath = urlPath;
   }

   /**
    * Gets the display name of this asset.
    *
    * @return the display name.
    */
   public String getName() {
      return name;
   }

   /**
    * Sets the display name of this asset.
    *
    * @param name the display name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Gets the name of the user that created this asset.
    *
    * @return the username.
    */
   public String getCreatedBy() {
      return createdBy;
   }

   /**
    * Sets the name of the user that created this asset.
    *
    * @param createdBy the username.
    */
   public void setCreatedBy(String createdBy) {
      this.createdBy = createdBy;
   }

   /**
    * Gets the date of creation for this asset.
    *
    * @return the creation date.
    */
   public long getCreatedDate() {
      return createdDate;
   }

   /**
    * Sets the creation date for this asset.
    *
    * @param createdDate the creation date.
    */
   public void setCreatedDate(long createdDate) {
      this.createdDate = createdDate;
   }

   /**
    * Gets description for this asset.
    *
    * @return description.
    */
   public String getDescription() {
      return description;
   }

   /**
    * Sets the description for this asset.
    *
    * @param description the asset's description.
    */
   public void setDescription(String description) {
      this.description = description;
   }

   /**
    * Gets the flag that indicates if the current user has permission to edit
    * the content of this asset.
    *
    * @return <tt>true</tt> if editable; <tt>false</tt> otherwise.
    */
   public boolean isEditable() {
      return editable;
   }

   /**
    * Sets the flag that indicates if the current user has permission to edit
    * the content of this asset.
    *
    * @param editable <tt>true</tt> if editable; <tt>false</tt> otherwise.
    */
   public void setEditable(boolean editable) {
      this.editable = editable;
   }

   /**
    * Gets a flag that indicates if the current user has permission to delete,
    * rename, or move this asset.
    *
    * @return <tt>true</tt> if deletable; <tt>false</tt> otherwise.
    */
   public boolean isDeletable() {
      return deletable;
   }

   /**
    * Sets a flag that indicates if the current user has permission to delete,
    * rename, or move this asset.
    *
    * @param deletable <tt>true</tt> if deletable; <tt>false</tt> otherwise.
    */
   public void setDeletable(boolean deletable) {
      this.deletable = deletable;
   }

   /**
    * Gets the create data label.
    */
   public String getCreatedDateLabel() {
      return createdDateLabel;
   }
   /**
    * Sets the create data label.
    *
    * @param createdDateLabel create date is applied format.
    */
   public void setCreatedDateLabel(String createdDateLabel) {
      this.createdDateLabel = createdDateLabel;
   }

   private final String type;
   private String id;
   private String path;
   private String urlPath;
   private String name;
   private String createdBy;
   private String description;
   private long createdDate;
   private String createdDateLabel;
   private boolean editable;
   private boolean deletable;
}
