package chainline.contracts

import java.math.BigInteger
import org.neo.smartcontract.framework.Helper.*
import org.neo.smartcontract.framework.SmartContract
import org.neo.smartcontract.framework.services.neo.Blockchain
import org.neo.smartcontract.framework.services.neo.Runtime
import org.neo.smartcontract.framework.services.neo.Storage
import org.neo.smartcontract.framework.services.system.ExecutionEngine

//                     __ __
//               __ __|__|__|__ __
//         __ __|__|__|__|__|__|__|
//   _____|__|__|__|__|__|__|__|__|___
//   \  < < <                         |
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//      C  H  A  I  N     L  I  N  E

typealias ScriptHash = ByteArray
typealias Reservation = ByteArray
typealias ReservationList = ByteArray

object HubContract : SmartContract() {

   const val TESTS_ENABLED = true

   // Byte array sizes
   const val VALUE_SIZE = 5
   const val TIMESTAMP_SIZE = 4
   const val RESERVATION_SIZE = 30  // timestamp + value + scripthash + bool
   const val SCRIPT_HASH_SIZE = 20  // 160 bits
   const val PUBLIC_KEY_SIZE = 32 // 256 bits

   // Maximum values
   const val MAX_GAS_VALUE = 549750000000 // approx. (2^40)/2 = 5497.5 GAS


   fun Main(operation: String, vararg args: ByteArray) : Any {
      // The entry points for each of the supported operations follow

      // Initialization
      if (operation == "initialize")
         return init(args[0], args[1], args[2], args[3])
      if (operation == "is_initialized")
         return isInitialized()

      // Stateless tests operations
      if (TESTS_ENABLED) {
         if (operation == "test_reservation_create")
            return reservation_create((args[0] as BigInteger?)!!, (args[1] as BigInteger?)!!,
               args[2], (args[3] as Boolean?)!!)
         if (operation == "test_reservation_getExpiry")
            return args[0].res_getExpiry()
         if (operation == "test_reservation_getValue")
            return args[0].res_getValue()
         if (operation == "test_reservation_getDestination")
            return args[0].res_getDestination()
         if (operation == "test_reservation_isMultiSigUnlocked")
            return args[0].res_isMultiSigUnlocked()
      }

      // Can't call IsInitialized() from here 'cause the compiler don't like it
      if (Storage.get(Storage.currentContext(), "Initialized").isEmpty()) {
         Runtime.notify("CLHubNotInitialized")
         return false
      }

      // Operations (only when initialized)
      if (operation == "wallet_validate")
         return wallet_validate(args[0], args[1])
      if (operation == "wallet_getBalance")
         return wallet_getBalance(args[0])
      if (operation == "wallet_getBalanceOnHold")
         return wallet_getBalanceOnHold(args[0])

      return false
   }

   // -====================-
   // -=  Initialization  =-
   // -====================-

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

   private fun wallet_validate(scriptHash: ScriptHash, pubKey: ByteArray): Boolean {
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

   private fun wallet_getBalance(scriptHash: ScriptHash): BigInteger {
      val account = Blockchain.getAccount(scriptHash)
      Runtime.notify("CLFoundWallet", account.scriptHash())
      return BigInteger.valueOf(account.getBalance(getAssetId()))
   }

   private fun wallet_getBalanceOnHold(scriptHash: ScriptHash): BigInteger {
      val reservations: ByteArray = Storage.get(Storage.currentContext(), scriptHash)
      if (reservations.isEmpty()) return 0 as BigInteger
      val header = Blockchain.getHeader(Blockchain.height())
      return reservations_getActiveHoldValue(reservations, header.timestamp())
   }

   // -==================-
   // -=  Reservations  =-
   // -==================-

   private fun revervations_count(all: ReservationList): Int {
      if (all.isEmpty()) return 0
      return all.size / RESERVATION_SIZE
   }

   private fun reservations_get(all: ByteArray, index: Int): Reservation {
      return all.range(index * RESERVATION_SIZE, RESERVATION_SIZE)
   }

   private fun reservations_getActiveHoldValue(all: ByteArray, nowTime: Int): BigInteger {
      // todo: clean up expired reservation entries
      var i = 0
      val total = 0 as BigInteger
      while (i < all.size) {
         val reservation = reservations_get(all, i)
         val expiry = reservation.res_getExpiry()
         if (expiry > nowTime as BigInteger) {
            val value = reservation.res_getValue()
            total.add(value)
         }
         i++
      }
      return total
   }

   private fun reservation_create(expiry: BigInteger, value: BigInteger, destination: ScriptHash,
                                  multiSigUnlocked: Boolean): Reservation {
      val trueBool = byteArrayOf(1)
      val falseBool = byteArrayOf(0)
      // size: 30 bytes
      val reservation = expiry.toByteArray(TIMESTAMP_SIZE)
         .concat(value.toByteArray(VALUE_SIZE))
         .concat(destination)  // script hash, 20 bytes
         .concat(if (multiSigUnlocked) trueBool else falseBool)  // 1 byte
      return reservation
   }

   private fun Reservation.res_getExpiry(): BigInteger {
      val expiry = this.take(TIMESTAMP_SIZE) as BigInteger?
      return expiry!!
   }

   private fun Reservation.res_getValue(): BigInteger {
      val value = this.range(TIMESTAMP_SIZE, VALUE_SIZE) as BigInteger?
      return value!!
   }

   private fun Reservation.res_getDestination(): ScriptHash {
      val scriptHash = this.range(TIMESTAMP_SIZE + VALUE_SIZE, SCRIPT_HASH_SIZE)
      return scriptHash
   }

   private fun Reservation.res_isMultiSigUnlocked(): Boolean {
      val trueBytes = byteArrayOf(1)
      val multiSigUnlocked = this.range(TIMESTAMP_SIZE + VALUE_SIZE + SCRIPT_HASH_SIZE, 1)
      return multiSigUnlocked.isNotEmpty() && multiSigUnlocked == trueBytes
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

   // -===========-
   // -=  Utils  =-
   // -===========-

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

   // -================-
   // -=  Extensions  =-
   // -================-

   fun ByteArray.reverse(): ByteArray {
      return reverseArray(this)
   }

   fun ByteArray.concat(b2: ByteArray): ByteArray {
      return concat(this, b2)
   }

   fun ByteArray.range(index: Int, count: Int): ByteArray {
      return range(this, index, count)
   }

   fun ByteArray.take(count: Int): ByteArray {
      return take(this, count)
   }

   fun BigInteger.toByteArray(count: Int): ByteArray {
      var bytes = this.toByteArray()
      if (bytes.size >= count) return bytes
      var zero = byteArrayOf(0)
      while (bytes.size < count) {
         bytes = bytes.concat(zero)
      }
      return bytes
   }
}
