using Neo.VM;
using Xunit;
using Xunit.Abstractions;

namespace CLTests.Utilities {
   public class VMHelper {
      public static void AssertNoFaultState(ExecutionEngine engine, ITestOutputHelper output) {
         bool hasFaulted = engine.State == VMState.FAULT;
         output.WriteLine("VM Fault! OpCode: {0}\n{1}\n{2}",
                          ExecutionEngine.LastOpCode.ToString(),
                          ExecutionEngine.LastException.Message,
                          ExecutionEngine.LastException.StackTrace);
         Assert.False(hasFaulted, "FAULT");
      }
   }
}
