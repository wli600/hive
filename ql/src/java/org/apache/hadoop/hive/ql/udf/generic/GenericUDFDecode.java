/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hive.ql.udf.generic;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;

import org.apache.hadoop.hive.ql.exec.Description;
import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.exec.UDFArgumentLengthException;
import org.apache.hadoop.hive.ql.exec.UDFArgumentTypeException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ConstantObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector.Category;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector.PrimitiveCategory;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.BinaryObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.StringObjectInspector;
import org.apache.hadoop.io.Text;

@Description(name = "decode",
    value = "_FUNC_(bin, str) - Decode the first argument using the second argument character set",
    extended = "Possible options for the character set are 'US_ASCII', 'ISO-8859-1',\n" +
    		"'UTF-8', 'UTF-16BE', 'UTF-16LE', and 'UTF-16'. If either argument\n" +
    		"is null, the result will also be null")
public class GenericUDFDecode extends GenericUDF {
  private transient CharsetDecoder decoder = null;
  private transient BinaryObjectInspector bytesOI = null;
  private transient StringObjectInspector charsetOI = null;

  @Override
  public ObjectInspector initialize(ObjectInspector[] arguments) throws UDFArgumentException {
    if (arguments.length != 2) {
      throw new UDFArgumentLengthException("Encode() requires exactly two arguments");
    }

    if (arguments[0].getCategory() != Category.PRIMITIVE ||
        ((PrimitiveObjectInspector)arguments[0]).getPrimitiveCategory() != PrimitiveCategory.BINARY){
      throw new UDFArgumentTypeException(0, "The first argument to Encode() must be a binary");
    }

    bytesOI = (BinaryObjectInspector) arguments[0];

    if (arguments[1].getCategory() != Category.PRIMITIVE ||
        ((PrimitiveObjectInspector)arguments[1]).getPrimitiveCategory() != PrimitiveCategory.STRING){
      throw new UDFArgumentTypeException(1, "The second argument to Encode() must be a string");
    }

    charsetOI = (StringObjectInspector) arguments[1];

    // If the character set for encoding is constant, we can optimize that
    StringObjectInspector charSetOI = (StringObjectInspector) arguments[1];
    if (charSetOI instanceof ConstantObjectInspector){
      String charSetName = ((Text) ((ConstantObjectInspector) charSetOI).getWritableConstantValue()).toString();
      decoder = Charset.forName(charSetName).newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
    }

    return (ObjectInspector) PrimitiveObjectInspectorFactory.javaStringObjectInspector;
  }

  @Override
  public Object evaluate(DeferredObject[] arguments) throws HiveException {
    byte[] value = bytesOI.getPrimitiveJavaObject(arguments[0].get());
    if (value == null) {
      return null;
    }

    ByteBuffer wrappedBytes = ByteBuffer.wrap(value);
    CharBuffer decoded;
    if (decoder != null){
      try {
        decoded = decoder.decode(wrappedBytes);
      } catch (CharacterCodingException e) {
        throw new HiveException(e);
      }
    } else {
      decoded = Charset.forName(charsetOI.getPrimitiveJavaObject(arguments[1].get())).decode(wrappedBytes);
    }
    return decoded.toString();
  }

  @Override
  public String getDisplayString(String[] children) {
    assert (children.length == 2);
    StringBuilder sb = new StringBuilder();
    sb.append("encode(");
    sb.append(children[0]).append(",");
    sb.append(children[1]).append(")");
    return sb.toString();
  }
}
