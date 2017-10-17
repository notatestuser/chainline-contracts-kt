package chainline.contracts

import chainline.contracts.HubContract.demand_store
import java.math.BigInteger
import org.neo.smartcontract.framework.SmartContract
import org.neo.smartcontract.framework.Helper.*
import org.neo.smartcontract.framework.services.neo.*
import org.neo.smartcontract.framework.services.system.ExecutionEngine

//                     __ __
//               __ __|__|__|__ __
//         __ __|__|__|__|__|__|__|__
//   _____|__|__|__|__|__|__|__|__|__|__
//   \  < < <                           |
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//       C  H  A  I  N     L  I  N  E

typealias ScriptHash = ByteArray
typealias Hash160 = ByteArray
typealias Hash160Pair = ByteArray
typealias Reservation = ByteArray
typealias ReservationList = ByteArray
typealias Demand = ByteArray
typealias DemandList = ByteArray
typealias Travel = ByteArray
typealias TravelList = ByteArray

object HubContract : SmartContract() {

   private const val TESTS_ENABLED = true

   // Byte array sizes
   private const val VALUE_SIZE = 5
   private const val TIMESTAMP_SIZE = 4
   private const val RESERVATION_SIZE = 30  // timestamp + value + script hash + bool
   private const val SCRIPT_HASH_SIZE = 20  // 160 bits
   private const val PUBLIC_KEY_SIZE = 32 // 256 bits
   private const val REP_REQUIRED_SIZE = 2
   private const val CARRY_SPACE_SIZE = 1
   private const val DEMAND_INFO_SIZE = 128
   private const val DEMAND_SIZE = TIMESTAMP_SIZE + VALUE_SIZE + REP_REQUIRED_SIZE + CARRY_SPACE_SIZE + DEMAND_INFO_SIZE
   private const val TRAVEL_SIZE = TIMESTAMP_SIZE + REP_REQUIRED_SIZE + CARRY_SPACE_SIZE

   // Maximum values
   private const val MAX_GAS_VALUE = 549750000000 // approx. (2^40)/2 = 5497.5 GAS

   // Fees
   private const val FEE_DEMAND_REWARD: Long = 300000000  // 3 GAS
   private const val FEE_TRAVEL_DEPOSIT: Long = 100000000  // 1 GAS

   // Storage key suffixes
   private const val STORAGE_KEY_SUFFIX_DEMAND: Byte = 1
   private const val STORAGE_KEY_SUFFIX_TRAVEL: Byte = 2

   fun Main(operation: String, vararg args: ByteArray) : Any {
      // The entry points for each of the supported operations follow

      // Initialization
      if (operation == "initialize")
         return init(args[0], args[1], args[2], args[3])
      if (operation == "is_initialized")
         return isInitialized()

      // Stateless test operations
      if (TESTS_ENABLED) {
         if (operation == "test_reservation_create")
            return reservation_create(BigInteger(args[0]), BigInteger(args[1]), args[2])
         if (operation == "test_demand_create")
            return demand_create(BigInteger(args[0]), BigInteger(args[1]), BigInteger(args[2]), BigInteger(args[3]), args[4])
         if (operation == "test_travel_create")
            return travel_create(BigInteger(args[0]), BigInteger(args[1]), BigInteger(args[2]))
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
         Runtime.notify("CL:ERR:HubNotInitialized")
         return false
      }

      // Operations (only when initialized)
      if (operation == "wallet_validate")
         return args[0].wallet_validate(args[1])
      if (operation == "wallet_getBalance")
         return args[0].wallet_getBalance()
      if (operation == "wallet_getBalanceOnHold")
         return args[0].wallet_getBalanceOnHold()

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
      val trueBytes = byteArrayOf(1)
      Storage.put(Storage.currentContext(), "AssetID", assetId)
      Storage.put(Storage.currentContext(), "WalletScriptP1", walletScriptP1)
      Storage.put(Storage.currentContext(), "WalletScriptP2", walletScriptP2)
      Storage.put(Storage.currentContext(), "WalletScriptP3", walletScriptP3)
      Storage.put(Storage.currentContext(), "Initialized", trueBytes)
      Runtime.notify("CL:OK:HubInitialized")
      return true
   }

   // -=============-
   // -=  Wallets  =-
   // -=============-

   private fun ScriptHash.wallet_validate(pubKey: ByteArray): Boolean {
      val expectedScript =
            getWalletScriptP1()
               .concat(pubKey)
               .concat(getWalletScriptP2())
               .concat(ExecutionEngine.executingScriptHash())
               .concat(getWalletScriptP3())
      val expectedScriptHash = hash160(expectedScript)
      if (this == expectedScriptHash) return true
      Runtime.notify("CL:ERR:WalletValidateFail", expectedScriptHash, expectedScript)
      return false
   }

