using System;
using Neo.VM;
using Xunit;
using Xunit.Abstractions;

namespace CLTests.Utilities {
   public class VMHelper {
      public static void AssertNoFaultState(ExecutionEngine engine, ITestOutputHelper output) {
         bool hasFaulted = engine.State == VMState.FAULT;
         if (!hasFaulted) return;

         // uncomment these lines when the custom neo-vm is loaded for extra logging.
         //if (ExecutionEngine.LastException == null) {
         //   output.WriteLine("VM Fault! OpCode: {0}",
         //                    ExecutionEngine.LastOpCode.ToString());
         //} else {
         //   output.WriteLine("VM Fault! OpCode: {0}\n{1}\n{2}",
         //                    ExecutionEngine.LastOpCode.ToString(),
         //                    ExecutionEngine.LastException.Message,
         //                    ExecutionEngine.LastException.StackTrace);
         //}

         output.WriteLine("Dumping evaluation stack:");
         foreach (StackItem item in engine.EvaluationStack) {
            try {
               output.WriteLine(BitConverter.ToString(item.GetByteArray()));
            } catch (Exception) { }
         }

         Assert.False(hasFaulted, "FAULT");
      }
   }
}
