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
package inetsoft.mv.fs;

import inetsoft.mv.comm.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.*;

/**
 * NBlock, node side block.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public class NBlock extends XBlock implements XTransferable, Serializable {
   /**
    * Constructor.
    */
   public NBlock() {
      super();
   }

   /**
    * Constructor.
    */
   public NBlock(String parent, String id) {
      super(parent, id);
   }

   /**
    * Get physical file length. -1 if this Nblock does not exist.
    */
   public final long getPhysicalLen() {
      return plen;
   }

   /**
    * Set physical file length.
    */
   public final void setPhysicalLen(long length) {
      this.plen = length;
   }

   /**
    * Check the block is valid or not.
    */
   public final boolean isValid() {
      return plen == getLength();
   }

   /**
    * Read this transferable.
    */
   @Override
   public final void read(XReadBuffer buf) throws IOException {
      super.read(buf);
      plen = buf.readLong();

      if(plen < 0) {
         plen = buf.readLong();
         setVersion(buf.readInt());
      }
   }

   /**
    * Write this transferable.
    */
   @Override
   public final void write(XWriteBuffer buf) throws IOException {
      super.write(buf);
      // Long.MIN_VALUE indicates we should read in version property since prior
      // to 11.2, no version attribute in NBlock
      buf.writeLong(Long.MIN_VALUE);
      buf.writeLong(plen);
      buf.writeInt(getVersion());
   }

   /**
    * Write attributes.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
      writer.print(" version=\"" + getVersion() + "\"");
   }

   /**
    * Parse attributes.
    */
   @Override
   protected void parseAttribute(Element tag) {
      super.parseAttribute(tag);
      String val = Tool.getAttribute(tag, "version");

      if(val != null) {
         setVersion(Integer.parseInt(val));
      }
   }

   private long plen;
}
