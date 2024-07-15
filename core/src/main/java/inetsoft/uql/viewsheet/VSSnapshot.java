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
package inetsoft.uql.viewsheet;

import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.Principal;

/**
 * Viewsheet snapshot, stores one state of a viewsheet.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class VSSnapshot extends AbstractSheet {
   /**
    * Constructor.
    */
   public VSSnapshot() {
      super();
   }

   /**
    * Constructor.
    */
   public VSSnapshot(AssetEntry entry, Viewsheet vs) {
      this(entry, vs, null);
   }

   /**
    * Constructor.
    */
   public VSSnapshot(AssetEntry entry, Viewsheet vs, String description) {
      this();
      ByteArrayOutputStream out = new ByteArrayOutputStream();

      try {
         PrintWriter writer =
            new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
         vs.writeState(writer, true);
         writer.close();
      }
      catch(Exception ex) {
         // ignore it
      }

      this.entry = entry;
      this.state = out.toByteArray();
      this.description = description;
   }

   /**
    * Create a viewsheet with the contained state information.
    * @param rep the specified asset repository.
    * @param user the specified principal.
    */
   public Viewsheet createViewsheet(AssetRepository rep, Principal user)
      throws Exception
   {
      Viewsheet vs = (Viewsheet) rep.getSheet(entry, user, false,
                                              AssetContent.ALL);

      if(vs == null) {
         throw new Exception(Catalog.getCatalog().getString(
            "viewer.viewsheet.snapDependsNotFound"));
      }

      if(state != null) {
         InputStream in = new ByteArrayInputStream(state);
         Document doc = Tool.parseXML(in);
         Element elem = doc.getDocumentElement();
         vs.parseState(elem);
      }

      return vs;
   }

   /**
    * Get the size of this sheet.
    * @return the size of this sheet.
    */
   @Override
   public Dimension getPixelSize() {
      return new Dimension(AssetUtil.defw, AssetUtil.defh);
   }

   /**
    * Check if contains an assembly.
    * @param name the specified assembly name.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   @Override
   public boolean containsAssembly(String name) {
      return false;
   }

   /**
    * Get an assembly by its entry.
    * @param entry the specified assembly entry.
    * @return the assembly, <tt>null</tt> if not found.
    */
   @Override
   public Assembly getAssembly(AssemblyEntry entry) {
      return null;
   }

   /**
    * Get an assembly by its name.
    * @param name the specified assembly name.
    * @return the assembly, <tt>null</tt> if not found.
    */
   @Override
   public Assembly getAssembly(String name) {
      return null;
   }

   /**
    * Get all the assemblies.
    * @return all the assemblies.
    */
   @Override
   public Assembly[] getAssemblies() {
      return new Assembly[0];
   }

   /**
    * Get the gap between two assemblies.
    * @return the gap between two assemblies.
    */
   @Override
   protected int getGap() {
      return 0;
   }

   /**
    * Get the outer dependents.
    * @return the outer dependents.
    */
   @Override
   public AssetEntry[] getOuterDependents() {
      return new AssetEntry[] {entry};
   }

   /**
    * Rename an outer dependent.
    * @param oentry the specified old entry.
    * @param nentry the specified new entry.
    */
   @Override
   public void renameOuterDependent(AssetEntry oentry, AssetEntry nentry) {
      if(oentry.equals(entry)) {
         if(oentry.getProperty("onReport") != null) {
            nentry.setProperty("onReport", oentry.getProperty("onReport"));
         }

         entry = nentry;
      }
   }

   /**
    * Get the outer dependencies.
    * @return the outer dependencies.
    */
   @Override
   public AssetEntry[] getOuterDependencies(boolean sort) {
      return new AssetEntry[0];
   }

   /**
    * Add an outer dependency.
    * @param entry the specified entry.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   @Override
   public boolean addOuterDependency(AssetEntry entry) {
      return true;
   }

   /**
    * Remove an outer dependency.
    * @param entry the specified entry.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   @Override
   public boolean removeOuterDependency(AssetEntry entry) {
      return true;
   }

   /**
    * Remove all the outer dependencies.
    */
   @Override
   public void removeOuterDependencies() {
   }

   /**
    * Update this sheet.
    * @param rep the specified asset repository.
    * @param entry the specified entry stored in.
    * @param user the specified principal.
    */
   @Override
   public boolean update(AssetRepository rep, AssetEntry entry, Principal user)
   {
      return true;
   }

   /**
    * Check if the sheet is valid.
    */
   @Override
   public void checkValidity(boolean checkCrossJoins) throws Exception {
   }

   /**
    * Check if the dependency is valid.
    */
   @Override
   public void checkDependencies() {
   }

   /**
    * Get the type of the sheet.
    * @return the type of the sheet.
    */
   @Override
   public int getType() {
      return VIEWSHEET_SNAPSHOT_ASSET;
   }

   /**
    * Reset the sheet.
    */
   @Override
   public void reset() {
   }

   /**
    * Get the assemblies depended on of an assembly in a viewsheet.
    * @param entry the specified assembly entry.
    */
   @Override
   public AssemblyRef[] getDependeds(AssemblyEntry entry) {
      return new AssemblyRef[0];
   }

   /**
    * Get the assemblies depended on of an assembly in a viewsheet.
    * @param entry the specified assembly entry.
    * @param view <tt>true</tt> to include view, <tt>false</tt> otherwise.
    * @param out <tt>out</tt> to include out, <tt>false</tt> otherwise.
    */
   @Override
   public AssemblyRef[] getDependeds(AssemblyEntry entry, boolean view,
                                     boolean out)
   {
      return new AssemblyRef[0];
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<VSSnapshot class=\"" + getClass().getName() + "\">");

      writer.println("<assetEntry>");
      entry.writeXML(writer);
      writer.println("</assetEntry>");

      String val = state == null ? null : Encoder.encodeAsciiHex(state);

      if(val != null) {
         writer.println("<state>");
         writer.print("<![CDATA[" + val + "]]>");
         writer.println("</state>");
      }

      if(description != null && !("").equals(description)) {
         writer.println("<description>");
         writer.print("<![CDATA[" + description + "]]>");
         writer.println("</description>");
      }

      writer.println("</VSSnapshot>");
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      Element enode = Tool.getChildNodeByTagName(elem, "assetEntry");
      enode = Tool.getFirstChildNode(enode);
      entry = AssetEntry.createAssetEntry(enode);
      Element node = Tool.getChildNodeByTagName(elem, "description");

      if(node != null) {
         description = Tool.getValue(node);
      }

      Element snode = Tool.getChildNodeByTagName(elem, "state");

      if(snode != null) {
         String val = Tool.getValue(snode);
         state = Encoder.decodeAsciiHex(val);
      }
   }

   /**
    * Clone this viewsheet snapshot.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone VSSnapshot", ex);
      }

      return null;
   }

   /**
    * Get the description.
    * @return the description.
    */
   @Override
   public String getDescription() {
      String desc = entry.getDescription();
      String preview = Catalog.getCatalog().getString("Preview");
      desc = desc.startsWith(preview) ? desc.substring(preview.length()) : desc;

      return description == null || ("").equals(description) ?
         desc : desc + " " + description;
   }

   /**
    * Set the description string.
    * @param desc new description string.
    */
   public void setSnapshotDescription(String desc) {
      this.description = desc;
   }

   /**
    * Get the description string.
    * @return current description string.
    */
   public String getSnapshotDescription() {
      return this.description;
   }

   private AssetEntry entry;
   private byte[] state;
   private String description;

   private static final Logger LOG =
      LoggerFactory.getLogger(VSSnapshot.class);
}
