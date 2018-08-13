// See LICENSE.SiFive for license details.
// See LICENSE.Berkeley for license details.

package freechips.rocketchip.subsystem

import Chisel._
import freechips.rocketchip.config._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket._
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._

class BaseSubsystemConfig extends Config ((site, here, up) => {
  // Tile parameters
  case PgLevels => if (site(XLen) == 64) 3 /* Sv39 */ else 2 /* Sv32 */
  case XLen => 64 // Applies to all cores
  case MaxHartIdBits => log2Up(site(RocketTilesKey).size)
  // Interconnect parameters
  case SystemBusKey => SystemBusParams(beatBytes = site(XLen)/8, blockBytes = site(CacheBlockBytes))
  case PeripheryBusKey => PeripheryBusParams(beatBytes = site(XLen)/8, blockBytes = site(CacheBlockBytes))
  case MemoryBusKey => MemoryBusParams(beatBytes = site(XLen)/8, blockBytes = site(CacheBlockBytes))
  case FrontBusKey => FrontBusParams(beatBytes = site(XLen)/8, blockBytes = site(CacheBlockBytes))
  // Additional device Parameters
  case ErrorParams => ErrorParams(Seq(AddressSet(0x3000, 0xfff)), maxAtomic=site(XLen)/8, maxTransfer=4096)
  case BootROMParams => BootROMParams(contentFileName = "./bootrom/bootrom.img")
  case DebugModuleParams => DefaultDebugModuleParams(site(XLen))
  case CLINTKey => Some(CLINTParams())
  case PLICKey => Some(PLICParams())
})

/* Composable partial function Configs to set individual parameters */
// BigCore default params
class WithNBigCores(n: Int) extends Config((site, here, up) => {
  case RocketTilesKey => {
    val big = RocketTileParams(
      core   = RocketCoreParams(
				mulDiv = Some(MulDivParams(
        mulUnroll = 8,
        mulEarlyOut = true,
        divEarlyOut = true))),
      dcache = Some(DCacheParams(
        rowBits = site(SystemBusKey).beatBits,
				nSets = 64, nWays = 4, nTLBEntries = 32,				//ref: 16KB DCache
        nMSHRs = 0,
        blockBytes = site(CacheBlockBytes))),
      icache = Some(ICacheParams(
        rowBits = site(SystemBusKey).beatBits,
				nSets = 64, nWays = 4, nTLBEntries = 32,				//ref: 16KB ICache, nTLBEntries=32
        blockBytes = site(CacheBlockBytes))))
    List.tabulate(n)(i => big.copy(hartId = i))
  }
})

class WithNSmallCores(n: Int) extends Config((site, here, up) => {
  case RocketTilesKey => {
    val small = RocketTileParams(
      core = RocketCoreParams(useVM = false, fpu = None),
      btb = None,
      dcache = Some(DCacheParams(
        rowBits = site(SystemBusKey).beatBits,
        nSets = 64,																		// ref: 4KB DCache
        nWays = 1,
        nTLBEntries = 4,
        nMSHRs = 0,
        blockBytes = site(CacheBlockBytes))),
      icache = Some(ICacheParams(
        rowBits = site(SystemBusKey).beatBits,
        nSets = 64,																		// ref: 4KB ICache
        nWays = 1,
        nTLBEntries = 4,
        blockBytes = site(CacheBlockBytes))))
    List.tabulate(n)(i => small.copy(hartId = i))
  }
})

class With1TinyCore extends Config((site, here, up) => {
  case XLen => 32
  case RocketTilesKey => List(RocketTileParams(
      core = RocketCoreParams(
        useVM = false,
        fpu = None,
        mulDiv = Some(MulDivParams(mulUnroll = 8))),
      btb = None,
      dcache = Some(DCacheParams(
        rowBits = site(SystemBusKey).beatBits,
        nSets = 256, // 16KB scratchpad
        nWays = 1,
        nTLBEntries = 4,
        nMSHRs = 0,
        blockBytes = site(CacheBlockBytes),
        scratch = Some(0x80000000L))),
      icache = Some(ICacheParams(
        rowBits = site(SystemBusKey).beatBits,
        nSets = 64,		//4KB ID
        nWays = 1,
        nTLBEntries = 4,
        blockBytes = site(CacheBlockBytes)))))
  case RocketCrossingKey => List(RocketCrossingParams(
    crossingType = SynchronousCrossing(),
    master = TileMasterPortParams()
  ))
})

