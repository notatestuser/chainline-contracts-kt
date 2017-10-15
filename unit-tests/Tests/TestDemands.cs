using Neo.VM;
using Xunit;
using Xunit.Abstractions;
using System.Linq;

namespace CLTests {
   public class TestDemands : Test {
      public TestDemands(ITestOutputHelper output) : base(output) { }

      readonly byte[] info = new byte[] {
         1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2,  // 32 bytes
         1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2,
         1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2,
         1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1,
         0xFF
      };

      readonly byte[] cityHash = new byte[] {
         5, 4, 3, 2, 1, 5, 4, 3, 2, 1,  // 10 bytes
         5, 4, 3, 2, 1, 5, 4, 3, 2,
         0xFF
      };

      [Fact]
      public void TestCreateDemand() {
         ExecutionEngine engine = LoadContract("HubContract");

         // private fun demand_create(cityHash: Hash160, repRequired: BigInteger, itemSize: BigInteger,
         //                            itemValue: BigInteger, info: ByteArray): Demand {

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(info);  // args[4] - info
            sb.EmitPush(100000000);  // args[3] - itemValue
            sb.EmitPush(1);  // args[2] - itemSize
            sb.EmitPush(2);   // args[1] - repRequired
            sb.EmitPush(cityHash);  // args[0] - cityHash
            sb.EmitPush(5);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_demand_create");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetByteArray();

         var expected = new byte[] {
            // cityHash
            5, 4, 3, 2, 1, 5, 4, 3, 2, 1,  // 10 bytes
            5, 4, 3, 2, 1, 5, 4, 3, 2,
            0xFF,
            // repRequired
            2, 0,
            // itemSize
            1,
            // itemValue (100000000)
            0x00, 0xE1, 0xF5, 0x05, 0x00
            // info
         }.Concat(info).ToArray();

         Assert.Equal(expected, result);
      }

      [Fact]
      public void TestCreateDemandValidationValueTooHigh() {
         ExecutionEngine engine = LoadContract("HubContract");

         // failure case: itemValue is too high below.

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(info);  // args[4] - info
            sb.EmitPush(550000000000);  // args[3] - itemValue
            sb.EmitPush(1);  // args[2] - itemSize
            sb.EmitPush(1);   // args[1] - repRequired
            sb.EmitPush(cityHash);  // args[0] - cityHash
            sb.EmitPush(5);
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
            sb.EmitPush(info);  // args[4] - info
            sb.EmitPush(100000000);  // args[3] - itemValue
            sb.EmitPush(128);  // args[2] - itemSize (max is 127)
            sb.EmitPush(1);   // args[1] - repRequired
            sb.EmitPush(cityHash);  // args[0] - cityHash
            sb.EmitPush(5);
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
            // cityHash
            5, 4, 3, 2, 1, 5, 4, 3, 2, 1,  // 10 bytes
            5, 4, 3, 2, 1, 5, 4, 3, 2,
            0xFF,
            // repRequired
            2, 0,
            // itemSize
            1,
            // itemValue (100000000)
            0x00, 0xE1, 0xF5, 0x05, 0x00
            // info
         }.Concat(info).ToArray();

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
            // cityHash
            5, 4, 3, 2, 1, 5, 4, 3, 2, 1,  // 10 bytes
            5, 4, 3, 2, 1, 5, 4, 3, 2,
            0xFF,
            // repRequired
            2, 0,
            // itemSize
            1,
            // itemValue (100000000)
            0x00, 0xE1, 0xF5, 0x05, 0x00
            // info
         }.Concat(info).ToArray();

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(demand);
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_demand_getInfoBlob");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetByteArray();
         Assert.Equal(info, result);
      }
   }
}
