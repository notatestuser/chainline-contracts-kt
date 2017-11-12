using Neo.VM;
using Xunit;
using Xunit.Abstractions;
using System;
using System.Linq;

namespace CLTests {
   public class TestTravels : Test {
      public TestTravels(ITestOutputHelper output) : base(output) { }

      static readonly byte[] ScriptHash = new byte[] {
         5, 4, 3, 2, 1, 5, 4, 3, 2, 1,  // line - 10 bytes
         5, 4, 3, 2, 1, 5, 4, 3, 2,
         0xFF
      };

      static readonly byte[] Travel = new byte[] {
         // expiry (4 byte timestamp)
         1, 0, 0, 0,
         // repRequired
         1, 0,
         // carrySpace
         2,
         // owner script hash (appended)
      }.Concat(ScriptHash).ToArray();

      [Fact]
      public void TestCreateTravel() {
         ExecutionEngine engine = LoadContract("HubContract");

         // private fun travel_create(pickupCityHash: Hash160, destCityHash: Hash160,
         //                            repRequired: BigInteger, carrySpace: BigInteger): Travel {

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(2);  // args[3] - carrySpace
            sb.EmitPush(1);  // args[2] - repRequired
            sb.EmitPush(1);  // args[1] - expiry
            sb.EmitPush(ScriptHash);  // args[0] - owner
            sb.EmitPush(4);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_travel_create");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetByteArray();
         Assert.Equal(Travel, result);
      }

      [Fact]
      public void TestCreateTravelValidationCarrySpaceTooHigh() {
         ExecutionEngine engine = LoadContract("HubContract");

         // failure case: carrySpace is too high below.

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(128);  // args[3] - carrySpace
            sb.EmitPush(1);  // args[2] - repRequired
            sb.EmitPush(1);  // args[1] - expiry
            sb.EmitPush(ScriptHash);  // args[0] - owner
            sb.EmitPush(4);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_travel_create");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetByteArray();
         Assert.Equal(new byte[] { }, result);
      }

      [Fact]
      public void TestGetTravelCarrySpace() {
         ExecutionEngine engine = LoadContract("HubContract");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(Travel);
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_travel_getCarrySpace");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetBigInteger();
         Assert.Equal(2, result);
      }

      [Fact]
      public void TestGetTravelOwnerScriptHash() {
         ExecutionEngine engine = LoadContract("HubContract");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(Travel);
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_travel_getOwnerScriptHash");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetByteArray();
         Assert.Equal(ScriptHash, result);
      }

      [Fact]
      public void TestGetTravelStorageKey() {
         ExecutionEngine engine = LoadContract("HubContract");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            // input is a city pair hash160
            sb.EmitPush(ScriptHash);
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_travel_getStorageKey");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetByteArray();
         Assert.Equal(ScriptHash.Concat(new byte[] { 2 }).ToArray(), result);
      }

      [Fact]
      public void TestGetTravelLookupKey() {
         ExecutionEngine engine = LoadContract("HubContract");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            // input is a city pair hash160
            sb.EmitPush(ScriptHash);
            sb.EmitPush(Travel);
            sb.EmitPush(2);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_travel_getLookupKey");  // operation
            ExecuteScript(engine, sb);
         }
         var expected = new byte[] {
            1, 0, 0, 0,  // nowTime
            1, 0, 0, 0,  // expiry
            2  // STORAGE_KEY_SUFFIX_TRAVEL
         }.Concat(ScriptHash).ToArray();
         var result = engine.EvaluationStack.Peek().GetByteArray();
         Assert.Equal(expected, result);
      }

