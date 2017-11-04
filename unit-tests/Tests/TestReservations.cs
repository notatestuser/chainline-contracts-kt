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
         }, result);
      }

      [Fact]
      public void TestGetReservationExpiry() {
         ExecutionEngine engine = LoadContract("HubContract");

         var reservation = new byte[] {
            0xFF, 0xFF, 0xFF, 0x7F, // timestamp is 4 bytes
            0xFF, 0xFF, 0xFF, 0x7F, 0,  // value is 5 bytes
            5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1,
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
      public void TestGetReservationRecipient() {
         ExecutionEngine engine = LoadContract("HubContract");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(new byte[] {
               0xFF, 0xFF, 0xFF, 0x7F, // timestamp is 4 bytes
               0xFF, 0xFF, 0xFF, 0x7F, 0,  // value is 5 bytes
               5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1,
            });
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_reservation_getRecipient");  // operation
            ExecuteScript(engine, sb);
         }

         var expected = new byte[] { 5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1 };
         var result = engine.EvaluationStack.Peek().GetByteArray();
         Assert.Equal(expected, result);
      }

      [Fact]
      public void TestGetTotalReservationHoldValue() {
         ExecutionEngine engine = LoadContract("HubContract");

         var reservations = new byte[] {
            // entry 0
            0xFF, 0xFF, 0xFF, 0x7F, // timestamp is 4 bytes
            10, 0, 0, 0, 0,  // value is 5 bytes
            5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1,
            // entry 1
            0xFF, 0xFF, 0xFF, 0x7F, // timestamp is 4 bytes
            10, 0, 0, 0, 0,  // value is 5 bytes
            5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1,
         };

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(reservations);  // args[0]
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_reservations_getReservedGasBalance");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetBigInteger();
         Assert.Equal(20, result);
      }

      [Fact]
      public void TestGetTotalReservationHoldValueWhenSomeExpired() {
         ExecutionEngine engine = LoadContract("HubContract");

         var reservations = new byte[] {
            // entry 0
            0xFF, 0xFF, 0xFF, 0x7F, // timestamp is 4 bytes
            10, 0, 0, 0, 0,  // value is 5 bytes
            5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1,
            // entry 1
            0x01, 0x00, 0x00, 0x00, // timestamp is 4 bytes
            10, 0, 0, 0, 0,  // value is 5 bytes
            5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1,
         };

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(reservations);  // args[0]
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_reservations_getReservedGasBalance");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetBigInteger();
         Assert.Equal(10, result);
      }

      [Fact]
      public void TestReservationFindBy() {
         ExecutionEngine engine = LoadContract("HubContract");

         var reservations = new byte[] {
            // entry 0
            0xFF, 0xFF, 0xFF, 0x7F, // timestamp is 4 bytes
            10, 0, 0, 0, 0,  // value is 5 bytes
            1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5,
            // entry 1
            0x01, 0x00, 0x00, 0x00, // timestamp is 4 bytes
            20, 0, 0, 0, 0,  // value is 5 bytes
            5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1,
            // entry 2
            0x02, 0x00, 0x00, 0x00, // timestamp is 4 bytes
            30, 0, 0, 0, 0,  // value is 5 bytes
            1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5,
         };

         var findByRecipient = new byte[] {
            5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1,
         };

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(findByRecipient);  // args[2]
            sb.EmitPush(20);  // args[1]
            sb.EmitPush(reservations);  // args[0]
            sb.EmitPush(3);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_reservations_findBy");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetBigInteger();
         Assert.Equal(1, result);
      }

      [Fact]
      public void TestReservationFindByNotFound() {
         ExecutionEngine engine = LoadContract("HubContract");

         var reservations = new byte[] {
            // entry 0
            0xFF, 0xFF, 0xFF, 0x7F, // timestamp is 4 bytes
            10, 0, 0, 0, 0,  // value is 5 bytes
            1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5,
            // entry 1
            0x01, 0x00, 0x00, 0x00, // timestamp is 4 bytes
            20, 0, 0, 0, 0,  // value is 5 bytes
            5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1,
            // entry 2
            0x02, 0x00, 0x00, 0x00, // timestamp is 4 bytes
            30, 0, 0, 0, 0,  // value is 5 bytes
            1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5,
         };

         var findByRecipient = new byte[] {
            5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1,
         };

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(findByRecipient);  // args[2]
            sb.EmitPush(25);  // args[1]
            sb.EmitPush(reservations);  // args[0]
            sb.EmitPush(3);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_reservations_findBy");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetBigInteger();
         Assert.Equal(-1, result);
      }

      [Fact]
      public void TestReservationReplaceRecipient() {
         ExecutionEngine engine = LoadContract("HubContract");

         var reservations = new byte[] {
            // entry 0
            0xFF, 0xFF, 0xFF, 0x7F, // timestamp is 4 bytes
            10, 0, 0, 0, 0,  // value is 5 bytes
            5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1,
            // entry 1
            0x01, 0x00, 0x00, 0x00, // timestamp is 4 bytes
            10, 0, 0, 0, 0,  // value is 5 bytes
            5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1,
         };

         var newRecipient = new byte[] {
            1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5
         };

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(newRecipient);  // args[2]
            sb.EmitPush(1);  // args[1]
            sb.EmitPush(reservations);  // args[0]
            sb.EmitPush(3);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_reservations_replaceRecipientAt");  // operation
            ExecuteScript(engine, sb);
         }

         var expected = new byte[] {
            // entry 0
            0xFF, 0xFF, 0xFF, 0x7F, // timestamp is 4 bytes
            10, 0, 0, 0, 0,  // value is 5 bytes
            5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1,
            // entry 1
            0x01, 0x00, 0x00, 0x00, // timestamp is 4 bytes
            10, 0, 0, 0, 0,  // value is 5 bytes
            1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5,
         };

         var result = engine.EvaluationStack.Peek().GetByteArray();
         Output.WriteLine(BitConverter.ToString(result));
         Assert.Equal(expected, result);
      }
   }
}
