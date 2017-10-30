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
   private const val REP_REQUIRED_SIZE = 2
   private const val CARRY_SPACE_SIZE = 1
   private const val DEMAND_INFO_SIZE = 128

   // Object sizes
   private const val RESERVATION_SIZE =
         TIMESTAMP_SIZE + VALUE_SIZE + SCRIPT_HASH_SIZE
   private const val DEMAND_SIZE =
         TIMESTAMP_SIZE + VALUE_SIZE + SCRIPT_HASH_SIZE + REP_REQUIRED_SIZE + CARRY_SPACE_SIZE + DEMAND_INFO_SIZE
   private const val TRAVEL_SIZE =
         TIMESTAMP_SIZE + REP_REQUIRED_SIZE + CARRY_SPACE_SIZE + SCRIPT_HASH_SIZE

   // Maximum values
   // the maximum value of a transaction is fixed at ~5497 GAS so that we can fit it into 5 bytes.
   private const val MAX_GAS_TX_VALUE = 549750000000 // approx. (2^40)/2 = 5497.5 GAS
   private const val MAX_INT: Long = 0x7FFFFFFF

   // Fees
   private const val FEE_DEMAND_REWARD: Long = 300000000  // 3 GAS
   private const val FEE_TRAVEL_DEPOSIT: Long = 100000000  // 1 GAS

   // Storage keys
   private const val STORAGE_KEY_SUFFIX_DEMAND: Byte = 1
   private const val STORAGE_KEY_SUFFIX_TRAVEL: Byte = 2
   private const val STORAGE_KEY_STATS_DEMANDS = "demandsCounter"
   private const val STORAGE_KEY_STATS_CITIES = "citiesCounter"
   private const val STORAGE_KEY_STATS_FUNDS = "reservedFundsCounter"

   /**
    * The entry point of the smart contract.
    *
    * @param operation The method to run, specified as a string.
    * @param args A variable length array of arguments provided to the method.
    */
   fun Main(operation: String, vararg args: ByteArray) : Any {
      // The entry points for each of the supported operations follow

      // Initialization
      if (operation == "initialize")
         return init(args[0], args[1], args[2])
      if (operation == "is_initialized")
         return isInitialized()

      //region Test operations
      if (TESTS_ENABLED) {
         if (operation == "test_reservation_create")
            return reservation_create(BigInteger(args[0]), BigInteger(args[1]), args[2])
         if (operation == "test_demand_create")
            return demand_create(args[0], BigInteger(args[1]), BigInteger(args[2]), BigInteger(args[3]), BigInteger(args[4]), args[5])
         if (operation == "test_demand_getItemValue")
            return args[0].demand_getItemValue()
         if (operation == "test_demand_getInfoBlob")
            return args[0].demand_getInfoBlob()
         if (operation == "test_demand_findMatchableDemand")
            return args[0].demands_findMatchableDemand(BigInteger(args[1]), BigInteger(args[2]), (args[3] as Int?)!!, true)
         if (operation == "test_travel_create")
            return travel_create(args[0], BigInteger(args[1]), BigInteger(args[2]), BigInteger(args[3]))
         if (operation == "test_travel_getCarrySpace")
            return args[0].travel_getCarrySpace()
         if (operation == "test_travel_getOwnerScriptHash")
            return args[0].travel_getOwnerScriptHash()
         if (operation == "test_travel_findMatchableTravel")
            return args[0].travel_findMatchableTravel(BigInteger(args[1]), BigInteger(args[2]), (args[3] as Int?)!!, true)
         if (operation == "test_reservation_getExpiry")
            return args[0].res_getExpiry()
         if (operation == "test_reservation_getValue")
            return args[0].res_getValue()
         if (operation == "test_reservation_getRecipient")
            return args[0].res_getRecipient()
         if (operation == "test_reservation_getTotalOnHoldValue")
            return args[0].res_getTotalOnHoldGasValue(1, true)
         if (operation == "test_reservation_findBy")
            return args[0].res_findBy(BigInteger(args[1]), args[2])
         if (operation == "test_reservation_replaceRecipientAt")
            return args[0].res_replaceRecipientAt((args[1] as Int?)!!, args[2])
      }
      //endregion

      // Stats query operations
      if (operation == "stats_getDemandsCount")
         return stats_getDemandsCount()
      if (operation == "stats_getCityUsageCount")
         return stats_getCityUsageCount()
      if (operation == "stats_getReservedFundsCount")
         return stats_getReservedFundsCount()
      if (operation == "storage_get")
         return Storage.get(Storage.currentContext(), args[0])

      // Can't call IsInitialized() from here 'cause the compiler don't like it
      if (Storage.get(Storage.currentContext(), "Initialized").isEmpty()) {
         Runtime.notify("CL:ERR:HubNotInitialized")
         return false
      }

      // Wallet query operations
      if (operation == "wallet_validate")
         return args[0].wallet_validate(args[1])
      if (operation == "wallet_getReputationScore")
         return args[0].wallet_getReputationScore()
      if (operation == "wallet_getGasBalance")
         return args[0].wallet_getGasBalance()
       if (operation == "wallet_getReservedGasBalance") {
          val reservations = Storage.get(Storage.currentContext(), args[0])
          if (reservations.isEmpty())
             return 0
          val nowTime = Blockchain.getHeader(Blockchain.height()).timestamp()
          val gasOnHold = reservations.res_getTotalOnHoldGasValue(nowTime)
          return gasOnHold
       }
      if (operation == "wallet_getFundReservations")
         return args[0].wallet_getFundReservations()
      if (operation == "wallet_requestTxOut") {
         if (args[0].wallet_validate(args[1])) {
            val reservations = args[0].wallet_getFundReservations()
            return args[0].wallet_requestTxOut(BigInteger(args[3]), reservations)
         }
         Runtime.notify("CL:ERR:InvalidWallet")
         return false
      }
      if (operation == "wallet_setReservationPaidToRecipientTxHash") {
         if (args[0].wallet_validate(args[1])) {
            val check = args[0].wallet_setReservationPaidToRecipientTxHash(args[2], BigInteger(args[3]), args[4])
            if (check) {
               // reset account states, allow new transactions
               Storage.delete(Storage.currentContext(), args[0])  // reset demand owner state
               Storage.delete(Storage.currentContext(), args[2])  // reset traveller/recipient state
               args[0].wallet_clearFundReservations()
               args[2].wallet_clearFundReservations()

               // increment account reputation scores
               args[0].wallet_incrementReputationScore()
               args[2].wallet_incrementReputationScore()
            }
         }
         return false
      }

      // State query operations
      if (operation == "wallet_getState") {
         val stateKey = args[0].wallet_getStateStorageKey()
         return Storage.get(Storage.currentContext(), stateKey)
      }
      if (operation == "demand_isMatched")
         return args[0].demand_isMatched()
      if (operation == "demand_getMatchKey")
         return args[0].demand_getMatchKey()
      if (operation == "demand_getTravelMatch") {
         val matchKey = args[0].demand_getMatchKey()
         return Storage.get(Storage.currentContext(), matchKey)
      }
      if (operation == "demand_getTravelMatchedAtTime") {
         val matchKey = args[0].demand_getMatchKey()
         val travel = Storage.get(Storage.currentContext(), matchKey)
         return travel.travel_getMatchedAtTime()
      }
      if (operation == "travel_isMatched")
         return args[0].travel_isMatched()
      if (operation == "travel_getMatchKey")
         return args[0].travel_getMatchKey()
      if (operation == "travel_getDemandMatch") {
         val matchKey = args[0].travel_getMatchKey()
         return Storage.get(Storage.currentContext(), matchKey)
      }
      if (operation == "travel_getDemandMatchedAtTime") {
         val matchKey = args[0].travel_getMatchKey()
         val demand = Storage.get(Storage.currentContext(), matchKey)
         return demand.demand_getMatchedAtTime()
      }

      // The following operations can write state
      if (! Runtime.checkWitness(args[0]))
         return false
      Runtime.notify("CL:OK:checkWitness")

      // Open and try to match a Demand
      if (operation == "demand_open") {
         if (args[0].wallet_validate(args[1]) &&
               args[0].wallet_canOpenDemandOrTravel()) {
            val demand = demand_create(args[0], BigInteger(args[2]), BigInteger(args[3]), BigInteger(args[4]), BigInteger(args[5]), args[6])
            return demand.demand_storeAndMatch(args[0], args[7], args[8])
         }
         return false
      }
      // Open and try to match a Travel
      if (operation == "travel_open") {
         if (args[0].wallet_validate(args[1]) &&
               args[0].wallet_canOpenDemandOrTravel()) {
            val travel = travel_create(args[0], BigInteger(args[2]), BigInteger(args[3]), BigInteger(args[4]))
            return travel.travel_storeAndMatch(args[0], args[5], args[6])
         }
         return false
      }

      return false
   }

   // -====================-
   // -=  Initialization  =-
   // -====================-
   //region initialization

   /**
    * Checks whether the contract has been initialized.
    *
    * @return true if the contract has been initialized
    */
   private fun isInitialized() = ! Storage.get(Storage.currentContext(), "Initialized").isEmpty()

   /**
    * Initializes the smart contract. This takes three parts of the wallet script as arguments.
    * This is then used to verify the integrity of user wallets as they are used in the system.
    *
    * @param walletScriptP1 part 1 of the wallet script code (code before the public key)
    * @param walletScriptP2 part 2 of the wallet script code (code after the public key, before the script hash)
    * @param walletScriptP3 part 3 of the wallet script code (code after the script hash)
    */
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

   //endregion

   // -=============-
   // -=  Wallets  =-
   // -=============-
   //region wallets

   /**
    * Validates an individual user wallet.
    *
    * @param pubKey the script hash of the user wallet to validate
    */
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

   /**
    * Gets the GAS balance of a user wallet.
    *
    * @return the GAS balance
    */
   private fun ScriptHash.wallet_getGasBalance(): Long {
      val account = Blockchain.getAccount(this)
      Runtime.notify("CL:OK:FoundWallet", account.scriptHash())
      return account.getBalance(getAssetId())
   }

   /**
    * Requests permission to perform a withdrawal from a previously validated user wallet.
    * Note: Please ensure that the wallet has been validated before this is called.
    *
    * @param value the outgoing value of the transaction
    * @param reservations the list of [reserved funds objects][Reservation] for the wallet
    * @return true if the transaction was cleared
    */
   private fun ScriptHash.wallet_requestTxOut(value: BigInteger, reservations: ReservationList): Boolean {
      Runtime.notify("CL:DBG:requestTxOut")
      // check if balance is enough after reserved funds are considered
      val balance = this.wallet_getGasBalance()
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

   /**
    * Reserves funds in a previously validated user wallet.
    *
    * Note: Please ensure that the wallet has been validated before this is called.
    *
    * @param expiry timestamp of when the reserved funds get released automatically
    * @param value the amount of GAS to hold in the reservation
    * @param recipient the recipient wallet that the funds are reserved for
    * @param overwrite true if existing reserved funds should be overwritten for this wallet
    * @return true on success
    */
   private fun ScriptHash.wallet_reserveFunds(expiry: BigInteger, value: BigInteger, recipient: ScriptHash,
                                              overwrite: Boolean): Boolean {
      val balance = this.wallet_getGasBalance()
      val toReserve = value.toLong()
      if (balance <= toReserve) {  // insufficient balance
         Runtime.notify("CL:ERR:InsufficientFunds1")
         return false
      }
      var reservations = byteArrayOf()
      if (! overwrite) {
         val reservations = Storage.get(Storage.currentContext(), this)
      }
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
      reservation.wallet_storeFundReservations(newReservations)
      Runtime.notify("CL:OK:ReservedFunds", reservation)

      // add to accumulating stats counter
      stats_recordReservedFunds(value)
      Runtime.notify("CL:DBG:UpdatedReservedFundsStats", value)

      return true
   }

   /**
    * Functionally equivalent to [wallet_reserveFunds] except that this should be used when the recipient is unknown.
    *
    * @see wallet_reserveFunds
    * @param expiry timestamp of when the reserved funds get released automatically
    * @param value the amount of GAS to withhold in the reservation
    * @param overwrite true if existing reserved funds should be overwritten for this wallet
    * @return true on success
    *
    */
   private fun ScriptHash.wallet_reserveFunds(expiry: BigInteger, value: BigInteger, overwrite: Boolean): Boolean {
      // argh, the compiler strikes again
      val emptyScriptHash = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
      return this.wallet_reserveFunds(expiry, value, emptyScriptHash, overwrite)
   }

   /**
    * Sets the transaction hash of funds that have been transferred to the recipient of [reserved funds][ReservationList].
    *
    * Because we can't access storage in a verify script, we have to use an invoke to set the tx hash.
    * This function finds the given transaction and makes sure the supplied value went to the recipient.
    * If the transaction or reserved funds cannot be found this method will fail and return false.
    *
    * @param recipient the recipient of the reserved funds
    * @param value the GAS value of the reserved funds
    * @param txHash the transaction hash
    * @return true on success
    */
   private fun ScriptHash.wallet_setReservationPaidToRecipientTxHash(recipient: ScriptHash, value: BigInteger,
                                                                     txHash: Hash256): Boolean {
      Runtime.notify("CL:DBG:setReservationPaidToRecipientTxHash")
      val reservations = this.wallet_getFundReservations()
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

   /**
    * Indicates whether a wallet is in a state that allows it to open a demand or travel.
    *
    * @param nowTime the current unix timestamp in seconds
    * @return true on success
    */
   private fun ScriptHash.wallet_canOpenDemandOrTravel(nowTime: Int = Blockchain.getHeader(Blockchain.height()).timestamp()): Boolean {
      Runtime.notify("CL:DBG:canOpenDemandOrTravel")
      val stateObject = Storage.get(Storage.currentContext(), this)
      if (stateObject.isEmpty())
         return true
      val expiryBytes = take(stateObject, TIMESTAMP_SIZE)
      val expiry = BigInteger(expiryBytes)
      if (nowTime > expiry.toInt())
         return false
      return true
   }

   /**
    * Gets the list of [reserved funds][ReservationList] for the given wallet.
    *
    * @return the serialized list of fund reservations
    */
   private fun ScriptHash.wallet_getFundReservations(): ReservationList {
      val key = this.wallet_getFundReservationsStorageKey()
      val reservations = Storage.get(Storage.currentContext(), key)
      return reservations
   }

   /**
    * Stores [reserved funds][ReservationList] for the given wallet.
    *
    * @param resList the [ReservationList] to store
    */
   private fun ScriptHash.wallet_storeFundReservations(resList: ReservationList) {
      val key = this.wallet_getFundReservationsStorageKey()
      Storage.put(Storage.currentContext(), key, resList)
   }

   /**
    * Deletes a wallet's [reserved funds][Reservation] from storage.
    */
   private fun ScriptHash.wallet_clearFundReservations() {
      val key = this.wallet_getFundReservationsStorageKey()
      Storage.delete(Storage.currentContext(), key)
   }

   /**
    * Gets the storage key used to store/lookup [reserved funds][ReservationList] for a wallet.
    */
   private fun ScriptHash.wallet_getFundReservationsStorageKey(): ByteArray {
      val suffix = byteArrayOf(0)
      val combined = this.concat(suffix)
      return combined
   }

   /**
    * Gets the reputation score for a user wallet.
    *
    * @return the user's reputation score (number of complete transactions)
    */
   private fun ScriptHash.wallet_getReputationScore(): BigInteger {
      val key = this.wallet_getReputationStorageKey()
      val storedBytes = Storage.get(Storage.currentContext(), key)
      return BigInteger(storedBytes)
   }

   /**
    * Stores a reputation score for a user wallet.
    *
    * @param rep the reputation score to store
    */
   private fun ScriptHash.wallet_storeReputationScore(rep: BigInteger) {
      val key = this.wallet_getReputationStorageKey()
      val bytes = rep.toByteArray()
      Storage.put(Storage.currentContext(), key, bytes)
   }

   /**
    * Increments the reputation score for the user wallet by 1.
    */
   private fun ScriptHash.wallet_incrementReputationScore() {
      val rep = this.wallet_getReputationScore()
      val newRep = rep + BigInteger.valueOf(1)
      this.wallet_storeReputationScore(newRep)
   }

   /**
    * Gets the storage key used to store/lookup user reputation for a wallet.
    */
   private fun ScriptHash.wallet_getReputationStorageKey(): ByteArray {
      val suffix = byteArrayOf(1)
      val combined = this.concat(suffix)
      return combined
   }

   /**
    * Gets the storage key used to store/lookup state information for a wallet.
    */
   private fun ScriptHash.wallet_getStateStorageKey() = this

   //endregion

   // -==================-
   // -=  Reservations  =-
   // -==================-
   //region reservations

   /**
    * Creates a [reserved funds object][Reservation].
    *
    * @param expiry timestamp of when the reserved funds get released automatically
    * @param value the amount of GAS to hold in the reservation
    * @param recipient the recipient wallet that the funds are reserved for
    * @return the [reserved funds object][Reservation]
    */
   private fun reservation_create(expiry: BigInteger, value: BigInteger, recipient: ScriptHash): Reservation {
      // size: 30 bytes
      val reservation = expiry.toByteArray(TIMESTAMP_SIZE)
            .concat(value.toByteArray(VALUE_SIZE))
            .concat(recipient)  // script hash, 20 bytes
      return reservation
   }

   /**
    * Gets the [reserved funds object][Reservation] at the given [index] in a [ReservationList].
    *
    * @param index the zero-based index of the [Reservation] to retrieve
    * @return the [Reservation], or an empty array if not found
    */
   private fun ReservationList.res_getAt(index: Int): Reservation = this.range(index * RESERVATION_SIZE, RESERVATION_SIZE)

   /**
    * Finds the [reserved funds object][Reservation] with the given [value] in a [ReservationList].
    *
    * @param value the value to match
    * @return the index at which the [Reservation] is at in the [ReservationList]
    */
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

   /**
    * Finds the [reserved funds object][Reservation] with the given [value] and [recipient] in a [ReservationList].
    *
    * @param value the value to match
    * @param recipient the recipient to match (AND)
    * @return the index at which the [Reservation] is at in the [ReservationList]
    */
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

   /**
    * Finds the [reserved funds object][Reservation] at the given [idx] and replaces its [recipient] with the one given.
    *
    * @param index the zero-based index of the [Reservation] to replace the [recipient] in
    * @param recipient the [recipient] to replace the existing one with in the [Reservation]
    * @return the new [ReservationList] containing the replaced entry
    */
   private fun ReservationList.res_replaceRecipientAt(idx: Int, recipient: ScriptHash): ReservationList {
      val items = this.size / RESERVATION_SIZE
      val skipCount = idx * RESERVATION_SIZE
      val before = range(this, 0, skipCount + TIMESTAMP_SIZE + VALUE_SIZE)
      val restIdx = skipCount + SCRIPT_HASH_SIZE
      val after = range(restIdx, (items - idx - 1) * RESERVATION_SIZE)
      val newList = before
            .concat(recipient)
            .concat(after)
      return newList
   }

   /**
    * Calculates the total value of GAS that a [list of reserved funds][ReservationList] is holding, considering expiry
    * times and whether funds were already paid out to the intended recipient.
    *
    * @param nowTime the current unix timestamp in seconds
    * @param assumeUnpaid skips a call to storage, used in testing
    * @return the total value of GAS that is reserved
    */
   private fun ReservationList.res_getTotalOnHoldGasValue(nowTime: Int, assumeUnpaid: Boolean = false): Long {
      // todo: clean up expired reservation entries
      if (this.isEmpty())
         return 0
      val count = this.size / RESERVATION_SIZE
      var i = 0
      var total: Long = 0
      while (i < count) {
         val reservation = this.res_getAt(i)
         if (assumeUnpaid || ! reservation.res_wasPaidToRecipient()) {
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

   /**
    * Gets the expiry timestamp for an individual [Reservation].
    *
    * @return the unix timestamp in seconds
    */
   private fun Reservation.res_getExpiry(): BigInteger {
      val expiryBytes = this.take(TIMESTAMP_SIZE)
      return BigInteger(expiryBytes)
   }

   /**
    * Gets the GAS value of an individual [Reservation].
    *
    * @return the GAS value of reserved funds
    */
   private fun Reservation.res_getValue(): BigInteger {
      val valueBytes = this.range(TIMESTAMP_SIZE, VALUE_SIZE)
      return BigInteger(valueBytes)
   }

   /**
    * Gets the intended recipient of reserved funds.
    *
    * @return the script hash of the recipient
    */
   private fun Reservation.res_getRecipient(): ScriptHash {
      val scriptHash = this.range(TIMESTAMP_SIZE + VALUE_SIZE, SCRIPT_HASH_SIZE)
      return scriptHash
   }

   /**
    * Determines whether the reserved funds were paid out to the recipient.
    *
    * @return true if the funds were paid to the recipient
    */
   private fun Reservation.res_wasPaidToRecipient(): Boolean {
      val stored = Storage.get(Storage.currentContext(), this)
      if (stored.size == TX_HASH_SIZE)
         return true
      return false
   }

   //endregion

   // -=============-
   // -=  Demands  =-
   // -=============-
   //region demands

   /**
    * Creates a new [Demand].
    *
    * Demand details:
    *   - pickup and destination cities (hashed) (kept in storage key)
    *   - product/contact info
    *   - expiry (timestamp)
    *   - minimum reputation requirement
    *   - carry space required (xs, sm, md, lg, xl) (1, 2, 3, 4, 5)
    *
    * @param owner the owner of the demand
    * @param expiry the time that the demand expires as a unix timestamp
    * @param repRequired the reputation required of a traveller in order for this demand to be matched
    * @param itemSize the size of the item (on a scale of 1-5)
    * @param itemValue the value of the item in GAS
    * @param infoBlob a 128 character string describing the desired item and the demand owner's contact details
    */
   private fun demand_create(owner: ScriptHash, expiry: BigInteger, repRequired: BigInteger, itemSize: BigInteger,
                             itemValue: BigInteger, infoBlob: ByteArray): Demand {
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
            .concat(owner)
            .concat(repRequired.toByteArray(REP_REQUIRED_SIZE))
            .concat(itemSize.toByteArray(CARRY_SPACE_SIZE))
            .concat(infoBlob)
      if (demand.size != expectedSize)
         return nil
      Runtime.notify("CL:OK:DemandCreated")
      return demand
   }

   /**
    * Performs all the legwork necessary to store and match a [Demand] with a [Travel].
    *
    * @param owner the owner of the demand
    * @param pickUpCityHash the ripemd160 hashed form of the pick-up city
    * @param dropOffCityHash the ripemd160 hashed form of the drop-off city
    * @return true on success
    */
   private fun Demand.demand_storeAndMatch(owner: ScriptHash, pickUpCityHash: Hash160, dropOffCityHash: Hash160): Boolean {
      Runtime.notify("CL:DBG:Demand.store")

      // store the demand object (state lock)
      Storage.put(Storage.currentContext(), owner, this)

      // store the demand object (cities key)
      val cityHashPair = pickUpCityHash.concat(dropOffCityHash)
      val cityHashPairKeyD = cityHashPair.demand_getStorageKey()
      val demandsForCity = Storage.get(Storage.currentContext(), cityHashPairKeyD)
      val newDemandsForCity = demandsForCity.concat(this)
      Storage.put(Storage.currentContext(), cityHashPairKeyD, newDemandsForCity)
      Runtime.notify("CL:OK:StoredDemand", cityHashPair)

      // increment city usage counters (for stats)
      stats_recordCityUsage(pickUpCityHash)
      stats_recordCityUsage(dropOffCityHash)
      Runtime.notify("CL:DBG:UpdatedCityUsageStats", cityHashPair)

      stats_recordDemand()
      Runtime.notify("CL:DBG:UpdatedDemandStats", cityHashPair)

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
      if (! travelsForCityPair.isEmpty()) {
         val repRequired = this.demand_getRepRequired()
         val carrySpaceRequired = this.demand_getItemSize()

         val nowTime = Blockchain.getHeader(Blockchain.height()).timestamp()
         val matchedTravel = travelsForCityPair.travel_findMatchableTravel(repRequired, carrySpaceRequired, nowTime)
         if (matchedTravel.isEmpty()) {  // no match
            Runtime.notify("CL:DBG:NoMatchableTravelForDemand:2")

            // reserve the item's value and fee (no match yet, reserve for later)
            this.demand_reserveValueAndFee(owner)

            Runtime.notify("CL:OK:ReservedDemandValueAndFee:2")
         } else {
            // match demand -> travel
            val nowTimeBigInt = BigInteger.valueOf(nowTime as Long)
            val nowTimeBytes = nowTimeBigInt.toByteArray(TIMESTAMP_SIZE)
            val matchKey = this.demand_getMatchKey()
            val timestampedMatch = matchedTravel.concat(nowTimeBytes)
            Storage.put(Storage.currentContext(), matchKey, timestampedMatch)

            // match travel -> demand
            val otherMatchKey = matchedTravel.travel_getMatchKey()
            val timestampedOtherMatch = this.concat(nowTimeBytes)
            Storage.put(Storage.currentContext(), otherMatchKey, timestampedOtherMatch)

            Runtime.notify("CL:OK:MatchedDemandWithTravel")

            // reserve the item's value and fee
            // this will overwrite existing fund reservations for this wallet
            // since we found a matchable travel we can set the recipient's script hash in the reservation (matchedTravel)
            this.demand_reserveValueAndFee(owner, matchedTravel)

            // switch the traveller's existing deposit reservation to a non-expiring one
            // todo: Disabled for now, an expiring deposit is a solution to potential problems with unsatisfactory or impossible demands
            //       beyond the MVP we will be able to use a matching system to overcome the need for this
            // val traveller = matchedTravel.travel_getOwnerScriptHash()
            // matchedTravel.travel_reserveNonExpiringDeposit(traveller)

            Runtime.notify("CL:OK:ReservedDemandValueAndFee:3")
         }
      }

      Runtime.notify("CL:RET:Demand.store")
      return true
   }

   /**
    * Gets the [Demand] at the given [index] in a [DemandList].
    *
    * @param index the zero-based index of the [Demand] to retrieve
    * @return the [Demand], or an empty array if not found
    */
   private fun DemandList.demands_getAt(index: Int): Travel = this.range(index * DEMAND_SIZE, DEMAND_SIZE)

   /**
    * Reserves the item's value and reward fee for a new demand.
    *
    * @param owner the owner of the demand
    */
   private fun Demand.demand_reserveValueAndFee(owner: ScriptHash) {
      val expiry = this.demand_getExpiry()
      val toReserve = this.demand_getTotalValue().toLong()
      owner.wallet_reserveFunds(expiry, BigInteger.valueOf(toReserve), true)
   }

   /**
    * Reserves the item's value and reward fee for a new demand.
    * This form of the method is used when a [Travel] was found to be matched with the [Demand].
    *
    * @param owner the owner of the demand
    * @param matchedTravel the travel that was matched with the demand
    */
   private fun Demand.demand_reserveValueAndFee(owner: ScriptHash, matchedTravel: Travel) {
      val expiry = this.demand_getExpiry()
      val toReserve = this.demand_getTotalValue().toLong()
      val travellerScriptHash = matchedTravel.travel_getOwnerScriptHash()
      owner.wallet_reserveFunds(expiry, BigInteger.valueOf(toReserve), travellerScriptHash, true)
   }

   /**
    * Finds a [Demand] in a [DemandList] that fits the given attributes.
    *
    * @param repRequired the desired reputation of the traveller
    * @param carrySpaceAvailable the carry space available (scale of 1-5)
    * @param nowTime the current unix timestamp in seconds
    * @return the [Demand] object or an empty array if nothing found
    */
   private fun DemandList.demands_findMatchableDemand(repRequired: BigInteger, carrySpaceAvailable: BigInteger,
                                                      nowTime: Int = Blockchain.getHeader(Blockchain.height()).timestamp(),
                                                      assumeUnmatched: Boolean = false): Demand {
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
         var ownerRep = repRequired
         if (repRequired > BigInteger.valueOf(0)) {
            val owner = demand.demand_getOwnerScriptHash()
            ownerRep = owner.wallet_getReputationScore()
         }
         if (expiry.toInt() > nowTime &&
               ownerRep >= repRequired &&
               carrySpaceAvailable >= itemSize &&
               (assumeUnmatched || ! demand.demand_isMatched()))
            return demand
         i++
      }
      return nil
   }

   /**
    * Determines whether the given [Demand] has been matched with a [Travel] object.
    *
    * @return true if the [Demand] has been matched
    */
   private fun Demand.demand_isMatched(): Boolean {
      Runtime.notify("CL:DBG:Demand.isMatched")
      val matchKey = this.demand_getMatchKey()
      val match = Storage.get(Storage.currentContext(), matchKey)
      return !match.isEmpty()
   }

   /**
    * Gets the expiry timestamp of a [Demand].
    *
    * @return the expiry time as a unix timestamp
    */
   private fun Demand.demand_getExpiry(): BigInteger {
      val bytes = this.take(TIMESTAMP_SIZE)
      return BigInteger(bytes)
   }

   /**
    * Gets the desired item's value.
    *
    * @return the item's GAS value
    */
   private fun Demand.demand_getItemValue(): BigInteger {
      val bytes = this.range(TIMESTAMP_SIZE, VALUE_SIZE)
      return BigInteger(bytes)
   }

   /**
    * Gets the [Demand]'s total value (item value + reward fee).
    *
    * @return the demand's GAS value
    */
   private fun Demand.demand_getTotalValue(): BigInteger {
      val bytes = this.range(TIMESTAMP_SIZE, VALUE_SIZE)
      return BigInteger(bytes) + BigInteger.valueOf(FEE_DEMAND_REWARD)
   }

   /**
    * Gets the owner of the [Demand].
    *
    * @return the owner's script hash
    */
   private fun Demand.demand_getOwnerScriptHash(): ScriptHash = this.range(TIMESTAMP_SIZE + VALUE_SIZE, SCRIPT_HASH_SIZE)

   /**
    * Gets the reputation score required of a [Travel] in order to match this [Demand].
    *
    * @return the minimum reputation score required
    */
   private fun Demand.demand_getRepRequired(): BigInteger {
      val bytes = this.range(TIMESTAMP_SIZE + VALUE_SIZE + SCRIPT_HASH_SIZE, REP_REQUIRED_SIZE)
      return BigInteger(bytes)
   }

   /**
    * Gets the size of the desired item.
    *
    * @return the size of the desired item (scale of 1-5)
    */
   private fun Demand.demand_getItemSize(): BigInteger {
      val bytes = this.range(TIMESTAMP_SIZE + VALUE_SIZE + SCRIPT_HASH_SIZE + REP_REQUIRED_SIZE, CARRY_SPACE_SIZE)
      return BigInteger(bytes)
   }

   /**
    * Gets the info string for a [Demand].
    *
    * @return the info string for a [Demand]
    */
   private fun Demand.demand_getInfoBlob(): ByteArray {
      return this.range(TIMESTAMP_SIZE + VALUE_SIZE + SCRIPT_HASH_SIZE + REP_REQUIRED_SIZE + CARRY_SPACE_SIZE, DEMAND_INFO_SIZE)
   }

   /**
    * Gets the time at which this [Demand] was matched with a [Travel].
    *
    * @return the time as a unix timestamp
    */
   private fun Demand.demand_getMatchedAtTime(): BigInteger {
      val bytes = this.range(TIMESTAMP_SIZE + VALUE_SIZE + SCRIPT_HASH_SIZE + REP_REQUIRED_SIZE + CARRY_SPACE_SIZE + DEMAND_INFO_SIZE, TIMESTAMP_SIZE)
      return BigInteger(bytes)
   }

   /**
    * Gets the storage key for a [Demand].
    *
    * @return the storage key
    */
   private fun Hash160Pair.demand_getStorageKey(): ByteArray {
      val demandStorageKeySuffix = byteArrayOf(STORAGE_KEY_SUFFIX_DEMAND)
      val cityHashPairKey = this.concat(demandStorageKeySuffix)
      return cityHashPairKey
   }

   /**
    * Gets the storage key to store a [Travel] match for this [Demand].
    *
    * @return the storage key
    */
   private fun Demand.demand_getMatchKey() = take(this, TIMESTAMP_SIZE + VALUE_SIZE)

   //endregion

   // -============-
   // -=  Travel  =-
   // -============-
   //region travel

   /**
    * Creates a new [Travel].
    *
    * Travel details:
    *   - pickup and destination cities (hashed) (kept in storage key)
    *   - departure (expiry) time
    *   - minimum reputation requirement
    *   - carry space available
    *   - the traveller's wallet script hash (for receiving funds held in the demand)
    *
    * @param owner the owner of the demand
    * @param expiry the time that the demand expires as a unix timestmap
    * @param repRequired the reputation required of a demander in order for this travel to be matched
    * @param carrySpace the carry space available (on a scale of 1-5)
    */
   private fun travel_create(owner: ScriptHash, expiry: BigInteger, repRequired: BigInteger, carrySpace: BigInteger): Travel {
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

   /**
    * Performs all the legwork necessary to store and match a [Travel] with a [Demand].
    *
    * @param owner the traveller
    * @param pickUpCityHash the ripemd160 hashed form of the pick-up city
    * @param dropOffCityHash the ripemd160 hashed form of the drop-off city
    * @return true on success
    */
   private fun Travel.travel_storeAndMatch(owner: ScriptHash, pickUpCityHash: Hash160, dropOffCityHash: Hash160): Boolean {
      Runtime.notify("CL:DBG:Travel.store")

      // store the travel object (state lock)
      Storage.put(Storage.currentContext(), owner, this)

      // store the travel object (cities key)
      val cityHashPair = pickUpCityHash.concat(dropOffCityHash)
      val cityHashPairKey = cityHashPair.travel_getStorageKey()
      val travelsForCityPair = Storage.get(Storage.currentContext(), cityHashPairKey)
      val newTravelsForCityPair = travelsForCityPair.concat(this)
      Storage.put(Storage.currentContext(), cityHashPairKey, newTravelsForCityPair)
      Runtime.notify("CL:OK:StoredTravel", cityHashPair)

      // increment city usage counters (for stats)
      stats_recordCityUsage(pickUpCityHash)
      stats_recordCityUsage(dropOffCityHash)
      Runtime.notify("CL:DBG:UpdatedCityUsageStats", cityHashPair)

      // reserve the security deposit
      // this will overwrite existing fund reservations for this wallet
      // it's here because the compiler doesn't like it being below the following block
      this.travel_reserveDeposit(owner)

      Runtime.notify("CL:OK:ReservedTravelDeposit", cityHashPair)

      // find a demand object to match this travel with
      val cityHashPairKeyD = cityHashPair.demand_getStorageKey()
      val demandsForCityPair = Storage.get(Storage.currentContext(), cityHashPairKeyD)
      if (! demandsForCityPair.isEmpty()) {
         val repRequired = this.travel_getRepRequired()
         val carrySpaceAvailable = this.travel_getCarrySpace()

         val nowTime = Blockchain.getHeader(Blockchain.height()).timestamp()
         val matchedDemand = demandsForCityPair.demands_findMatchableDemand(repRequired, carrySpaceAvailable, nowTime)
         if (! matchedDemand.isEmpty()) {
            // match travel -> demand
            val matchKey = this.travel_getMatchKey()
            val nowTimeBigInt = BigInteger.valueOf(nowTime as Long)
            val nowTimeBytes = nowTimeBigInt.toByteArray(TIMESTAMP_SIZE)
            val timestampedMatch = matchedDemand.concat(nowTimeBytes)
            Storage.put(Storage.currentContext(), matchKey, timestampedMatch)

            // match demand -> travel
            val otherMatchKey = matchedDemand.demand_getMatchKey()
            val timestampedOtherMatch = this.concat(nowTimeBytes)
            Storage.put(Storage.currentContext(), otherMatchKey, timestampedOtherMatch)

            Runtime.notify("CL:OK:MatchedTravelWithDemand")

            // re-reserve the non-expiring security deposit
            // unfortunately this line must be here due to compiler troubles with anything more complex
            // todo: Disabled for now, an expiring deposit is a solution to potential problems with unsatisfactory or impossible demands
            //       beyond the MVP we will be able to use a matching system to overcome the need for this
            //this.travel_reserveNonExpiringDeposit(owner)

            // rewrite the reservation that was created with the matched demand
            // this means that we inject the traveller's script hash as the "recipient" of the reserved funds
            // when a transaction to withdraw the funds is received (multi-sig) the destination wallet must match this one
            val demandOwner = matchedDemand.demand_getOwnerScriptHash()
            val demandValue = matchedDemand.demand_getTotalValue()
            val ownerReservationList = demandOwner.wallet_getFundReservations()
            val reservationAtIdx = ownerReservationList.res_findBy(demandValue)
            if (reservationAtIdx > 0) {
               val rewrittenReservationList = ownerReservationList.res_replaceRecipientAt(reservationAtIdx, owner)
               demandOwner.wallet_storeFundReservations(rewrittenReservationList)

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
      return true
   }

   /**
    * Gets the [Travel] at the given [index] in a [TravelList].
    *
    * @param index the zero-based index of the [Travel] to retrieve
    * @return the [Travel], or an empty array if not found
    */
   private fun TravelList.travels_getAt(index: Int): Travel {
      return this.range(index * TRAVEL_SIZE, TRAVEL_SIZE)
   }

   /**
    * Reserves the deposit due by a traveller when they create a [Travel].
    *
    * Note: Use of this method will overwrite existing reserved funds for this wallet.
    *
    * @owner the traveller's wallet script hash
    */
   private fun Travel.travel_reserveDeposit(owner: ScriptHash) {
      var expiry = this.travel_getExpiry()
      owner.wallet_reserveFunds(expiry, BigInteger.valueOf(FEE_TRAVEL_DEPOSIT), true)
   }

   /**
    * Reserves the non-expiring deposit due by a traveller when they create a [Travel].
    *
    * This method is to be used when a [Travel] has been matched with a [Demand] to provide more risk exposure to
    * a traveller who may not fulfill their commitment to deliver the consignment.
    *
    * Note: Use of this method will overwrite existing reserved funds for this wallet.
    *
    * @param owner the traveller's script hash
    */
   private fun Travel.travel_reserveNonExpiringDeposit(owner: ScriptHash) {
      var expiry = BigInteger.valueOf(MAX_INT)
      owner.wallet_reserveFunds(expiry, BigInteger.valueOf(FEE_TRAVEL_DEPOSIT), true)
   }

   /**
    * Finds a [Travel] in a [TravelList] that fits the given attributes.
    *
    * @param repRequired the desired reputation of the traveller
    * @param carrySpaceRequired the carry space required (scale of 1-5)
    * @param nowTime the current unix timestamp in seconds
    * @return the [Travel] object or an empty array if nothing found
    */
   private fun TravelList.travel_findMatchableTravel(repRequired: BigInteger, carrySpaceRequired: BigInteger,
                                                     nowTime: Int = Blockchain.getHeader(Blockchain.height()).timestamp(),
                                                     assumeUnmatched: Boolean = false): Travel {
      val nil = byteArrayOf()
      if (this.isEmpty())
         return nil
      val count = this.size / TRAVEL_SIZE
      var i = 0
      while (i < count) {
         val travel = this.travels_getAt(i)
         val expiryBytes = take(travel, TIMESTAMP_SIZE)
         val expiry = BigInteger(expiryBytes)
         val carrySpaceAvailable = travel.travel_getCarrySpace()
         var ownerRep = repRequired
         if (repRequired > BigInteger.valueOf(0)) {
            val owner = travel.travel_getOwnerScriptHash()
            ownerRep = owner.wallet_getReputationScore()
         }
         if (expiry.toInt() > nowTime &&
               ownerRep >= repRequired &&
               carrySpaceAvailable >= carrySpaceRequired &&
               (assumeUnmatched || ! travel.travel_isMatched()))
            return travel
         i++
      }
      return nil
   }

   /**
    * Determines whether the given [Travel] has been matched with a [Demand] object.
    *
    * @return true if this [Travel] has been matched
    */
   private fun Travel.travel_isMatched(): Boolean {
      Runtime.notify("CL:DBG:Travel.isMatched")
      val matchKey = this.travel_getMatchKey()
      val match = Storage.get(Storage.currentContext(), matchKey)
      return !match.isEmpty()
   }

   /**
    * Gets the expiry timestamp of a [Travel].
    *
    * @return the expiry time as a unix timestamp
    */
   private fun Travel.travel_getExpiry(): BigInteger {
      val bytes = this.take(TIMESTAMP_SIZE)
      return BigInteger(bytes)
   }

   /**
    * Gets the reputation score required of a [Demand] in order to match this [Travel].
    *
    * @return the minimum reputation score required
    */
   private fun Travel.travel_getRepRequired(): BigInteger {
      val bytes = this.range(TIMESTAMP_SIZE, REP_REQUIRED_SIZE)
      return BigInteger(bytes)
   }

   /**
    * Gets the item carry space available.
    *
    * @return the carry space available (scale of 1-5, 5 is largest)
    */
   private fun Travel.travel_getCarrySpace(): BigInteger {
      val bytes = this.range(TIMESTAMP_SIZE + REP_REQUIRED_SIZE, CARRY_SPACE_SIZE)
      return BigInteger(bytes)
   }

   /**
    * Gets the owner of the [Travel].
    *
    * @return the owner of the [Travel]
    */
   private fun Travel.travel_getOwnerScriptHash(): ScriptHash {
      val bytes = this.range(TIMESTAMP_SIZE + REP_REQUIRED_SIZE + CARRY_SPACE_SIZE, SCRIPT_HASH_SIZE)
      return bytes
   }

   /**
    * Gets the time at which this [Travel] was matched with a [Demand].
    *
    * @return the time as a unix timestamp
    */
   private fun Travel.travel_getMatchedAtTime(): BigInteger {
      val bytes = this.range(TIMESTAMP_SIZE + REP_REQUIRED_SIZE + CARRY_SPACE_SIZE + SCRIPT_HASH_SIZE, TIMESTAMP_SIZE)
      return BigInteger(bytes)
   }

   /**
    * Gets the storage key for a [Travel].
    *
    * @return the storage key
    */
   private fun Hash160Pair.travel_getStorageKey(): ByteArray {
      val travelStorageKeySuffix = byteArrayOf(STORAGE_KEY_SUFFIX_TRAVEL)
      val cityHashPairKey = this.concat(travelStorageKeySuffix)
      return cityHashPairKey
   }

   /**
    * Gets the storage key to store a [Demand] match for this [Travel].
    *
    * @return the storage key
    */
   private fun Travel.travel_getMatchKey() = take(this, TIMESTAMP_SIZE + REP_REQUIRED_SIZE + CARRY_SPACE_SIZE)

   //endregion

   // -===========-
   // -=  Stats  =-
   // -===========-
   //region stats

   /**
    * Increments the counter that keeps the number of [Demands] created over time.
    *
    * @see stats_getDemandsCount
    */
   private fun stats_recordDemand() {
      val key = STORAGE_KEY_STATS_DEMANDS
      val existing = BigInteger(Storage.get(Storage.currentContext(), key))
      val accum = existing + BigInteger.valueOf(1)
      Storage.put(Storage.currentContext(), key, accum)
   }

   /**
    * Increments the counter that keeps the number of unique cities used over time.
    *
    * @see stats_getCityUsageCount
    */
   private fun stats_recordCityUsage(city: Hash160) {
      val isRecorded = Storage.get(Storage.currentContext(), city)
      if (!isRecorded.isEmpty()) return
      val key = STORAGE_KEY_STATS_CITIES
      val trueBytes = byteArrayOf(1)
      val count = BigInteger(Storage.get(Storage.currentContext(), key))
      Storage.put(Storage.currentContext(), key, count + BigInteger.valueOf(1))
      Storage.put(Storage.currentContext(), city, trueBytes)  // don't count this city again
   }

   /**
    * Increments the counter that keeps a count of all funds reserved over time.
    *
    * @see stats_getReservedFundsCount
    * @param value the funds reserved this time
    */
   private fun stats_recordReservedFunds(value: BigInteger) {
      val key = STORAGE_KEY_STATS_FUNDS
      val existing = BigInteger(Storage.get(Storage.currentContext(), key))
      val accum = existing + value
      Storage.put(Storage.currentContext(), key, accum)
   }

   /**
    * Returns the number of demands created over time.
    */
   private fun stats_getDemandsCount() = BigInteger(Storage.get(Storage.currentContext(), STORAGE_KEY_STATS_DEMANDS))

   /**
    * Returns the number of unique cities used over time.
    */
   private fun stats_getCityUsageCount() = BigInteger(Storage.get(Storage.currentContext(), STORAGE_KEY_STATS_CITIES))

   /**
    * Returns the amount of funds reserved over time.
    */
   private fun stats_getReservedFundsCount() = BigInteger(Storage.get(Storage.currentContext(), STORAGE_KEY_STATS_FUNDS))

   //endregion

   // -=================-
   // -=  Init Params  =-
   // -=================-
   //region init params

   /**
    * The GAS asset ID as a byte array.
    */
   private fun getAssetId(): ByteArray {
      //return Storage.get(Storage.currentContext(), "AssetID")
      // now reversed. see: https://git.io/vdM02
      val gasAssetId = byteArrayOf(231 as Byte, 45, 40, 105, 121, 238 as Byte, 108, 177 as Byte, 3, 230 as Byte, 93,
            253 as Byte, 223 as Byte, 178 as Byte, 227 as Byte, 132 as Byte, 16, 11, 141 as Byte, 20, 142 as Byte, 119,
            88, 222 as Byte, 66, 228 as Byte, 22, 139 as Byte, 113, 121, 44, 96)
      return gasAssetId
   }

   /**
    * Gets part 1 of the wallet script code (code before the public key), set at init time.
    */
   private fun getWalletScriptP1() = Storage.get(Storage.currentContext(), "WalletScriptP1")

   /**
    * Gets part 2 of the wallet script code (code after the public key, before the script hash), set at init time.
    */
   private fun getWalletScriptP2() = Storage.get(Storage.currentContext(), "WalletScriptP2")

   /**
    * Gets part 3 of the wallet script code (code after the script hash), set at init time.
    */
   private fun getWalletScriptP3() = Storage.get(Storage.currentContext(), "WalletScriptP3")

   //endregion

   // -================-
   // -=  Extensions  =-
   // -================-
   //region extensions

   /**
    * Concatenates two [ByteArray] together.
    *
    * @param b2 the bytes to append
    * @return the combined byte array
    */
   fun ByteArray.concat(b2: ByteArray) = concat(this, b2)

   /**
    * Extracts a range of bytes from a [ByteArray].
    *
    * @param index the index to start at
    * @param count the number of bytes to extract, starting at [index]
    * @return the extracted bytes
    */
   fun ByteArray.range(index: Int, count: Int) = range(this, index, count)

   /**
    * Takes a number of bytes from the start of a [ByteArray].
    *
    * @param count the number of bytes to take
    * @return the extracted bytes
    */
   fun ByteArray.take(count: Int) = take(this, count)

   /**
    * Pads a [BigInteger] that has been converted to a [ByteArray] to a specified byte length.
    *
    * @param count the number of bytes to pad to
    * @return the padded byte array, able to be converted back to a [BigInteger] when needed
    */
   fun ByteArray.pad(count: Int): ByteArray {
      var bytes = this
      if (bytes.size >= count)
         return bytes
      var zero = byteArrayOf(0)
      while (bytes.size < count) {
         bytes = bytes.concat(zero)
      }
      return bytes
   }

   /**
    * Converts a [BigInteger] to a padded [ByteArray].
    *
    * @see pad
    * @param padToSize the size in bytes to pad to
    * @return the padded byte array, able to be converted back to a [BigInteger] when needed
    */
   fun BigInteger.toByteArray(padToSize: Int = 0): ByteArray {
      var bytes = this.toByteArray()
      return bytes.pad(padToSize)
   }

   //endregion
}
