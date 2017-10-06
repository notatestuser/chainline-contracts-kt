using System;
using Neo.VM;
using Xunit;
using Xunit.Abstractions;

namespace CLTests {
   public class TestReservations : Test {
      public TestReservations(ITestOutputHelper output) : base(output) { }

      [Fact]
      public void TestCreateReservation() {
         ExecutionEngine engine = LoadContract("HubContract");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(false);  // args[3]
            sb.EmitPush(new byte[] { 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5 });  // args[2]
            sb.EmitPush(2147483647);   // args[1] - value
            sb.EmitPush(2147483647);  // args[0] - timestamp
            sb.EmitPush(4);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_reservation_create");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetByteArray();

         output.WriteLine(BitConverter.ToString(result));

         Assert.Equal(new byte[] {
            0xFF, 0xFF, 0xFF, 0x7F, // timestamp is 4 bytes
            0xFF, 0xFF, 0xFF, 0x7F, 0,  // value is 5 bytes
            1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5,
            0,  // false
         }, result);
      }

      [Fact]
      public void TestGetReservationExpiry() {
         ExecutionEngine engine = LoadContract("HubContract");

         var reservation = new byte[] {
            0xFF, 0xFF, 0xFF, 0x7F, // timestamp is 4 bytes
            0xFF, 0xFF, 0xFF, 0x7F, 0,  // value is 5 bytes
            5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1,
            0,  // false
         };

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(reservation);  // args[0]
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_reservation_getExpiry");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetBigInteger();
         Assert.Equal(2147483647, result);
      }
   }
}