class WithNBanksPerMemChannel(n: Int) extends Config((site, here, up) => {
  case BankedL2Key => up(BankedL2Key, site).copy(nBanksPerChannel = n)
})

class WithNTrackersPerBank(n: Int) extends Config((site, here, up) => {
  case BroadcastKey => up(BroadcastKey, site).copy(nTrackers = n)
})

// This is the number of icache sets for all Rocket tiles
class WithL1ICacheSets(sets: Int) extends Config((site, here, up) => {
  case RocketTilesKey => up(RocketTilesKey, site) map { r =>
    r.copy(icache = r.icache.map(_.copy(nSets = sets))) }
})

// This is the number of icache sets for all Rocket tiles
class WithL1DCacheSets(sets: Int) extends Config((site, here, up) => {
  case RocketTilesKey => up(RocketTilesKey, site) map { r =>
    r.copy(dcache = r.dcache.map(_.copy(nSets = sets))) }
})

class WithL1ICacheWays(ways: Int) extends Config((site, here, up) => {
  case RocketTilesKey => up(RocketTilesKey, site) map { r =>
    r.copy(icache = r.icache.map(_.copy(nWays = ways)))
  }
})

class WithL1DCacheWays(ways: Int) extends Config((site, here, up) => {
  case RocketTilesKey => up(RocketTilesKey, site) map { r =>
    r.copy(dcache = r.dcache.map(_.copy(nWays = ways)))
  }
})

class WithCacheBlockBytes(linesize: Int) extends Config((site, here, up) => {
  case CacheBlockBytes => linesize
})

class WithBufferlessBroadcastHub extends Config((site, here, up) => {
  case BroadcastKey => up(BroadcastKey, site).copy(bufferless = true)
})

/**
 * WARNING!!! IGNORE AT YOUR OWN PERIL!!!
 *
 * There is a very restrictive set of conditions under which the stateless
 * bridge will function properly. There can only be a single tile. This tile
 * MUST use the blocking data cache (L1D_MSHRS == 0) and MUST NOT have an
 * uncached channel capable of writes (i.e. a RoCC accelerator).
 *
 * This is because the stateless bridge CANNOT generate probes, so if your
 * system depends on coherence between channels in any way,
 * DO NOT use this configuration.
 */
class WithIncoherentTiles extends Config((site, here, up) => {
  case RocketCrossingKey => up(RocketCrossingKey, site) map { r =>
    r.copy(master = r.master.copy(cork = Some(true)))
  }
  case BankedL2Key => up(BankedL2Key, site).copy(coherenceManager = { subsystem =>
    val ww = LazyModule(new TLWidthWidget(subsystem.sbus.beatBytes)(subsystem.p))
    (ww.node, ww.node, () => None)
  })
})

class WithRV32 extends Config((site, here, up) => {
  case XLen => 32
  case RocketTilesKey => up(RocketTilesKey, site) map { r =>
    r.copy(core = r.core.copy(
      fpu = r.core.fpu.map(_.copy(fLen = 32)),
      mulDiv = Some(MulDivParams(mulUnroll = 8))))
  }
})

class WithNonblockingL1(nMSHRs: Int) extends Config((site, here, up) => {
  case RocketTilesKey => up(RocketTilesKey, site) map { r =>
    r.copy(dcache = r.dcache.map(_.copy(nMSHRs = nMSHRs)))
  }
})

class WithNBreakpoints(hwbp: Int) extends Config ((site, here, up) => {
  case RocketTilesKey => up(RocketTilesKey, site) map { r =>
    r.copy(core = r.core.copy(nBreakpoints = hwbp))
  }
})

class WithRoccExample extends Config((site, here, up) => {
  case BuildRoCC => List(
    (p: Parameters) => {
        val accumulator = LazyModule(new AccumulatorExample(OpcodeSet.custom0, n = 4)(p))
        accumulator
    },
    (p: Parameters) => {
        val translator = LazyModule(new TranslatorExample(OpcodeSet.custom1)(p))
        translator
    },
    (p: Parameters) => {
        val counter = LazyModule(new CharacterCountExample(OpcodeSet.custom2)(p))
        counter
    })
})

