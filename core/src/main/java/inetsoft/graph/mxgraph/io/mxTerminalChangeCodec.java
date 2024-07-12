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
package inetsoft.graph.mxgraph.io;

import inetsoft.graph.mxgraph.model.mxGraphModel.mxTerminalChange;
import org.w3c.dom.Node;

import java.util.Map;

/**
 * Codec for mxChildChanges. This class is created and registered
 * dynamically at load time and used implicitely via mxCodec
 * and the mxCodecRegistry.
 */
public class mxTerminalChangeCodec extends mxObjectCodec {

   /**
    * Constructs a new model codec.
    */
   public mxTerminalChangeCodec()
   {
      this(new mxTerminalChange(), new String[]{ "model", "previous" },
           new String[]{ "cell", "terminal" }, null);
   }

   /**
    * Constructs a new model codec for the given arguments.
    */
   public mxTerminalChangeCodec(Object template, String[] exclude,
                                String[] idrefs, Map<String, String> mapping)
   {
      super(template, exclude, idrefs, mapping);
   }

   /* (non-Javadoc)
    * @see inetsoft.graph.mxgraph.io.mxObjectCodec#afterDecode(inetsoft.graph.mxgraph.io.mxCodec, org.w3c.dom.Node, java.lang.Object)
    */
   @Override
   public Object afterDecode(mxCodec dec, Node node, Object obj)
   {
      if(obj instanceof mxTerminalChange) {
         mxTerminalChange change = (mxTerminalChange) obj;

         change.setPrevious(change.getTerminal());
      }

      return obj;
   }

}
