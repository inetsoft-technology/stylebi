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
package inetsoft.report.pdf;

/**
 * This class holds the PDF encrypt info attributes. It can be set in
 * PDFPrinter to encrypt the generated PDF file.
 *
 * @version 8.0, 9/20/2005
 * @author Inetsoft Technology
 */
public class PDFEncryptInfo {
   /**
    * Check if encrypt enabled.
    */
   public boolean isEncrypted() {
      return owner != null || user != null;
   }
   
   /**
    * Set owner password(plain text).
    */
   public void setOwnerPassword(String password) {
      owner = password;
   }

   /**
    * Get the owner password(plain text).
    */
   public String getOwnerPassword() {
      return owner;
   }
   
   /**
    * Set the user password(plain text).
    */
   public void setUserPassword(String password) {
      user = password;
   }
   
   /**
    * Get the user password(plain text).
    */
   public String getUserPassword() {
      return user;
   }
   
   /**
    * Get the permission code.
    */
   public int getPermissionCode() {
      int code = -4;
      
      if(!isPrintPermitted()) {
         code -= PRINT;
      }
      
      if(!isCopyPermitted()) {
         code -= COPY_CONTENT;
      }
      
      if(!isChangePermitted()) {
         code -= CHANGE_CONTENT;
      }

      if(!isAddPermitted()) {
         code -= ADD_ANNOTATIONS;
      }
      
      return code;
   }

   /**
    * Set the print permission;
    */
   public void setPrintPermission(boolean perm) {
      printable = perm;
   }
   
   /**
    * Check if print permitted.
    */
   public boolean isPrintPermitted() {
      return printable;
   }
   
   /**
    * Set the change content permission.
    */
   public void setChangePermission(boolean perm) {
      changable = perm;
   }
   
   /**
    * Check if the change content operation permitted.
    */
   public boolean isChangePermitted() {
      return changable;
   }
   
   /**
    * Set the copy content operation permission.
    */
   public void setCopyPermission(boolean perm) {
      copypermission = perm;
   }
   
   /**
    * Check if the copy content operation permitted.
    */
   public boolean isCopyPermitted() {
      return copypermission;
   }
   
   /**
    * Set the add content permission.
    */
   public void setAddPermission(boolean perm) {
      addpermission = perm;
   }
   
   /** 
    * Check if the add content operation permitted.
    */
   public boolean isAddPermitted() {
      return addpermission;
   }

   public String toString() {
      return owner + "-" + user + "-" + printable + "-" + copypermission +
         "-" + changable + "-" + addpermission;
   }
   
   private String owner;
   private String user;
   
   private boolean printable = false;
   private boolean changable = false;
   private boolean copypermission = false;
   private boolean addpermission = false;
   
   private static final int PRINT          =  4;
   private static final int CHANGE_CONTENT =  8;
   private static final int COPY_CONTENT   = 16;
   private static final int ADD_ANNOTATIONS= 32;
}

