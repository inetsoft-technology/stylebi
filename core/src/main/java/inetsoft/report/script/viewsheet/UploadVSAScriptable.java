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
package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.internal.UploadVSAssemblyInfo;
import inetsoft.util.FileSystemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * The upload viewsheet assembly scriptable in viewsheet scope.
 *
 * @version 12.0
 * @author InetSoft Technology Corp
 */
public class UploadVSAScriptable extends VSAScriptable {
   /**
    * Create upload viewsheet assembly scriptable.
    * @param box the specified viewsheet sandbox.
    */
   public UploadVSAScriptable(ViewsheetSandbox box) {
      super(box);
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "UploadVSA";
   }

   @Override
   protected boolean hasActions() {
      return false;
   }

   /**
    * Initialize the assembly properties.
    */
   @Override
   protected void addProperties() {
      super.addProperties();
      UploadVSAssemblyInfo info = (UploadVSAssemblyInfo) getVSAssemblyInfo();

      try {
         addFunctionProperty(getClass(), "isLoaded");
         addFunctionProperty(getClass(), "getFileName");
         addProperty("submitOnChange", "isSubmitOnChange", "setSubmitOnChange",
                     boolean.class, info.getClass(), info);
      }
      catch(Exception ex) {
         LOG.warn("Failed to register the upload properties and functions", ex);
      }
   }

   /**
    * Check if file is loaded.
    */
   public boolean isLoaded() {
      UploadVSAssemblyInfo info = (UploadVSAssemblyInfo) getVSAssemblyInfo();

      if(info != null) {
         return info.isLoaded();
      }

      return false;
   }

   /**
    * Check if file is loaded.
    */
   public String getFileName() {
      UploadVSAssemblyInfo info = (UploadVSAssemblyInfo) getVSAssemblyInfo();

      if(info != null && info.getFileName() != null) {
         File upload = FileSystemService.getInstance().getCacheFile(info.getFileName());
         return upload.getPath();
      }

      return null;
   }

   /**
    * Get the suffix of a property, may be "" or [].
    * @param prop the property.
    */
   @Override
   public String getSuffix(Object prop) {
      if("getFileName".equals(prop) || "isLoaded".equals(prop)) {
         return "()";
      }

      return super.getSuffix(prop);
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(UploadVSAScriptable.class);
}
