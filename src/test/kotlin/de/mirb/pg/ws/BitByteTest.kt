package de.mirb.pg.ws

import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec
import java.util.*

class BitByteTest: StringSpec() {
  init {
    "length should return size of string" {
      val b1 = 1.toByte()
      val b2 = (-128).toByte()
      val b3 = 127.toByte()

      println("B1 (" + b1 +"): " + b1.toBitSet())
      println("B2 (" + b2 +"): " + b2.toBitSet())
      println("B3 (" + b3 +"): " + b3.toBitSet())

      val bs = BitSet(8)
      bs.set(0)
      bs.set(7)
      println("B1 (" + bs +"): " + Arrays.toString(bs.toByteArray()))
    }
  }

  fun Byte.toBitSet(): BitSet {
//    ByteArray(1){this}
    return BitSet.valueOf(ByteArray(1){this})
  }
}