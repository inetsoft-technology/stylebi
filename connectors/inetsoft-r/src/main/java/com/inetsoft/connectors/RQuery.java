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
package com.inetsoft.connectors;

import inetsoft.uql.tabular.ScriptedQuery;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * Data source for the R connector.
 */
@View(vertical = true, value = {
   @View1(type=ViewType.LABEL, text="Query R Script", col=1, paddingLeft=3),
   @View1("script"),
   @View1(type=ViewType.LABEL, text="Note: Script must return a single data frame", col=1, paddingLeft=3),
   @View1("preExecute"),
   @View1("postExecute")
})
public class RQuery extends TabularQuery implements ScriptedQuery {
   public static final String TYPE = "R";

   public RQuery() {
      super(TYPE);
   }

   @Property(label = "Script")
   @PropertyEditor(rows = 15, columns = 40)
   public String getScript() {
      return script;
   }

   public void setScript(String script) {
      this.script = script;
   }

   @Property(label = "Pre-execution JavaScript")
   @PropertyEditor(rows = 15, columns = 40)
   public String getPreExecute() {
      return preExecute;
   }

   public void setPreExecute(String preExecute) {
      this.preExecute = preExecute;
   }

   @Property(label = "Post-execution JavaScript")
   @PropertyEditor(rows = 15, columns = 40)
   public String getPostExecute() {
      return postExecute;
   }

   public void setPostExecute(String postExecute) {
      this.postExecute = postExecute;
   }

   @Override
   public void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(script != null) {
         writer.println("<script><![CDATA[" + script + "]]></script>");
      }

      if(preExecute != null) {
         writer.format("<preExecute><![CDATA[%s]]></preExecute>%n", preExecute);
      }

      if(postExecute != null) {
         writer.format("<postExecute><![CDATA[%s]]></postExecute>%n", postExecute);
      }
   }

   @Override
   public void parseAttributes(Element root) throws Exception {
      super.parseAttributes(root);
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      script = Tool.getChildValueByTagName(root, "script");
      preExecute = Tool.getChildValueByTagName(root, "preExecute");
      postExecute = Tool.getChildValueByTagName(root, "postExecute");
   }

   @Override
   public String getInputScript() {
      return preExecute != null && !preExecute.trim().isEmpty() ? preExecute : null;
   }

   @Override
   public String getOutputScript() {
      return postExecute != null && !postExecute.trim().isEmpty() ? postExecute : null;
   }

   private String script;
   private String preExecute;
   private String postExecute;
}
