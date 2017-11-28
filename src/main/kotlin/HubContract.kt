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
typealias Reservation = ByteArray
typealias ReservationList = ByteArray
typealias Demand = ByteArray
typealias DemandList = ByteArray
typealias Travel = ByteArray
typealias TravelList = ByteArray

object HubContract : SmartContract() {

   // Config
   private const val LOG_LEVEL = 3  // 1: errors, 2: info/warn, 3: debug
   private const val TESTS_ENABLED = true  // important! disable this for live deployments!
   private const val STATS_ENABLED = true  // enables stats recording and getters

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

   // Fees
   private const val FEE_DEMAND_REWARD:  Long = 400000000  // 4 GAS
   private const val FEE_TRAVEL_DEPOSIT: Long = 200000000  // 2 GAS

   // Item value limits
   // the maximum value of a transaction is fixed at ~5497 GAS so that we can fit it into 5 bytes.
   private const val MAX_GAS_ITEM_VALUE: Long = 549750000000 - FEE_DEMAND_REWARD  // (2^40)/2 = 5497.5 GAS
   private const val MIN_GAS_ITEM_VALUE: Long = 50000000  // 0.5 GAS

   // Storage keys
   private const val STORAGE_KEY_SUFFIX_RESERVATIONS: Byte = 0
   private const val STORAGE_KEY_SUFFIX_DEMAND: Byte = 1
   private const val STORAGE_KEY_SUFFIX_TRAVEL: Byte = 2
   private const val STORAGE_KEY_SUFFIX_REP: Byte = 3
   private const val STORAGE_KEY_STATS_DEMANDS = "DemandsCounter"
   private const val STORAGE_KEY_STATS_ROUTES = "RoutesCounter"
   private const val STORAGE_KEY_STATS_FUNDS = "FundsCounter"
   private const val STORAGE_KEY_INIT_WALLET_P1 = "WalletScriptP1"
   private const val STORAGE_KEY_INIT_WALLET_P2 = "WalletScriptP2"
   private const val STORAGE_KEY_INIT_WALLET_P3 = "WalletScriptP3"
   private const val STORAGE_KEY_INITIALIZED = "Initialized"

   // Settings
   private const val TRAVEL_EXTRA_EXPIRY_ON_MATCH: Long = 86400  // 24 hours

