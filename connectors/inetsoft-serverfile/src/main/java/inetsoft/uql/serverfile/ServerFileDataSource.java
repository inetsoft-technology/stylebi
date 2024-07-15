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
package inetsoft.uql.serverfile;

import inetsoft.sree.SreeEnv;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.File;
import java.io.PrintWriter;
import java.util.Objects;

@SuppressWarnings("unused")
@View(vertical=false, value={
        @View1(value="file")
})
public class ServerFileDataSource extends TabularDataSource<ServerFileDataSource> {
   public static final String TYPE = "SERVER_FILE";

   public ServerFileDataSource() {
      super(TYPE, ServerFileDataSource.class);
   }

   /**
    * Get the file. Set label as "Root Folder", select files from anywhere.
    */
   @Property(label="Root Folder", required = true)
   @PropertyEditor(editorProperties={
      @EditorProperty(name="relativeTo", method="getHomeFolder"),
      @EditorProperty(name="foldersOnly", value = "true")
   })
   public File getFile() {
      return file;
   }

   /**
    * Set the file.
    */
   public void setFile(File file) {
      this.file = file;
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(file != null) {
         writer.println("<rootFile><![CDATA[" + file.getAbsolutePath() + "]]></rootFile>");
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);

      String obj = Tool.getChildValueByTagName(root, "rootFile");

      if(obj != null) {
         file = new File(obj);
      }
   }

   public String getHomeFolder() {
      return SreeEnv.getProperty("data.file.basedir");
   }

   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof ServerFileDataSource)) {
         return false;
      }

      ServerFileDataSource ds = (ServerFileDataSource) obj;
      return super.equals(ds) && Objects.equals(file, ds.file);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), file);
   }

   private File file;

   private static final Logger LOG = LoggerFactory.getLogger(ServerFileDataSource.class.getName());
}
