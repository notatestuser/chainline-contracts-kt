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

class HubContract : SmartContract() {

   fun Main(operation: String, arg0: ByteArray, arg1: ByteArray, arg2: ByteArray, arg3: ByteArray) : Any {
      // The entry points for each of the supported operations follow

      // Initialization
      if (operation == "initialize")
         return init(arg0, arg1, arg2, arg3)
      if (operation == "is_initialized")
         return isInitialized()

      // Sanity Tests
      if (operation == "test_arrayrev")
         return arg0.reverse()
      if (operation == "test_arrayeq")
         return arg0 == arg1
      if (operation == "test_arrayneq")
         return arg0 != arg1
      if (operation == "test_bigintsize")
         return arg0.size

      // TODO: can't call IsInitialized() from here apparently
      if (Storage.get(Storage.currentContext(), "Initialized").isEmpty()) {
         Runtime.notify("CLHubNotInitialized")
         return false
      }

      // Operations (only when initialized)
      if (operation == "wallet_getbalance")
         return getWalletBalance(arg0)
      if (operation == "wallet_validate")
         return validateWallet(arg0, arg1)

      return false
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
