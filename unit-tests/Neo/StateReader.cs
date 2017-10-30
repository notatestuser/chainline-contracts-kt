using Neo;
using Neo.Core;
using Neo.VM;
using System.Collections;
using System.Text;
using Xunit.Abstractions;

namespace CLTests {
   public class DummyHeader : IInteropInterface {}

    class CustomStorageContext : IInteropInterface
    {
        public UInt160 ScriptHash;

        public Hashtable data;

        public CustomStorageContext()
        {
            data = new Hashtable();
        }

        public byte[] ToArray()
        {
            return ScriptHash.ToArray();
        }
    }

   public class StateReader : InteropService {
      ITestOutputHelper output;

      CustomStorageContext storageContext;

      public StateReader(ITestOutputHelper output) {
         this.output = output;
         this.storageContext = new CustomStorageContext();
         Register("Neo.Blockchain.GetHeader", Blockchain_GetHeader);
         Register("Neo.Header.GetTimestamp", Header_GetTimestamp);
         Register("Neo.Runtime.CheckWitness", Runtime_CheckWitness);
         Register("Neo.Runtime.Notify", Runtime_Notify);
         Register("Neo.Runtime.Log", Runtime_Notify);
         Register("Neo.Storage.GetContext", Storage_GetContext);
         Register("Neo.Storage.Get", Storage_Get);
         Register("Neo.Storage.Put", Storage_Put);
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

      protected virtual bool Runtime_CheckWitness(ExecutionEngine engine)
      {
         return true;
      }

      protected virtual bool Runtime_Notify(ExecutionEngine engine) {
         engine.EvaluationStack.Pop();
         return true;
      }

      public bool Storage_GetContext(ExecutionEngine engine)
      {
         this.storageContext.ScriptHash = new UInt160(engine.CurrentContext.ScriptHash);
         engine.EvaluationStack.Push(StackItem.FromInterface(this.storageContext));
         return true;
      }

      protected bool Storage_Get(ExecutionEngine engine)
      {
         CustomStorageContext context = engine.EvaluationStack.Pop().GetInterface<CustomStorageContext>();
         byte[] key = engine.EvaluationStack.Pop().GetByteArray();
         StorageItem item = new StorageItem
         {
               Value = (byte[])context.data[Encoding.UTF8.GetString(key)]
         };
         engine.EvaluationStack.Push(item?.Value ?? new byte[0]);
         return true;
      }

      protected bool Storage_Put(ExecutionEngine engine)
      {
         CustomStorageContext context = engine.EvaluationStack.Pop().GetInterface<CustomStorageContext>();
         byte[] key = engine.EvaluationStack.Pop().GetByteArray();
         if (key.Length > 1024) return false;
         byte[] value = engine.EvaluationStack.Pop().GetByteArray();
         context.data[Encoding.UTF8.GetString(key)] = value;
         return true;
      }
   }
}
