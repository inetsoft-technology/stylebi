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
package inetsoft.graph.mxgraph.io;

import org.w3c.dom.Node;

import java.util.Map;

/**
 * Codec for mxChildChanges. This class is created and registered
 * dynamically at load time and used implicitely via mxCodec
 * and the mxCodecRegistry.
 */
public class mxGenericChangeCodec extends mxObjectCodec {
   /**
    *
    */
   protected String fieldname;

   /**
    * Constructs a new model codec.
    */
   public mxGenericChangeCodec(Object template, String fieldname)
   {
      this(template, new String[]{ "model", "previous" },
           new String[]{ "cell" }, null, fieldname);
   }

   /**
    * Constructs a new model codec for the given arguments.
    */
   public mxGenericChangeCodec(Object template, String[] exclude,
                               String[] idrefs, Map<String, String> mapping, String fieldname)
   {
      super(template, exclude, idrefs, mapping);

      this.fieldname = fieldname;
   }

   /* (non-Javadoc)
    * @see inetsoft.graph.mxgraph.io.mxObjectCodec#afterDecode(inetsoft.graph.mxgraph.io.mxCodec, org.w3c.dom.Node, java.lang.Object)
    */
   @Override
   public Object afterDecode(mxCodec dec, Node node, Object obj)
   {
      Object cell = getFieldValue(obj, "cell");

      if(cell instanceof Node) {
         setFieldValue(obj, "cell", dec.decodeCell((Node) cell, false));
      }

      setFieldValue(obj, "previous", getFieldValue(obj, fieldname));

      return obj;
   }

}
