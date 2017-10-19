using System;
using Neo.VM;
using Xunit.Abstractions;

namespace CLTests {
   public class DummyHeader : IInteropInterface {}

   public class StateReader : InteropService {
      ITestOutputHelper output;

      public StateReader(ITestOutputHelper output) {
         this.output = output;
         Register("Neo.Blockchain.GetHeader", Blockchain_GetHeader);
         Register("Neo.Header.GetTimestamp", Header_GetTimestamp);
         Register("Neo.Runtime.Notify", Runtime_Notify);
         Register("Neo.Runtime.Log", Runtime_Notify);
      }

      protected virtual bool Blockchain_GetHeader(ExecutionEngine engine) {
         engine.EvaluationStack.Pop();
         engine.EvaluationStack.Push(StackItem.FromInterface(new DummyHeader()));
         return true;
      }

      protected virtual bool Header_GetTimestamp(ExecutionEngine engine) {
         engine.EvaluationStack.Push((int)1000);
         return true;
      }

      protected virtual bool Runtime_Notify(ExecutionEngine engine) {
         StackItem state = engine.EvaluationStack.Pop();
         output.WriteLine("Runtime.Notify called: {0}", state.ToString());
         return true;
      }
   }
}
