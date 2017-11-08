using Neo.VM;
using Xunit;
using Xunit.Abstractions;
using System;
using System.Linq;

namespace CLTests {
   public class TestStats : Test {
      public TestStats(ITestOutputHelper output) : base(output) { }

      [Fact]
      public void TestGetDemandStatsZero() {
         ExecutionEngine engine = LoadContract("HubContract");
         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(0);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("stats_getDemandsCount");
            ExecuteScript(engine, sb);
         }
         var result = engine.EvaluationStack.Peek().GetBigInteger();
         Assert.Equal(0, result);
      }

      [Fact]
      public void TestGetDemandStatsNonZero() {
         ExecutionEngine engine = LoadContract("HubContract");
         using (ScriptBuilder sb = new ScriptBuilder()) {
            // record a demand, get back the recorded stat
            sb.EmitPush(0);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_stats_recordDemandCreation");  // operation
            ExecuteScript(engine, sb);
         }
         ExecutionEngine engine2 = LoadContract("HubContract");
         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(0);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("stats_getDemandsCount");
            ExecuteScript(engine2, sb);
         }

         var result = engine2.EvaluationStack.Peek().GetBigInteger();
         Assert.Equal(1, result);
      }

      public void TestRouteUsageStatsZero() {
         ExecutionEngine engine = LoadContract("HubContract");
         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(0);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("stats_getRouteUsageCount");
            ExecuteScript(engine, sb);
         }
         var result = engine.EvaluationStack.Peek().GetBigInteger();
         Assert.Equal(0, result);
      }

      [Fact]
      public void TestRouteUsageStatsNonZero() {
         ExecutionEngine engine = LoadContract("HubContract");
         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(new byte[] { 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5 });
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_stats_recordRouteUsage");  // operation
            ExecuteScript(engine, sb);
         }
         ExecutionEngine engine2 = LoadContract("HubContract");
         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(0);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("stats_getRouteUsageCount");
            ExecuteScript(engine2, sb);
         }
         var result = engine2.EvaluationStack.Peek().GetBigInteger();
         Assert.Equal(1, result);
      }

      [Fact]
      public void TestRouteUsageStatsTwo() {
         ExecutionEngine engine = LoadContract("HubContract");
         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(new byte[] { 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5 });
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_stats_recordRouteUsage");  // operation
            ExecuteScript(engine, sb);
         }
         ExecutionEngine engine2 = LoadContract("HubContract");
         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(new byte[] { 5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1 });
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_stats_recordRouteUsage");  // operation
            ExecuteScript(engine2, sb);
         }
         ExecutionEngine engine3 = LoadContract("HubContract");
         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(0);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("stats_getRouteUsageCount");
            ExecuteScript(engine3, sb);
         }
         var result = engine3.EvaluationStack.Peek().GetBigInteger();
         Assert.Equal(2, result);
      }

      [Fact]
      public void TestReservedFundsStatsZero() {
         ExecutionEngine engine = LoadContract("HubContract");
         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(0);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("stats_getReservedFundsCount");
            ExecuteScript(engine, sb);
         }
         var result = engine.EvaluationStack.Peek().GetBigInteger();
         Assert.Equal(0, result);
      }
   }
}
