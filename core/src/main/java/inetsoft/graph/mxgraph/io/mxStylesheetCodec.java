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
package inetsoft.graph.mxgraph.io;

import inetsoft.graph.mxgraph.util.mxUtils;
import inetsoft.graph.mxgraph.view.mxStylesheet;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.*;

/**
 * Codec for mxStylesheets. This class is created and registered
 * dynamically at load time and used implicitely via mxCodec
 * and the mxCodecRegistry.
 */
public class mxStylesheetCodec extends mxObjectCodec {

   /**
    * Constructs a new model codec.
    */
   public mxStylesheetCodec()
   {
      this(new mxStylesheet());
   }

   /**
    * Constructs a new stylesheet codec for the given template.
    */
   public mxStylesheetCodec(Object template)
   {
      this(template, null, null, null);
   }

   /**
    * Constructs a new model codec for the given arguments.
    */
   public mxStylesheetCodec(Object template, String[] exclude,
                            String[] idrefs, Map<String, String> mapping)
   {
      super(template, exclude, idrefs, mapping);
   }

   /**
    * Encodes the given mxStylesheet.
    */
   public Node encode(mxCodec enc, Object obj)
   {
      Element node = enc.document.createElement(getName());

      if(obj instanceof mxStylesheet) {
         mxStylesheet stylesheet = (mxStylesheet) obj;
         Iterator<Map.Entry<String, Map<String, Object>>> it = stylesheet
            .getStyles().entrySet().iterator();

         while(it.hasNext()) {
            Map.Entry<String, Map<String, Object>> entry = it.next();

            Element styleNode = enc.document.createElement("add");
            String stylename = entry.getKey();
            styleNode.setAttribute("as", stylename);

            Map<String, Object> style = entry.getValue();
            Iterator<Map.Entry<String, Object>> it2 = style.entrySet()
               .iterator();

            while(it2.hasNext()) {
               Map.Entry<String, Object> entry2 = it2.next();
               Element entryNode = enc.document.createElement("add");
               entryNode.setAttribute("as",
                                      String.valueOf(entry2.getKey()));
               entryNode.setAttribute("value", getStringValue(entry2));
               styleNode.appendChild(entryNode);
            }

            if(styleNode.getChildNodes().getLength() > 0) {
               node.appendChild(styleNode);
            }
         }
      }

      return node;
   }

   /**
    * Returns the string for encoding the given value.
    */
   protected String getStringValue(Map.Entry<String, Object> entry)
   {
      if(entry.getValue() instanceof Boolean) {
         return ((Boolean) entry.getValue()) ? "1" : "0";
      }

      return entry.getValue().toString();
   }

   /**
    * Decodes the given mxStylesheet.
    */
   public Object decode(mxCodec dec, Node node, Object into)
   {
      Object obj = null;

      if(node instanceof Element) {
         String id = ((Element) node).getAttribute("id");
         obj = dec.objects.get(id);

         if(obj == null) {
            obj = into;

            if(obj == null) {
               obj = cloneTemplate(node);
            }

            if(id != null && id.length() > 0) {
               dec.putObject(id, obj);
            }
         }

         node = node.getFirstChild();

         while(node != null) {
            if(!processInclude(dec, node, obj)
               && node.getNodeName().equals("add")
               && node instanceof Element)
            {
               String as = ((Element) node).getAttribute("as");

               if(as != null && as.length() > 0) {
                  String extend = ((Element) node).getAttribute("extend");
                  Map<String, Object> style = (extend != null) ? ((mxStylesheet) obj)
                     .getStyles().get(extend) : null;

                  if(style == null) {
                     style = new Hashtable<String, Object>();
                  }
                  else {
                     style = new Hashtable<String, Object>(style);
                  }

                  Node entry = node.getFirstChild();

                  while(entry != null) {
                     if(entry instanceof Element) {
                        Element entryElement = (Element) entry;
                        String key = entryElement.getAttribute("as");

                        if(entry.getNodeName().equals("add")) {
                           String text = entry.getTextContent();
                           Object value = null;

                           if(text != null && text.length() > 0) {
                              value = mxUtils.eval(text);
                           }
                           else {
                              value = entryElement
                                 .getAttribute("value");

                           }

                           if(value != null) {
                              style.put(key, value);
                           }
                        }
                        else if(entry.getNodeName().equals("remove")) {
                           style.remove(key);
                        }
                     }

                     entry = entry.getNextSibling();
                  }

                  ((mxStylesheet) obj).putCellStyle(as, style);
               }
            }

            node = node.getNextSibling();
         }
      }

      return obj;
   }

}