   /**
    * The entry point of the smart contract.
    *
    * @param operation the method to run, specified as a string.
    * @param args a variable length array of arguments provided to the method.
    */
   fun Main(operation: String, vararg args: ByteArray) : Any {
      //region Initialization

      if (operation === "initialize")
         return initialize(args[0], args[1], args[2])
      if (operation === "is_initialized")
         return isInitialized()

      //endregion

      //region Test Entry Points

      if (TESTS_ENABLED) {
         val nil = byteArrayOf(0)
         if (operation === "test_storage_put")
            return Storage.put(Storage.currentContext(), args[0], args[1])
         if (operation === "test_initialize_getP1")
            return getWalletScriptP1()
         if (operation === "test_wallet_incrementReputationScore")
            return args[0].wallet_incrementReputationScore()
         if (operation === "test_reservation_create")
            return reservation_create(BigInteger(args[0]), BigInteger(args[1]), args[2])
         if (operation === "test_reservation_getExpiry")
            return args[0].res_getExpiry()
         if (operation === "test_reservation_getValue")
            return args[0].res_getValue()
         if (operation === "test_reservation_getRecipient")
            return args[0].res_getRecipient()
         if (operation === "test_reservations_getReservedGasBalance")
            return args[0].res_getTotalOnHoldGasValue(nil, 1)
         if (operation === "test_reservations_findByValue")
            return args[0].res_findByValue(BigInteger(args[1]))
         if (operation === "test_reservations_findByValueAndRecipient")
            return args[0].res_findByValueAndRecipient(BigInteger(args[1]), args[2])
         if (operation === "test_reservations_replaceRecipientAt")
            return args[0].res_replaceRecipientAt((args[1] as Int?)!!, args[2])
         if (operation === "test_demand_create")
            return demand_create(args[0], BigInteger(args[1]), BigInteger(args[2]), BigInteger(args[3]), BigInteger(args[4]), args[5], 1)
         if (operation === "test_demand_getItemValue")
            return args[0].demand_getItemValue()
         if (operation === "test_demand_getTotalValue")
            return args[0].demand_getTotalValue()
         if (operation === "test_demand_getInfoBlob")
            return args[0].demand_getInfoBlob()
         if (operation === "test_demand_getLookupKey")
            return args[0].demand_getLookupKey(args[1], 1)
         if (operation === "test_demand_getStorageKey")
            return args[0].demand_getStorageKey()
         if (operation === "test_demand_findMatchableDemand")
            return args[0].demands_findMatchableDemand(BigInteger(args[1]), BigInteger(args[2]), BigInteger(args[3]), (args[4] as Int?)!!)
         if (operation === "test_travel_create")
            return travel_create(args[0], BigInteger(args[1]), BigInteger(args[2]), BigInteger(args[3]), 1)
         if (operation === "test_travel_getCarrySpace")
            return args[0].travel_getCarrySpace()
         if (operation === "test_travel_getOwnerScriptHash")
            return args[0].travel_getOwnerScriptHash()
         if (operation === "test_travel_getLookupKey")
            return args[0].travel_getLookupKey(args[1], 1)
         if (operation === "test_travel_getStorageKey")
            return args[0].travel_getStorageKey()
         if (operation === "test_travel_findMatchableTravel")
            return args[0].travels_findMatchableTravel(BigInteger(args[1]), BigInteger(args[2]), BigInteger(args[3]), (args[4] as Int?)!!)
         if (operation === "test_stats_recordDemandCreation")
            return stats_recordDemandCreation()
         if (operation === "test_stats_recordRouteUsage")
            return stats_recordRouteUsage(args[0])
         if (operation === "test_stats_recordReservedFunds")
            return stats_recordReservedFunds(BigInteger(args[0]))
      }
      //endregion

      //region System Stats

      if (STATS_ENABLED) {
         if (operation === "stats_getDemandsCount")
            return stats_getDemandsCount()
         if (operation === "stats_getRouteUsageCount")
            return stats_getRouteUsageCount()
         if (operation === "stats_getReservedFundsCount")
            return stats_getReservedFundsCount()
         if (operation === "stats_getUserReputationScore")
            return args[0].wallet_getReputationScore()
      }
      //endregion

      // IsInitialized() - Compiler doesn't like that call being here
      if (Storage.get(Storage.currentContext(), STORAGE_KEY_INITIALIZED).isEmpty()) {
         Runtime.notify("CL:ERR:HubNotInitialized")
         return false
      }

      //region State Getters (Read Only, Local Invoke)

      // Wallet
      if (operation === "wallet_getGasBalance")
         return args[0].wallet_getGasBalance()
      if (operation === "wallet_getReservedGasBalance") {
         val reservations = args[0].wallet_getFundReservations()
         if (reservations.isEmpty()) return 0
         val nowTime = Blockchain.getHeader(Blockchain.height()).timestamp()
         val nil = byteArrayOf(0)
         return reservations.res_getTotalOnHoldGasValue(nil, nowTime)
      }
      if (operation === "wallet_requestTxOut") {
         if (args[0].wallet_validate(args[1])) {
            val nowTime = Blockchain.getHeader(Blockchain.height()).timestamp()
            return args[0].wallet_requestTxOut(nowTime)
         }
         Runtime.notify("CL:ERR:InvalidWallet")
         return false
      }

      // System State
      if (operation === "demand_isMatched")
         return args[0].demand_isMatched()
      if (operation === "demand_getMatchKey")
         return args[0].demand_getMatchKey()
      if (operation === "demand_getTravelMatch") {
         val matchKey = args[0].demand_getMatchKey()
         return Storage.get(Storage.currentContext(), matchKey)
      }
      if (operation === "demand_getTravelMatchedAtTime") {
         val matchKey = args[0].demand_getMatchKey()
         val travel = Storage.get(Storage.currentContext(), matchKey)
         return travel.travel_getMatchedAtTime()
      }
      if (operation === "travel_isMatched")
         return args[0].travel_isMatched()
      if (operation === "travel_getMatchKey")
         return args[0].travel_getMatchKey()
      if (operation === "travel_getDemandMatch") {
         val matchKey = args[0].travel_getMatchKey()
         return Storage.get(Storage.currentContext(), matchKey)
      }
      if (operation === "travel_getDemandMatchedAtTime") {
         val matchKey = args[0].travel_getMatchKey()
         val demand = Storage.get(Storage.currentContext(), matchKey)
         return demand.demand_getMatchedAtTime()
      }
      // Used by chainline-js to generate a tracking ID on opening a demand/travel
      if (operation === "timestamp")
         return Blockchain.getHeader(Blockchain.height()).timestamp()
      // Generic catch-all storage getter
      if (operation === "storage_get")
         return Storage.get(Storage.currentContext(), args[0])
      //endregion

      //region State Mutators (Blockchain Invokes)

      // Open and try to match a Demand
      if (operation === "demand_open") {
         val nowTime = Blockchain.getHeader(Blockchain.height()).timestamp()
         val outgoingAmount = BigInteger(args[5]) as Long + FEE_DEMAND_REWARD
         val nil = byteArrayOf(0)
         if (args[0].wallet_validate(args[1]) &&
               args[0].wallet_hasFunds(outgoingAmount, nil, nowTime, false) &&  // value in outputs are *paid* system fees
               args[0].wallet_canOpenDemandOrTravel(nowTime)) {
            val route = args[7]
            val demand = demand_create(args[0], BigInteger(args[2]), BigInteger(args[3]), BigInteger(args[4]), BigInteger(args[5]), args[6], nowTime)
            if (! demand.isEmpty() &&
                  demand.demand_storeAndMatch(args[0], route, nowTime)) {
               if (STATS_ENABLED) {  // the compiler will optimize this out if disabled
                  log_debug("CL:DBG:RecordingStats")
                  stats_recordRouteUsage(route)
                  stats_recordReservedFunds(BigInteger(args[5]))
                  stats_recordDemandCreation()
               }
               return true
            }
            Runtime.notify("CL:ERR:DemandValidationError")
         }
      }

      // Open and try to match a Travel
      if (operation === "travel_open") {
         val nowTime = Blockchain.getHeader(Blockchain.height()).timestamp()
         val nil = byteArrayOf(0)
         if (args[0].wallet_validate(args[1]) &&
               args[0].wallet_hasFunds(FEE_TRAVEL_DEPOSIT, nil, nowTime, false) &&
               args[0].wallet_canOpenDemandOrTravel(nowTime)) {
            val route = args[5]
            val travel = travel_create(args[0], BigInteger(args[2]), BigInteger(args[3]), BigInteger(args[4]), nowTime)
            if (! travel.isEmpty() &&
                  travel.travel_storeAndMatch(args[0], route, nowTime)) {
               if (STATS_ENABLED) {
                  log_debug("CL:DBG:RecordingStats")
                  stats_recordRouteUsage(route)
                  stats_recordReservedFunds(BigInteger.valueOf(FEE_TRAVEL_DEPOSIT))
               }
               return true
            }
            Runtime.notify("CL:ERR:TravelValidationError")
         }
      }

      // Exchange: Demand owner refunds the Travel owner
      if (operation === "wallet_setFundsPaidToRecipientTxHash") {
         // if true the entire Chain Line transaction is complete
         if (args[0].wallet_validate(args[1]) &&
               args[0].wallet_setFundsPaidToRecipientTxHash(args[2], BigInteger(args[3]), args[4])) {
            // reset account states, allow for new transactions
            args[0].wallet_clearState()
            args[2].wallet_clearState()

            // increment account reputation scores
            args[0].wallet_incrementReputationScore()
            args[2].wallet_incrementReputationScore()

            return true
         }
         return false
      }
      //endregion

      return false
   }

   // -====================-
   // -=  Initialization  =-
   // -====================-
   //region initialization

   /**
    * Initializes the smart contract. This takes the three parts of the wallet script as arguments.
    * These parts are stored and then used to verify the integrity of user wallets in the system.
    *
    * @param walletScriptP1 part 1 of the wallet script code (code before the public key)
    * @param walletScriptP2 part 2 of the wallet script code (code after the public key, before the script hash)
    * @param walletScriptP3 part 3 of the wallet script code (code after the script hash)
    */
   private fun initialize(walletScriptP1: ByteArray, walletScriptP2: ByteArray, walletScriptP3: ByteArray): Boolean {
      if (isInitialized()) return false
      val trueBytes = byteArrayOf(1)
      Storage.put(Storage.currentContext(), STORAGE_KEY_INIT_WALLET_P1, walletScriptP1)
      Storage.put(Storage.currentContext(), STORAGE_KEY_INIT_WALLET_P2, walletScriptP2)
      Storage.put(Storage.currentContext(), STORAGE_KEY_INIT_WALLET_P3, walletScriptP3)
      Storage.put(Storage.currentContext(), STORAGE_KEY_INITIALIZED, trueBytes)
      log_info("CL:OK:HubInitialized")
      return true
   }

   /**
    * Checks whether the contract has been initialized.
    *
    * @return true if the contract has been initialized
    */
   private fun isInitialized() = ! Storage.get(Storage.currentContext(), STORAGE_KEY_INITIALIZED).isEmpty()

   //endregion

   // -=============-
   // -=  Wallets  =-
   // -=============-
   //region wallets

