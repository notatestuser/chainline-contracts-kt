using Neo.VM;
using Xunit;
using Xunit.Abstractions;
using System;
using System.Linq;

namespace CLTests {
   public class TestDemands : Test {
      public TestDemands(ITestOutputHelper output) : base(output) { }

      readonly byte[] ScriptHash = new byte[] {
         5, 4, 3, 2, 1, 5, 4, 3, 2, 1,  // line - 10 bytes
         5, 4, 3, 2, 1, 5, 4, 3, 2,
         0xFF
      };

      readonly byte[] Info = new byte[] {
         1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2,  // line - 32 bytes
         1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2,
         1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2,
         1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1,
         0xFF
      };

      [Fact]
      public void TestCreateDemand() {
         ExecutionEngine engine = LoadContract("HubContract");

         // private fun demand_create(cityHash: Hash160, repRequired: BigInteger, itemSize: BigInteger,
         //                            itemValue: BigInteger, info: ByteArray): Demand {

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(Info);  // args[5] - info
            sb.EmitPush(100000000);  // args[4] - itemValue
            sb.EmitPush(1);  // args[3] - itemSize
            sb.EmitPush(2);  // args[2] - repRequired
            sb.EmitPush(1);  // args[1] - expiry
            sb.EmitPush(ScriptHash);  // args[0] - owner
            sb.EmitPush(6);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_demand_create");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetByteArray();

         var expected = new byte[] {
            // expiry (4 byte timestamp)
            1, 0, 0, 0,
            // itemValue (100000000)
            0x00, 0xE1, 0xF5, 0x05, 0x00,
            // owner script hash
            5, 4, 3, 2, 1, 5, 4, 3, 2, 1,  // line - 10 bytes
            5, 4, 3, 2, 1, 5, 4, 3, 2,
            0xFF,
            // repRequired
            2, 0,
            // itemSize
            1,
            // info
         }.Concat(Info).ToArray();

         Assert.Equal(expected, result);
      }

      [Fact]
      public void TestCreateDemandValidationValueTooHigh() {
         ExecutionEngine engine = LoadContract("HubContract");

         // failure case: itemValue is too high below.

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(Info);  // args[5] - info
            sb.EmitPush(550000000000);  // args[4] - itemValue
            sb.EmitPush(1);  // args[3] - itemSize
            sb.EmitPush(1);  // args[2] - repRequired
            sb.EmitPush(1);  // args[1] - expiry
            sb.EmitPush(ScriptHash);  // args[0] - owner
            sb.EmitPush(6);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_demand_create");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetByteArray();
         Assert.Equal(new byte[] { }, result);
      }

      [Fact]
      public void TestCreateDemandValidationItemSizeTooHigh() {
         ExecutionEngine engine = LoadContract("HubContract");

         // failure case: itemValue is too high below.

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(Info);  // args[5] - info
            sb.EmitPush(100000000);  // args[4] - itemValue
            sb.EmitPush(128);  // args[3] - itemSize (max is 127)
            sb.EmitPush(1);  // args[2] - repRequired
            sb.EmitPush(1);  // args[1] - expiry
            sb.EmitPush(ScriptHash);  // args[0] - owner
            sb.EmitPush(6);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_demand_create");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetByteArray();
         Assert.Equal(new byte[] { }, result);
      }

      [Fact]
      public void TestGetDemandItemValue() {
         ExecutionEngine engine = LoadContract("HubContract");

         var demand = new byte[] {
            // expiry (4 byte timestamp)
            1, 0, 0, 0,
            // itemValue (100000000)
            0x00, 0xE1, 0xF5, 0x05, 0x00,
            // owner script hash
            5, 4, 3, 2, 1, 5, 4, 3, 2, 1,  // line - 10 bytes
            5, 4, 3, 2, 1, 5, 4, 3, 2,
            0xFF,
            // repRequired
            2, 0,
            // itemSize
            1,
            // info
         }.Concat(Info).ToArray();

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(demand);
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_demand_getItemValue");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetBigInteger();

         Assert.Equal(100000000, result);
      }

      [Fact]
      public void TestGetDemandInfoBlob() {
         ExecutionEngine engine = LoadContract("HubContract");

         var demand = new byte[] {
            // expiry (4 byte timestamp)
            1, 0, 0, 0,
            // itemValue (100000000)
            0x00, 0xE1, 0xF5, 0x05, 0x00,
            // owner script hash
            5, 4, 3, 2, 1, 5, 4, 3, 2, 1,  // line - 10 bytes
            5, 4, 3, 2, 1, 5, 4, 3, 2,
            0xFF,
            // repRequired
            2, 0,
            // itemSize
            1,
            // info
         }.Concat(Info).ToArray();

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(demand);
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_demand_getInfoBlob");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetByteArray();
         Assert.Equal(Info, result);
      }

      [Fact]
      public void TestFindMatchableDemand() {
         ExecutionEngine engine = LoadContract("HubContract");

         var nowTime = 101;
         byte[] expiredExpiry = BitConverter.GetBytes(100).ToArray();
         byte[] futureExpiry = BitConverter.GetBytes(102).ToArray();

         // demand1 - already expired
         var demand1 = expiredExpiry.Concat(new byte[] {
            // expiry (4 byte timestamp) (prepended)
            // itemValue (100000000)
            0x00, 0xE1, 0xF5, 0x05, 0x00,
            // owner script hash
            5, 4, 3, 2, 1, 5, 4, 3, 2, 1,  // line - 10 bytes
            5, 4, 3, 2, 1, 5, 4, 3, 2,
            0xFF,
            // repRequired
            1, 0,
            // itemSize
            1
            // info
         }).Concat(Info).ToArray();

         // demand2 - item too large
         var demand2 = futureExpiry.Concat(new byte[] {
            // expiry (4 byte timestamp) (prepended)
            // itemValue (100000000)
            0x00, 0xE1, 0xF5, 0x05, 0x00,
            // owner script hash
            5, 4, 3, 2, 1, 5, 4, 3, 2, 1,  // line - 10 bytes
            5, 4, 3, 2, 1, 5, 4, 3, 2,
            0xFF,
            // repRequired
            1, 0,
            // itemSize
            2
            // info
         }).Concat(Info).ToArray();

         // demand3 - suitable
         var demand3 = futureExpiry.Concat(new byte[] {
            // expiry (4 byte timestamp) (prepended)
            // itemValue (100000000)
            0x00, 0xE1, 0xF5, 0x05, 0x00,
            // owner script hash
            5, 4, 3, 2, 1, 5, 4, 3, 2, 1,  // line - 10 bytes
            5, 4, 3, 2, 1, 5, 4, 3, 2,
            0xFF,
            // repRequired
            1, 0,
            // itemSize
            1
            // info
         }).Concat(Info).ToArray();

         var demands = demand1.Concat(demand2).Concat(demand3).ToArray();

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(nowTime);  // args[3] - nowTime
            sb.EmitPush(1);  // args[2] - carrySpace
            sb.EmitPush(0);  // args[1] - repRequired
            sb.EmitPush(demands);  // args[0]
            sb.EmitPush(4);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_demand_findMatchableDemand");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetByteArray();
         Assert.Equal(demand3, result);
      }
   }
}
