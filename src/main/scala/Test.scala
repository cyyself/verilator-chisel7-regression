import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

class Test extends RawModule {
  val io = IO(new Bundle {
    val in = Input(Vec(16, UInt(32.W)))
    val sel = Input(UInt(4.W))
    val out = Output(UInt(32.W))
  })

  val sel = UIntToOH(io.sel, 16)
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
