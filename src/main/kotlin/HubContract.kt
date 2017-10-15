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

typealias Hash160 = ByteArray
typealias ScriptHash = ByteArray
typealias Reservation = ByteArray
typealias ReservationList = ByteArray
typealias Demand = ByteArray
typealias Travel = ByteArray

object HubContract : SmartContract() {

   const val TESTS_ENABLED = true

   // Byte array sizes
   private const val VALUE_SIZE = 5
   private const val TIMESTAMP_SIZE = 4
   private const val RESERVATION_SIZE = 30  // timestamp + value + script hash + bool
   private const val SCRIPT_HASH_SIZE = 20  // 160 bits
   private const val PUBLIC_KEY_SIZE = 32 // 256 bits
   private const val REP_REQUIRED_SIZE = 2
   private const val CARRY_SPACE_SIZE = 1
   private const val DEMAND_INFO_SIZE = 128

   // Maximum values
   private const val MAX_GAS_VALUE = 549750000000 // approx. (2^40)/2 = 5497.5 GAS

   // Storage markers
   private const val ACCOUNT_MARKER_RESERVED_FUNDS = 0x01 as Byte

   // Account states
   private const val ACCOUNT_STATE_DEMAND = 0x02 as Byte
   private const val ACCOUNT_STATE_TRAVEL = 0x03 as Byte

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
         if (operation == "test_demand_create")
            return demand_create(args[0], (args[1] as BigInteger?)!!, (args[2] as BigInteger?)!!,
                                 (args[3] as BigInteger?)!!, args[4])
         if (operation == "test_travel_create")
            return travel_create(args[0], args[1], (args[2] as BigInteger?)!!, (args[3] as BigInteger?)!!)
         if (operation == "test_reservation_getExpiry")
            return args[0].res_getExpiry()
         if (operation == "test_reservation_getValue")
            return args[0].res_getValue()
         if (operation == "test_reservation_getDestination")
            return args[0].res_getDestination()
         if (operation == "test_reservation_isMultiSigUnlocked")
            return args[0].res_isMultiSigUnlocked()!!
         if (operation == "test_reservation_getTotalOnHoldValue")
            return args[0].res_getTotalOnHoldValue()
         if (operation == "test_demand_getItemValue")
            return args[0].demand_getItemValue()
         if (operation == "test_demand_getInfoBlob")
            return args[0].demand_getInfoBlob()
         if (operation == "test_travel_getCarrySpace")
            return args[0].travel_getCarrySpace()
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

