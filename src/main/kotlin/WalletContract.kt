package chainline.contracts;

import org.neo.smartcontract.framework.Helper
import org.neo.smartcontract.framework.SmartContract
import java.lang.StringBuilder

class WalletContract : SmartContract() {
   // public key
   // converter: https://conv.darkbyte.ru/


   fun Main(operation: String, arg0: ByteArray, arg1: ByteArray) : Any? {
      if (operation == "rev") return ReverseArray(arg0 as String)
      if (operation == "arrayeq") return arg0 == arg1
      if (operation == "arrayneq") return arg0 != arg1
      return false
   }

   private fun ReverseArray(input: String): ByteArray? {
      val sb = StringBuilder()
      sb.append(input[19])
      sb.append(input[18])
      sb.append(input[17])
      sb.append(input[16])
      sb.append(input[15])
      sb.append(input[14])
      sb.append(input[13])
      sb.append(input[12])
      sb.append(input[11])
      sb.append(input[10])
      sb.append(input[9])
      sb.append(input[8])
      sb.append(input[7])
      sb.append(input[6])
      sb.append(input[5])
      sb.append(input[4])
      sb.append(input[3])
      sb.append(input[2])
      sb.append(input[1])
      sb.append(input[0])
      return sb.toString() as ByteArray?
   }
}