   private fun ScriptHash.wallet_getBalance(): Long {
      val account = Blockchain.getAccount(this)
      Runtime.notify("CL:OK:FoundWallet", account.scriptHash())
      return account.getBalance(getAssetId())
   }

   private fun ScriptHash.wallet_getBalanceOnHold(): Long {
      // todo: validate the user's wallet
      //if (! this.wallet_validate())
      val reservations: ByteArray = Storage.get(Storage.currentContext(), this)
      if (reservations.isEmpty()) return 0
      val header = Blockchain.getHeader(Blockchain.height())
      return reservations_getTotalOnHoldValue(reservations, header.timestamp())
   }

   // -==================-
   // -=  Reservations  =-
   // -==================-

   private fun reservations_get(all: ReservationList, index: Int): Reservation {
      return all.range(index * RESERVATION_SIZE, RESERVATION_SIZE)
   }

   private fun reservations_getTotalOnHoldValue(all: ReservationList, nowTime: Int): Long {
      // todo: clean up expired reservation entries
      if (all.isEmpty())
         return 0
      val count = all.size / RESERVATION_SIZE
      var i = 0
      var total: Long = 0
      while (i < count) {
         val reservation = reservations_get(all, i)
         val expiryBytes = take(reservation, TIMESTAMP_SIZE)
         val expiry = BigInteger(expiryBytes)
         if (expiry.toInt() > nowTime) {
            val valueBytes = range(reservation, TIMESTAMP_SIZE, VALUE_SIZE)
            val value = BigInteger(valueBytes)
            total += value.toLong()
         }
         i++
      }
      return total
   }

   private fun reservation_create(expiry: BigInteger, value: BigInteger, destination: ScriptHash): Reservation {
      val falseBytes = byteArrayOf(0)
      // size: 30 bytes
      val reservation = expiry.toByteArray(TIMESTAMP_SIZE)
            .concat(value.toByteArray(VALUE_SIZE))
            .concat(destination)  // script hash, 20 bytes
            .concat(falseBytes)  // 1 byte
      return reservation
   }

   private fun Reservation.res_getExpiry(): BigInteger {
      val expiryBytes = this.take(TIMESTAMP_SIZE)
      return BigInteger(expiryBytes)
   }

   private fun Reservation.res_getValue(): BigInteger {
      val valueBytes = this.range(TIMESTAMP_SIZE, VALUE_SIZE)
      return BigInteger(valueBytes)
   }

   private fun Reservation.res_getDestination(): ScriptHash {
      val scriptHash = this.range(TIMESTAMP_SIZE + VALUE_SIZE, SCRIPT_HASH_SIZE)
      return scriptHash
   }

   private fun Reservation.res_isMultiSigUnlocked(): Boolean? {
      val trueBytes = byteArrayOf(1)
      val multiSigUnlocked = this.range(TIMESTAMP_SIZE + VALUE_SIZE + SCRIPT_HASH_SIZE, 1)
      return multiSigUnlocked == trueBytes
   }

   private fun ReservationList.res_getTotalOnHoldValue(): Long {
      val holdValue = reservations_getTotalOnHoldValue(this, 1)
      return holdValue
   }

   private fun ScriptHash.account_reserveFunds(expiry: BigInteger, value: BigInteger): Boolean {
      val balance = this.wallet_getBalance()
      val toReserve = value.toLong()
      if (balance <= toReserve) {  // insufficient balance
         Runtime.notify("CL:ERR:InsufficientFunds1")
         return false
      }
      val reservations: ByteArray = Storage.get(Storage.currentContext(), this)
      val header = Blockchain.getHeader(Blockchain.height())
      val gasOnHold = reservations_getTotalOnHoldValue(reservations, header.timestamp())
      if (gasOnHold < 0)  // wallet validation failed
         return false
      val effectiveBalance = balance - gasOnHold
      if (effectiveBalance < toReserve) {
         Runtime.notify("CL:ERR:InsufficientFunds2")
         return false
      }
      val emptyScriptHash = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
      val reservation = reservation_create(expiry, value, emptyScriptHash)
      val newReservations = reservations.concat(reservation)
      reservation.account_storeReservations(newReservations)
      Runtime.notify("CL:OK:ReservedFunds", reservation)
      return true
   }

   // -=============-
   // -=  Demands  =-
   // -=============-

