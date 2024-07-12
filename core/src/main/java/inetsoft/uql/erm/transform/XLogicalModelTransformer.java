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
package inetsoft.uql.erm.transform;

import inetsoft.util.Tool;
import inetsoft.util.XMLTool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * XLogicalModelTransformer, transforms a logical model node.
 *
 * version 10.1
 * @author InetSoft Technology Corp.
 */
public class XLogicalModelTransformer implements ERMTransformer {
   /**
    * Create a new instance of XLogicalModelTransformer.
    */
   public XLogicalModelTransformer(ERMTransformer transformer) {
      this.transformer = transformer;
   }

   /**
    * Transform the element according to the descriptor.
    * @param elem the element.
    * @param descriptor the transform descriptor.
    */
   @Override
   public void transform(Element elem, TransformDescriptor descriptor) {
      NodeList entityNodes = Tool.getChildNodesByTagName(elem, "entity");

      if(entityNodes != null) {
         transformEntities(entityNodes, descriptor);
      }

      NodeList dimensions = Tool.getChildNodesByTagName(elem, "Dimension");

      if(dimensions != null) {
         transformCubeMembers(dimensions, descriptor);
      }

      Element measure = Tool.getChildNodeByTagName(elem, "Measures");

      if(measure != null) {
         transformMeasures(measure, descriptor);
      }
   }

   /**
    * Transform the entities.
    */
   private void transformEntities(NodeList nodes, TransformDescriptor desc) {
      for(int i = 0; nodes != null && i < nodes.getLength(); i++) {
         Element elem0 = (Element) nodes.item(i);
         NodeList attributes = Tool.getChildNodesByTagName(elem0, "attribute");
         int dbtype = desc.getDBType();

         for(int j = 0; attributes != null && j < attributes.getLength(); j++) {
            Element attrelem = (Element) attributes.item(j);
            Element expr = Tool.getChildNodeByTagName(attrelem, "expr");
            Element table = Tool.getChildNodeByTagName(attrelem, "table");
            Element column = Tool.getChildNodeByTagName(attrelem, "column");

            if(table != null && column != null) {
               String tname = Tool.getValue(table);
               String cname = Tool.getValue(column);

               if(desc.getCaseStyle() == TransformDescriptor.UPPERCASE) {
                  desc.replaceColumnNode(column, attrelem, cname.toUpperCase());
               }
               else {
                  desc.replaceColumnNode(column, attrelem, cname.toLowerCase());
               }

               desc.replaceTableNode(table, attrelem,
                  transformTable(tname, desc));
            }

            // transform the expression
            if(expr != null) {
               String field = Tool.getValue(expr);
               transformExpression(expr, desc, field);
            }
         }
      }
   }

   /**
    * Transform the expression.
    */
   private void transformExpression(Element elem, TransformDescriptor desc,
                                    String expr) {
      StringBuilder str = new StringBuilder(expr);
      int start = 0;

      while(start < str.length()) {
         int idx = str.indexOf("field['", start);

         if(idx != -1) {
            int idx0 = str.indexOf("']", idx);

            if(idx0 != -1) {
               String fname = str.substring(idx + 7, idx0);
               int dot = fname.lastIndexOf('.');
               String tname = dot < 0 ? null : fname.substring(0, dot);
               String cname = dot < 0 ?
                  null : fname.substring(dot + 1, fname.length());
               String nname = transformTable(tname, desc) + "." + cname;
               str.replace(idx + 7, idx0, nname);
               start = idx + 1;
            }
            else{
               break;
            }

            XMLTool.replaceValue(elem, str.toString());
         }
         else {
            break;
         }
      }
   }

   /**
    * Transform the cube members.
    */
   private void transformCubeMembers(NodeList nodes, TransformDescriptor desc) {
      for(int i = 0; nodes != null && i < nodes.getLength(); i++) {
         Element elem = (Element) nodes.item(i);
         NodeList members = Tool.getChildNodesByTagName(elem, "CubeMember");
         int dbtype = desc.getDBType();

         for(int j = 0; members != null && j < members.getLength(); j++) {
            Element elem0 = (Element) members.item(j);
            Element dRef = Tool.getChildNodeByTagName(elem0, "dataRef");
            String clsName = Tool.getAttribute(dRef, "class");
            String tab = Tool.getAttribute(dRef, "datasource");
            String col = Tool.getAttribute(dRef, "name");

            // transform year cube member
            String name = Tool.getAttribute(elem0, "name");

            if("Year".equals(name) && (className).equals(clsName)) {
               XMLTool.replaceValue(dRef,
                  getYearDimension(transformTable(tab, desc), col, dbtype));
            }

            // transform quarater cube member
            if("Quarter".equals(name) && (className).equals(clsName)) {
               XMLTool.replaceValue(dRef,
                  getQuarterDimension(transformTable(tab, desc), col, dbtype));
            }

            // transform month cube member
            if("Month".equals(name) && (className).equals(clsName)) {
               XMLTool.replaceValue(dRef,
                  getMonthDimension(transformTable(tab, desc), col, dbtype));
            }
         }
      }
   }

   /**
    * Transform the measures.
    */
   private void transformMeasures(Element elem, TransformDescriptor desc) {
      NodeList measures = Tool.getChildNodesByTagName(elem, "Measure");

      for(int i = 0; measures != null && i < measures.getLength(); i++) {
         Element elem0 = (Element) measures.item(i);
         Element dataRef = Tool.getChildNodeByTagName(elem0, "dataRef");
         String entity = Tool.getAttribute(dataRef, "entity");
         String exprRef = Tool.getValue(dataRef);

         if(exprRef !=null) {
            transformExpression(dataRef, desc, exprRef);
         }
      }
   }

   /**
    * Get the year cube member.
    */
   private String getYearDimension(String table, String col, int type) {
      String year = null;

      if(type == TransformDescriptor.ORACLE) {
         year = "to_number(to_char(field['" + table + "." + col + "'],'YYYY'))";
      }
      else if(type == TransformDescriptor.DB2) {
         year = "year(field['" + table + "." + col + "'])";
      }
      else if(type == TransformDescriptor.SQLSERVER) {
         year = "datepart(year,field['" + table + "." + col + "'])";
      }

      return year;
   }

   /**
    * Get the quarter cube member.
    */
   private String getQuarterDimension(String table, String col, int type) {
      String quarter = null;

      if(type == TransformDescriptor.ORACLE) {
         quarter = "to_number(to_char(field['" + table + "." + col + "'],'Q'))";
      }
      else if(type == TransformDescriptor.DB2){
         quarter = "quarter(field['" + table + "." + col + "'])";
      }
      else if(type == TransformDescriptor.SQLSERVER) {
         quarter = "datepart(quarter,field['" + table + "." + col + "'])";
      }

      return quarter;
   }

   /**
    * Get the month cube member.
    */
   private String getMonthDimension(String table, String col, int type) {
      String month = null;

      if(type == TransformDescriptor.ORACLE) {
         month = "to_number(to_char(field['" + table + "." + col + "'],'MM'))";
      }
      else if(type == TransformDescriptor.DB2){
         month = "month(field['" + table + "." + col + "'])";
      }
      else if(type == TransformDescriptor.SQLSERVER) {
         month = "datepart(month,field['" + table + "." + col + "'])";
      }

      return month;
   }

   /**
    * Transform table.
    * @param table the specified table.
    * @return the transformed table.
    */
   @Override
   public String transformTable(String table, TransformDescriptor descriptor) {
      return transformer.transformTable(table, descriptor);
   }

   private static String className = "inetsoft.uql.erm.ExpressionRef";
   private ERMTransformer transformer;
}
