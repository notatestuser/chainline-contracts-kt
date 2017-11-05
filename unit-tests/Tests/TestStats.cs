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

      public void TestCityUsageStatsZero() {
         ExecutionEngine engine = LoadContract("HubContract");

         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(0);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("stats_getCityUsageCount");
            ExecuteScript(engine, sb);
         }
         var result = engine.EvaluationStack.Peek().GetBigInteger();
         Assert.Equal(0, result);
      }

      [Fact]
      public void TestCityUsageStatsNonZero() {
         ExecutionEngine engine = LoadContract("HubContract");
         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(new byte[] { 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5 });
            sb.EmitPush(1);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("test_stats_recordCityUsage");  // operation
            ExecuteScript(engine, sb);
         }
         ExecutionEngine engine2 = LoadContract("HubContract");
         using (ScriptBuilder sb = new ScriptBuilder()) {
            sb.EmitPush(0);
            sb.Emit(OpCode.PACK);
            sb.EmitPush("stats_getCityUsageCount");
            ExecuteScript(engine2, sb);
         }
         var result = engine2.EvaluationStack.Peek().GetBigInteger();
         Assert.Equal(1, result);
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
