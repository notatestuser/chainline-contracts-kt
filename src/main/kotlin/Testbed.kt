package chainline.contracts

import org.neo.smartcontract.framework.Helper.*
import org.neo.smartcontract.framework.SmartContract
import org.neo.smartcontract.framework.services.neo.Blockchain
import java.math.BigInteger

object Testbed : SmartContract() {

   //val field1 = "To the moon!"

   fun Main(operation: String, vararg args: ByteArray): Any {
      // Sanity Tests
      val field2 = "To the moon!"
      if (operation == "test_field")
         return field2
      if (operation == "test_arrayrev")
         return args[0].reverse()
      if (operation == "test_arrayeq")
         return args[0] == args[1]
      if (operation == "test_arrayneq")
         return args[0] != args[1]
      if (operation == "test_bigintsize")
         return args[0].size
      if (operation == "test_bigintpad")
         return padIntToBytes(5, args[0] as BigInteger?)
      if (operation == "test_timestamp") {
         val header = Blockchain.getHeader(Blockchain.height())
         return header.timestamp()
      }
      return false
   }

   private fun padIntToBytes(count: Int, toPad: BigInteger?): ByteArray {
      var zero = byteArrayOf(0)
      var bytes = toPad!!.toByteArray()
      while (bytes.size < count) {
         bytes = bytes.concat(zero)
      }
      return bytes
   }

   private fun reverseArray(input: ByteArray): ByteArray {
      var reversed = concat(
         input[input.size - 1] as ByteArray,
         input[input.size - 2] as ByteArray)
      var i = input.size - 3
      do {
         reversed = concat(reversed, input[i] as ByteArray)
      } while (--i >= 0)
      return reversed
   }

   fun ByteArray.reverse(): ByteArray {
      return reverseArray(this)
   }

   fun ByteArray.concat(b2: ByteArray): ByteArray {
      return concat(this, b2)
   }
}