class WithDefaultBtb extends Config((site, here, up) => {
  case RocketTilesKey => up(RocketTilesKey, site) map { r =>
    r.copy(btb = Some(BTBParams()))
  }
})

class WithFastMulDiv extends Config((site, here, up) => {
  case RocketTilesKey => up(RocketTilesKey, site) map { r =>
    r.copy(core = r.core.copy(mulDiv = Some(
      MulDivParams(mulUnroll = 8, mulEarlyOut = (site(XLen) > 32), divEarlyOut = true)
  )))}
})

class WithoutMulDiv extends Config((site, here, up) => {
  case RocketTilesKey => up(RocketTilesKey, site) map { r =>
    r.copy(core = r.core.copy(mulDiv = None))
  }
})

class WithoutFPU extends Config((site, here, up) => {
  case RocketTilesKey => up(RocketTilesKey, site) map { r =>
    r.copy(core = r.core.copy(fpu = None))
  }
})

class WithFPUWithoutDivSqrt extends Config((site, here, up) => {
  case RocketTilesKey => up(RocketTilesKey, site) map { r =>
    r.copy(core = r.core.copy(fpu = r.core.fpu.map(_.copy(divSqrt = false))))
  }
})

class WithBootROMFile(bootROMFile: String) extends Config((site, here, up) => {
  case BootROMParams => up(BootROMParams, site).copy(contentFileName = bootROMFile)
})

class WithSynchronousRocketTiles extends Config((site, here, up) => {
  case RocketCrossingKey => up(RocketCrossingKey, site) map { r =>
    r.copy(crossingType = SynchronousCrossing())
  }
})

class WithAsynchronousRocketTiles(depth: Int, sync: Int) extends Config((site, here, up) => {
  case RocketCrossingKey => up(RocketCrossingKey, site) map { r =>
    r.copy(crossingType = AsynchronousCrossing(depth, sync))
  }
})

class WithRationalRocketTiles extends Config((site, here, up) => {
  case RocketCrossingKey => up(RocketCrossingKey, site) map { r =>
    r.copy(crossingType = RationalCrossing())
  }
})

class WithEdgeDataBits(dataBits: Int) extends Config((site, here, up) => {
  case MemoryBusKey => up(MemoryBusKey, site).copy(beatBytes = dataBits/8)
  case ExtIn => up(ExtIn, site).map(_.copy(beatBytes = dataBits/8))

})

class WithJtagDTM extends Config ((site, here, up) => {
  case IncludeJtagDTM => true
})

class WithDebugSBA extends Config ((site, here, up) => {
  case DebugModuleParams => up(DebugModuleParams).copy(hasBusMaster = true)
})

class WithNBitPeripheryBus(nBits: Int) extends Config ((site, here, up) => {
  case PeripheryBusKey => up(PeripheryBusKey, site).copy(beatBytes = nBits/8)
})

class WithoutTLMonitors extends Config ((site, here, up) => {
  case MonitorsEnabled => false
})

class WithNExtTopInterrupts(nExtInts: Int) extends Config((site, here, up) => {
  case NExtTopInterrupts => nExtInts
})

class WithNMemoryChannels(n: Int) extends Config((site, here, up) => {
  case BankedL2Key => up(BankedL2Key, site).copy(nMemoryChannels = n)
})

class WithExtMemSize(n: Long) extends Config((site, here, up) => {
  case ExtMem => up(ExtMem, site).map(_.copy(size = n))
})

class WithDTS(model: String, compat: Seq[String]) extends Config((site, here, up) => {
  case DTSModel => model
  case DTSCompat => compat
})

class WithTimebase(hertz: BigInt) extends Config((site, here, up) => {
  case DTSTimebase => hertz
})

class WithDefaultMemPort extends Config((site, here, up) => {
  case ExtMem => Some(MasterPortParams(
                      base = x"8000_0000",
                      size = x"1000_0000",
                      beatBytes = site(MemoryBusKey).beatBytes,
                      idBits = 4))
})

class WithNoMemPort extends Config((site, here, up) => {
  case ExtMem => None
})

