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
package inetsoft.uql.asset.internal;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetObject;
import inetsoft.util.FileVersions;
import inetsoft.util.Tool;
import org.apache.ignite.binary.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Asset folder represents a folder in an asset repository. It contains
 * sub worksheet/folder entries.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
@JsonSerialize(using = AssetFolder.Serializer.class)
@JsonDeserialize(using = AssetFolder.Deserializer.class)
public class AssetFolder implements AssetObject, Binarylizable {
   /**
    * Constructor.
    */
   public AssetFolder() {
      super();
      entryMap = new HashMap<>();
   }

   /**
    * Get the sub entries.
    * @return the sub entries.
    */
   public AssetEntry[] getEntries() {
      synchronized(entryMap) {
         return entryMap.keySet().toArray(new AssetEntry[0]);
      }
   }

   public List<AssetEntry> getEntries(AssetEntry.Type type) {
      synchronized(entryMap) {
         return entryMap.keySet().stream().filter(a -> a.getType() == type).collect(Collectors.toList());
      }
   }

   /**
    * Check if contains one asset entry.
    * @param entry the specified asset entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean containsEntry(AssetEntry entry) {
      synchronized(entryMap) {
         return entryMap.containsKey(entry);
      }
   }

   /**
    * Add one asset entry.
    * @param entry the specified asset entry.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean addEntry(AssetEntry entry) {
      synchronized(entryMap) {
         entryMap.put(entry, entry);
         return true;
      }
   }

   /**
    * Remove one sset entry.
    * @param entry the specified asset entry.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean removeEntry(AssetEntry entry) {
      synchronized(entryMap) {
         return entryMap.remove(entry) != null;
      }
   }

   /**
    * Get one asset entry.
    * @param entry the specified asset entry.
    * @return the corresponding asset entry stored in the asset folder.
    */
   public AssetEntry getEntry(AssetEntry entry) {
      synchronized(entryMap) {
         return entryMap.get(entry);
      }
   }

   /**
    * Clear the sub entries.
    */
   public void clear() {
      synchronized(entryMap) {
         entryMap.clear();
      }
   }

   public IdentityID getOwner() {
      return owner;
   }

   public void setOwner(IdentityID owner) {
      this.owner = owner;
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      return "AssetFolder@" + System.identityHashCode(this) + entries;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<assetFolder>");
      writer.println("<Version>" + FileVersions.ASSET + "</Version>");
      writer.print("<owner>");
      writer.print("<![CDATA[" + owner + "]]>");
      writer.print("</owner>");

      synchronized(entryMap) {
         for(AssetEntry entry : entryMap.keySet()) {
            entry.writeXML(writer);
         }
      }

      writer.println("</assetFolder>");
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      NodeList list = Tool.getChildNodesByTagName(elem, "assetEntry");

      synchronized(entryMap) {
         for(int i = 0; i < list.getLength(); i++) {
            Element entrynode = (Element) list.item(i);
            AssetEntry entry = AssetEntry.createAssetEntry(entrynode);
            entryMap.put(entry, entry);
         }
      }

      owner = IdentityID.getIdentityIDFromKey(Tool.getChildValueByTagName(elem, "owner"));
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         AssetFolder folder = (AssetFolder) super.clone();
         folder.owner = owner;

         synchronized(entryMap) {
            folder.entryMap = new HashMap<>(entryMap);
         }

         return folder;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   public int size() {
      return entryMap.size();
   }

   private void writeObject(ObjectOutputStream output) throws IOException {
      if(entryMap == null) {
         entries = new HashSet<>();
      }
      else {
         synchronized(entryMap) {
            entries = new HashSet<>(entryMap.keySet());
         }
      }

      output.defaultWriteObject();
   }

   private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
      input.defaultReadObject();
      entryMap = new HashMap<>();
      entries.forEach(a -> entryMap.put(a, a));
   }

   private HashSet<AssetEntry> entries;
   private IdentityID owner; // for task folder.
   private transient HashMap<AssetEntry, AssetEntry> entryMap;

   @Override
   public void writeBinary(BinaryWriter writer) throws BinaryObjectException {
      writer.writeObject("owner", owner);

      synchronized(entryMap) {
         writer.writeInt("size", entryMap.size());

         for(AssetEntry entry : entryMap.keySet()) {
            writer.writeObject("entry", entry);
         }
      }
   }

   @Override
   public void readBinary(BinaryReader reader) throws BinaryObjectException {
      IdentityID owner = reader.readObject("owner");
      setOwner(owner);
      int size = reader.readInt("size");
      entryMap = new HashMap<>();

      for(int i = 0; i < size; i++) {
         AssetEntry entry = reader.readObject("entry");
         entryMap.put(entry, entry);
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(AssetFolder.class);

   static final class Serializer extends StdSerializer<AssetFolder> {
      public Serializer() {
         super(AssetFolder.class);
      }

      @Override
      public void serialize(AssetFolder value, JsonGenerator gen, SerializerProvider provider)
         throws IOException
      {
         gen.writeStartObject();
         gen.writeStringField("version", FileVersions.ASSET);
         gen.writeStringField("owner", value.owner == null ? null : value.owner.convertToKey());
         gen.writeArrayFieldStart("entries");

         if(value.entryMap != null) {
            List<AssetEntry> entries = new ArrayList<>(value.entryMap.keySet()); // defensive copy

            for(AssetEntry entry : entries) {
               gen.writeObject(entry);
            }

            gen.writeEndArray();
            gen.writeEndObject();
         }
      }
   }

   static final class Deserializer extends StdDeserializer<AssetFolder> {
      public Deserializer() {
         super(AssetFolder.class);
      }

      @Override
      public AssetFolder deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
         AssetFolder value = new AssetFolder();
         ObjectNode root = p.getCodec().readTree(p);
         value.owner = IdentityID.getIdentityIDFromKey(root.get("owner").asText(null));
         JsonNode node = root.get("entries");

         if(node != null && node.isArray()) {
            ObjectMapper mapper = (ObjectMapper) p.getCodec();

            for(JsonNode child : node) {
               AssetEntry entry = mapper.convertValue(child, AssetEntry.class);
               value.entryMap.put(entry, entry);
            }
         }

         return value;
      }
   }
}
