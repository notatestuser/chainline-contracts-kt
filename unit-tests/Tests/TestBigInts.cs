using Neo.VM;
using Xunit;
using Xunit.Abstractions;

namespace CLTests {
   public class TestBigInts : Test {
      public TestBigInts(ITestOutputHelper output) : base(output) { }

      [Fact]
      public void TestIntSizeByteBoundary() {
         ExecutionEngine engine = LoadContract("Testbed");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(127);  // args[0]
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_bigintsize");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetBigInteger();
         Assert.Equal(1, result);
      }

      [Fact]
      public void TestIntSizeOverByteBoundary() {
         ExecutionEngine engine = LoadContract("Testbed");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(128);  // args[0]
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_bigintsize");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetBigInteger();
         Assert.Equal(2, result);
      }

      [Fact]
      public void TestIntSizeShortBoundary() {
         ExecutionEngine engine = LoadContract("Testbed");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(32767);  // args[0]
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_bigintsize");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetBigInteger();
         Assert.Equal(2, result);
      }

      [Fact]
      public void TestIntSizeOverShortBoundary() {
         ExecutionEngine engine = LoadContract("Testbed");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(32768);  // args[0]
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_bigintsize");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetBigInteger();
         Assert.Equal(3, result);
      }

      [Fact]
      public void TestIntSize1GASSize() {
         ExecutionEngine engine = LoadContract("Testbed");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(100000000);  // 1 GAS
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_bigintsize");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetBigInteger();
         Assert.Equal(4, result);
      }

      [Fact]
      public void TestIntSizeIntBoundary() {
         ExecutionEngine engine = LoadContract("Testbed");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            // in a real transaction this is only 21.47 NEO/GAS!
            // adding another byte brings that max to ~5497.55
            sb.EmitPush(2147483647);  // args[0]
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_bigintsize");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetBigInteger();
         Assert.Equal(4, result);
      }

      [Fact]
      public void TestIntSizeOverIntBoundary() {
         ExecutionEngine engine = LoadContract("Testbed");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(2147483648);  // args[0]
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_bigintsize");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetBigInteger();
         Assert.Equal(5, result);
      }

      [Fact]
      public void TestIntPad() {
         ExecutionEngine engine = LoadContract("Testbed");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(2147483647);  // args[0]
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_bigintpad");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetBigInteger();
         Assert.Equal(2147483647, result);
      }

      [Fact]
      public void TestIntPadBytes() {
         ExecutionEngine engine = LoadContract("Testbed");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(2147483647);  // args[0]
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_bigintpad");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetByteArray();
         Assert.Equal(new byte[] { 0xFF, 0xFF, 0xFF, 0x7F, 0 }, result);
      }

      [Fact]
      public void TestSmallIntPad() {
         ExecutionEngine engine = LoadContract("Testbed");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(16);  // args[0]
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_bigintpad");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetBigInteger();
         Assert.Equal(16, result);
      }

      [Fact]
      public void TestSmallIntPadBytes() {
         ExecutionEngine engine = LoadContract("Testbed");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(16);  // args[0]
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_bigintpad");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetByteArray();
         Assert.Equal(new byte[] { 16, 0, 0, 0, 0 }, result);
      }

      [Fact]
      public void TestZeroPad() {
         ExecutionEngine engine = LoadContract("Testbed");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(0);  // args[0]
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_bigintpad");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetBigInteger();
         Assert.Equal(0, result);
      }

      [Fact]
      public void TestZeroPadBytes() {
         ExecutionEngine engine = LoadContract("Testbed");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(0);  // args[0]
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_bigintpad");  // operation
            ExecuteScript(engine, sb);
         }

         var result = engine.EvaluationStack.Peek().GetByteArray();
         Assert.Equal(new byte[] { 0, 0, 0, 0, 0 }, result);
      }
   }
}
