using System;
using Neo.VM;
using Xunit.Abstractions;

namespace CLTests {
   public class StateReader : InteropService {
      ITestOutputHelper output;

      public StateReader(ITestOutputHelper output) {
         this.output = output;
         Register("Neo.Runtime.Notify", Runtime_Notify);
         Register("Neo.Runtime.Log", Runtime_Notify);
      }

      protected virtual bool Runtime_Notify(ExecutionEngine engine) {
         StackItem state = engine.EvaluationStack.Pop();
         output.WriteLine("Runtime.Notify called: {0}", state.ToString());
         return true;
      }
   }
}
