using Xunit.Abstractions;
using Neo.VM;
using CLTests.Utilities;

namespace CLTests {
   public abstract class Test {
      public ITestOutputHelper output { private set; get; }
      public ExecutionHelper executionHelper { private set; get; }

      public Test(ITestOutputHelper output) {
         this.output = output;
         this.executionHelper = new ExecutionHelper(output);
      }

      protected void AssertNoFaultState(ExecutionEngine engine) {
         VMHelper.AssertNoFaultState(engine, output);
      }
   }
}
