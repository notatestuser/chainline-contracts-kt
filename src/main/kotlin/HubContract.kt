package chainline.contracts

import java.math.BigInteger
import org.neo.smartcontract.framework.*
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
typealias PublicKey = ByteArray
typealias Hash160 = ByteArray
typealias Hash256 = ByteArray
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
   private const val SCRIPT_HASH_SIZE = 20  // 160 bits
   private const val TX_HASH_SIZE = 32  // 256 bits
   private const val PUBLIC_KEY_SIZE = 32
   private const val REP_REQUIRED_SIZE = 2
   private const val CARRY_SPACE_SIZE = 1
   private const val DEMAND_INFO_SIZE = 128

   // Object sizes
   private const val RESERVATION_SIZE =
         TIMESTAMP_SIZE + VALUE_SIZE + SCRIPT_HASH_SIZE + 1
   private const val DEMAND_SIZE =
         TIMESTAMP_SIZE + VALUE_SIZE + SCRIPT_HASH_SIZE + REP_REQUIRED_SIZE + CARRY_SPACE_SIZE + DEMAND_INFO_SIZE
   private const val TRAVEL_SIZE =
         TIMESTAMP_SIZE + SCRIPT_HASH_SIZE + REP_REQUIRED_SIZE + CARRY_SPACE_SIZE

   // Maximum values
   // the maximum value of a transaction is fixed at ~5497 GAS so that we can fit it into 5 bytes.
   private const val MAX_GAS_TX_VALUE = 549750000000 // approx. (2^40)/2 = 5497.5 GAS

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
         return init(args[0], args[1], args[2])
      if (operation == "is_initialized")
         return isInitialized()

      // Stateless test operations
      if (TESTS_ENABLED) {
         if (operation == "test_reservation_create")
            return reservation_create(BigInteger(args[0]), BigInteger(args[1]), args[2])
         if (operation == "test_demand_create")
            return demand_create(BigInteger(args[0]), BigInteger(args[1]), BigInteger(args[2]), BigInteger(args[3]), args[4], args[5])
         if (operation == "test_travel_create")
            return travel_create(BigInteger(args[0]), BigInteger(args[1]), BigInteger(args[2]), args[3])
         if (operation == "test_reservation_getExpiry")
            return args[0].res_getExpiry()
         if (operation == "test_reservation_getValue")
            return args[0].res_getValue()
         if (operation == "test_reservation_getDestination")
            return args[0].res_getRecipient()
         if (operation == "test_reservation_isMultiSigUnlocked")
            return args[0].res_isMultiSigUnlocked()
         if (operation == "test_reservation_getTotalOnHoldValue")
            return args[0].res_getTotalOnHoldGasValue(1)
         if (operation == "test_demand_getItemValue")
            return args[0].demand_getItemValue()
         if (operation == "test_demand_getInfoBlob")
            return args[0].demand_getInfoBlob()
         if (operation == "test_travel_getCarrySpace")
            return args[0].travel_getCarrySpace()
         if (operation == "test_travel_getOwnerScriptHash")
            return args[0].travel_getOwnerScriptHash()
      }

      // Can't call IsInitialized() from here 'cause the compiler don't like it
      if (Storage.get(Storage.currentContext(), "Initialized").isEmpty()) {
         Runtime.notify("CL:ERR:HubNotInitialized")
         return false
      }

      // Wallet operations
      if (operation == "wallet_validate")
         return args[0].wallet_validate(args[1])
      if (operation == "wallet_getBalance")
         return args[0].wallet_getBalance()
      // if (operation == "wallet_getBalanceOnHold") {
      //    val reservations = Storage.get(Storage.currentContext(), args[0])
      //    if (reservations.isEmpty())
      //       return 0
      //    val nowTime = Blockchain.getHeader(Blockchain.height()).timestamp()
      //    val gasOnHold = reservations.res_getTotalOnHoldGasValue(nowTime)
      // }
      if (operation == "wallet_requestTxOut") {
         if (args[0].wallet_validate(args[1])) {
            val reservations = args[0].account_getReservations()
            return args[0].wallet_requestTxOut(BigInteger(args[3]), reservations)
         }
         Runtime.notify("CL:ERR:InvalidWallet")
         return false
      }
      if (operation == "wallet_setReservationPaidToRecipientTxHash") {
         if (args[0].wallet_validate(args[1]))
            return args[0].wallet_setReservationPaidToRecipientTxHash(args[2], BigInteger(args[3]), args[4])
         return false
      }

      return false
   }

   // -====================-
   // -=  Initialization  =-
   // -====================-

   private fun isInitialized(): Boolean {
      return ! Storage.get(Storage.currentContext(), "Initialized").isEmpty()
   }

   private fun init(walletScriptP1: ByteArray, walletScriptP2: ByteArray, walletScriptP3: ByteArray): Boolean {
      if (isInitialized()) return false
      val trueBytes = byteArrayOf(1)
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
      if (this == expectedScriptHash)
         return true
      Runtime.notify("CL:ERR:WalletValidateFail", expectedScriptHash, expectedScript)
      return false
   }

   private fun ScriptHash.wallet_getBalance(): Long {
      val account = Blockchain.getAccount(this)
      Runtime.notify("CL:OK:FoundWallet", account.scriptHash())
      return account.getBalance(getAssetId())
   }

   private fun ScriptHash.wallet_requestTxOut(value: BigInteger, reservations: ReservationList): Boolean {
      Runtime.notify("CL:DBG:requestTxOut")
      // check if balance is enough after reserved funds are considered
      val balance = this.wallet_getBalance()
      val nowTime = Blockchain.getHeader(Blockchain.height()).timestamp()
      val gasOnHold = reservations.res_getTotalOnHoldGasValue(nowTime)
      val effectiveBalance = balance - gasOnHold
      if (effectiveBalance < value.toLong()) {
         Runtime.notify("CL:ERR:InsufficientBalance")
         return false  // insufficient non-reserved funds
      }
      Runtime.notify("CL:OK:RequestTxOut1")
      return true
   }

   // because we can't access storage in a verify script, we have to use an invoke to set the tx hash.
   // this function finds the given transaction and makes sure the supplied value went to the recipient
   private fun ScriptHash.wallet_setReservationPaidToRecipientTxHash(recipient: ScriptHash, value: BigInteger,
                                                                     txHash: Hash256): Boolean {
      Runtime.notify("CL:DBG:setReservationPaidToRecipientTxHash")
      val reservations = this.account_getReservations()
      val matchIdx = reservations.res_findBy(value, recipient)
      if (matchIdx > -1) {
         val matchedRes = reservations.res_getAt(matchIdx)
         val stored = Storage.get(Storage.currentContext(), matchedRes)
         if (stored.isEmpty()) {
            val tx = Blockchain.getTransaction(txHash)
            val outputs = tx!!.outputs()
            var txValue: Long = 0
            outputs.forEach {
               if (it.scriptHash() == recipient)
                  txValue += it.value()
            }
            if (txValue >= value.toLong()) {
               Storage.put(Storage.currentContext(), matchedRes, txHash)
               return true
            }
            return false
         }
         return false
      }
      return false
   }

   // -==================-
   // -=  Reservations  =-
   // -==================-

   private fun reservation_create(expiry: BigInteger, value: BigInteger, destination: ScriptHash): Reservation {
      val falseBytes = byteArrayOf(0)
      // size: 30 bytes
      val reservation = expiry.toByteArray(TIMESTAMP_SIZE)
            .concat(value.toByteArray(VALUE_SIZE))
            .concat(destination)  // script hash, 20 bytes
            .concat(falseBytes)  // 1 byte
      return reservation
   }

   private fun ReservationList.res_getAt(index: Int): Reservation {
      return this.range(index * RESERVATION_SIZE, RESERVATION_SIZE)
   }

   private fun ReservationList.res_findBy(value: BigInteger): Int {
      if (this.isEmpty())
         return -1
      val count = this.size / RESERVATION_SIZE
      var i = 0
      while (i < count) {
         val reservation = this.res_getAt(i)
         val valueBytes = range(reservation, TIMESTAMP_SIZE, VALUE_SIZE)
         val valueFound = BigInteger(valueBytes)
         if (value == valueFound)
            return i
         i++
      }
      return -1
   }

   private fun ReservationList.res_findBy(value: BigInteger, recipient: ScriptHash): Int {
      if (this.isEmpty())
         return -1
      val count = this.size / RESERVATION_SIZE
      var i = 0
      while (i < count) {
         val reservation = this.res_getAt(i)
         val valueBytes = range(reservation, TIMESTAMP_SIZE, VALUE_SIZE)
         val valueFound = BigInteger(valueBytes)
         val recipientFound = reservation.res_getRecipient()
         if (value == valueFound &&
               recipient == recipientFound)
            return i
         i++
      }
      return -1
   }

   private fun ReservationList.res_replaceRecipientAt(idx: Int, recipient: ScriptHash): ReservationList {
      val falseBytes = byteArrayOf(0)
      val items = this.size / RESERVATION_SIZE
      val skipCount = idx * RESERVATION_SIZE
      val before = range(this, 0, skipCount + TIMESTAMP_SIZE + VALUE_SIZE)
      val restIdx = skipCount + SCRIPT_HASH_SIZE + 1
      val after = range(restIdx, this.size - restIdx)
      val newList = before
            .concat(recipient)
            .concat(falseBytes)
            .concat(after)
      return newList
   }

   private fun ReservationList.res_unlockMultiSigAt(idx: Int): ReservationList {
      val trueBytes = byteArrayOf(1)
      val items = this.size / RESERVATION_SIZE
      val skipCount = idx * RESERVATION_SIZE
      val before = range(this, 0, skipCount + TIMESTAMP_SIZE + VALUE_SIZE + SCRIPT_HASH_SIZE)
      val restIdx = skipCount
      val after = range(restIdx, this.size - restIdx)
      val newList = before
            .concat(trueBytes)
            .concat(after)
      return newList
   }

   private fun ReservationList.res_getTotalOnHoldGasValue(nowTime: Int): Long {
      // todo: clean up expired reservation entries
      if (this.isEmpty())
         return 0
      val count = this.size / RESERVATION_SIZE
      var i = 0
      var total: Long = 0
      while (i < count) {
         val reservation = this.res_getAt(i)
         if (!reservation.res_wasPaidToRecipient()) {
            val expiryBytes = take(reservation, TIMESTAMP_SIZE)
            val expiry = BigInteger(expiryBytes)
            if (expiry.toInt() > nowTime) {
               val valueBytes = range(reservation, TIMESTAMP_SIZE, VALUE_SIZE)
               val value = BigInteger(valueBytes)
               total += value.toLong()
            }
         }
         i++
      }
      return total
   }

   private fun Reservation.res_getExpiry(): BigInteger {
      val expiryBytes = this.take(TIMESTAMP_SIZE)
      return BigInteger(expiryBytes)
   }

   private fun Reservation.res_getValue(): BigInteger {
      val valueBytes = this.range(TIMESTAMP_SIZE, VALUE_SIZE)
      return BigInteger(valueBytes)
   }

   private fun Reservation.res_getRecipient(): ScriptHash {
      val scriptHash = this.range(TIMESTAMP_SIZE + VALUE_SIZE, SCRIPT_HASH_SIZE)
      return scriptHash
   }

   private fun Reservation.res_isMultiSigUnlocked(): Boolean {
      val trueBytes = byteArrayOf(1)
      val multiSigUnlocked = this.range(TIMESTAMP_SIZE + VALUE_SIZE + SCRIPT_HASH_SIZE, 1)
      return multiSigUnlocked!! == trueBytes
   }

   private fun Reservation.res_wasPaidToRecipient(): Boolean {
      val stored = Storage.get(Storage.currentContext(), this)
      if (stored.size == TX_HASH_SIZE)
         return true
      return false
   }

   private fun ScriptHash.account_reserveFunds(expiry: BigInteger, value: BigInteger, recipient: ScriptHash): Boolean {
      val balance = this.wallet_getBalance()
      val toReserve = value.toLong()
      if (balance <= toReserve) {  // insufficient balance
         Runtime.notify("CL:ERR:InsufficientFunds1")
         return false
      }
      val reservations = Storage.get(Storage.currentContext(), this)
      val nowTime = Blockchain.getHeader(Blockchain.height()).timestamp()
      val gasOnHold = reservations.res_getTotalOnHoldGasValue(nowTime)
      if (gasOnHold < 0)  // wallet validation failed
         return false
      val effectiveBalance = balance - gasOnHold
      if (effectiveBalance < toReserve) {
         Runtime.notify("CL:ERR:InsufficientFunds2")
         return false
      }
      val reservation = reservation_create(expiry, value, recipient)
      val newReservations = reservations.concat(reservation)
      reservation.account_storeReservations(newReservations)
      Runtime.notify("CL:OK:ReservedFunds", reservation)
      return true
   }

   private fun ScriptHash.account_reserveFunds(expiry: BigInteger, value: BigInteger): Boolean {
      // argh, the compiler strikes again
      val emptyScriptHash = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
      val ret = this.account_reserveFunds(expiry, value, emptyScriptHash)
      return ret
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
                             ownerScriptHash: ScriptHash, infoBlob: ByteArray): Demand {
      Runtime.notify("CL:DBG:CreatingDemand")
      // checking individual arg lengths doesn't seem to work here
      // I tried a lot of things, grr compiler
      val nil = byteArrayOf()
      if (itemValue.toLong() > MAX_GAS_TX_VALUE)
         return nil
      // size: 160 bytes
      val expectedSize = DEMAND_SIZE
      val demand = expiry.toByteArray(TIMESTAMP_SIZE)
            .concat(itemValue.toByteArray(VALUE_SIZE))
            .concat(ownerScriptHash)
            .concat(repRequired.toByteArray(REP_REQUIRED_SIZE))
            .concat(itemSize.toByteArray(CARRY_SPACE_SIZE))
            .concat(infoBlob)
      if (demand.size != expectedSize)
         return nil
      Runtime.notify("CL:OK:DemandCreated")
      return demand
   }

   private fun Demand.demand_store(owner: ScriptHash, pickUpCityHash: Hash160, dropOffCityHash: Hash160) {
      Runtime.notify("CL:DBG:Demand.store")
      if (owner.account_isInNullState()) {
         Runtime.notify("CL:DBG:StoringDemand")

         // store the demand object
         val cityHashPair = pickUpCityHash.concat(dropOffCityHash)
         val cityHashPairKeyD = cityHashPair.demand_getStorageKey()
         val demandsForCity = Storage.get(Storage.currentContext(), cityHashPairKeyD)
         val newDemandsForCity = demandsForCity.concat(this)
         Storage.put(Storage.currentContext(), cityHashPairKeyD, newDemandsForCity)
         Runtime.notify("CL:OK:StoredDemand", cityHashPair)

         // find a travel object to match this demand with
         val cityHashPairKeyT = cityHashPair.travel_getStorageKey()
         val travelsForCityPair = Storage.get(Storage.currentContext(), cityHashPairKeyT)

         // no travels available! no match can be made yet
         if (travelsForCityPair.isEmpty()) {
            Runtime.notify("CL:DBG:NoMatchableTravelForDemand:1")
            // reserve the item's value and fee (no match yet, reserve for later)
            this.demand_reserveValueAndFee(owner)
            Runtime.notify("CL:OK:ReservedDemandValueAndFee:1")
         }

         // random compiler errors made the below messy and split this if. it's not my fault :)
         // we have matches! walk through the travels, find one that is appropriate to match
         if (!travelsForCityPair.isEmpty()) {
            val matchKey = this.demand_getMatchKey()
            val repRequired = this.demand_getRepRequired()
            val carrySpaceRequired = this.demand_getItemSize()
            val nowTime = Blockchain.getHeader(Blockchain.height()).timestamp()
            val matchedTravel = travelsForCityPair.travels_findMatchableTravel(repRequired, carrySpaceRequired, nowTime)
            if (matchedTravel.isEmpty()) {  // no match
               Runtime.notify("CL:DBG:NoMatchableTravelForDemand:2")
               // reserve the item's value and fee (no match yet, reserve for later)
               this.demand_reserveValueAndFee(owner)
               Runtime.notify("CL:OK:ReservedDemandValueAndFee:2")
            } else {
               // match demand -> travel
               val nowTimeBigInt = BigInteger.valueOf(nowTime as Long)
               val nowTimeBytes = nowTimeBigInt.toByteArray(TIMESTAMP_SIZE)
               val timestampedMatch = matchedTravel.concat(nowTimeBytes)
               Storage.put(Storage.currentContext(), matchKey, timestampedMatch)

               // match travel -> demand
               val otherMatchKey = matchedTravel.travel_getMatchKey()
               val timestampedOtherMatch = this.concat(nowTimeBytes)
               Storage.put(Storage.currentContext(), otherMatchKey, timestampedOtherMatch)

               Runtime.notify("CL:OK:MatchedDemandWithTravel")

               // clear existing reserved funds
               // (this is ok to do as an account may only have one demand or travel active at any one time)
               Storage.delete(Storage.currentContext(), owner)

               // reserve the item's value and fee
               // since we found a matchable travel we can set the recipient script hash in the reservation
               this.demand_reserveValueAndFee(owner, matchedTravel)

               Runtime.notify("CL:OK:ReservedDemandValueAndFee:3")
            }
         }

         Runtime.notify("CL:RET:Demand.store")
      }
   }

   private fun DemandList.demands_getAt(index: Int): Travel {
      return this.range(index * DEMAND_SIZE, DEMAND_SIZE)
   }

   private fun Demand.demand_reserveValueAndFee(owner: ScriptHash, matchedTravel: Travel) {
      val expiry = this.demand_getExpiry()
      val itemValue = this.demand_getItemValue().toLong()
      val toReserve = itemValue + FEE_DEMAND_REWARD
      val travellerScriptHash = matchedTravel.travel_getOwnerScriptHash()
      owner.account_reserveFunds(expiry, BigInteger.valueOf(toReserve), travellerScriptHash)
   }

   private fun Demand.demand_reserveValueAndFee(owner: ScriptHash) {
      val expiry = this.demand_getExpiry()
      val itemValue = this.demand_getItemValue().toLong()
      val toReserve = itemValue + FEE_DEMAND_REWARD
      owner.account_reserveFunds(expiry, BigInteger.valueOf(toReserve))
   }

   private fun Demand.demand_getExpiry(): BigInteger {
      val bytes = this.take(TIMESTAMP_SIZE)
      return BigInteger(bytes)
   }

   private fun Demand.demand_getItemValue(): BigInteger {
      val bytes = this.range(TIMESTAMP_SIZE, VALUE_SIZE)
      return BigInteger(bytes)
   }

   private fun Demand.demand_getOwnerScriptHash(): ScriptHash {
      val bytes = this.range(TIMESTAMP_SIZE + VALUE_SIZE, SCRIPT_HASH_SIZE)
      return bytes
   }

   private fun Demand.demand_getRepRequired(): BigInteger {
      val bytes = this.range(TIMESTAMP_SIZE + VALUE_SIZE + SCRIPT_HASH_SIZE, REP_REQUIRED_SIZE)
      return BigInteger(bytes)
   }

   private fun Demand.demand_getItemSize(): BigInteger {
      val bytes = this.range(TIMESTAMP_SIZE + VALUE_SIZE + SCRIPT_HASH_SIZE + REP_REQUIRED_SIZE, CARRY_SPACE_SIZE)
      return BigInteger(bytes)
   }

   private fun Demand.demand_getInfoBlob(): ByteArray {
      return this.range(TIMESTAMP_SIZE + VALUE_SIZE + SCRIPT_HASH_SIZE + REP_REQUIRED_SIZE + CARRY_SPACE_SIZE, DEMAND_INFO_SIZE)
   }

   private fun Demand.demand_getMatchedAtTime(): BigInteger {
      val bytes = this.range(TIMESTAMP_SIZE + VALUE_SIZE + SCRIPT_HASH_SIZE + REP_REQUIRED_SIZE + CARRY_SPACE_SIZE + DEMAND_INFO_SIZE, TIMESTAMP_SIZE)
      return BigInteger(bytes)
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
               ! demand.demand_isMatched())
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
   //   - departure (expiry) time
   //   - minimum reputation requirement
   //   - carry space available
   //   - the traveller's wallet script hash (for receiving funds held in the demand)
   private fun travel_create(expiry: BigInteger, repRequired: BigInteger, carrySpace: BigInteger, owner: ScriptHash): Travel {
      Runtime.notify("CL:DBG:CreatingTravel")
      val nil = byteArrayOf()
      // size: 27 bytes
      val travel = expiry.toByteArray(TIMESTAMP_SIZE)
            .concat(repRequired.toByteArray(REP_REQUIRED_SIZE))
            .concat(carrySpace.toByteArray(CARRY_SPACE_SIZE))
            .concat(owner)
      if (travel.size != TRAVEL_SIZE)
         return nil
      Runtime.notify("CL:OK:TravelCreated")
      return travel
   }

   private fun Travel.travel_store(owner: ScriptHash, pickUpCityHash: Hash160, dropOffCityHash: Hash160) {
      Runtime.notify("CL:DBG:Travel.store")
      if (owner.account_isInNullState()) {
         Runtime.notify("CL:DBG:StoringTravel")

         // store the travel object
         val cityHashPair = pickUpCityHash.concat(dropOffCityHash)
         val cityHashPairKey = cityHashPair.travel_getStorageKey()
         val travelsForCityPair = Storage.get(Storage.currentContext(), cityHashPairKey)
         val newTravelsForCityPair = travelsForCityPair.concat(this)
         Storage.put(Storage.currentContext(), cityHashPairKey, newTravelsForCityPair)

         Runtime.notify("CL:OK:StoredTravel", cityHashPair)

         // clear existing reserved funds
         // (this is ok to do as an account may only have one demand or travel active at any one time)
         Storage.delete(Storage.currentContext(), owner)

         // reserve the security deposit
         // it's here because the compiler doesn't like it being below the following block
         this.travel_reserveDeposit(owner)

         Runtime.notify("CL:OK:ReservedTravelDeposit", cityHashPair)

         // find a demand object to match this travel with
         val cityHashPairKeyD = cityHashPair.demand_getStorageKey()
         val demandsForCityPair = Storage.get(Storage.currentContext(), cityHashPairKeyD)
         if (!demandsForCityPair.isEmpty()) {
            val matchKey = this.travel_getMatchKey()
            val repRequired = this.travel_getRepRequired()
            val carrySpaceAvailable = this.travel_getCarrySpace()
            val nowTime = Blockchain.getHeader(Blockchain.height()).timestamp()
            val matchedDemand = demandsForCityPair.demands_findMatchableDemand(repRequired, carrySpaceAvailable, nowTime)
            if (!matchedDemand.isEmpty()) {
               // match travel -> demand
               val nowTimeBigInt = BigInteger.valueOf(nowTime as Long)
               val nowTimeBytes = nowTimeBigInt.toByteArray(TIMESTAMP_SIZE)
               val timestampedMatch = nowTimeBytes.concat(matchedDemand)
               Storage.put(Storage.currentContext(), matchKey, timestampedMatch)

               // match demand -> travel
               val otherMatchKey = matchedDemand.demand_getMatchKey()
               val timestampedOtherMatch = this.concat(nowTimeBytes)
               Storage.put(Storage.currentContext(), otherMatchKey, timestampedOtherMatch)

               Runtime.notify("CL:OK:MatchedTravelWithDemand")

               // rewrite the reservation that was created with the matched demand
               // this means that we inject the traveller's script hash as the "recipient" of the reserved funds
               // when a transaction to withdraw the funds is received (multi-sig) the destination wallet must match this one
               val demandOwner = matchedDemand.demand_getOwnerScriptHash()
               val demandValue = matchedDemand.demand_getItemValue()
               val demandExpiry = matchedDemand.demand_getExpiry()
               val ownerReservationList = demandOwner.account_getReservations()
               val reservationAtIdx = ownerReservationList.res_findBy(demandValue)
               if (reservationAtIdx > 0) {
                  val rewrittenReservationList = ownerReservationList.res_replaceRecipientAt(reservationAtIdx, owner)
                  demandOwner.account_storeReservations(rewrittenReservationList)
                  Runtime.notify("CL:OK:RewroteDemandFundsReservation")
               } else {
                  Runtime.notify("CL:ERR:ReservationForDemandNotFound")
               }
            } else {
               Runtime.notify("CL:DBG:NoMatchableDemandForTravel:1")
            }
         } else {
            Runtime.notify("CL:DBG:NoMatchableDemandForTravel:2")
         }

         Runtime.notify("CL:RET:Travel.store")
      }
   }

   private fun TravelList.travels_getAt(index: Int): Travel {
      return this.range(index * TRAVEL_SIZE, TRAVEL_SIZE)
   }

   private fun Travel.travel_reserveDeposit(owner: ScriptHash) {
      val expiry = this.travel_getExpiry()
      owner.account_reserveFunds(expiry, BigInteger.valueOf(FEE_TRAVEL_DEPOSIT))
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

   private fun Travel.travel_getOwnerScriptHash(): ScriptHash {
      val bytes = this.range(TIMESTAMP_SIZE + REP_REQUIRED_SIZE + CARRY_SPACE_SIZE, SCRIPT_HASH_SIZE)
      return bytes
   }

   private fun Travel.travel_getMatchedAtTime(): BigInteger {
      val bytes = this.range(TIMESTAMP_SIZE + REP_REQUIRED_SIZE + CARRY_SPACE_SIZE + SCRIPT_HASH_SIZE, TIMESTAMP_SIZE)
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
         val reservation = reservations.res_getAt(i)
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
