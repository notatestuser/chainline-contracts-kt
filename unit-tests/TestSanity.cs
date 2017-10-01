using CLTests.Utilities;
using Neo.Cryptography;
using Neo.VM;
using Xunit;
using Xunit.Abstractions;

namespace CLTests {
   public class TestSanity {
      private readonly ITestOutputHelper output;
      private ExecutionHelper executionHelper;

      public TestSanity(ITestOutputHelper output) {
         this.output = output;
         this.executionHelper = new ExecutionHelper(output);
      }

      [Fact]
      public void TestByteArrayEquality() {
         byte[] program = executionHelper.Compile("HubContract");
         var engine = new ExecutionEngine(null, Crypto.Default);
         engine.LoadScript(program);

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(0);  // arg3
            sb.EmitPush(new byte[] { });  // arg2
            sb.EmitPush(new byte[] { 1, 2, 3, 4, 5 });  // arg1
            sb.EmitPush(new byte[] { 1, 2, 3, 4, 5 });  // arg0
            sb.EmitPush("test_arrayeq");  // operation

            engine.LoadScript(sb.ToArray());
         }

         engine.Execute();
         Assert.False(engine.State == VMState.FAULT, "FAULT");

         bool result = engine.EvaluationStack.Peek().GetBoolean();
         Assert.True(result);
      }

      [Fact]
      public void TestByteArrayEqualityFalse() {
         byte[] program = executionHelper.Compile("HubContract");
         var engine = new ExecutionEngine(null, Crypto.Default);
         engine.LoadScript(program);

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(0);  // arg3
            sb.EmitPush(new byte[] { });  // arg2
            sb.EmitPush(new byte[] { 5, 4, 3, 2, 1 });  // arg1
            sb.EmitPush(new byte[] { 1, 2, 3, 4, 5 });  // arg0
            sb.EmitPush("test_arrayeq");  // operation

            engine.LoadScript(sb.ToArray());
         }

         engine.Execute();
         Assert.False(engine.State == VMState.FAULT, "FAULT");

         bool result = engine.EvaluationStack.Peek().GetBoolean();
         Assert.False(result);
      }

      [Fact]
      public void TestByteArrayInequality() {
         byte[] program = executionHelper.Compile("HubContract");
         var engine = new ExecutionEngine(null, Crypto.Default);
         engine.LoadScript(program);

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(0);  // arg3
            sb.EmitPush(new byte[] { });  // arg2
            sb.EmitPush(new byte[] { 5, 4, 3, 2, 1 });  // arg1
            sb.EmitPush(new byte[] { 1, 2, 3, 4, 5 });  // arg0
            sb.EmitPush("test_arrayneq");  // operation

            engine.LoadScript(sb.ToArray());
         }

         engine.Execute();
         Assert.False(engine.State == VMState.FAULT, "FAULT");

         bool result = engine.EvaluationStack.Peek().GetBoolean();
         Assert.True(result);
      }

      [Fact]
      public void TestByteArrayInequalityFalse() {
         byte[] program = executionHelper.Compile("HubContract");
         var engine = new ExecutionEngine(null, Crypto.Default);
         engine.LoadScript(program);

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(0);  // arg3
            sb.EmitPush(new byte[] { });  // arg2
            sb.EmitPush(new byte[] { 1, 2, 3, 4, 5 });  // arg1
            sb.EmitPush(new byte[] { 1, 2, 3, 4, 5 });  // arg0
            sb.EmitPush("test_arrayneq");  // operation

            engine.LoadScript(sb.ToArray());
         }

         engine.Execute();
         Assert.False(engine.State == VMState.FAULT, "FAULT");

         bool result = engine.EvaluationStack.Peek().GetBoolean();
         Assert.False(result);
      }
   }
}
