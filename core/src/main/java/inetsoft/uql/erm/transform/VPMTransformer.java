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
package inetsoft.uql.erm.transform;

import inetsoft.uql.erm.vpm.VpmCondition;
import inetsoft.uql.jdbc.XSet;
import inetsoft.util.Tool;
import org.w3c.dom.*;

import java.util.HashMap;
import java.util.Map;

/**
 * VPMTransformer, transforms a vpm node.
 *
 * version 10.1
 * @author InetSoft Technology Corp
 */
public class VPMTransformer implements XElementTransformer {
   /**
    * Create a new instance of VPMTransformer.
    */
   public VPMTransformer(Map phyMap) {
      this.phyMap = phyMap;
   }

   /**
    * Transform the element according to the descriptor.
    * @param elem the element.
    */
   @Override
   public void transform(Element elem, TransformDescriptor desc) {
      NodeList vpmNodes = Tool.getChildNodesByTagName(elem, "vpmObject");

      for(int i = 0; i < vpmNodes.getLength(); i++) {
         Element vpmNode = (Element) vpmNodes.item(i);
         Element condNode0 = Tool.getChildNodeByTagName(vpmNode, "conditions");
         NodeList condObjNodes =
            Tool.getChildNodesByTagName(condNode0, "vpmObject");

         // replace VpmCondition schema
         for(int j = 0; j < condObjNodes.getLength(); j++) {
            if(condObjNodes.item(j) instanceof Element) {
               Element condNode = (Element) condObjNodes.item(j);
               int type = Integer.parseInt(Tool.getAttribute(condNode, "type"));
               basedView = type == VpmCondition.PHYSICMODEL ? true : false;

               Element condNode1 =
                  Tool.getChildNodeByTagName(condNode, "conditions");

               // replace table
               Element tableNode =
                  Tool.getChildNodeByTagName(condNode, "table");
               String otname = Tool.getValue(tableNode);

               // if vpmcondition base table is xpartition, don't need to
               // transform the base table name
               if(!basedView) {
                  String tname = desc.transformTableName(otname);
                  desc.replaceTableNode(tableNode, condNode, tname);
               }

               if(condNode1 != null) {
                  NodeList expNodes = condNode1.getChildNodes();

                  for(int k = 0; k < expNodes.getLength(); k++) {
                     if(expNodes.item(k) instanceof Element) {
                        Element expNode = (Element) expNodes.item(k);
                        // replace all condition fields
                        transformXFilterNode(expNode, desc, otname);
                     }
                  }
               }
            }
         }

         // replace hiddenColumns, hiddenColumns may null
         Element hidCols = Tool.getChildNodeByTagName(vpmNode, "hiddenColumns");

         if(hidCols != null) {
            Element hidObjNode =
               Tool.getChildNodeByTagName(hidCols, "vpmObject");
            Element hidCol = Tool.getChildNodeByTagName(
               hidObjNode, "hiddenColumns");
            NodeList refNodes = Tool.getChildNodesByTagName(hidCol, "dataRef");

            for(int j = 0; j < refNodes.getLength(); j++) {
               if(refNodes.item(j) instanceof Element) {
                  Element dataRefNode = (Element) refNodes.item(j);
                  String ename = desc.transformTableName(
                     dataRefNode.getAttribute("entity"));
                  dataRefNode.setAttribute("entity", ename);

                  String aname = desc.transformColName(
                     dataRefNode.getAttribute("attribute"));
                  dataRefNode.setAttribute("attribute", aname);
               }
            }
         }
      }
   }

   /**
    * Transform condition fields.
    */
   private void transformXFilterNode(Element elem, TransformDescriptor desc,
      String tname)
   {
      if(elem.getTagName().equals(XSet.XML_TAG)) {
         NodeList childNodes = elem.getChildNodes();

         for(int i = 0; i < childNodes.getLength(); i++) {
            if(childNodes.item(i) instanceof Element) {
               Element childNode = (Element) childNodes.item(i);
               transformXFilterNode(childNode, desc, tname);
            }
         }
      }
      else {
         NodeList nodes = elem.getChildNodes();

         for(int i = 0; i < nodes.getLength(); i++) {
            if(nodes.item(i) instanceof Element) {
               Element node = (Element) nodes.item(i);

               if(node.getTagName().equals("expression")) {
                  replaceField(node, desc, tname);
               }
               else {
                  NodeList expressionNodes = node.getChildNodes();

                  for(int j = 0; j < expressionNodes.getLength(); j++) {
                     if(expressionNodes.item(j) instanceof Element) {
                        Element expressionNode =
                           (Element) expressionNodes.item(j);
                        replaceField(expressionNode, desc, tname);
                     }
                  }
               }
            }
         }
      }
   }

   /**
    * Replace field.
    */
   private void replaceField(Element node, TransformDescriptor desc,
      String tname)
   {
      if(node.toString() == null || node.toString().length() <= 0){
         return;
      }

      if("Field".equals(node.getAttribute("type"))) {
         String field = Tool.getValue(node);

         // if vpmcondition's base table is XPartition, should check the table
         // name need to be transformed, sometimes needn't, such as alias table
         if(basedView) {
            int cidx = field.lastIndexOf(".");
            String table = field.substring(0, cidx);
            ERMTransformer transformer = (ERMTransformer) phyMap.get(tname);
            String ntname = transformer.transformTable(table, desc);
            field = ntname + desc.transformColName(
               field.substring(cidx, field.length()));
         }
         else {
            field = desc.transformTableName(field);
         }

         replaceValue(node, field);
      }
   }

   /**
    * Replace value.
    */
   private void replaceValue(Element elem, String value) {
      NodeList nodes = elem.getChildNodes();
      Document doc = elem.getOwnerDocument();

      for(int i = 0; i < nodes.getLength(); i++) {
         Node node = nodes.item(i);

         if(node.getNodeType() == Element.CDATA_SECTION_NODE) {
            Node nnode = doc.createCDATASection(value);
            elem.replaceChild(nnode, node);
            break;
         }
      }
   }

   private Map phyMap = new HashMap();
   private boolean basedView = false;
}
