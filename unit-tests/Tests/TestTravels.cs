﻿using Neo.VM;
using Xunit;
using Xunit.Abstractions;
using System.Linq;

namespace CLTests {
   public class TestTravels : Test {
      public TestTravels(ITestOutputHelper output) : base(output) { }

      readonly byte[] ScriptHash = new byte[] {
         5, 4, 3, 2, 1, 5, 4, 3, 2, 1,  // line - 10 bytes
         5, 4, 3, 2, 1, 5, 4, 3, 2,
         0xFF
      };

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

         var expected = new byte[] {
            // expiry (4 byte timestamp)
            1, 0, 0, 0,
            // repRequired
            1, 0,
            // carrySpace
            2,
         }.Concat(ScriptHash).ToArray();

         Assert.Equal(expected, result);
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

         var travel = new byte[] {
            // expiry (4 byte timestamp)
            1, 0, 0, 0,
            // repRequired
            1, 0,
            // carrySpace
            2,
         }.Concat(ScriptHash).ToArray();

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(travel);
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

         var travel = new byte[] {
            // expiry (4 byte timestamp)
            1, 0, 0, 0,
            // repRequired
            1, 0,
            // carrySpace
            2,
         }.Concat(ScriptHash).ToArray();

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(travel);
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_travel_getOwnerScriptHash");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetByteArray();
         Assert.Equal(ScriptHash, result);
      }
   }
}