   /**
    * Calls [Runtime.checkWitness] on the provided script hash.
    * Avoids making the call if the current ScriptContainer is null, which is the case when rpc "invokescript" is used.
    */
   private fun ScriptHash.wallet_checkWitness(): Boolean {
      log_debug("CL:DBG:wallet_checkWitness")
      // skip checkWitness when a local invocation (via rpc invokescript) is performed
      // ScriptContainer is null in this case and it causes a VM fault.
      if (vm_booland(ExecutionEngine.scriptContainer(), true)) {
         vm_throwIfNot(Runtime.checkWitness(this))
         log_info("CL:OK:checkWitness")
      }
      return true
   }

   /**
    * Calls [wallet_checkWitness] on and validates the integrity of the provided script hash.
    *
    * @see wallet_checkWitness
    * @param pubKey the script hash of the user wallet to validate
    * @return true if the wallet is valid
    */
   private fun ScriptHash.wallet_validate(pubKey: PublicKey): Boolean {
      log_debug("CL:DBG:wallet_validate")
      val expectedScript =
         getWalletScriptP1()
            .concat(pubKey)
            .concat(getWalletScriptP2())
            .concat(ExecutionEngine.executingScriptHash())
            .concat(getWalletScriptP3())
      val expectedScriptHash = hash160(expectedScript)
      if (this.wallet_checkWitness() &&
            this === expectedScriptHash)
         return true
      Runtime.notify("CL:ERR:WalletValidate", expectedScriptHash, expectedScript)  // the compiler does not like log_* here
      return false
   }

   /**
    * Gets the GAS balance of a user wallet.
    *
    * @return the GAS balance as a fixed8 int
    */
   private fun ScriptHash.wallet_getGasBalance(): Long {
      val account = Blockchain.getAccount(this)
      return getBalance(account, getGasAssetId())  // fixes a bug where stack items were in the wrong order in SYSCALL
   }

   /**
    * Requests permission to perform a withdrawal from a user wallet using the value in tx outputs.
    * Note: Please ensure that the script has been through [wallet_validate] before this is called.
    *
    * @param nowTime the current unix timestamp in seconds
    * @return true if the transaction is clear to proceed
    */
   private fun ScriptHash.wallet_requestTxOut(nowTime: Int): Boolean {
      log_debug("CL:DBG:wallet_requestTxOut")
      // allow rpc invokescript to run though
      if (vm_booland(ExecutionEngine.scriptContainer(), true)) {
         // find the recipient of a contract transaction
         // funds reserved for this recipient will be excluded from the count to allow a demand owner to refund a traveller
         val tx = ExecutionEngine.scriptContainer() as Transaction?
         val outputs = tx!!.outputs()
         var recipient = byteArrayOf(0)
         outputs.forEach {
            // invokes will not count as their GAS is sent back to the caller
            if (it.scriptHash() !== this &&
                  it.assetId() === getGasAssetId() &&
                  it.value() > 0)
               recipient = it.scriptHash()
         }
         // a requiredBalance of 0 is intentional so that it does not double count outgoing value
         return this.wallet_hasFunds(0, recipient, nowTime, true)
      }
      val nil = byteArrayOf(0)
      return this.wallet_hasFunds(0, nil, nowTime, true)
   }

   /**
    * Check if the wallet has enough funds to perform a transaction after reserved funds and tx outputs are considered.
    * Note: Please ensure that the script has been through [wallet_validate] before this is called.
    *
    * @param requiredBalance the effective balance of the wallet required (after tx outputs counted)
    * @param countOutputs count transaction outputs? this is unnecessary if this is a hub invocation (system fee already taken)
    * @return true if the wallet's effective balance is greater than or equal to [requiredBalance]
    */
   private fun ScriptHash.wallet_hasFunds(requiredBalance: Long, excludeRecipient: ScriptHash, nowTime: Int, countOutputs: Boolean): Boolean {
      log_debug2("CL:DBG:wallet_hasFunds+", BigInteger.valueOf(requiredBalance))
      val balance = this.wallet_getGasBalance()
      log_debug2("CL:DBG:Balance+", balance)
      if (balance >= requiredBalance) {
         val reservations = this.wallet_getFundReservations()
         val gasOnHold = reservations.res_getTotalOnHoldGasValue(excludeRecipient, nowTime)
         var txOutgoing: Long = 0
         if (countOutputs) {
            txOutgoing += tx_getOutgoingGasValue(this)
         }
         val effectiveBalance = balance - gasOnHold - txOutgoing
         log_debug2("CL:DBG:GasOnHold+", gasOnHold)
         log_debug2("CL:DBG:TxOutgoing+", txOutgoing)
         log_debug2("CL:DBG:EffectiveBalance+", effectiveBalance)
         if (effectiveBalance == 0 as Long ||  // yeah, don't ask...
               effectiveBalance >= requiredBalance)
            return true
         Runtime.notify("CL:ERR:InsufficientFunds:2+", requiredBalance, effectiveBalance)
         return false
      }
      Runtime.notify("CL:ERR:InsufficientFunds:1+", requiredBalance, balance)
      return false
   }

   /**
    * Reserves funds in a previously validated user wallet. Overwrites any existing fund reservations.
    *
    * Note: Please ensure that the wallet has been validated before this is called.
    *
    * @param expiry timestamp of when the reserved funds get released automatically
    * @param value the amount of GAS to hold in the reservation as a fixed8 int
    * @param recipient the recipient script hash that the funds are reserved for (set to owner if not known yet)
    * @return true on success
    */
   private fun ScriptHash.wallet_reserveFunds(expiry: BigInteger, value: BigInteger, recipient: ScriptHash) {
      // reserve the funds
      // the wallet's effective balance has already been checked in [wallet_validate]
      val reservation = reservation_create(expiry, value, recipient)
      this.wallet_storeFundReservations(reservation)
      log_info3("CL:OK:ReservedFunds+", value, reservation)
   }

   /**
    * Sets the transaction hash of funds that have been transferred to the recipient of [reserved funds][ReservationList].
    *
    * Because we can't access storage in a verify script, we have to use an invoke to set the tx hash.
    * This function finds the given transaction and makes sure the supplied value went to the recipient.
    * If the transaction or reserved funds cannot be found this method will fail and return false.
    *
    * @param recipient the recipient of the reserved funds
    * @param value the GAS value of the reserved funds as a fixed8 int
    * @param txHash the transaction hash
    * @return true on success
    */
   private fun ScriptHash.wallet_setFundsPaidToRecipientTxHash(recipient: ScriptHash, value: BigInteger, txHash: Hash256): Boolean {
      log_debug("CL:DBG:setReservationPaidToRecipientTxHash")
      // in most cases a Reservation is created with its owner's script hash set as the recipient.
      // this is merely its starting state and we shouldn't allow the user to actually perform an exchange with themselves.
      if (recipient !== this &&
            Storage.get(Storage.currentContext(), txHash).isEmpty()) {  // tx consumed?
         val reservations = this.wallet_getFundReservations()
         val matchIdx = reservations.res_findByValueAndRecipient(value, recipient)
         if (matchIdx > -1) {
            val matchedRes = reservations.res_getAt(matchIdx)
            val stored = Storage.get(Storage.currentContext(), matchedRes)
            if (stored.isEmpty()) {
               val tx = Blockchain.getTransaction(txHash)
               val outputs = tx!!.outputs()
               var txValue: Long = 0
               outputs.forEach {
                  if (it.scriptHash() === recipient)
                     txValue += it.value()
               }
               if (txValue >= value.toLong()) {
                  Storage.put(Storage.currentContext(), matchedRes, txHash)
                  Storage.put(Storage.currentContext(), txHash, matchedRes)  // "consume" the tx
                  return true
               }
               return false
            }
            return false
         }
         return false
      }
      return false
   }

