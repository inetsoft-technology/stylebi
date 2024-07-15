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
package inetsoft.report.io;

import inetsoft.util.IteratorEnumeration;

import java.util.Enumeration;
import java.util.HashSet;

/**
 * Describes an export type.
 *
 * @author InetSoft Technology
 * @since  7.0
 */
public class ExportType {
   /**
    * Gets the unique identifier for this export type.
    *
    * @return the unique identifier.
    */
   public int getFormatId() {
      return formatId;
   }

   /**
    * Sets the unique identifier for this export type.
    *
    * @param formatId the unique identifier.
    */
   public void setFormatId(int formatId) {
      this.formatId = formatId;
   }

   /**
    * Gets the format option string for this export type.
    *
    * @return the format option string.
    */
   public String getFormatOption() {
      return formatOption;
   }

   /**
    * Sets the format option string for this export type.
    *
    * @param formatOption the format option string.
    */
   public void setFormatOption(String formatOption) {
      this.formatOption = formatOption;
   }

   /**
    * Gets the file extension for this export type.
    *
    * @return the file extension.
    */
   public String getExtension() {
      return extension;
   }

   /**
    * Sets the file extension for this export type.
    *
    * @param extension the file extension.
    */
   public void setExtension(String extension) {
      this.extension = extension;
   }

   /**
    * Gets the old version file extension for this export type.
    *
    * @return the file extension.
    */
   public String getOldExtension() {
      return oldExtension;
   }

   /**
    * Sets the old version file extension for this export type.
    *
    * @param oldExtension the file extension.
    */
   public void setOldExtension(String oldExtension) {
      this.oldExtension = oldExtension;
   }

   /**
    * Gets the MIME type for this export type.
    *
    * @return the MIME type.
    */
   public String getMimeType() {
      return mimeType;
   }

   /**
    * Sets the MIME type for this export type.
    *
    * @param mimeType the MIME type.
    */
   public void setMimeType(String mimeType) {
      this.mimeType = mimeType;
   }

   /**
    * Gets the MIME type for this export type.
    *
    * @return the MIME type.
    */
   public String getOldMimeType() {
      return oldMimeType;
   }

   /**
    * Sets the MIME type for this export type.
    *
    * @param mimeType the MIME type.
    */
   public void setOldMimeType(String mimeType) {
      this.oldMimeType = mimeType;
   }

   /**
    * Determines if this export type can be sent by email.
    *
    * @return <code>true</code> if this export type can be sent by email;
    *         <code>false</code> otherwise.
    */
   public boolean isMailSupported() {
      return mailSupported;
   }

   /**
    * Sets whether this export type can be sent by email.
    *
    * @param mailSupported <code>true</code> if this export type can be sent by
    *                      email; <code>false</code> otherwise.
    */
   public void setMailSupported(boolean mailSupported) {
      this.mailSupported = mailSupported;
   }

   /**
    * Determines if this export type is visible.
    *
    * @return <code>true</code> if this export type is visible,
    *         <code>false</code> otherwise.
    */
   public boolean isVisible() {
      return visible;
   }

   /**
    * Sets whether this export type is visible.
    *
    * @param visible <code>true</code> if this export type is visible,
    * <code>false</code> otherwise.
    */
   public void setVisible(boolean visible) {
      this.visible = visible;
   }

   /**
    * Determines if this export type can be exported from the web.
    *
    * @return <code>true</code> if this export type can be exported from the
    *         web; <code>false</code> otherwise.
    */
   public boolean isExportSupported() {
      return exportSupported;
   }

   /**
    * Sets whether this export type can be exported from the web.
    *
    * @param exportSupported <code>true</code> if this export type can be
    *                        exported from the web; <code>false</code>
    *                        otherwise.
    */
   public void setExportSupported(boolean exportSupported) {
      this.exportSupported = exportSupported;
   }

   /**
    * Gets a description of this export type. The description is used in the
    * interfaces for server export, mailing, saving in the archive, and
    * scheduling reports, and on the export menu of the Report Designer.
    *
    * @return the description.
    */
   public String getDescription() {
      return description;
   }

   /**
    * Sets the description of this export type. The description is used in the
    * interfaces for server export, mailing, saving in the archive, and
    * scheduling reports, and on the export menu of the Report Designer.
    *
    * @param description the description.
    */
   public void setDescription(String description) {
      this.description = description;
   }

   /**
    * Gets the index of this export type. The index is used to sort sets of
    * export types.
    *
    * @return the index of this export type.
    */
   public int getIndex() {
      return index;
   }

   /**
    * Sets the index of this export type. The index is used to sort sets of
    * export types.
    *
    * @param index the index of this export type.
    */
   public void setIndex(int index) {
      this.index = index;
   }

   /**
    * Gets the key used to determine if this export type is included on the
    * "Export" menu of the designer.
    *
    * @return the key for this export type.
    */
   public String getDesignerKey() {
      return designerKey;
   }

   /**
    * Sets the key used to determine if this export type is included on the
    * "Export" menu of the designer.
    *
    * @param designerKey the key for this export type.
    */
   public void setDesignerKey(String designerKey) {
      this.designerKey = designerKey;
   }

   /**
    * Gets the class of the ExportAction used to export a report using this
    * export type in the Report Designer. If the action class is
    * <code>null</code> this export type will not be available on the export
    * menu.
    *
    * @return the export action class.
    */
   public String getActionClass() {
      return actionClass;
   }

   /**
    * Sets the class of the ExportAction used to export a report using this
    * export type in the Report Designer. If the action class is
    * <code>null</code> this export type will not be available on the export
    * menu.
    *
    * @param actionClass the export action class.
    */
   public void setActionClass(String actionClass) {
      this.actionClass = actionClass;
   }

   /**
    * Gets the ExportFactory that creates the Generator or Formatter for this
    * export type.
    *
    * @return an ExportFactory object.
    */
   public ExportFactory getExportFactory() {
      return exportFactory;
   }

   /**
    * Sets the ExportFactory that creates the Generator or Formatter for this
    * export type.
    *
    * @param exportFactory an ExportFactory object.
    */
   public void setExportFactory(ExportFactory exportFactory) {
      this.exportFactory = exportFactory;
   }

   /**
    * Adds a supplemental ID for this export type. Supplemental IDs are
    * typically old or deprecated constants referring to this export type.
    *
    * @param supplementalId the ID to add.
    */
   public void addSupplementalId(int supplementalId) {
      supplementalIds.add(supplementalId);
   }

   /**
    * Gets the supplemental IDs associated with this export type. Supplemental
    * IDs are typically old or deprecated constants referring to this export
    * type.
    *
    * @return an Enumeration of Integer objects.
    */
   public Enumeration getSupplementalIds() {
      return new IteratorEnumeration(supplementalIds.iterator());
   }

   /**
    * Return if the target export type is for vs archive.
    */
   public boolean isForVSArchive() {
      return false;
   }

   private int formatId;
   private int index;
   private String formatOption;
   private String extension;
   private String oldExtension;
   private String mimeType;
   private String oldMimeType;
   private boolean mailSupported;
   private boolean exportSupported;
   private boolean visible = true;
   private String description;
   private String designerKey;
   private String actionClass;
   private ExportFactory exportFactory;
   private HashSet<Integer> supplementalIds = new HashSet<>();
}