   //   demand details:
   //   - pickup and destination cities (hashed) (kept in storage key)
   //   - product/contact info
   //   - expiry (timestamp)
   //   - minimum reputation requirement
   //   - carry space required (sm, md, lg) (1, 2, 3)
   private fun demand_create(expiry: BigInteger, repRequired: BigInteger, itemSize: BigInteger, itemValue: BigInteger,
                             infoBlob: ByteArray): Demand {
      Runtime.notify("CL:DBG:CreatingDemand")
      // checking individual arg lengths doesn't seem to work here
      // I tried a lot of things, grr compiler
      val nil = byteArrayOf()
      if (itemValue.toLong() > MAX_GAS_VALUE)
         return nil
      // size: 140 bytes
      val expectedSize = DEMAND_SIZE
      val demand = expiry.toByteArray(TIMESTAMP_SIZE)
            .concat(itemValue.toByteArray(VALUE_SIZE))
            .concat(repRequired.toByteArray(REP_REQUIRED_SIZE))
            .concat(itemSize.toByteArray(CARRY_SPACE_SIZE))
            .concat(infoBlob)
      if (demand.size != expectedSize)
         return nil
      Runtime.notify("CL:OK:DemandCreated")
      return demand
   }

   private fun Demand.demand_store(owner: ScriptHash, pickUpCityHash: Hash160, dropOffCityHash: Hash160) {
      Runtime.notify("CL:DBG:storeDemand")
      if (owner.account_isInNullState()) {
         Runtime.notify("CL:DBG:StoringDemand")

         // store the demand object
         val cityHashPair = pickUpCityHash.concat(dropOffCityHash)
         val cityHashPairKeyD = cityHashPair.demand_getStorageKey()
         val demandsForCity = Storage.get(Storage.currentContext(), cityHashPairKeyD)
         val newDemandsForCity = demandsForCity.concat(this)
         Storage.put(Storage.currentContext(), cityHashPairKeyD, newDemandsForCity)

         Runtime.notify("CL:OK:StoredDemand", cityHashPair)

         val expiry = this.demand_getExpiry()
         val itemValue = this.demand_getItemValue()
         val toReserve = itemValue.toLong() + FEE_DEMAND_REWARD
         owner.account_reserveFunds(expiry, BigInteger.valueOf(toReserve))

         Runtime.notify("CL:OK:ReservedDemandValueAndFee")

         // find a travel object to match this demand with
         val cityHashPairKeyT = cityHashPair.travel_getStorageKey()
         val travelsForCityPair = Storage.get(Storage.currentContext(), cityHashPairKeyT)
         if (!travelsForCityPair.isEmpty()) {
            val matchKey = this.demand_getMatchKey()
            val repRequired = this.demand_getRepRequired()
            val carrySpaceRequired = this.demand_getItemSize()
            val nowTime = Blockchain.getHeader(Blockchain.height()).timestamp()
            val nowTimeBigInt = BigInteger.valueOf(nowTime as Long)
            val nowTimeBytes = nowTimeBigInt.toByteArray(TIMESTAMP_SIZE)
            val matchedTravel = travelsForCityPair.travels_findMatchableTravel(repRequired, carrySpaceRequired, nowTime)
            val timestampedMatch = nowTimeBytes.concat(matchedTravel)
            Storage.put(Storage.currentContext(), matchKey, timestampedMatch)

            Runtime.notify("CL:OK:MatchedDemandWithTravel")
         }

         Runtime.notify("CL:DBG:Demand.store")
      }
   }

   private fun Demand.demand_getExpiry(): BigInteger {
      val bytes = this.take(TIMESTAMP_SIZE)
      return BigInteger(bytes)
   }

   private fun Demand.demand_getItemValue(): BigInteger {
      val bytes = this.range(TIMESTAMP_SIZE, VALUE_SIZE)
      return BigInteger(bytes)
   }

   private fun Demand.demand_getRepRequired(): BigInteger {
      val bytes = this.range(TIMESTAMP_SIZE + VALUE_SIZE, REP_REQUIRED_SIZE)
      return BigInteger(bytes)
   }

   private fun Demand.demand_getItemSize(): BigInteger {
      val bytes = this.range(TIMESTAMP_SIZE + VALUE_SIZE + REP_REQUIRED_SIZE, CARRY_SPACE_SIZE)
      return BigInteger(bytes)
   }

   private fun Demand.demand_getInfoBlob(): ByteArray {
      return this.range(TIMESTAMP_SIZE + VALUE_SIZE + REP_REQUIRED_SIZE + CARRY_SPACE_SIZE, DEMAND_INFO_SIZE)
   }