   /**
    * Indicates whether a wallet is in a state that allows it to open a demand or travel.
    * At the moment this contract supports one active transaction per user wallet at any one time.
    *
    * @param nowTime the current unix timestamp in seconds
    * @return true on success
    */
   private fun ScriptHash.wallet_canOpenDemandOrTravel(nowTime: Int): Boolean {
      log_debug("CL:DBG:canOpenDemandOrTravel?")

      // check active fund reservations (should be enough)
      val nil = byteArrayOf(0)
      val reservations = this.wallet_getFundReservations()
      val gasOnHold = reservations.res_getTotalOnHoldGasValue(nil, nowTime)
      if (gasOnHold > 0)
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
      return Storage.get(Storage.currentContext(), key)
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
    * Clears all wallet system state, allowing for new Chain Line transactions.
    */
   private fun ScriptHash.wallet_clearState() {
      Storage.delete(Storage.currentContext(), this)
      this.wallet_clearFundReservations()
   }

   /**
    * Gets the storage key used to store/lookup [reserved funds][ReservationList] for a wallet.
    */
   private fun ScriptHash.wallet_getFundReservationsStorageKey(): ByteArray {
      val suffix = byteArrayOf(STORAGE_KEY_SUFFIX_RESERVATIONS)
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
      val oneBytes = byteArrayOf(1)
      val one = BigInteger(oneBytes)
      val rep = this.wallet_getReputationScore()
      val newRep = rep + one
      this.wallet_storeReputationScore(newRep)
   }

   /**
    * Gets the storage key used to store/lookup user reputation for a wallet.
    */
   private fun ScriptHash.wallet_getReputationStorageKey(): ByteArray {
      val suffix = byteArrayOf(STORAGE_KEY_SUFFIX_REP)
      val combined = this.concat(suffix)
      return combined
   }

   //endregion

   // -=================-
   // -=  Transaction  =-
   // -=================-
   //region transactions

   /**
    * Finds the outgoing GAS value of the current transaction. This is useful for calculating whether the wallet can cover
    * system fees when funds are going to be reserved in the invocation.
    *
    * @param originator the initiating wallet
    * @return the value of the transaction in GAS as a fixed8 long
    */
   private fun tx_getOutgoingGasValue(originator: ScriptHash): Long {
      var gasTxValue: Long = 0  // as a fixed8 long

      // skip the count when a local invocation (via rpc invokescript) is performed
      // ScriptContainer is null in this case and it causes a VM fault.
      if (vm_booland(ExecutionEngine.scriptContainer(), true)) {
         val tx = ExecutionEngine.scriptContainer() as Transaction?
         val gasAssetId = getGasAssetId()

         // count inputs first in case funds do not appear in outputs (system fee payments, etc.)
         val refs = tx!!.references()
         refs.forEach {
            if (it.assetId() === gasAssetId)
               gasTxValue += it.value()
         }
         if (gasTxValue > 0) {
            val outputs = tx.outputs()
            outputs.forEach {
               // invokes will not count as their GAS is returned to the caller
               if (it.scriptHash() === originator &&
                  it.assetId() === gasAssetId)
                  gasTxValue -= it.value()
            }
         }
      }
      return gasTxValue
   }

   //endregion

   // -==================-
   // -=  Reservations  =-
   // -==================-
   //region reservations

   /**
    * Creates a [reserved funds object][Reservation].
    *
    * @param expiry timestamp of when the reserved funds get released automatically
    * @param value the amount of GAS to hold in the reservation as a fixed8 int
    * @param recipient the recipient wallet that the funds are reserved for
    * @return the [reserved funds object][Reservation]
    */
   private fun reservation_create(expiry: BigInteger, value: BigInteger, recipient: ScriptHash): Reservation {
      val nil = byteArrayOf()
      if (value.toLong() > MAX_GAS_ITEM_VALUE)
         return nil
      // size: 29 bytes
      val expectedSize = RESERVATION_SIZE
      val reservation = expiry.toByteArray(TIMESTAMP_SIZE)
         .concat(value.toByteArray(VALUE_SIZE))
         .concat(recipient)  // script hash, 20 bytes
      // checking individual arg lengths doesn't seem to work here
      // I tried a lot of things, grr compiler
      if (reservation.size != expectedSize) {
         Runtime.notify("CL:ERR:UnexpectedReservationSize", reservation)  // compiler woes
         vm_throw()  // abort here
         return nil
      }
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
    * @param value the value to match as a fixed8 int
    * @return the index at which the [Reservation] is at in the [ReservationList]
    */
   private fun ReservationList.res_findByValue(value: BigInteger): Int {
      if (this.isEmpty())
         return -1
      val count = this.size / RESERVATION_SIZE
      var i = 0
      while (i < count) {
         val reservation = this.res_getAt(i)
         val valueBytes = range(reservation, TIMESTAMP_SIZE, VALUE_SIZE)
         val valueFound = BigInteger(valueBytes)
         if (value == valueFound)  // uses `==` to force a numeric, not a raw byte array, equality check
            return i
         i++
      }
      return -1
   }

   /**
    * Finds the [reserved funds object][Reservation] with the given [value] and [recipient] in a [ReservationList].
    *
    * @param value the value to match as a fixed8 int
    * @param recipient the recipient to match (AND)
    * @return the index at which the [Reservation] is at in the [ReservationList]
    */
   private fun ReservationList.res_findByValueAndRecipient(value: BigInteger, recipient: ScriptHash): Int {
      if (this.isEmpty())
         return -1
      val count = this.size / RESERVATION_SIZE
      var i = 0
      while (i < count) {
         val reservation = this.res_getAt(i)
         val valueBytes = range(reservation, TIMESTAMP_SIZE, VALUE_SIZE)
         val valueFound = BigInteger(valueBytes)
         val recipientFound = reservation.res_getRecipient()
         if (value == valueFound &&  // uses `==` to force a numeric, not a raw byte array, equality check
               recipient === recipientFound)
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
   private fun ReservationList.res_getTotalOnHoldGasValue(excludeRecipient: ScriptHash, nowTime: Int): Long {
      // todo: clean up expired reservation entries
      if (this.isEmpty())
         return 0
      val count = this.size / RESERVATION_SIZE
      var i = 0
      var total: Long = 0
      while (i < count) {
         val reservation = this.res_getAt(i)
         if (! reservation.res_wasPaidToRecipient()) {
            val expiryBytes = take(reservation, TIMESTAMP_SIZE)
            val expiry = BigInteger(expiryBytes)
            val recipient = reservation.res_getRecipient()
            if (expiry.toInt() > nowTime &&
                  recipient !== excludeRecipient) {
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
      return this.range(TIMESTAMP_SIZE + VALUE_SIZE, SCRIPT_HASH_SIZE)
   }

   /**
    * Determines whether the reserved funds were paid out to the recipient.
    *
    * @return true if the funds were paid to the recipient
    */
   private fun Reservation.res_wasPaidToRecipient(): Boolean {
      val stored = Storage.get(Storage.currentContext(), this)
      if (stored.isEmpty())  // `==` for a numeric equality check
         return false
      return stored.size == TX_HASH_SIZE
   }

   //endregion

   // -=============-
   // -=  Demands  =-
   // -=============-
   //region demands

   /**
    * Creates a new [Demand] object.
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
    * @param itemValue the value of the item in GAS as a fixed8 int
    * @param infoBlob a 128 character string describing the desired item and the demand owner's contact details
    * @param nowTime the current unix timestamp in seconds
    * @return the serialized [Demand] or an empty byte array on failure
    */
   private fun demand_create(owner: ScriptHash, expiry: BigInteger, repRequired: BigInteger, itemSize: BigInteger,
                             itemValue: BigInteger, infoBlob: ByteArray, nowTime: Int): Demand {
      val nil = byteArrayOf()
      // validate inputs
      if (itemValue.toLong() < MIN_GAS_ITEM_VALUE)
         return nil
      if (itemValue.toLong() > MAX_GAS_ITEM_VALUE)
         return nil
      if (itemValue.toLong() <= 0)
         return nil
      if (expiry.toLong() < nowTime as Long)
         return nil
      if (itemSize.toLong() <= 0)
         return nil
      // there is no need to validate out a negative repRequired or overly large ints here.
      // the size check below will catch the latter!
      // size: 160 bytes
      val expectedSize = DEMAND_SIZE
      val demand = expiry.toByteArray(TIMESTAMP_SIZE)
            .concat(itemValue.toByteArray(VALUE_SIZE))
            .concat(owner)
            .concat(repRequired.toByteArray(REP_REQUIRED_SIZE))
            .concat(itemSize.toByteArray(CARRY_SPACE_SIZE))
            .concat(infoBlob)
      // checking individual arg lengths would be the most ideal form of validation here, but that didn't want to compile
      if (demand.size != expectedSize) {
         Runtime.notify("CL:ERR:UnexpectedDemandSize", demand)  // compiler woes
         return nil
      }
      return demand
   }

   /**
    * Performs all the legwork necessary to store and match a [Demand] with a [Travel].
    *
    * @param owner the owner of the demand
    * @param cityPairHash the ripemd160 hash of: script hash (hex) (salt) + origin city + destination city (ascii)
    * @return true on success
    */
   private fun Demand.demand_storeAndMatch(owner: ScriptHash, cityPairHash: Hash160, nowTime: Int): Boolean {
      log_debug("CL:DBG:Demand.store")

      val nowTimeBigInt = BigInteger.valueOf(nowTime as Long)
      val nowTimeBytes = nowTimeBigInt.toByteArray(TIMESTAMP_SIZE)

      // owner state lock with timestamp
      // so that it's straightforward for the client to know which block this landed in
      val demandWithTime = this.concat(nowTimeBytes)
      Storage.put(Storage.currentContext(), owner, demandWithTime)

      // store the demand object (lookup key)
      val lookupKey = this.demand_getLookupKey(cityPairHash, nowTime)
      Storage.put(Storage.currentContext(), lookupKey, demandWithTime)

      // store the demand object (cities hash key) for matching
      val cityPairHashKeyD = cityPairHash.demand_getStorageKey()
      val demandsForCity = Storage.get(Storage.currentContext(), cityPairHashKeyD)
      val newDemandsForCity = demandsForCity.concat(this)
      Storage.put(Storage.currentContext(), cityPairHashKeyD, newDemandsForCity)
      log_info2("CL:OK:StoredDemand", cityPairHash)

      // find a travel object to match this demand with
      val cityPairHashKeyT = cityPairHash.travel_getStorageKey()
      val travelsForCityPair = Storage.get(Storage.currentContext(), cityPairHashKeyT)

      // no travels available? no match can be made yet!
      if (travelsForCityPair.isEmpty()) {
         log_debug("CL:DBG:NoMatchableTravelForDemand:1")

         // reserve the item's value and fee (no match yet, reserve for later)
         this.demand_reserveValueAndFee(owner)
         log_info("CL:OK:ReservedDemandValueAndFee:1")
      }

      // random compiler errors made the below messy and split this if. it's not my fault :)
      // we have matches! walk through the travels, find one that is appropriate to match
      if (! travelsForCityPair.isEmpty()) {
         val repRequired = this.demand_getRepRequired()
         val carrySpaceRequired = this.demand_getItemSize()
         val demandExpiry = this.demand_getExpiry()

         // do the matching
         val matchedTravel = travelsForCityPair.travels_findMatchableTravel(repRequired, carrySpaceRequired, demandExpiry, nowTime)
         if (matchedTravel.isEmpty()) {  // no match
            log_debug("CL:DBG:NoMatchableTravelForDemand:2")

            // reserve the item's value and fee (no match yet, reserve for later)
            this.demand_reserveValueAndFee(owner)
            log_info("CL:OK:ReservedDemandValueAndFee:2")
         } else {
            // the compiler really wants this here!
            val nowTimeBigInt2 = BigInteger.valueOf(nowTime as Long)
            val nowTimeBytes2 = nowTimeBigInt2.toByteArray(TIMESTAMP_SIZE)

            // match demand -> travel
            val matchKey = this.demand_getMatchKey()
            val timestampedMatch = matchedTravel.concat(nowTimeBytes2)
            Storage.put(Storage.currentContext(), matchKey, timestampedMatch)
            log_info("CL:OK:MatchedDemandWithTravel")

            // match travel -> demand
            val otherMatchKey = matchedTravel.travel_getMatchKey()
            val timestampedOtherMatch = this.concat(nowTimeBytes2)
            Storage.put(Storage.currentContext(), otherMatchKey, timestampedOtherMatch)
            log_info("CL:OK:MatchedTravelWithDemand")

            // switch the travel's existing expiry to a new time after the expiry of the demand
            val newExpiry = demandExpiry + TRAVEL_EXTRA_EXPIRY_ON_MATCH as BigInteger
            val traveller = matchedTravel.travel_getOwnerScriptHash()
            matchedTravel.travel_overwriteExpiry(traveller, newExpiry)
            log_debug("CL:OK:UpdatedTravelExpiry")

            // reserve the item's value and fee
            // this will overwrite existing fund reservations for this wallet
            // since we found a matchable travel we can set the recipient's script hash in the reservation (matchedTravel)
            this.demand_reserveValueAndFee2(owner, matchedTravel)
            log_debug("CL:OK:ReservedDemandValueAndFee:3")
         }
      }
      // always a success (for now)
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
      val toReserve = this.demand_getTotalValue()
      val expiry = this.demand_getExpiry()
      owner.wallet_reserveFunds(expiry, toReserve, owner)
   }

   /**
    * Reserves the item's value and reward fee for a new demand.
    * This form of the method is used when a [Travel] was found to be matched with the [Demand].
    *
    * @param owner the owner of the demand
    * @param matchedTravel the travel that was matched with the demand
    */
   private fun Demand.demand_reserveValueAndFee2(owner: ScriptHash, matchedTravel: Travel) {
      val expiry = this.demand_getExpiry()
      val toReserve = this.demand_getTotalValue()
      val travellerScriptHash = matchedTravel.travel_getOwnerScriptHash()
      owner.wallet_reserveFunds(expiry, toReserve, travellerScriptHash)
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
                                                      expiresAfter: BigInteger, nowTime: Int): Demand {
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
         val demandMatchKey = demand.demand_getMatchKey()
         var ownerRep = repRequired
         if (repRequired > BigInteger.valueOf(0)) {
            val owner = demand.demand_getOwnerScriptHash()
            ownerRep = owner.wallet_getReputationScore()
         }
         if (expiry.toInt() > nowTime &&
               expiry > expiresAfter &&
               ownerRep >= repRequired &&
               carrySpaceAvailable >= itemSize &&
               Storage.get(Storage.currentContext(), demandMatchKey).isEmpty())  // inlined demand_isMatched(), ❤ compiler
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
      log_debug("CL:DBG:Demand.isMatched")
      val matchKey = this.demand_getMatchKey()
      val match = Storage.get(Storage.currentContext(), matchKey)
      return ! match.isEmpty()
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
    * @return the item's GAS value as a fixed8 int
    */
   private fun Demand.demand_getItemValue(): BigInteger {
      val bytes = this.range(TIMESTAMP_SIZE, VALUE_SIZE)
      return BigInteger(bytes)
   }

   /**
    * Gets the [Demand]'s total value (item value + reward fee).
    *
    * @return the demand's GAS value as a fixed8 int
    */
   private fun Demand.demand_getTotalValue(): BigInteger {
      // broken down like this to avoid a VM fault
      val bytes = this.range(TIMESTAMP_SIZE, VALUE_SIZE)
      val fee = FEE_DEMAND_REWARD as BigInteger
      val value = BigInteger(bytes)
      val total = value + fee
      return total
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
    * Gets the key used to store the demand for tracking lookup.
    * Tracking requires knowledge of the "identifier" and city pair.
    *
    * @param cityPairHash the ripemd160 hash of: script hash (hex) (salt) + origin city + destination city (ascii)
    * @return the lookup key for use with [Storage]
    */
   private fun Demand.demand_getLookupKey(cityPairHash: Hash160, nowTime: Int): ByteArray {
      val nowBigInt = nowTime as BigInteger
      val nowBytes = nowBigInt.toByteArray(TIMESTAMP_SIZE)
      val expiryBytes = take(this, TIMESTAMP_SIZE)
      val suffix = byteArrayOf(STORAGE_KEY_SUFFIX_DEMAND)
      val key = nowBytes
         .concat(expiryBytes)
         .concat(suffix)
         .concat(cityPairHash)
      return key
   }

   /**
    * Gets the storage key to store a [Travel] match for this [Demand].
    *
    * @return the match key for use with [Storage]
    */
   private fun Demand.demand_getMatchKey() =
      take(this, TIMESTAMP_SIZE + VALUE_SIZE + SCRIPT_HASH_SIZE)

   /**
    * Gets the storage key for a [Demand], which is essentially just the city pair hash with a suffix.
    * This is called on the city pair hash.
    *
    * @return the storage key (city pair hash + 1 byte suffix)
    */
   private fun Hash160.demand_getStorageKey(): ByteArray {
      val demandStorageKeySuffix = byteArrayOf(STORAGE_KEY_SUFFIX_DEMAND)
      return this.concat(demandStorageKeySuffix)
   }

   //endregion

   // -============-
   // -=  Travel  =-
   // -============-
   //region travel

   /**
    * Creates a new [Travel] object.
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
    * @param nowTime the current unix timestamp in seconds
    * @return the serialized [Travel] or an empty byte array on failure
    */
   private fun travel_create(owner: ScriptHash, expiry: BigInteger, repRequired: BigInteger, carrySpace: BigInteger, nowTime: Int): Travel {
      val nil = byteArrayOf()
      // no validation necessary, because:
      // - if expiry, repRequired or carrySpace are < 0 this Travel will simply never be matched
      // - any abuse of BigInteger sizes (e.g. a huge repRequired) will overflow its allocated slot and result in a nil return value
      // size: 27 bytes
      val travel = expiry.toByteArray(TIMESTAMP_SIZE)
            .concat(repRequired.toByteArray(REP_REQUIRED_SIZE))
            .concat(carrySpace.toByteArray(CARRY_SPACE_SIZE))
            .concat(owner)
      // checking individual arg lengths would be the most ideal form of validation here, but that didn't want to compile
      if (travel.size != TRAVEL_SIZE) {
         Runtime.notify("CL:ERR:UnexpectedTravelSize", travel)
         return nil
      }
      return travel
   }

   /**
    * Performs all the legwork necessary to store and match a [Travel] with a [Demand].
    *
    * @param owner the traveller
    * @param cityPairHash the ripemd160 hash of: script hash (hex) (salt) + origin city + destination city (ascii)
    * @return true on success
    */
   private fun Travel.travel_storeAndMatch(owner: ScriptHash, cityPairHash: Hash160, nowTime: Int): Boolean {
      log_debug("CL:DBG:Travel.store")

      val nowTimeBigInt = BigInteger.valueOf(nowTime as Long)
      val nowTimeBytes = nowTimeBigInt.toByteArray(TIMESTAMP_SIZE)

      // owner state lock with timestamp
      // so that it's straightforward for the client to know which block this landed in
      val travelWithTime = this.concat(nowTimeBytes)
      Storage.put(Storage.currentContext(), owner, travelWithTime)

      // store the travel object (lookup key)
      val lookupKey = this.travel_getLookupKey(cityPairHash, nowTime)
      Storage.put(Storage.currentContext(), lookupKey, travelWithTime)

      // store the travel object (cities hash key) for matching
      val cityPairHashKey = cityPairHash.travel_getStorageKey()
      val travelsForCityPair = Storage.get(Storage.currentContext(), cityPairHashKey)
      val newTravelsForCityPair = travelsForCityPair.concat(this)
      Storage.put(Storage.currentContext(), cityPairHashKey, newTravelsForCityPair)
      log_info2("CL:OK:StoredTravel", cityPairHash)

      // reserve the security deposit
      // this will overwrite existing fund reservations for this wallet
      // it's here because the compiler doesn't like it being below the following block
      this.travel_reserveDeposit(owner)

      // find a demand object to match this travel with
      val cityPairHashKeyD = cityPairHash.demand_getStorageKey()
      val demandsForCityPair = Storage.get(Storage.currentContext(), cityPairHashKeyD)
      if (! demandsForCityPair.isEmpty()) {
         val repRequired = this.travel_getRepRequired()
         val carrySpaceAvailable = this.travel_getCarrySpace()
         val travelExpiry = this.travel_getExpiry()

         // do the matching
         val matchedDemand = demandsForCityPair.demands_findMatchableDemand(repRequired, carrySpaceAvailable, travelExpiry, nowTime)
         if (! matchedDemand.isEmpty()) {
            // match travel -> demand
            val matchKey = this.travel_getMatchKey()
            val timestampedMatch = matchedDemand.concat(nowTimeBytes)
            Storage.put(Storage.currentContext(), matchKey, timestampedMatch)
            log_info("CL:OK:MatchedTravelWithDemand")

            // match demand -> travel
            val otherMatchKey = matchedDemand.demand_getMatchKey()
            val timestampedOtherMatch = this.concat(nowTimeBytes)
            Storage.put(Storage.currentContext(), otherMatchKey, timestampedOtherMatch)
            log_info("CL:OK:MatchedDemandWithTravel")

            // switch the traveller's existing deposit reservation to one that expires after the demand
            // unfortunately this line must be here due to compiler troubles with anything more complex
            val demandExpiry = matchedDemand.demand_getExpiry()
            val newExpiry = demandExpiry + TRAVEL_EXTRA_EXPIRY_ON_MATCH as BigInteger
            this.travel_overwriteExpiry(owner, newExpiry)
            // it's ok to proceed if that was false. the overall state is still fine.

            // overwrite the reservation that was created with the matched demand
            // this means that we inject the traveller's script hash as the "recipient" of the reserved funds
            // when a transaction to withdraw the funds is received (multi-sig) the destination wallet must match this one
            val demandOwner = matchedDemand.demand_getOwnerScriptHash()
            val demandValue = matchedDemand.demand_getTotalValue()
            val ownerReservationList = demandOwner.wallet_getFundReservations()
            val reservationAtIdx = ownerReservationList.res_findByValue(demandValue)
            if (reservationAtIdx >= 0) {
               val rewrittenReservationList = ownerReservationList.res_replaceRecipientAt(reservationAtIdx, owner)
               demandOwner.wallet_storeFundReservations(rewrittenReservationList)
               log_debug("CL:OK:UpdatedDemandFundsReservation")

            } else {
               // the reservation could not be found!
               // todo: this has put us in a weird state which should definitely be rolled back.
               Runtime.notify("CL:ERR:ReservationForDemandNotFound")
               return false
            }
         } else if (LOG_LEVEL > 2) {
            Runtime.notify("CL:DBG:NoMatchableDemandForTravel:1")
         }
      } else if (LOG_LEVEL > 2) {
         Runtime.notify("CL:DBG:NoMatchableDemandForTravel:2")
      }
      // match or no match, this was a success.
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
      owner.wallet_reserveFunds(expiry, FEE_TRAVEL_DEPOSIT as BigInteger, owner)
   }

   /**
    * Re-reserves the deposit due by a traveller when they create a [Travel] with a revised expiry time.
    * This method also updates a wallet's "state lock" with the new expiry time.
    *
    * This method is to be used when a [Travel] has been matched with a [Demand] to match up the expiry times
    * of both objects.
    *
    * Note: Use of this method will overwrite the existing reserved funds record for the owner's wallet.
    *
    * @param owner the traveller's script hash
    * @param expiry the new expiry time
    */
   private fun Travel.travel_overwriteExpiry(owner: ScriptHash, expiry: BigInteger) {
      val deposit = BigInteger.valueOf(FEE_TRAVEL_DEPOSIT)
      owner.wallet_reserveFunds(expiry, deposit, owner)
   }

   /**
    * Finds a [Travel] in a [TravelList] that fits the given attributes.
    *
    * @param repRequired the desired reputation of the traveller
    * @param carrySpaceRequired the carry space required (scale of 1-5)
    * @param expiryLimit must expire before this limit (normally the demand's expiry time)
    * @param nowTime the current unix timestamp in seconds
    * @return the [Travel] object or an empty array if nothing found
    */
   private fun TravelList.travels_findMatchableTravel(repRequired: BigInteger, carrySpaceRequired: BigInteger,
                                                      expiryLimit: BigInteger, nowTime: Int): Travel {
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
         val travelMatchKey = travel.travel_getMatchKey()
         var ownerRep = repRequired
         if (repRequired > BigInteger.valueOf(0)) {
            val owner = travel.travel_getOwnerScriptHash()
            ownerRep = owner.wallet_getReputationScore()
         }
         if (expiry.toInt() > nowTime &&
               expiry < expiryLimit &&
               ownerRep >= repRequired &&
               carrySpaceAvailable >= carrySpaceRequired &&
               Storage.get(Storage.currentContext(), travelMatchKey).isEmpty())
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
      log_debug("CL:DBG:Travel.isMatched")
      val matchKey = this.travel_getMatchKey()
      val match = Storage.get(Storage.currentContext(), matchKey)
      return ! match.isEmpty()
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
      return this.range(TIMESTAMP_SIZE + REP_REQUIRED_SIZE + CARRY_SPACE_SIZE, SCRIPT_HASH_SIZE)
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
    * Gets the key used to store the demand for tracking lookup.
    * Tracking requires knowledge of the "identifier" and city pair.
    *
    * @param cityPairHash the ripemd160 hash of: script hash (hex) (salt) + origin city + destination city (ascii)
    * @return the lookup key for use with [Storage]
    */
   private fun Travel.travel_getLookupKey(cityPairHash: Hash160, nowTime: Int): ByteArray {
      val nowBigInt = nowTime as BigInteger
      val nowBytes = nowBigInt.toByteArray(TIMESTAMP_SIZE)
      val expiryBytes = take(this, TIMESTAMP_SIZE)
      val suffix = byteArrayOf(STORAGE_KEY_SUFFIX_TRAVEL)
      val key = nowBytes
         .concat(expiryBytes)
         .concat(suffix)
         .concat(cityPairHash)
      return key
   }

   /**
    * Gets the storage key to store a [Demand] match for this [Travel].
    *
    * @return the match key for use with [Storage]
    */
   private fun Travel.travel_getMatchKey() =
      take(this, TIMESTAMP_SIZE + REP_REQUIRED_SIZE + CARRY_SPACE_SIZE + SCRIPT_HASH_SIZE)

   /**
    * Gets the storage key for a [Travel], which is essentially just the city pair hash with a suffix.
    * This is called on the city pair hash.
    *
    * @return the storage key (city pair hash + 1 byte suffix)
    */
   private fun Hash160.travel_getStorageKey(): ByteArray {
      val travelStorageKeySuffix = byteArrayOf(STORAGE_KEY_SUFFIX_TRAVEL)
      return this.concat(travelStorageKeySuffix)
   }

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
   private fun stats_recordDemandCreation() {
      val key = STORAGE_KEY_STATS_DEMANDS
      val existingBytes = Storage.get(Storage.currentContext(), key)
      val existingCount = BigInteger(existingBytes)
      val oneBytes = byteArrayOf(1)
      val one = BigInteger(oneBytes)
      var plusOne = existingCount + one
      Storage.put(Storage.currentContext(), key, plusOne)
   }

   /**
    * Increments the counter that keeps the number of unique routes used over time.
    *
    * @see stats_getRouteUsageCount
    */
   private fun stats_recordRouteUsage(cityPairHash: Hash160): Boolean {
      val isRecorded = Storage.get(Storage.currentContext(), cityPairHash)
      if (isRecorded.isEmpty()) {
         val key = STORAGE_KEY_STATS_ROUTES
         val trueBytes = byteArrayOf(1)
         val existingBytes = Storage.get(Storage.currentContext(), key)
         // don't count this city again
         Storage.put(Storage.currentContext(), cityPairHash, trueBytes)
         // increment the counter
         val existingCount = BigInteger(existingBytes)
         val one = BigInteger(trueBytes)
         val plusOne = existingCount + one
         Storage.put(Storage.currentContext(), key, plusOne)
         return true
      }
      return false
   }

   /**
    * Increments the counter that keeps a count of all funds reserved over time.
    *
    * @see stats_getReservedFundsCount
    * @param value the funds reserved this time as a fixed8 int
    */
   private fun stats_recordReservedFunds(value: BigInteger) {
      val key = STORAGE_KEY_STATS_FUNDS
      val existingBytes = Storage.get(Storage.currentContext(), key)
      val existingCount = BigInteger(existingBytes)
      val plusValue = existingCount + value
      Storage.put(Storage.currentContext(), key, plusValue)
   }

   /**
    * Returns the number of demands created over time.
    */
   private fun stats_getDemandsCount() = BigInteger(Storage.get(Storage.currentContext(), STORAGE_KEY_STATS_DEMANDS))

   /**
    * Returns the number of unique routes used over time.
    */
   private fun stats_getRouteUsageCount() = BigInteger(Storage.get(Storage.currentContext(), STORAGE_KEY_STATS_ROUTES))

   /**
    * Returns the amount of funds reserved over time as a fixed8 int.
    */
   private fun stats_getReservedFundsCount() = BigInteger(Storage.get(Storage.currentContext(), STORAGE_KEY_STATS_FUNDS))

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

   // -=============-
   // -=  Logging  =-
   // -=============-
   //region logging

   /**
    * Dispatches a [Runtime.notify] with the specified arg at the DEBUG level (3).
    */
   private fun log_debug(msg: String) {
      if (LOG_LEVEL > 2) Runtime.notify(msg)
   }

   /**
    * Dispatches a [Runtime.notify] with the specified args at the DEBUG level (3).
    */
   private fun log_debug2(msg: String, arg: Any) {
      if (LOG_LEVEL > 2) Runtime.notify(msg, arg)
   }

   /**
    * Dispatches a [Runtime.notify] with the specified arg at INFO level (2).
    */
   private fun log_info(msg: String) {
      if (LOG_LEVEL > 1) Runtime.notify(msg)
   }

   /**
    * Dispatches a [Runtime.notify] with the specified args at INFO level (2).
    */
   private fun log_info2(msg: String, arg: Any) {
      if (LOG_LEVEL > 1) Runtime.notify(msg, arg)
   }

   /**
    * Dispatches a [Runtime.notify] with the specified args at INFO level (2).
    */
   private fun log_info3(msg: String, arg0: Any, arg1: Any) {
      if (LOG_LEVEL > 1) Runtime.notify(msg, arg0, arg1)
   }

   /**
    * Dispatches a [Runtime.notify] with the specified arg at the ERROR level (1).
    */
   private fun log_err(msg: String) {
      if (LOG_LEVEL > 0) Runtime.notify(msg)
   }

   //endregion

   // -==========-
   // -=  Misc  =-
   // -==========-
   //region misc

   /**
    * Returns the GAS asset ID as a byte array.
    */
   private fun getGasAssetId(): ByteArray {
      val gasAssetId = byteArrayOf(
         231 as Byte, 45, 40, 105, 121, 238 as Byte, 108, 177 as Byte, 183 as Byte, 230 as Byte, 93,
         253 as Byte, 223 as Byte, 178 as Byte, 227 as Byte, 132 as Byte, 16, 11, 141 as Byte,
         20, 142 as Byte, 119, 88, 222 as Byte, 66, 228 as Byte, 22, 139 as Byte, 113, 121, 44, 96)
      return gasAssetId
   }

   /**
    * Gets part 1 of the wallet script code (code before the public key), set at init time.
    */
   private fun getWalletScriptP1() = Storage.get(Storage.currentContext(), STORAGE_KEY_INIT_WALLET_P1)

   /**
    * Gets part 2 of the wallet script code (code after the public key, before the script hash), set at init time.
    */
   private fun getWalletScriptP2() = Storage.get(Storage.currentContext(), STORAGE_KEY_INIT_WALLET_P2)

   /**
    * Gets part 3 of the wallet script code (code after the script hash), set at init time.
    */
   private fun getWalletScriptP3() = Storage.get(Storage.currentContext(), STORAGE_KEY_INIT_WALLET_P3)

   /**
    * Calls "Neo.Account.GetBalance".
    *
    * @param account the [Account]
    * @param asset_id the asset to get the [Account]'s balance for
    * @return the [Account]'s balance for the specified asset
    */
   @Syscall("Neo.Account.GetBalance")
   private external fun getBalance(account: Account, asset_id: ByteArray): Long

   /**
    * Inserts the "BOOLAND" OpCode.
    * Returns true if the given args are both true.
    */
   @OpCode(org.neo.vm._OpCode.BOOLAND)
   private external fun vm_booland(arg0: Any, arg1: Any): Boolean

   /**
    * Inserts the "THROW" OpCode.
    * Aborts execution.
    */
   @OpCode(org.neo.vm._OpCode.THROW)
   private external fun vm_throw()

   /**
    * Inserts the "THROWIFNOT" OpCode.
    * Aborts execution if the supplied [arg] is not true.
    */
   @OpCode(org.neo.vm._OpCode.THROWIFNOT)
   private external fun vm_throwIfNot(arg: Any)

   //endregion
}