class WithDefaultMMIOPort extends Config((site, here, up) => {
  case ExtBus => Some(MasterPortParams(
                      base = x"6000_0000",
                      size = x"2000_0000",
                      beatBytes = site(MemoryBusKey).beatBytes,
                      idBits = 4))
})

class WithNoMMIOPort extends Config((site, here, up) => {
  case ExtBus => None
})

class WithDefaultSlavePort extends Config((site, here, up) => {
  case ExtIn  => Some(SlavePortParams(beatBytes = 8, idBits = 8, sourceBits = 4))
})

class WithNoSlavePort extends Config((site, here, up) => {
  case ExtIn => None
})

class WithScratchpadsOnly extends Config((site, here, up) => {
  case RocketTilesKey => up(RocketTilesKey, site) map { r =>
    r.copy(
      core = r.core.copy(useVM = false),
      dcache = r.dcache.map(_.copy(
        nSets = 256, // 16Kb scratchpad
        nWays = 1,
        scratch = Some(0x80000000L))))
  }
})

//*****************************************************
// TH:
//*****************************************************
class WithNBigRV64imac(n: Int, l1iNWays: Int, l1dNWays: Int) extends Config((site, here, up) => {
  case RocketTilesKey => {
    val big = RocketTileParams(
      core   = RocketCoreParams(
				mulDiv = Some(MulDivParams(mulUnroll = 8, mulEarlyOut = true, divEarlyOut = true)),
				fpu = None),
      dcache = Some(DCacheParams(
				nSets = 64, nWays = l1dNWays, //ref: l1dNWays = 4 -> 16KB L1D
        nTLBEntries = 32, nMSHRs = 0,
				rowBits = site(SystemBusKey).beatBits, blockBytes = site(CacheBlockBytes))),
      icache = Some(ICacheParams(
				nSets = 64, nWays = l1iNWays, //ref: l1dNWays =4 -> 16KB L1I
        nTLBEntries = 32,
				rowBits = site(SystemBusKey).beatBits, blockBytes = site(CacheBlockBytes))))
    List.tabulate(n)(i => big.copy(hartId = i))
  }
})

//====
class WithNBigRV64imacf(n: Int, l1iNWays: Int, l1dNWays: Int) extends Config((site, here, up) => {
  case RocketTilesKey => {
    val big = RocketTileParams(
      core   = RocketCoreParams(
				mulDiv = Some(MulDivParams(mulUnroll = 8, mulEarlyOut = true, divEarlyOut = true)),
				fpu = Some(FPUParams())	// default FPU params: fLen=64, divSqrt=true, sfmaLatency=3, dfmaLatency=4
			),
      dcache = Some(DCacheParams(
				nSets = 64, nWays = l1dNWays, //ref: l1dNWays = 4 -> 16KB L1D
        nTLBEntries = 32, nMSHRs = 0,
				rowBits = site(SystemBusKey).beatBits, blockBytes = site(CacheBlockBytes))),
      icache = Some(ICacheParams(
				nSets = 64, nWays = l1iNWays,  //ref: l1dNWays =4 -> 16KB L1I
        nTLBEntries = 32,
				rowBits = site(SystemBusKey).beatBits, blockBytes = site(CacheBlockBytes))))
    List.tabulate(n)(i => big.copy(hartId = i))
  }
})

