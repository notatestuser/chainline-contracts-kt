using Neo.VM;
using Xunit;
using Xunit.Abstractions;

namespace CLTests {
   public class TestByteArrays : Test {
      public TestByteArrays(ITestOutputHelper output) : base(output) { }

      [Fact]
      public void TestNullBytes() {
         ExecutionEngine engine = LoadContract("Testbed");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(0);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_null");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetByteArray();
         Assert.Equal(new byte[] {}, result);
      }

      [Fact]
      public void TestNullIsEmpty() {
         ExecutionEngine engine = LoadContract("Testbed");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(0);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_null_isEmpty");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetBoolean();
         Assert.True(result);
      }

      [Fact]
      public void TestTrueBytes() {
         ExecutionEngine engine = LoadContract("Testbed");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(0);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_true");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetByteArray();
         Assert.Equal(new byte[] { 1 }, result);
      }

      [Fact]
      public void TestFalseBytes() {
         ExecutionEngine engine = LoadContract("Testbed");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(0);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_false");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetByteArray();
         Assert.Equal(new byte[] {}, result);
      }

      [Fact]
      public void TestField() {
         ExecutionEngine engine = LoadContract("Testbed");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(0);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_field");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetString();
         Assert.Equal("To the moon!", result);
      }

      [Fact]
      public void TestByteArrayEquality() {
         ExecutionEngine engine = LoadContract("Testbed");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(new byte[] { 1, 2, 3, 4, 5 });  // args[1]
            sb.EmitPush(new byte[] { 1, 2, 3, 4, 5 });  // args[0]
            sb.EmitPush(2);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_arrayeq");  // operation
            ExecuteScript(engine, sb);
         }

         bool result = engine.EvaluationStack.Peek().GetBoolean();
         Assert.True(result);
      }

      [Fact]
      public void TestByteArrayEqualityFalse() {
         ExecutionEngine engine = LoadContract("Testbed");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(new byte[] { 5, 4, 3, 2, 1 });  // args[1]
            sb.EmitPush(new byte[] { 1, 2, 3, 4, 5 });  // args[0]
            sb.EmitPush(2);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_arrayeq");  // operation
            ExecuteScript(engine, sb);
         }

         bool result = engine.EvaluationStack.Peek().GetBoolean();
         Assert.False(result);
      }

      [Fact]
      public void TestByteArrayInequality() {
         ExecutionEngine engine = LoadContract("Testbed");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(new byte[] { 5, 4, 3, 2, 1 });  // args[1]
            sb.EmitPush(new byte[] { 1, 2, 3, 4, 5 });  // args[0]
            sb.EmitPush(2);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_arrayneq");  // operation
            ExecuteScript(engine, sb);
         }

         bool result = engine.EvaluationStack.Peek().GetBoolean();
         Assert.True(result);
      }

      [Fact]
      public void TestByteArrayInequalityFalse() {
         ExecutionEngine engine = LoadContract("Testbed");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(new byte[] { 1, 2, 3, 4, 5 });  // args[1]
            sb.EmitPush(new byte[] { 1, 2, 3, 4, 5 });  // args[0]
            sb.EmitPush(2);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_arrayneq");  // operation
            ExecuteScript(engine, sb);
         }

         bool result = engine.EvaluationStack.Peek().GetBoolean();
         Assert.False(result);
      }

      [Fact]
      public void TestByteArraySubstr() {
         ExecutionEngine engine = LoadContract("Testbed");

         var randomBlob = new byte[] {
            0xFF, 0xFF, 0xFF, 0x7F,
            10, 0, 0, 0, 0,
            5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1,
            0,
         };

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(5);
            sb.EmitPush(4);
            sb.EmitPush(randomBlob);  // args[1]
            sb.EmitPush(3);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_substr");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetByteArray();
         Assert.Equal(new byte[] { 10, 0, 0, 0, 0 }, result);
      }
   }
}
