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

      protected ExecutionEngine LoadContract(string contractName) {
         byte[] program = executionHelper.Compile(contractName);
         var engine = new ExecutionEngine(null, new Crypto());
         engine.LoadScript(program);
         return engine;
      }

      protected void ExecuteScript(ExecutionEngine engine, ScriptBuilder sb) {
         engine.LoadScript(sb.ToArray());
         engine.Execute();
         VMHelper.AssertNoFaultState(engine, output);
      }
   }
}