class WithNSmallRV64imac(n: Int, l1iNWays: Int, l1dNWays: Int) extends Config((site, here, up) => {
  case RocketTilesKey => {
    val small = RocketTileParams(
      core = RocketCoreParams(useVM = false, fpu = None),
      btb = None,
      dcache = Some(DCacheParams(
        nSets = 64,	nWays = l1dNWays,																		// ref: l1dNWays=1 -> 4KB L1D
        nTLBEntries = 4, nMSHRs = 0,
				rowBits = site(SystemBusKey).beatBits, blockBytes = site(CacheBlockBytes))),
      icache = Some(ICacheParams(
        nSets = 64, nWays = l1iNWays,																		// ref: l1iNWays=1 -> 4KB L1I
        nTLBEntries = 4,
        rowBits = site(SystemBusKey).beatBits, blockBytes = site(CacheBlockBytes))))
    List.tabulate(n)(i => small.copy(hartId = i))
  }
})
//====
class WithMixedRV64imacf(n: Int, m: Int,
												 l1iNWaysBC: Int, l1dNWaysBC: Int,
												 l1iNWaysSC: Int, l1dNWaysSC: Int) extends Config((site, here, up) => {
  case RocketTilesKey => {
    val big = RocketTileParams(
      core   = RocketCoreParams(
				mulDiv = Some(MulDivParams(mulUnroll = 8, mulEarlyOut = true, divEarlyOut = true)),
				fpu = Some(FPUParams())	// default FPU params: fLen=64, divSqrt=true, sfmaLatency=3, dfmaLatency=4
			),
      dcache = Some(DCacheParams(
				nSets = 64, nWays = l1dNWaysBC,
        nTLBEntries = 32, nMSHRs = 0,
        rowBits = site(SystemBusKey).beatBits, blockBytes = site(CacheBlockBytes)
			)),
      icache = Some(ICacheParams(
				nSets = 64, nWays = l1iNWaysBC,
				nTLBEntries = 32,
        rowBits = site(SystemBusKey).beatBits, blockBytes = site(CacheBlockBytes)
			))
		)
    val small = RocketTileParams(
      core = RocketCoreParams(
				useVM = false, fpu = None
			),
      btb = None,
      dcache = Some(DCacheParams(
        nSets = 64, nWays = l1dNWaysSC,
        nTLBEntries = 4,
        nMSHRs = 0,
        rowBits = site(SystemBusKey).beatBits, blockBytes = site(CacheBlockBytes)
			)),
      icache = Some(ICacheParams(
        nSets = 64, nWays = l1iNWaysSC,
        nTLBEntries = 4,
        rowBits = site(SystemBusKey).beatBits, blockBytes = site(CacheBlockBytes))
			)
		)
    List.tabulate(n)(i => big.copy(hartId = i))++
    List.tabulate(m)(i => small.copy(hartId = i+n))
  }
})

//====
class With1E31RV32IMAC extends Config((site, here, up) => {	// isa = RV32IMAC
  case XLen => 32                                                    // RV32
	case RocketTilesKey => List(RocketTileParams(
    core = RocketCoreParams(
      useVM = false,
      fpu = None,
			useAtomics = true, 							                						   // A
			useCompressed = true,                           						   // C
      mulDiv = Some(MulDivParams(                                    // M
				mulUnroll = 8, divUnroll = 32,
        mulEarlyOut = true, divEarlyOut = true)),
      nLocalInterrupts = 16,
			nBreakpoints = 2,
      nPMPs = 8,
			nPerfCounters = 6,																						// 0-6
			haveBasicCounters = true
    ),
    //btb = None,
    btb = Some(BTBParams(
			nEntries=28,
			bhtParams=Some(BHTParams(nEntries=512))
		)),
    dcache = Some(DCacheParams(
      rowBits = site(SystemBusKey).beatBits,
      nSets = 1024, nWays = 1,    		// 32KB L1D scratchpad as SPM (nWays=1)
      nTLBEntries = 4,
      nMSHRs = 0,
      blockBytes = site(CacheBlockBytes),	// Cache line size = 64B
      scratch = Some(0x80000000L)			    // 0x80000000L -> start address of application binary
		)),
    icache = Some(ICacheParams(
      rowBits = site(SystemBusKey).beatBits,
      nSets = 128, nWays = 2,					// 16KB L1I
      nTLBEntries = 4,
      blockBytes = site(CacheBlockBytes)
		))
	))
  case RocketCrossingKey => List(RocketCrossingParams(
    crossingType = SynchronousCrossing(),
    master = TileMasterPortParams(cork = Some(true))
  ))
})

