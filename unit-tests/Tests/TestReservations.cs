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
            sb.EmitPush(new byte[] { 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5 });  // args[2]
            sb.EmitPush(2147483647);   // args[1] - value
            sb.EmitPush(2147483647);  // args[0] - timestamp
            sb.EmitPush(3);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_reservation_create");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetByteArray();

         Assert.Equal(new byte[] {
            0xFF, 0xFF, 0xFF, 0x7F, // timestamp is 4 bytes
            0xFF, 0xFF, 0xFF, 0x7F, 0,  // value is 5 bytes
            1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5,
            0,  // false
         }, result);
      }

      [Fact]
      public void TestGetAttribute() {
         ExecutionEngine engine = LoadContract("HubContract");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(new byte[] {
               0xFF, 0xFF, 0xFF, 0x7F, // timestamp is 4 bytes
               0xFF, 0xFF, 0xFF, 0x7F, 0,  // value is 5 bytes
               5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1,
               1,  // true
            });
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_reservation_isMultiSigUnlocked");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetBoolean();

         Assert.True(result);
      }

      [Fact]
      public void TestGetAttribute2() {
         ExecutionEngine engine = LoadContract("HubContract");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(new byte[] {
               0xFF, 0xFF, 0xFF, 0x7F, // timestamp is 4 bytes
               0xFF, 0xFF, 0xFF, 0x7F, 0,  // value is 5 bytes
               5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1,
               0,  // true
            });
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_reservation_isMultiSigUnlocked");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetBoolean();

         Assert.False(result);
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

      [Fact]
      public void TestGetTotalReservationHoldValue() {
         ExecutionEngine engine = LoadContract("HubContract");

         var reservations = new byte[] {
            // entry 1
            0xFF, 0xFF, 0xFF, 0x7F, // timestamp is 4 bytes
            10, 0, 0, 0, 0,  // value is 5 bytes
            5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1,
            0,  // false
            // entry 2
            0xFF, 0xFF, 0xFF, 0x7F, // timestamp is 4 bytes
            10, 0, 0, 0, 0,  // value is 5 bytes
            5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1,
            0,  // false
         };

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(reservations);  // args[0]
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_reservation_getTotalOnHoldValue");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetBigInteger();
         Assert.Equal(20, result);
      }

      [Fact]
      public void TestGetTotalReservationHoldValueWhenSomeExpired() {
         ExecutionEngine engine = LoadContract("HubContract");

         var reservations = new byte[] {
            // entry 1
            0xFF, 0xFF, 0xFF, 0x7F, // timestamp is 4 bytes
            10, 0, 0, 0, 0,  // value is 5 bytes
            5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1,
            0,  // false
            // entry 2
            0x01, 0x00, 0x00, 0x00, // timestamp is 4 bytes
            10, 0, 0, 0, 0,  // value is 5 bytes
            5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1,
            0,  // false
         };

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(reservations);  // args[0]
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_reservation_getTotalOnHoldValue");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetBigInteger();
         Assert.Equal(10, result);
      }
   }
}
