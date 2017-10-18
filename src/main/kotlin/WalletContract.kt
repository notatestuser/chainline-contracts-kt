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

	@Appcall("30a2b04139d714564eb956896498616cf8acc8db")
	private external fun HubContract(operation: String, vararg args: Any): Boolean

   fun Main(signature: ByteArray): Boolean {
      // The account owner's public key
      // Included in at wallet creation time
      // Converter: https://conv.darkbyte.ru
      val ownerPubKey = byteArrayOf(
         3, 114, 247 as Byte, 98, 137 as Byte, 198 as Byte, 155 as Byte, 181 as Byte, 138 as Byte, 142 as Byte, 92, 125, 43, 79,
         21, 38, 234 as Byte, 139 as Byte, 38, 192 as Byte, 131 as Byte, 178 as Byte, 169 as Byte, 88, 194 as Byte, 30, 188 as Byte,
         3, 25, 110, 0, 188 as Byte, 192 as Byte)

      // GAS asset ID
      // 602c79718b16e442de58778e148d0b1084e3b2dffd5de6b7b16cee7969282de7
      val gasAssetId = byteArrayOf(
         231 as Byte, 45, 40, 105, 121, 238 as Byte, 108, 177 as Byte, 3, 230 as Byte, 93,
         253 as Byte, 223 as Byte, 178 as Byte, 227 as Byte, 132 as Byte, 16, 11, 141 as Byte,
         20, 142 as Byte, 119, 88, 222 as Byte, 66, 228 as Byte, 22, 139 as Byte, 113, 121, 44, 96)

      // Ensure that we're processing a withdrawal
      if (Runtime.trigger() != TriggerType.Verification)
         return false

      // Verify the tx against the wallet owner's pubkey
      if (!verifySignature(signature, ownerPubKey))
         return false

      // Check that the assetId is GAS
      val tx = ExecutionEngine.scriptContainer() as Transaction?
      val reference = tx!!.references()[0]
      val assetId = reference.assetId()
      if (assetId != gasAssetId)
         return false

      // Get the tx amount (count the gas in outputs)
      val executingScriptHash = ExecutionEngine.executingScriptHash()
      val outputs = tx!!.outputs()
      var value: Long = 0
      outputs.forEach {
         if (it.scriptHash() == executingScriptHash)
            value += it.value()
      }

      val result = HubContract("wallet_requestTxOut", signature, ownerPubKey, value)
      return result
   }
}