   private fun Hash160Pair.demand_getStorageKey(): ByteArray {
      val demandStorageKeySuffix = byteArrayOf(STORAGE_KEY_SUFFIX_DEMAND)
      val cityHashPairKey = this.concat(demandStorageKeySuffix)
      return cityHashPairKey
   }

   private fun Demand.demand_getMatchKey(): ByteArray {
      val key = take(this, TIMESTAMP_SIZE + VALUE_SIZE)
      return key
   }

   private fun Demand.demand_isMatched(): Boolean {
      Runtime.notify("CL:DBG:Demand.isMatched")
      val matchKey = this.demand_getMatchKey()
      val match = Storage.get(Storage.currentContext(), matchKey)
      return !match.isEmpty()
   }

   private fun DemandList.demands_getAt(index: Int): Travel {
      return this.range(index * DEMAND_SIZE, DEMAND_SIZE)
   }

   private fun DemandList.demands_findMatchableDemand(repRequired: BigInteger, carrySpaceAvailable: BigInteger,
                                                      nowTime: Int = Blockchain.getHeader(Blockchain.height()).timestamp()): Demand {
      val nil = byteArrayOf()
      if (this.isEmpty())
         return nil
      val count = this.size / DEMAND_SIZE
      var i = 0
      while (i < count) {
         val demand = this.demands_getAt(i)
         val expiryBytes = take(demand, TIMESTAMP_SIZE)
         val expiry = BigInteger(expiryBytes)
         val itemSize = demand.demand_getItemSize()
         if (expiry.toInt() > nowTime &&
               carrySpaceAvailable >= itemSize &&
               !demand.demand_isMatched())
            return demand
         i++
      }
      return nil
   }

   // -============-
   // -=  Travel  =-
   // -============-

   //   travel details:
   //   - pickup and destination cities (hashed) (kept in storage key)
   //   - departure (expiry) time (kept in state, see ScriptHash.account_storeState)
   //   - minimum reputation requirement
   //   - carry space available
   //   - demand matched with (script hash)
   private fun travel_create(expiry: BigInteger, repRequired: BigInteger, carrySpace: BigInteger): Travel {
      Runtime.notify("CL:DBG:CreatingTravel")
      val nil = byteArrayOf()
      // size: 7 bytes
      val travel = expiry.toByteArray(TIMESTAMP_SIZE)
            .concat(repRequired.toByteArray(REP_REQUIRED_SIZE))
            .concat(carrySpace.toByteArray(CARRY_SPACE_SIZE))
      if (travel.size != TRAVEL_SIZE)
         return nil
      Runtime.notify("CL:OK:TravelCreated")
      return travel
   }

   private fun Travel.travel_store(owner: ScriptHash, pickUpCityHash: Hash160, dropOffCityHash: Hash160) {
      Runtime.notify("CL:DBG:storeTravel")
      if (owner.account_isInNullState()) {
         Runtime.notify("CL:DBG:StoringTravel")

         // store the travel object
         val cityHashPair = pickUpCityHash.concat(dropOffCityHash)
         val cityHashPairKey = cityHashPair.travel_getStorageKey()
         val travelsForCityPair = Storage.get(Storage.currentContext(), cityHashPairKey)
         val newTravelsForCityPair = travelsForCityPair.concat(this)
         Storage.put(Storage.currentContext(), cityHashPairKey, newTravelsForCityPair)

         Runtime.notify("CL:OK:StoredTravel", cityHashPair)

         // reserve the security deposit
         val expiry = this.travel_getExpiry()
         owner.account_reserveFunds(expiry, BigInteger.valueOf(FEE_TRAVEL_DEPOSIT))

         Runtime.notify("CL:OK:ReservedTravelDeposit", cityHashPair)

         // find a demand object to match this travel with
         val cityHashPairKeyD = cityHashPair.demand_getStorageKey()
         val demandsForCityPair = Storage.get(Storage.currentContext(), cityHashPairKeyD)
         if (!demandsForCityPair.isEmpty()) {
            val matchKey = this.travel_getMatchKey()
            val repRequired = this.travel_getRepRequired()
            val carrySpaceAvailable = this.travel_getCarrySpace()
            val nowTime = Blockchain.getHeader(Blockchain.height()).timestamp()
            val nowTimeBigInt = BigInteger.valueOf(nowTime as Long)
            val nowTimeBytes = nowTimeBigInt.toByteArray(TIMESTAMP_SIZE)
            val matchedDemand = demandsForCityPair.demands_findMatchableDemand(repRequired, carrySpaceAvailable, nowTime)
            val timestampedMatch = nowTimeBytes.concat(matchedDemand)
            Storage.put(Storage.currentContext(), matchKey, timestampedMatch)

            Runtime.notify("CL:OK:MatchedTravelWithDemand")
         }

         Runtime.notify("CL:DBG:Travel.store")
      }
   }

