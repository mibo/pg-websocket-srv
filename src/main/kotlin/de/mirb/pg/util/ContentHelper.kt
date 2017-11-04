/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package de.mirb.pg.util

import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.StringReader
import java.io.UnsupportedEncodingException
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Random

/**
 *
 */
object ContentHelper {

  class Stream constructor(content: ByteBuffer) {
    private val data = content.duplicate()
    init {
      if(data.position() > 0) {
        data.flip()
      }
    }

    constructor(data: ByteArray) : this(ByteBuffer.wrap(data))

    constructor(content: String, charset: Charset) : this(content.toByteArray(charset))

    fun asStream(): InputStream {
      return ByteArrayInputStream(asArray())
    }

    fun asArray(): ByteArray {
      val tmpBuffer = data.duplicate()
      val tmp: ByteArray = kotlin.ByteArray(tmpBuffer.limit())
      tmpBuffer.get(tmp)
      return tmp
    }

    @JvmOverloads
    fun asString(charset: Charset = StandardCharsets.UTF_8): String {
      return String(asArray(), charset)
    }

    @Throws(IOException::class)
    @JvmOverloads
    fun print(out: OutputStream = System.out): Stream {
      Channels.newChannel(out).write(data.duplicate())
      return this
    }

    @Throws(IOException::class)
    fun asStringWithLineSeparation(separator: String): String {
      val br = BufferedReader(StringReader(asString()))
      val sb = StringBuilder(br.readLine())
      var line: String? = br.readLine()
      while (line != null) {
        sb.append(separator).append(line)
        line = br.readLine()
      }
      return sb.toString()
    }

    @Throws(IOException::class)
    fun asStreamWithLineSeparation(separator: String): InputStream {
      val asString = asStringWithLineSeparation(separator)
      return ByteArrayInputStream(asString.toByteArray(charset("UTF-8")))
    }

    /**
     * Number of lines separated by line breaks (`CRLF`).
     * A content string like `text\r\nmoreText` will result in
     * a line count of `2`.
     *
     * @return lines count
     */
    fun linesCount(): Int {
      return ContentHelper.countLines(asString(), "\r\n")
    }
  }

  fun asString(buffer: ByteBuffer, charset: Charset = StandardCharsets.UTF_8): String {
    return ContentHelper.toStream(buffer).asString(charset)
  }

  @Throws(IOException::class)
  fun toStream(buffer: ByteBuffer): Stream {
    return Stream(buffer)
  }

  @Throws(IOException::class)
  fun toStream(stream: InputStream): Stream {
    var result = ByteArray(0)
    val tmp = ByteArray(8192)
    var readCount = stream.read(tmp)
    while (readCount >= 0) {
      val innerTmp = ByteArray(result.size + readCount)
      System.arraycopy(result, 0, innerTmp, 0, result.size)
      System.arraycopy(tmp, 0, innerTmp, result.size, readCount)
      result = innerTmp
      readCount = stream.read(tmp)
    }
    stream.close()
    return Stream(result)
  }

  @JvmOverloads
  fun toStream(content: String, charset: Charset = StandardCharsets.UTF_8): Stream {
    try {
      return Stream(content, charset)
    } catch (e: UnsupportedEncodingException) {
      throw RuntimeException("UTF-8 should be supported on each system.")
    }

  }

  @Throws(IOException::class)
  @JvmOverloads
  fun inputStreamToString(input: InputStream, preserveLineBreaks: Boolean = false): String {
    val bufferedReader = BufferedReader(InputStreamReader(input, Charset.forName("UTF-8")))
    val stringBuilder = StringBuilder()
    var line: String? = bufferedReader.readLine()

    while (line != null) {
      stringBuilder.append(line)
      if (preserveLineBreaks) {
        stringBuilder.append("\n")
      }
      line = bufferedReader.readLine()
    }

    bufferedReader.close()

    return stringBuilder.toString()
  }

  @Throws(IOException::class)
  fun inputStreamToStringCRLFLineBreaks(input: InputStream): String {
    val bufferedReader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
    val stringBuilder = StringBuilder()
    var line: String? = bufferedReader.readLine()

    while (line != null) {
      stringBuilder.append(line)
      stringBuilder.append("\r\n")
      line = bufferedReader.readLine()
    }

    bufferedReader.close()

    return stringBuilder.toString()
  }

  @JvmOverloads
  fun countLines(content: String?, lineBreak: String = "\r\n"): Int {
    if (content == null) {
      return -1
    }

    var lastPos = content.indexOf(lineBreak)
    var count = 1

    while (lastPos >= 0) {
      lastPos = content.indexOf(lineBreak, lastPos + 1)
      count++
    }
    return count
  }

  /**
   * Encapsulate given content in an [InputStream] with charset `UTF-8`.
   *
   * @param content to encapsulate content
   * @return content as stream
   */
  fun encapsulate(content: String): InputStream {
    return encapsulate(content, StandardCharsets.UTF_8)
  }

  /**
   * Encapsulate given content in an [InputStream] with given charset.
   *
   * @param content to encapsulate content
   * @param charset to be used charset
   * @return content as stream
   * @throws UnsupportedEncodingException if charset is not supported
   */
  @Throws(UnsupportedEncodingException::class)
  fun encapsulate(content: String, charset: Charset): InputStream {
    return ByteArrayInputStream(content.toByteArray(charset))
  }

  /**
   * Generate a string with given length containing random upper case characters ([A-Z]).
   *
   * @param len length of to generated string
   * @return random upper case characters ([A-Z]).
   */
  fun generateDataStream(len: Int): InputStream {
    return encapsulate(generateData(len))
  }

  /**
   * Generates a string with given length containing random upper case characters ([A-Z]).
   * @param len length of the generated string
   * @return random upper case characters ([A-Z])
   */
  fun generateData(len: Int): String {
    val random = Random()
    val b = StringBuilder(len)
    for (j in 0..len - 1) {
      val c = ('A' + random.nextInt('Z' - 'A' + 1)).toChar()
      b.append(c)
    }
    return b.toString()
  }

}
