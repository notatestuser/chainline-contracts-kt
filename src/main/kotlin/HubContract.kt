package chainline.contracts;

import org.neo.smartcontract.framework.Helper
import org.neo.smartcontract.framework.SmartContract
import java.math.BigInteger

//                  __/___
//            _____/______|
//    _______/_____\_______\_____
//    \              < < <       |
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//     C  H  A  I  N    L  I  N  E

// The compiler doesn't support member fields or companion classes so
// this code is kind of ugly!

class HubContract : SmartContract() {
   fun Main(operation: String, arg0: ByteArray, arg1: ByteArray) : Any {
      if (operation == "test_arrayrev")
         return ReverseArray(arg0)
      if (operation == "test_arrayeq")
         return arg0 == arg1
      if (operation == "test_arrayneq")
         return arg0 != arg1
      if (operation == "test_bigintsize")
         return arg0.size
      return false
   }

   private fun ReverseArray(input: ByteArray): ByteArray {
      var reversed = Helper.concat(
            input[input.size - 1] as ByteArray,
            input[input.size - 2] as ByteArray)
      var i = input.size - 3
      do {
         reversed = Helper.concat(reversed, input[i] as ByteArray)
      } while (--i >= 0)
      return reversed
   }
}
