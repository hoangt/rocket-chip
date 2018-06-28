// See LICENSE.SiFive for license details.

package freechips.rocketchip.tile

import Chisel._

import freechips.rocketchip.config.{Parameters, Field}
import freechips.rocketchip.subsystem.CacheBlockBytes
import freechips.rocketchip.tilelink.ClientMetadata
import freechips.rocketchip.util._

trait L2CacheParams {
  def nSets:         Int
  def nWays:         Int
  def rowBits:       Int
	def cacheIdBits
  def nTLBEntries:   Int
  def blockBytes:    Int // TODO this is ignored in favor of p(CacheBlockBytes) in BaseTile
}

trait HasL2CacheParameters extends HasTileParameters {
  val cacheParams: L2CacheParams
  private val bundleParams = p(SharedMemoryTLEdge).bundle

  def nSets = cacheParams.nSets
  def blockOffBits = lgCacheBlockBytes
  def idxBits = log2Up(cacheParams.nSets)
  def untagBits = blockOffBits + idxBits
  def tagBits = bundleParams.addressBits - untagBits
  def nWays = cacheParams.nWays
  def wayBits = log2Up(nWays)
  def isDM = nWays == 1
  def rowBits = cacheParams.rowBits
  def rowBytes = rowBits/8
  def rowOffBits = log2Up(rowBytes)
  def nTLBEntries = cacheParams.nTLBEntries

  def cacheDataBits = bundleParams.dataBits
  def cacheDataBeats = (cacheBlockBytes * 8) / cacheDataBits
  def refillCycles = cacheDataBeats
}

abstract class L2CacheModule(implicit val p: Parameters) extends Module
  with HasL2CacheParameters

abstract class L2CacheBundle(implicit val p: Parameters) extends ParameterizedBundle()(p)
  with HasL2CacheParameters