      [Fact]
      public void TestFindMatchableTravelPass() {
         ExecutionEngine engine = LoadContract("HubContract");

         var nowTime = 101;
         byte[] expiredExpiry = BitConverter.GetBytes(100).ToArray();
         byte[] futureExpiry = BitConverter.GetBytes(102).ToArray();

         // travel1 - already expired
         var travel1 = expiredExpiry.Concat(new byte[] {
            // expiry (4 byte timestamp) (prepended)
            // repRequired
            1, 0,
            // carrySpace
            2,
            // owner script hash (appended)
         }).Concat(ScriptHash).ToArray();

         // travel2 - carry space insufficient
         var travel2 = futureExpiry.Concat(new byte[] {
            // expiry (4 byte timestamp) (prepended)
            // repRequired
            1, 0,
            // carrySpace
            2,
            // owner script hash (appended)
         }).Concat(ScriptHash).ToArray();

         // travel3 - suitable
         var travel3 = futureExpiry.Concat(new byte[] {
            // expiry (4 byte timestamp) (prepended)
            // repRequired
            1, 0,
            // carrySpace
            3,
            // owner script hash (appended)
         }).Concat(ScriptHash).ToArray();

         var travels = travel1.Concat(travel2).Concat(travel3).ToArray();

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(nowTime);  // args[4] - nowTime
            sb.EmitPush(1500000000);  // args[3] - expiresBefore, big int test :)
            sb.EmitPush(3);  // args[2] - carrySpaceRequired
            sb.EmitPush(0);  // args[1] - repRequired
            sb.EmitPush(travels);  // args[0]
            sb.EmitPush(5);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_travel_findMatchableTravel");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetByteArray();
         Assert.Equal(travel3, result);
      }

      [Fact]
      public void TestFindMatchableTravelWhenFirstIsMatched() {
         var nowTime = 101;
         byte[] expiredExpiry = BitConverter.GetBytes(100).ToArray();
         byte[] futureExpiry = BitConverter.GetBytes(102).ToArray();

         // travel1 - suitable but matched :(
         var travel1 = futureExpiry.Concat(new byte[] {
            // expiry (4 byte timestamp) (prepended)
            // repRequired
            1, 0,
            // carrySpace
            3,
            // owner script hash (appended)
         }).Concat(ScriptHash).ToArray();

         // travel2 - suitable, unmatched
         var travel2 = futureExpiry.Concat(new byte[] {
            // expiry (4 byte timestamp) (prepended)
            // repRequired
            1, 0,
            // carrySpace
            4,  // different to travel1 above
            // owner script hash (appended)
         }).Concat(ScriptHash).ToArray();

         var travels = travel1.Concat(travel2).ToArray();

         ExecutionEngine engine1 = LoadContract("HubContract");
         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(new byte[] { 1 });  // args[1] - value
            sb.EmitPush(travel1);  // args[0] - key
            sb.EmitPush(2);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_storage_put");  // operation
            ExecuteScript(engine1, sb);
         }

         ExecutionEngine engine2 = LoadContract("HubContract");
         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(nowTime);  // args[4] - nowTime
            sb.EmitPush(1500000000);  // args[3] - expiresBefore, big int test :)
            sb.EmitPush(3);  // args[2] - carrySpaceRequired
            sb.EmitPush(0);  // args[1] - repRequired
            sb.EmitPush(travels);  // args[0]
            sb.EmitPush(5);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_travel_findMatchableTravel");  // operation
            ExecuteScript(engine2, sb);
         }

         var result = engine2.EvaluationStack.Peek().GetByteArray();
         Assert.Equal(travel2, result);
      }

      [Fact]
      public void TestFindMatchableTravelFail() {
         ExecutionEngine engine = LoadContract("HubContract");

         var nowTime = 101;
         byte[] expiredExpiry = BitConverter.GetBytes(100).ToArray();
         byte[] futureExpiry = BitConverter.GetBytes(102).ToArray();

         // travel1 - already expired
         var travel1 = expiredExpiry.Concat(new byte[] {
            // expiry (4 byte timestamp) (prepended)
            // repRequired
            1, 0,
            // carrySpace
            2,
            // owner script hash (appended)
         }).Concat(ScriptHash).ToArray();

         // travel2 - expires after supplied `expiresBefore`
         var travel2 = futureExpiry.Concat(new byte[] {
            // expiry (4 byte timestamp) (prepended)
            // repRequired
            1, 0,
            // carrySpace
            3,
            // owner script hash (appended)
         }).Concat(ScriptHash).ToArray();

         var travels = travel1.Concat(travel2).ToArray();

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(nowTime);  // args[4] - nowTime
            sb.EmitPush(101);  // args[3] - expiresBefore
            sb.EmitPush(3);  // args[2] - carrySpaceRequired
            sb.EmitPush(0);  // args[1] - repRequired
            sb.EmitPush(travels);  // args[0]
            sb.EmitPush(5);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_travel_findMatchableTravel");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetByteArray();
         Assert.Equal(new byte[] {}, result);
      }
   }
}