//====
class With1E51RV64IMAC extends Config((site, here, up) => {	// isa = RV64IMAC
  case XLen => 64                                                      // RV64
	case RocketTilesKey => List(RocketTileParams(
    core = RocketCoreParams(
      useVM = false,
      fpu = None,
			useAtomics = true, 							                						   // A
			useCompressed = true,                           						   // C
      mulDiv = Some(MulDivParams(                                    // M
				mulUnroll = 8, divUnroll = 32,
        mulEarlyOut = true, divEarlyOut = true)),
      nLocalInterrupts = 16,
			nBreakpoints = 2,
      nPMPs = 8,
			nPerfCounters = 6,																						// 0-6
			haveBasicCounters = true
    ),
    //btb = None,
    btb = Some(BTBParams(
			nEntries=28,
			bhtParams=Some(BHTParams(nEntries=512))
		)),
    dcache = Some(DCacheParams(
      rowBits = site(SystemBusKey).beatBits,
      nSets = 1024, nWays = 1,    			// 64KB L1D scratchpad as SPM (nWays=1)
      nTLBEntries = 4,
      nMSHRs = 0,
      blockBytes = site(CacheBlockBytes),	// Cache line size = 64B
      scratch = Some(0x80000000L)			// 0x80000000L -> start address of application binary
		)),
    icache = Some(ICacheParams(
      rowBits = site(SystemBusKey).beatBits,
      nSets = 128, nWays = 2,					// 16KB L1I
      nTLBEntries = 4,
      blockBytes = site(CacheBlockBytes)
		))
	))
  case RocketCrossingKey => List(RocketCrossingParams(
    crossingType = SynchronousCrossing(),
    master = TileMasterPortParams(cork = Some(true))
  ))
})

//====
class WithNE31RV32IMAC(n: Int) extends Config((site, here, up) => {	// isa = RV32IMAC
  case XLen => 32                                                      // RV32
	case RocketTilesKey => {
  	val mcE31 = RocketTileParams(
      core = RocketCoreParams(
        useVM = false,
        fpu = None,
				useAtomics = true, 							                						   // A
				useCompressed = true,                           						   // C
        mulDiv = Some(MulDivParams(                                    // M
					mulUnroll = 8, divUnroll = 32,
          mulEarlyOut = true, divEarlyOut = true)),
        nLocalInterrupts = 16,
				nBreakpoints = 2,
        nPMPs = 8,
				nPerfCounters = 6,																						// 0-6
				haveBasicCounters = true
      ),
      //btb = None,
      btb = Some(BTBParams(
				nEntries=28,
				bhtParams=Some(BHTParams(nEntries=512))
			)),
      dcache = Some(DCacheParams(
        rowBits = site(SystemBusKey).beatBits,
        nSets = 1024, nWays = 1,    		// 32KB L1D scratchpad as SPM (nWays=1)
        nTLBEntries = 4,
        nMSHRs = 0,
        blockBytes = site(CacheBlockBytes)	// Cache line size = 64B
			)),
      icache = Some(ICacheParams(
        rowBits = site(SystemBusKey).beatBits,
        nSets = 128, nWays = 2,					// 16KB L1I
        nTLBEntries = 4,
        blockBytes = site(CacheBlockBytes)
			))
		)
		List.tabulate(n)(i => mcE31.copy(hartId = i))
	}
})

//====
class WithNE51RV64IMAC(n: Int) extends Config((site, here, up) => {	// isa = RV32IMAC
  case XLen => 64                                                      // RV64
	case RocketTilesKey => {
  	val mcE51 = RocketTileParams(
      core = RocketCoreParams(
        useVM = false,
        fpu = None,
				useAtomics = true, 							                						   // A
				useCompressed = true,                           						   // C
        mulDiv = Some(MulDivParams(                                    // M
					mulUnroll = 8, divUnroll = 32,
          mulEarlyOut = true, divEarlyOut = true)),
        nLocalInterrupts = 16,
				nBreakpoints = 2,
        nPMPs = 8,
				nPerfCounters = 6,																						// 0-6
				haveBasicCounters = true
      ),
      //btb = None,
      btb = Some(BTBParams(
				nEntries=28,
				bhtParams=Some(BHTParams(nEntries=512))
			)),
      dcache = Some(DCacheParams(
        rowBits = site(SystemBusKey).beatBits,
        nSets = 1024, nWays = 1,    				// 64KB L1D scratchpad
        nTLBEntries = 4,
        nMSHRs = 0,
        blockBytes = site(CacheBlockBytes)	// Cache line size = 64B
			)),
      icache = Some(ICacheParams(
        rowBits = site(SystemBusKey).beatBits,
        nSets = 128, nWays = 2,					// 16KB L1I
        nTLBEntries = 4,
        blockBytes = site(CacheBlockBytes)
			))
		)
		List.tabulate(n)(i => mcE51.copy(hartId = i))
	}
})
