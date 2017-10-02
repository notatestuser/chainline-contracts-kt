using Neo.VM;
using Xunit;
using Xunit.Abstractions;

namespace CLTests {
   public class TestUtils : Test {
      public TestUtils(ITestOutputHelper output) : base(output) { }

      [Fact]
      public void TestArrayReverse() {
         ExecutionEngine engine = LoadContract("HubContract");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(new byte[] { 0 });
            sb.EmitPush(new byte[] { 0 });
            sb.EmitPush(new byte[] { 0 });  // arg1
            sb.EmitPush(new byte[] { 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5 });  // arg0
            sb.EmitPush("test_arrayrev");  // operation

            engine.LoadScript(sb.ToArray());
         }
         engine.Execute();
         AssertNoFaultState(engine);

         var result = engine.EvaluationStack.Peek().GetByteArray();
         Assert.Equal(new byte[] { 5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1 }, result);
      }
   }
}
