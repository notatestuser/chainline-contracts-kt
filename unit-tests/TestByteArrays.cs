using Neo.VM;
using Xunit;
using Xunit.Abstractions;

namespace CLTests {
   public class TestSanity : Test {
      public TestSanity(ITestOutputHelper output) : base(output) { }

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
   }
}
