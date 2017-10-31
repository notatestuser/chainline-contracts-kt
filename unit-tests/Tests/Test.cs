using Xunit.Abstractions;
using Neo.Cryptography;
using Neo.VM;
using CLTests.Utilities;

namespace CLTests {
   public abstract class Test {
      public ITestOutputHelper Output { private set; get; }
      public ExecutionHelper ExecutionHelper { private set; get; }
      public InteropService InteropService { private set; get; }

      public Test(ITestOutputHelper output) {
         this.Output = output;
         this.ExecutionHelper = new ExecutionHelper(output);
         this.InteropService = new StateReader(output);
      }

      protected ExecutionEngine LoadContract(string contractName) {
         byte[] program = ExecutionHelper.Compile(contractName);
         var engine = new ExecutionEngine(null, Crypto.Default, null, InteropService);
         engine.LoadScript(program);
         return engine;
      }

      protected void ExecuteScript(ExecutionEngine engine, ScriptBuilder sb) {
         engine.LoadScript(sb.ToArray());
         engine.Execute();
         VMHelper.AssertNoFaultState(engine, Output);
      }
   }
}
