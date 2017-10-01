using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using Xunit.Abstractions;

namespace CLTests.Utilities {
   class ExecutionHelper {
      private const string BasePath = "../../../../";

      private static readonly object Lock = new Object();
      private static Dictionary<string, byte[]> Cache = new Dictionary<string, byte[]>();
      private ITestOutputHelper output;

      public ExecutionHelper(ITestOutputHelper output) {
         this.output = output;
      }

      internal class ConvertTask {
         public static bool Execute(string path, ITestOutputHelper output) {
            output.WriteLine("Working directory is {0}", Path.GetFullPath(path));

            ProcessStartInfo pinfo = new ProcessStartInfo {
               FileName = "make",
               Arguments = "build-avms",
               WorkingDirectory = path,
               UseShellExecute = false,
               RedirectStandardInput = false,
               RedirectStandardOutput = true,
               RedirectStandardError = true,
               CreateNoWindow = true,
               StandardOutputEncoding = System.Text.Encoding.UTF8
            };

            Process p = Process.Start(pinfo);
            string stdout = p.StandardOutput.ReadToEnd();
            string stderr = p.StandardError.ReadToEnd();
            p.WaitForExit();
            output.WriteLine(stdout);
            output.WriteLine(stderr);

            if (p.ExitCode == 0)
               return true;
            return false;
         }
      }

      public byte[] Compile(string contractName) {
         lock (Lock) {
            if (Cache.ContainsKey(contractName))
               if (Cache.TryGetValue(contractName, out byte[] cachedBytes))
                  return cachedBytes;

            Console.WriteLine("Building AVMs at: " + BasePath);

            if (!ConvertTask.Execute(BasePath, output)) {
               throw new Exception("Compile task failed!");
            }

            string avmPath = Path.GetFullPath(
                              Path.Combine(BasePath, $"{contractName}.avm"));
            Console.WriteLine("Using AVM at: " + avmPath);

            byte[] bytes = File.ReadAllBytes(avmPath);
            Cache.Add(contractName, bytes);

            return bytes;
         }
      }
   }
}
