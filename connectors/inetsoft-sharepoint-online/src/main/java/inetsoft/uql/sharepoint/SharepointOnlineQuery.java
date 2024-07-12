/*
 * inetsoft-sharepoint-online - StyleBI is a business intelligence web application.
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
package inetsoft.uql.sharepoint;

import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

@View(vertical = true, value = {
   @View1("site"),
   @View1("list")
})
public class SharepointOnlineQuery extends TabularQuery {
   public SharepointOnlineQuery() {
      super(SharepointOnlineDataSource.TYPE);
   }

   @Property(label = "Site", required = true)
   @PropertyEditor(tagsMethod = "getSites")
   public String getSite() {
      return site;
   }

   @SuppressWarnings("unused")
   public void setSite(String site) {
      this.site = site;
   }

   @Property(label = "List", required = true)
   @PropertyEditor(dependsOn = "site", tagsMethod = "getLists")
   public String getList() {
      return list;
   }

   @SuppressWarnings("unused")
   public void setList(String list) {
      this.list = list;
   }

   @SuppressWarnings("unused")
   public String[][] getSites() {
      SharepointOnlineDataSource ds = (SharepointOnlineDataSource) getDataSource();
      return SharepointOnlineRuntime.getSites(ds);
   }

   @SuppressWarnings("unused")
   public String[][] getLists() {
      SharepointOnlineDataSource ds = (SharepointOnlineDataSource) getDataSource();
      return SharepointOnlineRuntime.getLists(ds, site);
   }

   @Override
   public XTypeNode[] getOutputColumns() {
      XTypeNode[] columns = super.getOutputColumns();

      if((columns == null || columns.length == 0) && getDataSource() != null) {
         columns = fetchColumns();

         if(columns.length > 0) {
            setOutputColumns(columns);
         }
      }

      return columns;
   }

   private XTypeNode[] fetchColumns() {
      SharepointOnlineDataSource ds = (SharepointOnlineDataSource) getDataSource();
      return SharepointOnlineRuntime.getListColumns(ds, site, list);
   }

   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(site != null) {
         writer.format("<site><![CDATA[%s]]></site>%n", site);
      }

      if(list != null) {
         writer.format("<list><![CDATA[%s]]></list>%n", list);
      }
   }

   @Override
   protected void parseContents(Element element) throws Exception {
      super.parseContents(element);
      site = Tool.getChildValueByTagName(element, "site");
      list = Tool.getChildValueByTagName(element, "list");
   }

   private String site;
   private String list;
}
