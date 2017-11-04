using System;
using Neo.Cryptography;
using Neo.VM;
using Xunit;
using Xunit.Abstractions;

namespace CLTests {
   public class TestInitialize : Test {
      public TestInitialize(ITestOutputHelper output) : base(output) { }

      [Fact]
      public void TestSetAndGetInitialize() {
         // initialize
         ExecutionEngine engine = LoadContract("HubContract");
         using (ScriptBuilder sb = new ScriptBuilder()) {
            // initialize the contract, get back the stored blobs
            sb.EmitPush(new byte[] { 7, 8, 9 });
            sb.EmitPush(new byte[] { 4, 5, 6 });
            sb.EmitPush(new byte[] { 1, 2, 3 });
            sb.EmitPush(3);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("initialize");  // operation
            ExecuteScript(engine, sb);
         }

         // retrieve
         ExecutionEngine engine2 = LoadContract("HubContract");
         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(0);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_initialize_getP1");
            ExecuteScript(engine2, sb);
         }
         var result = engine2.EvaluationStack.Peek().GetByteArray();
         Assert.Equal(new byte[] { 1, 2, 3 }, result);
      }

      [Fact]
      public void TestSetAndIsInitializedIsTrue() {
         // test is_initialized (expect false)
         ExecutionEngine engine = LoadContract("HubContract");
         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(0);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("is_initialized");
            ExecuteScript(engine, sb);
         }
         var result = engine.EvaluationStack.Peek().GetBoolean();
         Assert.False(result);

         // initialize
         ExecutionEngine engine1 = LoadContract("HubContract");
         using (ScriptBuilder sb = new ScriptBuilder()) {
            // initialize the contract, get back the stored blobs
            sb.EmitPush(new byte[] { 7, 8, 9 });
            sb.EmitPush(new byte[] { 4, 5, 6 });
            sb.EmitPush(new byte[] { 1, 2, 3 });
            sb.EmitPush(3);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("initialize");  // operation
            ExecuteScript(engine1, sb);
         }

         // test is_initialized (expect true)
         ExecutionEngine engine2 = LoadContract("HubContract");
         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(0);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("is_initialized");
            ExecuteScript(engine2, sb);
         }
         var result2 = engine2.EvaluationStack.Peek().GetBoolean();
         Assert.True(result2);
      }
   }
}