   private fun wallet_getBalanceOnHold(scriptHash: ScriptHash): Long {
      val reservations: ByteArray = Storage.get(Storage.currentContext(), scriptHash)
      if (reservations.isEmpty()) return 0
      val header = Blockchain.getHeader(Blockchain.height())
      return reservations_getTotalOnHoldValue(reservations, header.timestamp())
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

   private fun reservations_getTotalOnHoldValue(all: ByteArray, nowTime: Int): Long {
      // todo: clean up expired reservation entries
      val count = all.size
      var i = 0
      var total: Long = 0
      while (i < count) {
         val reservation = reservations_get(all, i)
         val expiry = take(reservation, TIMESTAMP_SIZE) as Int?
         if (expiry!! > nowTime) {
            val value = range(reservation, TIMESTAMP_SIZE, VALUE_SIZE) as BigInteger?
            total += value!!.toLong()
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

   private fun Reservation.res_isMultiSigUnlocked(): Boolean? {
      val multiSigUnlocked = this.range(TIMESTAMP_SIZE + VALUE_SIZE + SCRIPT_HASH_SIZE, 1)
      return multiSigUnlocked as Boolean?
   }

   private fun ReservationList.res_getTotalOnHoldValue(): Long {
      val holdValue = reservations_getTotalOnHoldValue(this, 1)
      return holdValue
   }

   // -=============-
   // -=  Demands  =-
   // -=============-

   //   demand details:
   //   - product/contact info
   //   - destination city (hashed)
   //   - expiry (timestamp) (kept in state, see ScriptHash.account_storeState)
   //   - minimum reputation requirement
   //   - carry space required (sm, md, lg) (1, 2, 3)
   private fun demand_create(cityHash: Hash160, repRequired: BigInteger, itemSize: BigInteger,
                             itemValue: BigInteger, infoBlob: ByteArray): Demand {
      // checking individual arg lengths doesn't seem to work here
      // I tried a lot of things, grr compiler
      val nil = byteArrayOf()
      if (itemValue.toLong() > MAX_GAS_VALUE)
         return nil
      // size: 156 bytes
      val expectedSize =
            20 + REP_REQUIRED_SIZE + CARRY_SPACE_SIZE + VALUE_SIZE + DEMAND_INFO_SIZE
      val demand = cityHash
            .concat(repRequired.toByteArray(REP_REQUIRED_SIZE))
            .concat(itemSize.toByteArray(CARRY_SPACE_SIZE))
            .concat(itemValue.toByteArray(VALUE_SIZE))
            .concat(infoBlob)
      if (demand.size != expectedSize)
         return nil
      return demand
   }

   private fun Demand.demand_getCityHash(): Hash160 {
      val bytes = this.take(20)  // 160 / 8
      return bytes
   }

   private fun Demand.demand_getRepRequired(): BigInteger {
      val bytes = this.range(20, REP_REQUIRED_SIZE)
      return (bytes as BigInteger?)!!
   }

   private fun Demand.demand_getItemSize(): BigInteger {
      val bytes = this.range(20 + REP_REQUIRED_SIZE, CARRY_SPACE_SIZE)
      return (bytes as BigInteger?)!!
   }

   private fun Demand.demand_getItemValue(): BigInteger {
      val bytes = this.range(20 + REP_REQUIRED_SIZE + CARRY_SPACE_SIZE, VALUE_SIZE)
      return (bytes as BigInteger?)!!
   }

   private fun Demand.demand_getInfoBlob(): ByteArray {
      val bytes = this.range(20 + REP_REQUIRED_SIZE + CARRY_SPACE_SIZE + VALUE_SIZE, DEMAND_INFO_SIZE)
      return bytes
   }

   // -=============-
   // -=  Travel   =-
   // -=============-

   //   travel details:
   //   - pickup city (hashed)
   //   - destination city (hashed)
   //   - departure (expiry) time (kept in state, see ScriptHash.account_storeState)
   //   - minimum reputation requirement
   //   - carry space available
   private fun travel_create(pickupCityHash: Hash160, destCityHash: Hash160,
                             repRequired: BigInteger, carrySpace: BigInteger): Travel {
      val nil = byteArrayOf()
      val expectedSize =
            20 + 20 + REP_REQUIRED_SIZE + CARRY_SPACE_SIZE
      // size: 43 bytes
      val reservation = pickupCityHash
            .concat(destCityHash)
            .concat(repRequired.toByteArray(REP_REQUIRED_SIZE))
            .concat(carrySpace.toByteArray(CARRY_SPACE_SIZE))
      if (reservation.size != expectedSize)
         return nil
      return reservation
   }

   private fun Travel.travel_getPickupCityHash(): Hash160 {
      val bytes = this.take(20)
      return bytes
   }

   private fun Travel.travel_getDestCityHash(): Hash160 {
      val bytes = this.range(20, 20)
      return bytes
   }

   private fun Travel.travel_getRepRequired(): BigInteger {
      val bytes = this.range(20 + 20, REP_REQUIRED_SIZE)
      return (bytes as BigInteger?)!!
   }

   private fun Travel.travel_getCarrySpace(): BigInteger {
      val bytes = this.range(20 + 20 + REP_REQUIRED_SIZE, CARRY_SPACE_SIZE)
      return (bytes as BigInteger?)!!
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

   private fun ScriptHash.account_storeState(marker: ByteArray, expiry: BigInteger) {
      val state =  expiry.toByteArray(TIMESTAMP_SIZE).concat(marker)
      Storage.put(Storage.currentContext(), this, state)
   }

   private fun ScriptHash.account_storeReservation(res: Reservation) {
      val marker = byteArrayOf(ACCOUNT_MARKER_RESERVED_FUNDS)
      val key = this.concat(marker)
      Storage.put(Storage.currentContext(), key, res)
   }

   private fun ScriptHash.account_storeDemand(demand: Demand, expiry: BigInteger) {
      val demandStateMarker = byteArrayOf(ACCOUNT_STATE_DEMAND)
      if (this.account_isInNullState()) {
         val key = this.concat(demandStateMarker)
         Storage.put(Storage.currentContext(), key, demand)
         this.account_storeState(demandStateMarker, expiry)
      }
   }

   private fun ScriptHash.account_storeTravel(travel: Travel, expiry: BigInteger) {
      val travelStateMarker = byteArrayOf(ACCOUNT_STATE_TRAVEL)
      if (this.account_isInNullState()) {
         val key = this.concat(travelStateMarker)
         Storage.put(Storage.currentContext(), key, travel)
         this.account_storeState(travelStateMarker, expiry)
      }
   }

   private fun ScriptHash.account_isInNullState(): Boolean {
      val nowTime = Blockchain.getHeader(Blockchain.height()).timestamp()
      val state = Storage.get(Storage.currentContext(), this)
      if (state.isEmpty())
         return true
      val expiry = take(state, TIMESTAMP_SIZE) as Int?
      if (expiry!! > nowTime)
         return true
      return false
   }

   // -================-
   // -=  Extensions  =-
   // -================-

   fun ByteArray.concat(b2: ByteArray): ByteArray {
      return concat(this, b2)
   }

   fun ByteArray.range(index: Int, count: Int): ByteArray {
      return range(this, index, count)
   }

   fun ByteArray.take(count: Int): ByteArray {
      return take(this, count)
   }

   fun ByteArray.pad(count: Int): ByteArray {
      var bytes = this
      if (bytes.size >= count) return bytes
      var zero = byteArrayOf(0)
      while (bytes.size < count) {
         bytes = bytes.concat(zero)
      }
      return bytes
   }

   fun BigInteger.toByteArray(count: Int): ByteArray {
      var bytes = this.toByteArray()
      return bytes.pad(count)
   }
}
