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

import inetsoft.graph.mxgraph.model.mxGraphModel;
import inetsoft.graph.mxgraph.model.mxICell;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Map;

/**
 * Codec for mxGraphModels. This class is created and registered
 * dynamically at load time and used implicitly via mxCodec
 * and the mxCodecRegistry.
 */
public class mxModelCodec extends mxObjectCodec {

   /**
    * Constructs a new model codec.
    */
   public mxModelCodec()
   {
      this(new mxGraphModel());
   }

   /**
    * Constructs a new model codec for the given template.
    */
   public mxModelCodec(Object template)
   {
      this(template, null, null, null);
   }

   /**
    * Constructs a new model codec for the given arguments.
    */
   public mxModelCodec(Object template, String[] exclude, String[] idrefs,
                       Map<String, String> mapping)
   {
      super(template, exclude, idrefs, mapping);
   }

   /**
    * Encodes the given mxGraphModel by writing a (flat) XML sequence
    * of cell nodes as produced by the mxCellCodec. The sequence is
    * wrapped-up in a node with the name root.
    */
   protected void encodeObject(mxCodec enc, Object obj, Node node)
   {
      if(obj instanceof mxGraphModel) {
         Node rootNode = enc.document.createElement("root");
         mxGraphModel model = (mxGraphModel) obj;
         enc.encodeCell((mxICell) model.getRoot(), rootNode, true);
         node.appendChild(rootNode);
      }
   }

   /**
    * Reads the cells into the graph model. All cells are children of the root
    * element in the node.
    */
   public Node beforeDecode(mxCodec dec, Node node, Object into)
   {
      if(node instanceof Element) {
         Element elt = (Element) node;
         mxGraphModel model = null;

         if(into instanceof mxGraphModel) {
            model = (mxGraphModel) into;
         }
         else {
            model = new mxGraphModel();
         }

         // Reads the cells into the graph model. All cells
         // are children of the root element in the node.
         Node root = elt.getElementsByTagName("root").item(0);
         mxICell rootCell = null;

         if(root != null) {
            Node tmp = root.getFirstChild();

            while(tmp != null) {
               mxICell cell = dec.decodeCell(tmp, true);

               if(cell != null && cell.getParent() == null) {
                  rootCell = cell;
               }

               tmp = tmp.getNextSibling();
            }

            root.getParentNode().removeChild(root);
         }

         // Sets the root on the model if one has been decoded
         if(rootCell != null) {
            model.setRoot(rootCell);
         }
      }

      return node;
   }

}
