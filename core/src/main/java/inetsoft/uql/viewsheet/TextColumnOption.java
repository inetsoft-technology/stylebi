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

import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TextColumnOption stores column options for FormRef.
 *
 * @version 11.2
 * @author InetSoft Technology Corp
 */
public class TextColumnOption extends ColumnOption {
   /**
    * Constructor.
    */
   public TextColumnOption() {
   }

   /**
    * Constructor.
    */
   public TextColumnOption(String pattern, String msg, boolean form) {
      this.pattern = pattern;
      this.msg = msg;
      this.form = form;
   }

   @Override
   public String getErrorMessage() {
      String msg = getMessage();

      if(msg == null || msg.isEmpty()) {
         msg = "Value doesn't match pattern: " + pattern;
      }

      return msg;
   }

   /**
    * Get error pattern.
    */
   public String getPattern() {
      return pattern;
   }

   /**
    * Get column option type.
    * @return the type of column option.
    */
   @Override
   public String getType() {
      return ColumnOption.TEXT;
   }

   /**
    * Check whether the value is invalid by the range setting.
    */
   @Override
   public boolean validate(Object val) {
      if(pattern != null && !"".equals(pattern) && val != null) {
         Pattern patt = Pattern.compile(pattern);
         Matcher match = patt.matcher(val.toString());

         if(!match.matches()) {
            return false;
         }
      }

      return true;
   }

   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(pattern != null) {
         writer.println("<pattern><![CDATA[" + Tool.encodeCDATA(pattern) +
            "]]></pattern>");
      }
   }

   @Override
   protected void parseAttributes(Element tag) throws Exception {
      super.parseAttributes(tag);
      // for bc
      pattern = Tool.getAttribute(tag, "pattern");
   }

   @Override
   protected void parseContents(Element tag) throws Exception {
      super.parseContents(tag);
      Element pnode = Tool.getChildNodeByTagName(tag, "pattern");

      if(pnode != null) {
         pattern = Tool.decodeCDATA(Tool.getValue(pnode));
      }
   }

   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof TextColumnOption) || !super.equals(obj)) {
         return false;
      }

      TextColumnOption opt = (TextColumnOption) obj;
      return Tool.equals(pattern, opt.pattern);
   }

   private String pattern;
}