   private fun Travel.travel_getExpiry(): BigInteger {
      val bytes = this.take(TIMESTAMP_SIZE)
      return BigInteger(bytes)
   }

   private fun Travel.travel_getRepRequired(): BigInteger {
      val bytes = this.range(TIMESTAMP_SIZE, REP_REQUIRED_SIZE)
      return BigInteger(bytes)
   }

   private fun Travel.travel_getCarrySpace(): BigInteger {
      val bytes = this.range(TIMESTAMP_SIZE + REP_REQUIRED_SIZE, CARRY_SPACE_SIZE)
      return BigInteger(bytes)
   }

   private fun Hash160Pair.travel_getStorageKey(): ByteArray {
      val travelStorageKeySuffix = byteArrayOf(STORAGE_KEY_SUFFIX_TRAVEL)
      val cityHashPairKey = this.concat(travelStorageKeySuffix)
      return cityHashPairKey
   }

   private fun Travel.travel_getMatchKey(): ByteArray {
      return this
   }

   private fun Travel.travel_isMatched(): Boolean {
      Runtime.notify("CL:DBG:Travel.isMatched")
      val matchKey = this.travel_getMatchKey()
      val match = Storage.get(Storage.currentContext(), matchKey)
      return !match.isEmpty()
   }

   private fun TravelList.travels_getAt(index: Int): Travel {
      return this.range(index * TRAVEL_SIZE, TRAVEL_SIZE)
   }

   private fun TravelList.travels_findMatchableTravel(repRequired: BigInteger, carrySpaceRequired: BigInteger, nowTime: Int): Travel {
      val nil = byteArrayOf()
      if (this.isEmpty())
         return nil
      val count = this.size / TRAVEL_SIZE
      var i = 0
      while (i < count) {
         val travel = this.travels_getAt(i)
         val expiryBytes = take(travel, TIMESTAMP_SIZE)
         val expiry = BigInteger(expiryBytes)
         val carrySpaceAvailable = this.travel_getCarrySpace()
         if (expiry.toInt() > nowTime &&
               carrySpaceAvailable >= carrySpaceRequired &&
               !travel.travel_isMatched())
            return travel
         i++
      }
      return nil
   }

   // -=============-
   // -=  Storage  =-
   // -=============-

   private fun getAssetId(): ByteArray {
      //return Storage.get(Storage.currentContext(), "AssetID")
      // now reversed. see: https://git.io/vdM02
      val gasAssetId = byteArrayOf(231 as Byte, 45, 40, 105, 121, 238 as Byte, 108, 177 as Byte, 3, 230 as Byte, 93,
         253 as Byte, 223 as Byte, 178 as Byte, 227 as Byte, 132 as Byte, 16, 11, 141 as Byte, 20, 142 as Byte, 119,
         88, 222 as Byte, 66, 228 as Byte, 22, 139 as Byte, 113, 121, 44, 96)
      return gasAssetId
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

   private fun ScriptHash.account_getReservations(): ByteArray {
      val reservations = Storage.get(Storage.currentContext(), this)
      return reservations
   }

   private fun ScriptHash.account_storeReservations(resList: ReservationList) {
      Storage.put(Storage.currentContext(), this, resList)
   }

   private fun ScriptHash.account_isInNullState(nowTime: Int = Blockchain.getHeader(Blockchain.height()).timestamp()): Boolean {
      // checking active reservations tells us what state the account is in
      val reservations = Storage.get(Storage.currentContext(), this)
      if (reservations.isEmpty())
         return true
      val size = reservations.size
      var i = 0
      while (i < size) {
         val reservation = reservations_get(reservations, i)
         val expiryBytes = take(reservation, TIMESTAMP_SIZE)
         val expiry = BigInteger(expiryBytes)
         val valueBytes = range(reservation, TIMESTAMP_SIZE, VALUE_SIZE)
         val value = BigInteger(valueBytes)
         if (expiry.toInt() > nowTime && value.toLong() > 0)
            return false
         i++
      }
      return true
   }

   // -=============-
   // -=  Helpers  =-
   // -=============-

   private fun getEmptyScriptHash(): ByteArray {
      val emptyScriptHash = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
      return emptyScriptHash
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
