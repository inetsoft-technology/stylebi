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

import inetsoft.graph.mxgraph.model.mxGraphModel.mxRootChange;
import inetsoft.graph.mxgraph.model.mxICell;
import org.w3c.dom.Node;

import java.util.Map;

/**
 * Codec for mxChildChanges. This class is created and registered
 * dynamically at load time and used implicitly via mxCodec
 * and the mxCodecRegistry.
 */
public class mxRootChangeCodec extends mxObjectCodec {

   /**
    * Constructs a new model codec.
    */
   public mxRootChangeCodec()
   {
      this(new mxRootChange(), new String[]{ "model", "previous", "root" },
           null, null);
   }

   /**
    * Constructs a new model codec for the given arguments.
    */
   public mxRootChangeCodec(Object template, String[] exclude,
                            String[] idrefs, Map<String, String> mapping)
   {
      super(template, exclude, idrefs, mapping);
   }

   /* (non-Javadoc)
    * @see inetsoft.graph.mxgraph.io.mxObjectCodec#afterEncode(inetsoft.graph.mxgraph.io.mxCodec, java.lang.Object, org.w3c.dom.Node)
    */
   @Override
   public Node afterEncode(mxCodec enc, Object obj, Node node)
   {
      if(obj instanceof mxRootChange) {
         enc.encodeCell((mxICell) ((mxRootChange) obj).getRoot(), node, true);
      }

      return node;
   }

   /**
    * Reads the cells into the graph model. All cells are children of the root
    * element in the node.
    */
   public Node beforeDecode(mxCodec dec, Node node, Object into)
   {
      if(into instanceof mxRootChange) {
         mxRootChange change = (mxRootChange) into;

         if(node.getFirstChild() != null
            && node.getFirstChild().getNodeType() == Node.ELEMENT_NODE)
         {
            // Makes sure the original node isn't modified
            node = node.cloneNode(true);

            Node tmp = node.getFirstChild();
            change.setRoot(dec.decodeCell(tmp, false));

            Node tmp2 = tmp.getNextSibling();
            tmp.getParentNode().removeChild(tmp);
            tmp = tmp2;

            while(tmp != null) {
               tmp2 = tmp.getNextSibling();

               if(tmp.getNodeType() == Node.ELEMENT_NODE) {
                  dec.decodeCell(tmp, true);
               }

               tmp.getParentNode().removeChild(tmp);
               tmp = tmp2;
            }
         }
      }

      return node;
   }

   /* (non-Javadoc)
    * @see inetsoft.graph.mxgraph.io.mxObjectCodec#afterDecode(inetsoft.graph.mxgraph.io.mxCodec, org.w3c.dom.Node, java.lang.Object)
    */
   @Override
   public Object afterDecode(mxCodec dec, Node node, Object obj)
   {
      if(obj instanceof mxRootChange) {
         mxRootChange change = (mxRootChange) obj;
         change.setPrevious(change.getRoot());
      }

      return obj;
   }

}
