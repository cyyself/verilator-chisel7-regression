import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

class Test extends RawModule {
  val width = 9 // 2**9 = 512 entries
  val io = IO(new Bundle {
    val in = Input(Vec((1 << width), UInt(32.W)))
    val sel = Input(UInt(width.W))
    val out = Output(UInt(32.W))
  })

  val sel = UIntToOH(io.sel, 1 << width)
  io.out := Mux1H(sel, io.in)
}

object Test extends App {
  ChiselStage.emitSystemVerilogFile(
    new Test,
    Array(
      "--target-dir", args(0)
    ),
    firtoolOpts = Array()
  )
}
