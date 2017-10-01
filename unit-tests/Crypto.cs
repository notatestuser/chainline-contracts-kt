using System;
using Neo.VM;

namespace CLTests {
   public class Crypto : ICrypto {
      public Crypto() {
      }

      public byte[] Hash160(byte[] message) {
         throw new NotImplementedException();
      }

      public byte[] Hash256(byte[] message) {
         throw new NotImplementedException();
      }

      public bool VerifySignature(byte[] message, byte[] signature, byte[] pubkey) {
         throw new NotImplementedException();
      }
   }
}
