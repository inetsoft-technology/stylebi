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
package inetsoft.report.composition.command;

import inetsoft.report.composition.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.*;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.io.PrintWriter;
import java.security.Principal;

/**
 * Init grid command.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class InitGridCommand extends AssetCommand {
   /**
    * Constructor.
    */
   public InitGridCommand() {
      super();
   }

   /**
    * Constructor.
    * @param id worksheet id.
    * @param cols cols's width.
    * @param rows rows's height.
    * @param entry worksheet entry.
    */
   public InitGridCommand(String id, int[] cols, int[] rows, AssetEntry entry) {
      this(id, cols, rows, entry, null, null);
   }

   /**
    * Constructor.
    * @param id worksheet id.
    * @param cols cols's width.
    * @param rows rows's height.
    * @param entry worksheet entry.
    * @param vsid viewsheet id.
    */
   public InitGridCommand(String id, int[] cols, int[] rows, AssetEntry entry, String vsid) {
      this(id, cols, rows, entry, null, vsid);
   }

   /**
    * Constructor.
    * @param id worksheet id.
    * @param cols cols's width.
    * @param rows rows's height.
    * @param entry worksheet entry.
    * @param origEntry the base entry of a preview sheet.
    * @param vsid viewsheet id.
    */
   public InitGridCommand(String id, int[] cols, int[] rows, AssetEntry entry,
                          AssetEntry origEntry, String vsid)
   {
      this();
      this.id = id;
      this.cols = cols;
      this.rows = rows;

      Principal user0 = ThreadContext.getContextPrincipal();
      Catalog ucata = Catalog.getCatalog(user0, Catalog.REPORT);
      String str = entry.getAlias() != null ?
         ucata.getString(entry.getAlias()) : ucata.getString(entry.getName());
      entry.setProperty("localStr", str);

      put("entry", entry);
      put("vsid", vsid);

      if(origEntry != null) {
         put("origEntry", origEntry);
      }
   }

   /**
    * Set the initing flag.
    */
   public void setIniting(boolean initing) {
      put("initing", initing + "");
   }

   /**
    * Check if is initing.
    */
   public boolean isIniting() {
      return "true".equals(get("initing"));
   }

   /**
    * Set the editable flag.
    */
   public void setEditable(boolean editable) {
      put("editable", editable + "");
   }

   /**
    * Check if is editable.
    */
   private boolean isEditable() {
      return !"false".equals(get("editable"));
   }

   /**
    * Set the lockOwner.
    */
   public void setLockOwner(String lockOwner) {
      put("lockOwner", lockOwner);
   }

   /**
    * Get the lockOwner.
    */
   public String getLockOwner() {
      return (String) get("lockOwner");
   }

   /**
    * Get last modified.
    */
   public String getLastModified() {
      return (String) get("lastModified");
   }

   /**
    * Set last modified.
    */
   public void setLastModified(String modified) {
      if(modified != null) {
         put("lastModified", modified);
      }
   }

   /**
    * Set the embedded id of the sheet.
    */
   public void setEmbeddedID(String id) {
      put("eid", id);
   }

   /**
    * Get the embedded id of the sheet.
    */
   public String getEmbeddedID() {
      return (String) get("eid");
   }

   /**
    * Set the parent id of worksheet.
    */
   public void setParentID(String id) {
      put("pid", id);
   }

   /**
    * Get the parent id of worksheet.
    */
   public String getParentID() {
      return (String) get("pid");
   }

   /**
    * Set the view size.
    */
   public void setViewSize(Dimension size) {
      put("viewWidth", size.width + "");
      put("viewHeight", size.height + "");
   }

   /**
    * Write contents.
    * @param writer the specified print writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);
      writer.println("<id value=\"" + id + "\" colCount=\"" + cols.length +
         "\" rowCount=\"" + rows.length + "\"/>");

      for(int i = 0; i < cols.length; i++) {
         if(cols[i] != AssetUtil.defw) {
            writer.println("<c idx=\"" + i +"\" w=\"" + cols[i] + "\"/>");
         }
      }

      for(int i = 0; i < rows.length; i++) {
         if(rows[i] != AssetUtil.defh) {
            writer.println("<r idx=\"" + i +"\" h=\"" + rows[i] + "\"/>");
         }
      }
   }

   /**
    * Read in the contents of this object from an xml tag.
    * @param tag the specified xml element.
    */
   @Override
   protected void parseContents(Element tag) throws Exception {
      super.parseContents(tag);
      Element id = Tool.getChildNodeByTagName(tag, "id");
      this.id = Tool.getAttribute(id, "value");
      int colCount = Integer.parseInt(Tool.getAttribute(id, "colCount"));
      int rowCount = Integer.parseInt(Tool.getAttribute(id, "rowCount"));
      NodeList list = Tool.getChildNodesByTagName(tag, "c");
      this.cols = new int[colCount];

      for(int i = 0; i < cols.length; i++) {
         cols[i] = AssetUtil.defw;
      }

      if(list != null) {
         for(int i = 0; i < list.getLength(); i++) {
            Element col = (Element) list.item(i);
            cols[Integer.parseInt(Tool.getAttribute(col, "idx"))] =
               Integer.parseInt(Tool.getAttribute(col, "w"));
         }
      }

      list = Tool.getChildNodesByTagName(tag, "r");
      this.rows = new int[rowCount];

      for(int i = 0; i < rows.length; i++) {
         rows[i] = AssetUtil.defh;
      }

      if(list != null) {
         for(int i = 0; i < list.getLength(); i++) {
            Element row = (Element) list.item(i);
            rows[Integer.parseInt(Tool.getAttribute(row, "idx"))] =
               Integer.parseInt(Tool.getAttribute(row, "h"));
         }
      }
   }

   /**
    * Get id.
    */
   @Override
   public String getID() {
      return id;
   }

   private String id;
   private int[] cols, rows;
}
