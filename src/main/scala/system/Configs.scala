// See LICENSE.SiFive for license details.
// See LICENSE.Berkeley for license details.

package freechips.rocketchip.system

import Chisel._
import freechips.rocketchip.config.Config
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.debug.{IncludeJtagDTM, JtagDTMKey}
import freechips.rocketchip.diplomacy._

class WithJtagDTMSystem extends freechips.rocketchip.subsystem.WithJtagDTM
class WithDebugSBASystem extends freechips.rocketchip.subsystem.WithDebugSBA

class BaseConfig extends Config(
  new WithDefaultMemPort() ++
  new WithDefaultMMIOPort() ++
  new WithDefaultSlavePort() ++
  new WithTimebase(BigInt(1000000)) ++ // 1 MHz
  new WithDTS("freechips,rocketchip-unknown", Nil) ++
  new WithNExtTopInterrupts(2) ++
  new BaseSubsystemConfig()
)

class DefaultConfig extends Config(new WithNBigCores(1) ++ new BaseConfig)

class DefaultBufferlessConfig extends Config(
  new WithBufferlessBroadcastHub ++ new WithNBigCores(1) ++ new BaseConfig)

class DefaultSmallConfig extends Config(new WithNSmallCores(1) ++ new BaseConfig)
class DefaultRV32Config extends Config(new WithRV32 ++ new DefaultConfig)

class DualBankConfig extends Config(
  new WithNBanksPerMemChannel(2) ++ new BaseConfig)

class DualChannelConfig extends Config(new WithNMemoryChannels(2) ++ new BaseConfig)

class DualChannelDualBankConfig extends Config(
  new WithNMemoryChannels(2) ++
  new WithNBanksPerMemChannel(2) ++ new BaseConfig)

class RoccExampleConfig extends Config(new WithRoccExample ++ new DefaultConfig)

class Edge128BitConfig extends Config(
  new WithEdgeDataBits(128) ++ new BaseConfig)
class Edge32BitConfig extends Config(
  new WithEdgeDataBits(32) ++ new BaseConfig)

class SingleChannelBenchmarkConfig extends Config(new DefaultConfig)
class DualChannelBenchmarkConfig extends Config(new WithNMemoryChannels(2) ++ new SingleChannelBenchmarkConfig)
class QuadChannelBenchmarkConfig extends Config(new WithNMemoryChannels(4) ++ new SingleChannelBenchmarkConfig)
class OctoChannelBenchmarkConfig extends Config(new WithNMemoryChannels(8) ++ new SingleChannelBenchmarkConfig)

class EightChannelConfig extends Config(new WithNMemoryChannels(8) ++ new BaseConfig)

class DualCoreConfig extends Config(
  new WithNBigCores(2) ++ new BaseConfig)

class TinyConfig extends Config(
  new WithNoMemPort ++
  new WithNMemoryChannels(0) ++
  new WithIncoherentTiles ++
  new With1TinyCore ++
  new BaseConfig)

class MemPortOnlyConfig extends Config(
  new WithNoMMIOPort ++
  new WithNoSlavePort ++
  new DefaultConfig
)

class MMIOPortOnlyConfig extends Config(
  new WithNoSlavePort ++
  new WithNoMemPort ++
  new WithNMemoryChannels(0) ++
  new WithIncoherentTiles ++
  new WithScratchpadsOnly ++
  new DefaultConfig
)

class BaseFPGAConfig extends Config(new BaseConfig)

class DefaultFPGAConfig extends Config(new WithNSmallCores(1) ++ new BaseFPGAConfig)
class DefaultFPGASmallConfig extends Config(new DefaultFPGAConfig)

//*****************************************************
//TH
//*****************************************************
class MixedCoresConfig extends Config(new WithMixedRV64imacf(2, 4, 4, 4, 1, 1) ++ new BaseConfig)

class MixedCoresFPGAConfig extends Config(new WithMixedRV64imacf(2, 4, 4, 4, 1, 1) ++ new BaseFPGAConfig)

// Exploration
class cSC4kL1RV64imac   extends Config(new WithNSmallRV64imac(1, 1, 1)  ++ new BaseConfig)
class cSC16kL1RV64imac  extends Config(new WithNBigRV64imac(1, 4, 4)    ++ new BaseConfig)
class cSC64kL1RV64imac  extends Config(new WithNBigRV64imac(1, 16, 16)  ++ new BaseConfig)

class cSC16kL1RV64imacf extends Config(new WithNBigRV64imacf(1, 4, 4)   ++ new BaseConfig)
class cSC64kL1RV64imacf extends Config(new WithNBigRV64imacf(1, 16, 16) ++ new BaseConfig)

// SiFive reference
class E31 extends Config(
	new WithNoMemPort ++
	new WithNMemoryChannels(0) ++
	new WithIncoherentTiles ++
	new With1E31RV32IMAC
	++ new BaseConfig)

class E51 extends Config(
	new WithNoMemPort ++
	new WithNMemoryChannels(0) ++
	new WithIncoherentTiles ++
	new With1E51RV64IMAC
	++ new BaseConfig)

class E32 extends Config(new WithNE31RV32IMAC(2) ++ new BaseConfig)
class E34 extends Config(new WithNE31RV32IMAC(4) ++ new BaseConfig)

class E52 extends Config(new WithNE51RV64IMAC(2) ++ new BaseConfig)
class E54 extends Config(new WithNE51RV64IMAC(4) ++ new BaseConfig)
