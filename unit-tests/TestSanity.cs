using Neo.VM;
using Xunit;
using Xunit.Abstractions;

namespace CLTests {
   public class TestSanity : Test {
      public TestSanity(ITestOutputHelper output) : base(output) { }

      [Fact]
      public void TestByteArrayEquality() {
         ExecutionEngine engine = LoadContract("HubContract");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(new byte[] { 1, 2, 3, 4, 5 });  // args[1]
            sb.EmitPush(new byte[] { 1, 2, 3, 4, 5 });  // args[0]
            sb.EmitPush(2);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_arrayeq");  // operation

            engine.LoadScript(sb.ToArray());
         }
         engine.Execute();
         AssertNoFaultState(engine);

         bool result = engine.EvaluationStack.Peek().GetBoolean();
         Assert.True(result);
      }

      [Fact]
      public void TestByteArrayEqualityFalse() {
         ExecutionEngine engine = LoadContract("HubContract");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(new byte[] { 5, 4, 3, 2, 1 });  // args[1]
            sb.EmitPush(new byte[] { 1, 2, 3, 4, 5 });  // args[0]
            sb.EmitPush(2);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_arrayeq");  // operation

            engine.LoadScript(sb.ToArray());
         }
         engine.Execute();
         AssertNoFaultState(engine);

         bool result = engine.EvaluationStack.Peek().GetBoolean();
         Assert.False(result);
      }

      [Fact]
      public void TestByteArrayInequality() {
         ExecutionEngine engine = LoadContract("HubContract");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(new byte[] { 5, 4, 3, 2, 1 });  // args[1]
            sb.EmitPush(new byte[] { 1, 2, 3, 4, 5 });  // args[0]
            sb.EmitPush(2);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_arrayneq");  // operation

            engine.LoadScript(sb.ToArray());
         }
         engine.Execute();
         AssertNoFaultState(engine);

         bool result = engine.EvaluationStack.Peek().GetBoolean();
         Assert.True(result);
      }

      [Fact]
      public void TestByteArrayInequalityFalse() {
         ExecutionEngine engine = LoadContract("HubContract");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(new byte[] { 1, 2, 3, 4, 5 });  // args[1]
            sb.EmitPush(new byte[] { 1, 2, 3, 4, 5 });  // args[0]
            sb.EmitPush(2);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_arrayneq");  // operation

            engine.LoadScript(sb.ToArray());
         }
         engine.Execute();
         AssertNoFaultState(engine);

         bool result = engine.EvaluationStack.Peek().GetBoolean();
         Assert.False(result);
      }

      [Fact]
      public void TestIntSizeByteBoundary() {
         ExecutionEngine engine = LoadContract("HubContract");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(127);  // args[0]
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_bigintsize");  // operation

            engine.LoadScript(sb.ToArray());
         }
         engine.Execute();
         AssertNoFaultState(engine);

         var result = engine.EvaluationStack.Peek().GetBigInteger();
         Assert.Equal(1, result);
      }

      [Fact]
      public void TestIntSizeOverByteBoundary() {
         ExecutionEngine engine = LoadContract("HubContract");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(128);  // args[0]
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_bigintsize");  // operation

            engine.LoadScript(sb.ToArray());
         }
         engine.Execute();
         AssertNoFaultState(engine);

         var result = engine.EvaluationStack.Peek().GetBigInteger();
         Assert.Equal(2, result);
      }

      [Fact]
      public void TestIntSizeShortBoundary() {
         ExecutionEngine engine = LoadContract("HubContract");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(32767);  // args[0]
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_bigintsize");  // operation

            engine.LoadScript(sb.ToArray());
         }
         engine.Execute();
         AssertNoFaultState(engine);

         var result = engine.EvaluationStack.Peek().GetBigInteger();
         Assert.Equal(2, result);
      }

      [Fact]
      public void TestIntSizeOverShortBoundary() {
         ExecutionEngine engine = LoadContract("HubContract");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(32768);  // args[0]
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_bigintsize");  // operation

            engine.LoadScript(sb.ToArray());
         }
         engine.Execute();
         AssertNoFaultState(engine);

         var result = engine.EvaluationStack.Peek().GetBigInteger();
         Assert.Equal(3, result);
      }
   }
}
