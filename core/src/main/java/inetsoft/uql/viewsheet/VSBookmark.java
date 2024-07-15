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

import inetsoft.sree.security.IdentityID;
import inetsoft.uql.viewsheet.internal.AnnotationVSUtil;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Viewsheet bookmark, stores the bookmarks of a viewsheet per user.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class VSBookmark implements XMLSerializable {
   /**
    * The home bookmark.
    */
   public static final String HOME_BOOKMARK = "(Home)";
   /**
    * The default state when opening a viewsheet.
    */
   public static final String INITIAL_STATE = "(Initial)";

   /**
    * Create a viewsheet bookmark.
    */
   public VSBookmark() {
      super();

      this.bmap = new HashMap<>();
      this.bookmarksInfo = new HashMap<>();
   }

   /**
    * Create a viewsheet bookmark.
    * @param identifier the specified identifier.
    * @param user the specified user.
    */
   public VSBookmark(String identifier, IdentityID user) {
      this();

      this.identifier = identifier;
      this.user = user;
   }

   /**
    * Get the viewsheet identifier.
    * @return the viewsheet identifier.
    */
   public String getIdentifier() {
      return identifier;
   }

   /**
    * Set the viewsheet identifier.
    * @param identifier the specified viewsheet identifier.
    */
   public void setIdentifier(String identifier) {
      this.identifier = identifier;
   }

   /**
    * Get the user of this viewsheet bookmark.
    * @return the user of this viewsheet bookmark.
    */
   public IdentityID getUser() {
      return user;
   }

   /**
    * Set the user.
    * @param user the specified user.
    */
   public void setUser(IdentityID user) {
      this.user = user;

      for(VSBookmarkInfo info : bookmarksInfo.values()) {
         info.setOwner(user);
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

      VSBookmark that = (VSBookmark) o;
      return Objects.equals(identifier, that.identifier) &&
         Objects.equals(user, that.user) &&
         Objects.equals(bookmark, that.bookmark);
   }

   @Override
   public int hashCode() {
      return Objects.hash(identifier, user, bookmark);
   }

   /**
    * Check if contains a bookmark.
    * @param name the name of the specified bookmark.
    * @return <tt>true</tt> if contains it, <tt>false</tt> otherwise.
    */
   public boolean containsBookmark(String name) {
      return bmap.containsKey(name);
   }

   /**
    * Get user defined default bookmark.
    * @return user defined default bookmark if any.
    */
   public DefaultBookmark getDefaultBookmark() {
      return bookmark;
   }

   /**
    * Set user defined default bookmark.
    * @param bookmark the specified user defined bookmark.
    */
   public void setDefaultBookmark(DefaultBookmark bookmark) {
      this.bookmark = bookmark;
   }

   /**
    * Get the bookmark.
    * @param name the specified bookmark name.
    * @param vs the specifie viewsheet.
    * @return the viewsheet applied this bookmark.
    */
   public Viewsheet getBookmark(String name, Viewsheet vs) {
      byte[] bytes = (byte[]) bmap.get(name);

      if(bytes == null) {
         return vs;
      }

      try {
         ByteArrayInputStream in = new ByteArrayInputStream(bytes);
         Document doc = Tool.parseXML(in);
         Element elem = doc.getDocumentElement();

         if(HOME_BOOKMARK.equals(name)) {
            AnnotationVSUtil.parseAllAnnotations(elem, vs);
         }
         else {
            vs.parseState(elem, true);
         }
      }
      catch(Exception ex) {
         // should not happen
         LOG.error("Failed to parse bookmark", ex);
      }

      return vs;
   }

   /**
    * Get specified bookmarks content.
    */
   public Object getBookmarkData(String name) {
      return bmap.get(name);
   }

   /**
    * Set specified bookmarks content.
    */
   public void setBookmarkData(String name, Object data) {
      bmap.put(name, data);
   }

   /**
    * Add the default bookmark.
    * @param vs the specified viewsheet.
    */
   public void addHomeBookmark(Viewsheet vs, boolean runtime) {
      addBookmark(HOME_BOOKMARK, vs, VSBookmarkInfo.ALLSHARE, false, runtime);
   }

   /**
    * Get the home bookmark.
    * @param vs the specified viewsheet.
    * @return the viewsheet applied the home bookmark.
    */
   public Viewsheet getHomeBookmark(Viewsheet vs) {
      return getBookmark(HOME_BOOKMARK, vs);
   }

   /**
    * Add current state as a bookmark.
    * @param name the specified bookmark name.
    * @param vs the specified viewsheet.
    */
   public void addBookmark(String name, Viewsheet vs, int type,
      boolean readOnly, boolean runtime)
   {
      try {
         ByteArrayOutputStream out = new ByteArrayOutputStream();
         PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
         vs.writeState(writer, runtime);
         writer.close();
         bmap.put(name, out.toByteArray());
         long ctime = System.currentTimeMillis();

         if(bookmarksInfo.get(name) == null) {
            bookmarksInfo.put(name, new VSBookmarkInfo(name, type, user, readOnly, ctime, ctime, ctime));
         }
         else {
            bookmarksInfo.put(name, new VSBookmarkInfo(name, type, user, readOnly, ctime));
         }
      }
      catch(Exception ex) {
         // should not happen
         LOG.error("Failed to write bookmark", ex);
      }
   }

   /**
    * Remove a bookmark.
    * @param name the specified bookmark name.
    */
   public void removeBookmark(String name) {
      bmap.remove(name);
      bookmarksInfo.remove(name);
   }

   /**
    * Rename a bookmark.
    * @param nname the specified new bookmark name.
    * @param oname the specified old bookmark name.
    */
   public void editBookmark(String nname, String oname, int type,
      boolean readOnly)
   {
      if(!oname.equals(HOME_BOOKMARK) && !nname.equals(HOME_BOOKMARK)) {
         Object data = bmap.get(oname);

         if(data == null) {
            throw new RuntimeException("Bookmark doesn't exist: " + oname);
         }

         data = bmap.remove(oname);
         bmap.put(nname, data);

         VSBookmarkInfo info = bookmarksInfo.get(oname);
         info.setName(nname);
         info.setType(type);
         info.setReadOnly(readOnly);
         long ctime = new Date().getTime();
         info.setLastModified(ctime);
         bookmarksInfo.remove(oname);
         bookmarksInfo.put(nname, info);
      }
   }

   /**
    * Clear all bookmarks.
    */
   public void clearBookmarks() {
      bmap.clear();
      bookmarksInfo.clear();
   }

   /**
    * Get all bookmark names.
    * @return all bookmark names.
    */
   public String[] getBookmarks() {
      return bmap.keySet().stream()
         .sorted()
         .toArray(String[]::new);
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<bookmarks class=\"" + getClass().getName() + "\"");

      if(bookmark != null) {
         writer.print(" defaultBookmark=\"");
         writer.print(Tool.escape(bookmark.getName()));
         writer.print("\"");

         String defOwner = Tool.escape(bookmark.getOwner().convertToKey());

         if(!"".equals(defOwner)) {
            writer.print(" defaultBookmarkUser=\"");
            writer.print(defOwner);
            writer.print("\"");
         }
      }

      writer.println(">");
      writer.println("<Version>" + FileVersions.ASSET + "</Version>");

      for(Map.Entry<String, Object> e : bmap.entrySet()) {
         String key = e.getKey();
         byte[] bytes = (byte[]) e.getValue();
         String val = Encoder.encodeAsciiHex(bytes);

         writer.println("<bookmark>");
         writer.print("<name>");
         writer.print("<![CDATA[" + key + "]]>");
         writer.println("</name>");
         writer.print("<value>");
         writer.print("<![CDATA[" + val + "]]>");
         writer.println("</value>");

         VSBookmarkInfo info = bookmarksInfo.get(key);

         writer.print("<type>");
         writer.print("<![CDATA[" + info.getType() + "]]>");
         writer.println("</type>");
         writer.print("<readOnly>");
         writer.print("<![CDATA[" + info.isReadOnly() + "]]>");
         writer.println("</readOnly>");
         writer.print("<lastModifyTime>");
         writer.print("<![CDATA[" + info.getLastModified() + "]]>");
         writer.println("</lastModifyTime>");
         writer.print("<lastAccessedTime>");
         writer.print("<![CDATA[" + info.getLastAccessed() + "]]>");
         writer.println("</lastAccessedTime>");
         writer.print("<createTime>");
         writer.print("<![CDATA[" + info.getCreateTime() + "]]>");
         writer.println("</createTime>");
         writer.println("</bookmark>");
      }

      writer.println("</bookmarks>");
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      if(Tool.getAttribute(elem, "defaultBookmark") != null) {
         String name = Tool.getAttribute(elem, "defaultBookmark");
         IdentityID owner = IdentityID.getIdentityIDFromKey(Tool.getAttribute(elem, "defaultBookmarkUser"));
         bookmark = new DefaultBookmark(name, owner);
      }

      NodeList bnodes = Tool.getChildNodesByTagName(elem, "bookmark");

      for(int i = 0; i < bnodes.getLength(); i++) {
         Element bnode = (Element) bnodes.item(i);
         Element nnode = Tool.getChildNodeByTagName(bnode, "name");
         Element vnode = Tool.getChildNodeByTagName(bnode, "value");
         Element typeNode = Tool.getChildNodeByTagName(bnode, "type");
         Element rwNode = Tool.getChildNodeByTagName(bnode, "readOnly");
         Element modifyTimeNode =
            Tool.getChildNodeByTagName(bnode, "lastModifyTime");
         Element accessTimeNode =
                 Tool.getChildNodeByTagName(bnode, "lastAccessedTime");
         Element createTimeNode =
                 Tool.getChildNodeByTagName(bnode, "createTime");

         String name = Tool.getValue(nnode);

         // for BC, remove the old initalize bookmark status
         if("__DEFAULT_BOOKMARK__".equals(name)) {
            continue;
         }

         String val = Tool.getValue(vnode);

         // Fix backwards compatibility issue, in previous versions
         // (before 11.4) all Bookmarks were "private" and did not contain
         // a "type" attribute. Note: this is reverting change from bug
         // bug1353395468311

         int type = typeNode == null ? VSBookmarkInfo.PRIVATE :
            Integer.parseInt(Tool.getValue(typeNode));
         boolean readOnly = rwNode == null || Tool.equals("true", Tool.getValue(rwNode));

         long lastModifyTime = modifyTimeNode == null ? System.currentTimeMillis() :
                 Long.parseLong(Tool.getValue(modifyTimeNode));
         long lastAccessTime = lastModifyTime;

         if(accessTimeNode != null && Long.parseLong(Tool.getValue(accessTimeNode)) > 0) {
            lastAccessTime = Long.parseLong(Tool.getValue(accessTimeNode));
         }

         long createTime = createTimeNode == null ? -1 :
                 Long.parseLong(Tool.getValue(createTimeNode));
         byte[] bytes = Encoder.decodeAsciiHex(val);

         bmap.put(name, bytes);
         VSBookmarkInfo info = new VSBookmarkInfo(name, type, user, readOnly, lastModifyTime, lastAccessTime,
                 createTime);
         bookmarksInfo.put(name, info);
      }
   }

   /**
    * Get specified bookmark info.
    */
   public VSBookmarkInfo getBookmarkInfo(String name) {
      return bookmarksInfo.get(name);
   }

   /**
    * Set specified bookmark info.
    */
   public void setBookmarkInfo(String name, VSBookmarkInfo info) {
      bookmarksInfo.put(name, info);
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      return "VSBookmark@" + super.hashCode() + "[" + user + ", " + identifier +
             ", " + bookmark + ", " + bmap + ", " + bookmarksInfo + "]";
   }

   public class DefaultBookmark {
      public DefaultBookmark(String name, IdentityID owner) {
         this.name = name;
         this.owner = owner;
      }

      public String getName() {
         return name;
      }

      public void setName(String name) {
         this.name = name;
      }

      public IdentityID getOwner() {
         return owner;
      }

      public void setOwner(IdentityID owner) {
         this.owner = owner;
      }

      public String toString() {
         return "[name:" + name + ", owner:" + owner + "]";
      }

      private String name;
      private IdentityID owner;
   }

   private Map<String, Object> bmap; // bookmark map
   private String identifier; // viewsheet identifier
   private IdentityID user; // viewsheet user
   private DefaultBookmark bookmark; // user defined default bookmark
   private Map<String, VSBookmarkInfo> bookmarksInfo;

   private static final Logger LOG = LoggerFactory.getLogger(VSBookmark.class);
}
