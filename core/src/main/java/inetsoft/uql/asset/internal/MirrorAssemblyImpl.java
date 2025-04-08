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
package inetsoft.uql.asset.internal;

import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.asset.*;
import inetsoft.util.Tool;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.security.Principal;
import java.util.*;

/**
 * MirrorAssemblyImpl implements MirrorAssembly.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class MirrorAssemblyImpl implements MirrorAssembly {
   /**
    * Constructor.
    */
   public MirrorAssemblyImpl() {
      super();
      this.auto = true;
      setLastModified(System.currentTimeMillis());
   }

   /**
    * Constructor.
    */
   public MirrorAssemblyImpl(AssetEntry entry, boolean outer,
                             WSAssembly assembly) {
      this();

      if(outer && entry == null) {
         throw new RuntimeException("AssetEntry should not be null!");
      }

      if(assembly == null) {
         throw new RuntimeException("assembly is null!");
      }

      this.entry = entry;
      this.outer = outer;

      setAssembly(assembly);
   }

   /**
    * Get the worksheet entry.
    * @return the worksheet entry of the mirror assembly.
    */
   @Override
   public AssetEntry getEntry() {
      return entry;
   }

   /**
    * Set the worksheet entry.
    * @param entry the specified worksheet entry.
    */
   @Override
   public void setEntry(AssetEntry entry) {
      if(!isOuterMirror()) {
         throw new RuntimeException("Only outer mirror requires asset entry!");
      }

      this.entry = entry;
   }

   /**
    * Get the assembly name.
    * @return the assembly name.
    */
   @Override
   public String getAssemblyName() {
      return mirror;
   }

   /**
    * Check if is outer mirror.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isOuterMirror() {
      return outer;
   }

   /**
    * Set last modified time.
    * @param modified the specified last modified time.
    */
   @Override
   public void setLastModified(long modified) {
      this.modified = modified;
      this.smodified = AssetUtil.getDateTimeFormat().format(new Date(modified));
   }

   /**
    * Get the last modified time.
    * @return the last modified time of the worksheet.
    */
   @Override
   public long getLastModified() {
      return modified;
   }

   /**
    * Check if is auto update.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isAutoUpdate() {
      return auto || !isOuterMirror();
   }

   /**
    * Set auto update.
    * @param auto <tt>true</tt> to open auto update.
    */
   @Override
   public void setAutoUpdate(boolean auto) {
      if(!isOuterMirror()) {
         return;
      }

      this.auto = auto;
   }

   /**
    * Update the inner mirror.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   @Override
   public boolean update() {
      return update0() != null;
   }

   private WSAssembly update0() {
      Worksheet ws = this.ws;

      if(ws == null) {
         return null;
      }

      WSAssembly assembly = (WSAssembly) ws.getAssembly(mirror);

      if(assembly == null && !mirror.startsWith(Assembly.TABLE_VS)) {
         return null;
      }

      synchronized(this) {
         if(assembly != null) {
            this.assembly = assembly;
         }
         else {
            assembly = this.assembly;
         }
      }

      if(assembly != null) {
         assembly.update();
      }

      return assembly;
   }

   /**
    * Update the outer mirror.
    * @param engine the specified asset repository.
    * @param user the specified user.
    */
   @Override
   public void updateMirror(AssetRepository engine, Principal user) throws Exception {
      if(!isOuterMirror()) {
         return;
      }

      List list = (List) local.get();

      if(list == null) {
         list = new ArrayList();
         local.set(list);
      }

      if(list.contains(entry)) {
         return;
      }

      try {
         list.add(entry);
         WSAssembly[] created =
            AssetUtil.copyOuterAssemblies(engine, entry, user, this.ws, null);
         setAssembly(created[created.length - 1]);
         Worksheet ws = (Worksheet) engine.getSheet(entry, user, false, AssetContent.ALL);

         setLastModified(ws.getLastModified());
      }
      finally {
         list.remove(entry);
      }
   }

   /**
    * Set the assembly.
    * @param assembly the assembly of the mirror assembly.
    */
   public void setAssembly(WSAssembly assembly) {
      synchronized(this) {
         if(assembly != null) {
            assembly.update();
         }

         // keep reference for the mirrored assembly might change at runtime
         this.assembly = assembly;
         this.mirror = assembly.getName();
      }
   }

   /**
    * Get the assembly.
    * @return the assembly of the mirror assembly.
    */
   public Assembly getAssembly(boolean cache) {
      synchronized(this) {
         if(!cache && assembly != null &&
            !assembly.getName().startsWith(TableAssembly.TABLE_VS))
         {
            assembly = null;
         }
      }

      return getAssembly();
   }

   /**
    * Get the assembly.
    * @return the assembly of the mirror assembly.
    */
   @Override
   public Assembly getAssembly() {
      synchronized(this) {
         if(assembly != null) {
            return assembly;
         }
      }

      return update0();
   }

   /**
    * Check if the mirror assembly is valid.
    */
   @Override
   public void checkValidity(boolean checkCrossJoins) throws Exception {
      if(assembly != null) {
         assembly.checkValidity(checkCrossJoins);
      }
   }

   /**
    * Set the worksheet.
    * @param ws the specified worksheet.
    */
   public void setWorksheet(Worksheet ws) {
      this.ws = ws;
      update();
   }

   /**
    * Get the worksheet.
    * @return the worksheet of the assembly.
    */
   public Worksheet getWorksheet() {
      return ws;
   }

   /**
    * Rename the assemblies depended on.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   @Override
   public void renameDepended(String oname, String nname) {
      if(oname.equals(mirror) && ws != null) {
         WSAssembly assembly = (WSAssembly) ws.getAssembly(nname);

         if(assembly != null) {
            setAssembly(assembly);
         }
      }
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<mirrorAssembly");

      if(outer) {
         writer.print(" outer=\"" + outer + "\"");
      }

      if(!auto) {
         writer.print(" auto=\"" + auto + "\"");
      }

      writer.print(" modified=\"" + modified + "\"");

      if(entry != null) {
         writer.print(" description=\"" +
            Tool.escape(entry.getDescription()) + "\"");
         writer.print(" source=\"" + Tool.escape(entry.toIdentifier()) + "\"");
      }

      writer.println(">");

      writer.print("<mirror>");
      writer.print("<![CDATA[" + mirror + "]]>");
      writer.println("</mirror>");

      if(entry != null && !Tool.isCompact()) {
         // CAUTION! This tag will be used to find dependency
         writer.println();
         writer.print("<assetDependency>");
         writer.print("<![CDATA[" + Tool.escape(entry.toIdentifier()) + "]]>");
         writer.println("</assetDependency>");
      }

      setLastModified(modified);

      writer.print("<smodified>");
      writer.print("<![CDATA[" + smodified + "]]>");
      writer.println("</smodified>");

      writer.println("</mirrorAssembly>");
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      outer = "true".equals(Tool.getAttribute(elem, "outer"));
      auto = !"false".equals(Tool.getAttribute(elem, "auto"));
      modified = Long.parseLong(Tool.getAttribute(elem, "modified"));
      String identifier = Tool.getAttribute(elem, "source");

      if(identifier != null) {
         identifier = handleWSOrgMismatch(identifier);
         entry = AssetEntry.createAssetEntry(identifier);
      }

      mirror = Tool.getChildValueByTagName(elem, "mirror");
      smodified = Tool.getChildValueByTagName(elem, "smodified");
      setLastModified(modified);
   }

   /**
    * Clone the object.
    */
   @Override
   public synchronized Object clone() {
      try {
         MirrorAssemblyImpl mirror = (MirrorAssemblyImpl) super.clone();

         if(entry != null) {
            mirror.entry = (AssetEntry) entry.clone();
         }

         if(assembly != null) {
            mirror.assembly = (WSAssembly) assembly.clone();
         }

         return mirror;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   /**
    * Clear cache.
    */
   public synchronized void clearCache() {
      assembly = null;
   }

   public String handleWSOrgMismatch(String identifier){
      String curOrgId = OrganizationManager.getInstance().getCurrentOrgID();
      int numCarets = StringUtils.countMatches(identifier, "^");

      if(numCarets >= 4) {
         int orgIdx = identifier.lastIndexOf("^");
         return identifier.substring(0, orgIdx + 1) + curOrgId;
      }
      else {
         return identifier + "^" + curOrgId;
      }
   }

   private static ThreadLocal local = new ThreadLocal(); // thread local

   private AssetEntry entry; // asset entry
   private String mirror; // mirror name
   private boolean outer; // outer mirror flag
   private boolean auto; // auto update flag
   private long modified; // last modified
   private String smodified; // modified string, for flash side

   private transient WSAssembly assembly; // runtime assembly
   private transient Worksheet ws; // runtime worksheet

   private static final Logger LOG =
      LoggerFactory.getLogger(MirrorAssemblyImpl.class);
}
