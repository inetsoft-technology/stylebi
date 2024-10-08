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

import inetsoft.graph.mxgraph.model.mxGraphModel.mxChildChange;
import inetsoft.graph.mxgraph.model.mxICell;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Map;

/**
 * Codec for mxChildChanges. This class is created and registered
 * dynamically at load time and used implicitely via mxCodec
 * and the mxCodecRegistry.
 */
public class mxChildChangeCodec extends mxObjectCodec {

   /**
    * Constructs a new model codec.
    */
   public mxChildChangeCodec()
   {
      this(new mxChildChange(), new String[]{ "model", "child",
                                              "previousIndex" }, new String[]{ "parent", "previous" }, null);
   }

   /**
    * Constructs a new model codec for the given arguments.
    */
   public mxChildChangeCodec(Object template, String[] exclude,
                             String[] idrefs, Map<String, String> mapping)
   {
      super(template, exclude, idrefs, mapping);
   }

   /* (non-Javadoc)
    * @see inetsoft.graph.mxgraph.io.mxObjectCodec#isReference(java.lang.Object, java.lang.String, java.lang.Object, boolean)
    */
   @Override
   public boolean isReference(Object obj, String attr, Object value,
                              boolean isWrite)
   {
      if(attr.equals("child") && obj instanceof mxChildChange
         && (((mxChildChange) obj).getPrevious() != null || !isWrite))
      {
         return true;
      }

      return idrefs.contains(attr);
   }

   /* (non-Javadoc)
    * @see inetsoft.graph.mxgraph.io.mxObjectCodec#afterEncode(inetsoft.graph.mxgraph.io.mxCodec, java.lang.Object, org.w3c.dom.Node)
    */
   @Override
   public Node afterEncode(mxCodec enc, Object obj, Node node)
   {
      if(obj instanceof mxChildChange) {
         mxChildChange change = (mxChildChange) obj;
         Object child = change.getChild();

         if(isReference(obj, "child", child, true)) {
            // Encodes as reference (id)
            mxCodec.setAttribute(node, "child", enc.getId(child));
         }
         else {
            // At this point, the encoder is no longer able to know which cells
            // are new, so we have to encode the complete cell hierarchy and
            // ignore the ones that are already there at decoding time. Note:
            // This can only be resolved by moving the notify event into the
            // execute of the edit.
            enc.encodeCell((mxICell) child, node, true);
         }
      }

      return node;
   }

   /**
    * Reads the cells into the graph model. All cells are children of the root
    * element in the node.
    */
   public Node beforeDecode(mxCodec dec, Node node, Object into)
   {
      if(into instanceof mxChildChange) {
         mxChildChange change = (mxChildChange) into;

         if(node.getFirstChild() != null
            && node.getFirstChild().getNodeType() == Node.ELEMENT_NODE)
         {
            // Makes sure the original node isn't modified
            node = node.cloneNode(true);

            Node tmp = node.getFirstChild();
            change.setChild(dec.decodeCell(tmp, false));

            Node tmp2 = tmp.getNextSibling();
            tmp.getParentNode().removeChild(tmp);
            tmp = tmp2;

            while(tmp != null) {
               tmp2 = tmp.getNextSibling();

               if(tmp.getNodeType() == Node.ELEMENT_NODE) {
                  // Ignores all existing cells because those do not need
                  // to be re-inserted into the model. Since the encoded
                  // version of these cells contains the new parent, this
                  // would leave to an inconsistent state on the model
                  // (ie. a parent change without a call to
                  // parentForCellChanged).
                  String id = ((Element) tmp).getAttribute("id");

                  if(dec.lookup(id) == null) {
                     dec.decodeCell(tmp, true);
                  }
               }

               tmp.getParentNode().removeChild(tmp);
               tmp = tmp2;
            }
         }
         else {
            String childRef = ((Element) node).getAttribute("child");
            change.setChild(dec.getObject(childRef));
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
      if(obj instanceof mxChildChange) {
         mxChildChange change = (mxChildChange) obj;

         // Cells are encoded here after a complete transaction so the previous
         // parent must be restored on the cell for the case where the cell was
         // added. This is needed for the local model to identify the cell as a
         // new cell and register the ID.
         ((mxICell) change.getChild()).setParent((mxICell) change
            .getPrevious());
         change.setPrevious(change.getParent());
         change.setPreviousIndex(change.getIndex());
      }

      return obj;
   }

}
