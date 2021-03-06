﻿using Neo.VM;
using Xunit;
using Xunit.Abstractions;

namespace CLTests {
   public class TestUtils : Test {
      public TestUtils(ITestOutputHelper output) : base(output) { }

      [Fact]
      public void TestArrayReverse() {
         ExecutionEngine engine = LoadContract("Testbed");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(new byte[] { 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5 });  // args[0]
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_arrayrev");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetByteArray();
         Assert.Equal(new byte[] { 5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1 }, result);
      }
   }
}
