using Neo.VM;
using Xunit;
using Xunit.Abstractions;

namespace CLTests {
   public class TestSanity : Test {
      public TestSanity(ITestOutputHelper output) : base(output) { }

      [Fact]
      public void TestByteArrayEquality() {
         byte[] program = executionHelper.Compile("HubContract");
         var engine = new ExecutionEngine(null, new Crypto());
         engine.LoadScript(program);

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(new byte[] { 1, 2, 3, 4, 5 });  // arg1
            sb.EmitPush(new byte[] { 1, 2, 3, 4, 5 });  // arg0
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
         byte[] program = executionHelper.Compile("HubContract");
         var engine = new ExecutionEngine(null, new Crypto());
         engine.LoadScript(program);

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(new byte[] { 5, 4, 3, 2, 1 });  // arg1
            sb.EmitPush(new byte[] { 1, 2, 3, 4, 5 });  // arg0
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
         byte[] program = executionHelper.Compile("HubContract");
         var engine = new ExecutionEngine(null, new Crypto());
         engine.LoadScript(program);

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(new byte[] { 5, 4, 3, 2, 1 });  // arg1
            sb.EmitPush(new byte[] { 1, 2, 3, 4, 5 });  // arg0
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
         byte[] program = executionHelper.Compile("HubContract");
         var engine = new ExecutionEngine(null, new Crypto());
         engine.LoadScript(program);

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(new byte[] { 1, 2, 3, 4, 5 });  // arg1
            sb.EmitPush(new byte[] { 1, 2, 3, 4, 5 });  // arg0
            sb.EmitPush("test_arrayneq");  // operation

            engine.LoadScript(sb.ToArray());
         }

         engine.Execute();
         AssertNoFaultState(engine);

         bool result = engine.EvaluationStack.Peek().GetBoolean();
         Assert.False(result);
      }
   }
}
