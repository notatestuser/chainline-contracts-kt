package chainline.contracts

import org.neo.smartcontract.framework.Helper.*
import org.neo.smartcontract.framework.SmartContract
import org.neo.smartcontract.framework.services.neo.Blockchain
import org.neo.smartcontract.framework.services.neo.Runtime
import org.neo.smartcontract.framework.services.neo.Storage
import org.neo.smartcontract.framework.services.system.ExecutionEngine
import java.math.BigInteger

//                  __/___
//            _____/______|
//    _______/_____\_______\_____
//    \              < < <       |
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//     C  H  A  I  N    L  I  N  E

// The compiler doesn't support member fields or companion classes so
// this code is kind of ugly!

object HubContract : SmartContract() {

   private val MAX_TX_VALUE = 549750000000 // approx. (2^40)/2 = 5497.5 GAS

   fun Main(operation: String, vararg args: ByteArray) : Any {
      // The entry points for each of the supported operations follow

      // Initialization
      if (operation == "initialize")
         return init(args[0], args[1], args[2], args[3])
      if (operation == "is_initialized")
         return isInitialized()

      // TODO: can't call IsInitialized() from here apparently
      if (Storage.get(Storage.currentContext(), "Initialized").isEmpty()) {
         Runtime.notify("CLHubNotInitialized")
         return false
      }

      // Operations (only when initialized)
      if (operation == "wallet_getbalance")
         return getWalletBalance(args[0])
      if (operation == "wallet_validate")
         return validateWallet(args[0], args[1])

      return false
   }

   // -==================-
   // -= Initialization =-
   // -==================-

   private fun isInitialized(): Boolean {
      return ! Storage.get(Storage.currentContext(), "Initialized").isEmpty()
   }

   private fun init(assetId: ByteArray, walletScriptP1: ByteArray, walletScriptP2: ByteArray,
                    walletScriptP3: ByteArray): Boolean {
      if (isInitialized()) return false
      Storage.put(Storage.currentContext(), "AssetID", assetId)
      Storage.put(Storage.currentContext(), "WalletScriptP1", walletScriptP1)
      Storage.put(Storage.currentContext(), "WalletScriptP2", walletScriptP2)
      Storage.put(Storage.currentContext(), "WalletScriptP3", walletScriptP3)
      Storage.put(Storage.currentContext(), "Initialized", 1 as ByteArray)
      Runtime.notify("CLHubInitialized")
      return true
   }

   // -================-
   // -=  Operations  =-
   // -================-

   private fun getWalletBalance(scriptHash: ByteArray): BigInteger {
      val account = Blockchain.getAccount(scriptHash)
      Runtime.notify("CLFoundWallet", account.scriptHash())
      return BigInteger.valueOf(account.getBalance(getAssetId()))
   }

   private fun validateWallet(scriptHash: ByteArray, pubKey: ByteArray): Boolean {
      val expectedScript =
            getWalletScriptP1()
               .concat(pubKey)
               .concat(getWalletScriptP2())
               .concat(ExecutionEngine.executingScriptHash())
               .concat(getWalletScriptP3())
      val expectedScriptHash = hash160(expectedScript)
      if (scriptHash == expectedScriptHash) return true
      Runtime.notify("CLWalletValidateFail", expectedScriptHash, expectedScript)
      return false
   }

   // -=============-
   // -=  Storage  =-
   // -=============-

   private fun getAssetId(): ByteArray {
      return Storage.get(Storage.currentContext(), "AssetID")
   }

   private fun getWalletScriptP1(): ByteArray {
      return Storage.get(Storage.currentContext(), "WalletScriptP1")
   }

   private fun getWalletScriptP2(): ByteArray {
      return Storage.get(Storage.currentContext(), "WalletScriptP2")
   }

   private fun getWalletScriptP3(): ByteArray {
      return Storage.get(Storage.currentContext(), "WalletScriptP3")
   }

   // -=============-
   // -=   Utils   =-
   // -=============-

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

   // -==============-
   // -= Extensions =-
   // -==============-

   fun ByteArray.reverse(): ByteArray {
      return reverseArray(this)
   }

   fun ByteArray.concat(b2: ByteArray): ByteArray {
      return concat(this, b2)
   }
}
