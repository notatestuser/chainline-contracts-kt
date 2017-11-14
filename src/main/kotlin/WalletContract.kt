package chainline.contracts

import org.neo.smartcontract.framework.*
import org.neo.smartcontract.framework.services.neo.*
import org.neo.smartcontract.framework.services.system.ExecutionEngine

//                     __ __
//               __ __|__|__|__ __
//         __ __|__|__|__|__|__|__|__
//   _____|__|__|__|__|__|__|__|__|__|__
//   \  < < <                           |
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//       C  H  A  I  N     L  I  N  E

object WalletContract : SmartContract() {
   /**
    * The entry point of the smart contract.
    * @param operation The method to run, specified as a string.
    * @param args A variable length array of arguments provided to the method.
    */
   fun Main(signature: ByteArray): Boolean {
      // the account owner's public key
      // included in at wallet creation time
      // converter: https://conv.darkbyte.ru
      val ownerPubKey = byteArrayOf(
            3, 114, 247 as Byte, 98, 137 as Byte, 198 as Byte, 155 as Byte, 181 as Byte, 138 as Byte, 142 as Byte, 92, 125, 43, 79,
            21, 38, 234 as Byte, 139 as Byte, 38, 192 as Byte, 131 as Byte, 178 as Byte, 169 as Byte, 88, 194 as Byte, 30, 188 as Byte,
            3, 25, 110, 0, 188 as Byte, 192 as Byte)

      // GAS asset ID
      // 602c79718b16e442de58778e148d0b1084e3b2dffd5de6b7b16cee7969282de7
      val gasAssetId = byteArrayOf(
            231 as Byte, 45, 40, 105, 121, 238 as Byte, 108, 177 as Byte, 183 as Byte, 230 as Byte, 93,
            253 as Byte, 223 as Byte, 178 as Byte, 227 as Byte, 132 as Byte, 16, 11, 141 as Byte,
            20, 142 as Byte, 119, 88, 222 as Byte, 66, 228 as Byte, 22, 139 as Byte, 113, 121, 44, 96)

      // verify the signature against the wallet owner's pubkey
      // this is used because the InteropInterface VM type cannot be consumed via GetByteArray() (https://git.io/vFcJf)
      // unfortunately that is what !call() previously made the VM do here (it becomes IFNE -> NUMNOTEQUAL, then FAULT)
      vm_throwIfNot(verifySignature(signature, ownerPubKey))

      // ensure that we are processing a withdrawal
      // todo: causes a FAULT for now, not a big deal
      //if (Runtime.trigger() !== TriggerType.Verification)
      //   return true

      // find the GAS value of the tx; is it worth making an appcall to check the reserved balance?
      val tx = ExecutionEngine.scriptContainer() as Transaction?
      val executingScriptHash = ExecutionEngine.executingScriptHash()

      // count all inputs first in case funds do not appear in outputs (system fee payments?)
      val refs = tx!!.references()
      var gasTxValue: Long = 0  // as a fixed8 long
      refs.forEach {
         if (it.assetId() === gasAssetId)
            gasTxValue += it.value()
      }
      if (gasTxValue > 0) {
         val outputs = tx.outputs()
         outputs.forEach {
            // invokes will not count as their GAS is returned to the caller
            if (it.scriptHash() === executingScriptHash &&
                  it.assetId() === gasAssetId)
               gasTxValue -= it.value()
         }

         // call the HubContract to validate the withdrawal
         if (gasTxValue > 0)
            return HubContract("wallet_requestTxOut", executingScriptHash, ownerPubKey, gasTxValue)
      }

      // allow anything else
      return true
   }

   /**
    * Calls the [HubContract] with the specified [operation] and [args].
    */
   // The target hash here must be in little endian (reversed when taken from the GUI)
   @Appcall("4eda3d53166c5c1c3dbda21fd31d015024b0510b")
   private external fun HubContract(operation: String, vararg args: Any): Boolean

   /**
    * Inserts the "THROWIFNOT" VM OpCode.
    * Aborts execution if the supplied [arg] is not true.
    */
   @OpCode(org.neo.vm._OpCode.THROWIFNOT)
   private external fun vm_throwIfNot(arg: Any)
}
