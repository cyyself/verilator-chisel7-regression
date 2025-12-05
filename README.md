# Verilator Chisel 7 Regression Test

## Want to see the issue by self-compilation?

```bash
make
```

## The Issue

We discovered a significant performance regression in Verilator simulation when using Chisel 7 compared to Chisel 6 for designs that utilize `Mux1H` with a large number of inputs with LLVM-19.

```scala
class Test extends RawModule {
  val width = 7 // 2**7 = 128 entries
  val io = IO(new Bundle {
    val in = Input(Vec((1 << width), UInt(32.W)))
    val sel = Input(UInt(width.W))
    val out = Output(UInt(32.W))
  })

  val sel = UIntToOH(io.sel, 1 << width)
  io.out := Mux1H(sel, io.in)
}
```

In Chisel 6.7.0, the Verilog will be like:

```systemverilog
  assign io_out =
    (io_sel == 7'h0 ? io_in_0 : 32'h0) | (io_sel == 7'h1 ? io_in_1 : 32'h0)
    | (io_sel == 7'h2 ? io_in_2 : 32'h0) | (io_sel == 7'h3 ? io_in_3 : 32'h0)
    | (io_sel == 7'h4 ? io_in_4 : 32'h0) | (io_sel == 7'h5 ? io_in_5 : 32'h0)
    | (io_sel == 7'h6 ? io_in_6 : 32'h0) | (io_sel == 7'h7 ? io_in_7 : 32'h0)
    | (io_sel == 7'h8 ? io_in_8 : 32'h0) | (io_sel == 7'h9 ? io_in_9 : 32'h0)
    // ... snip ...
```
In Chisel 7.3.0, the Verilog will be like:

```systemverilog
  wire [127:0] sel = 128'h1 << io_sel;	// src/main/scala/chisel3/util/OneHot.scala:65:12
  assign io_out =
    (sel[0] ? io_in_0 : 32'h0) | (sel[1] ? io_in_1 : 32'h0) | (sel[2] ? io_in_2 : 32'h0)
    | (sel[3] ? io_in_3 : 32'h0) | (sel[4] ? io_in_4 : 32'h0) | (sel[5] ? io_in_5 : 32'h0)
    | (sel[6] ? io_in_6 : 32'h0) | (sel[7] ? io_in_7 : 32'h0) | (sel[8] ? io_in_8 : 32'h0)
    | (sel[9] ? io_in_9 : 32'h0) | (sel[10] ? io_in_10 : 32'h0)
    // ... snip ...
```

It has no difference in functionality, but let's look at the Verilator output for both versions.

In Chisel 6.7.0, Verilator generates the following code:

```cpp
    vlSelfRef.Test__DOT____VdfgBinToOneHot_Tab_h807d22cc_0_0[vlSelfRef.Test__DOT____VdfgBinToOneHot_Pre_h807d22cc_0_0] = 0U;
    vlSelfRef.Test__DOT____VdfgBinToOneHot_Tab_h807d22cc_0_0[vlSelfRef.io_sel] = 1U;
    vlSelfRef.Test__DOT____VdfgBinToOneHot_Pre_h807d22cc_0_0 
        = vlSelfRef.io_sel;
    __Vdeeptemp_h9151389e__0 = ((vlSelfRef.Test__DOT____VdfgBinToOneHot_Tab_h807d22cc_0_0
                                 [9U] ? vlSelfRef.io_in_9
                                  : 0U) | ((vlSelfRef.Test__DOT____VdfgBinToOneHot_Tab_h807d22cc_0_0
                                            [0x0aU]
                                             ? vlSelfRef.io_in_10
                                             : 0U) 
                                           | ((vlSelfRef.Test__DOT____VdfgBinToOneHot_Tab_h807d22cc_0_0
                                               [0x0bU]
                                                ? vlSelfRef.io_in_11
                                                : 0U) 
```

In Chisel 7.3.0, Verilator generates the following code:

```cpp
    __Vtemp_1[0U] = 1U;
    __Vtemp_1[1U] = 0U;
    __Vtemp_1[2U] = 0U;
    __Vtemp_1[3U] = 0U;
    VL_SHIFTL_WWI(128,128,7, __Vtemp_2, __Vtemp_1, (IData)(vlSelfRef.io_sel));
    __Vtemp_3[0U] = 1U;
    __Vtemp_3[1U] = 0U;
    __Vtemp_3[2U] = 0U;
    __Vtemp_3[3U] = 0U;
    VL_SHIFTL_WWI(128,128,7, __Vtemp_4, __Vtemp_3, (IData)(vlSelfRef.io_sel));
    __Vtemp_5[0U] = 1U;
    __Vtemp_5[1U] = 0U;
    __Vtemp_5[2U] = 0U;
    __Vtemp_5[3U] = 0U;
    VL_SHIFTL_WWI(128,128,7, __Vtemp_6, __Vtemp_5, (IData)(vlSelfRef.io_sel));
    __Vtemp_7[0U] = 1U;
    __Vtemp_7[1U] = 0U;
    __Vtemp_7[2U] = 0U;
    __Vtemp_7[3U] = 0U;
    // ... snip ...
```

and Then:


```cpp
    __Vdeeptemp_h20de80fe__0 = (((0x00000400U & __Vtemp_20[0U])
                                  ? vlSelfRef.io_in_10
                                  : 0U) | (((0x00000800U 
                                             & __Vtemp_22[0U])
                                             ? vlSelfRef.io_in_11
                                             : 0U) 
                                           | (((0x00001000U 
                                                & __Vtemp_24[0U])
                                                ? vlSelfRef.io_in_12
                                                : 0U) 
    // ... snip ...
```

As we can see, in Chisel 6.7.0, Verilator is able to optimize the Mux1H implementation into a simple array lookup, while in Chisel 7.3.0, Verilator generates a lot of shift operations and bitwise AND operations, which significantly increases the complexity of the generated code and leads to performance degradation during simulation.

As a result, let's look at the `analyze/chisel6.s` and `analyze/chisel7.s` files:

```asm
void VTest___024root___ico_sequent__TOP__0(VTest___024root* vlSelf) {
     b40:	55                   	push   %rbp
# ... snip ...
    285c:	e9 01 ee ff ff       	jmp    1662 <_Z37VTest___024root___ico_sequent__TOP__0P15VTest___024root+0xb22>
```

The code size is only `0x1d1c` bytes, 1449 instructions for Chisel 6.7.0, while for Chisel 7.3.0:

```asm
void VTest___024root___ico_sequent__TOP__0(VTest___024root* vlSelf) {
     b10:	55                   	push   %rbp
    bbca:	eb b4                	jmp    bb80 <_Z37VTest___024root___ico_sequent__TOP__0P15VTest___024root+0xb070>
```

The code size is `0xb0ba` bytes, 12266 instructions for Chisel 7.3.0, which is more then **8x** more instructions generated, and **6x** larger code size.

As a result, we see a significant performance degradation in Verilator simulation when using Chisel 7.2.0 compared to Chisel 6.6.0 for designs that utilize Mux1H with a large number of inputs. [XiangShan-kunminghuv3](https://github.com/OpenXiangShan/XiangShan/tree/kunminghu-v3) shows Verilator simulation 10 iterations time as below:

| Chisel Version | Commit Hash | Time (s) |
|----------------|-------------|----------|
| Chisel 6.6.0   | [ea82e26](https://github.com/OpenXiangShan/XiangShan/tree/ea82e26120b60939725f658cead524180f2c90df) | [13m 16s](https://github.com/OpenXiangShan/XiangShan/actions/runs/18900241730/job/53945743283) |
| Chisel 7.2.0.  | [2e4c95e](https://github.com/OpenXiangShan/XiangShan/tree/2e4c95e8c5ae1d440c264d70674029db7498718f) | [43m 31s](https://github.com/OpenXiangShan/XiangShan/actions/runs/18900969970/job/53947938368) |

This regression has a significant impact on simulation performance by **3.28x** slower in this real-world design.

Let's hope this issue can be addressed in future versions of Chisel or Verilator to restore the previous performance levels.

## How well it can be?

I have tried to add `--preserve-aggregate=all` to let chisel to use vector of wires instead of multiple signals, the Verilog will be like:

```verilog
// Generated by CIRCT firtool-1.62.1
module Test(	// home/cyy/tmp/verilator-chisel7-regression/src/main/scala/Test.scala:5:7
  input  [127:0][31:0] io_in,	// home/cyy/tmp/verilator-chisel7-regression/src/main/scala/Test.scala:7:14
  input  [6:0]         io_sel,	// home/cyy/tmp/verilator-chisel7-regression/src/main/scala/Test.scala:7:14
  output [31:0]        io_out	// home/cyy/tmp/verilator-chisel7-regression/src/main/scala/Test.scala:7:14
);

  wire [31:0] _GEN =
    (io_sel == 7'h0 ? io_in[7'h0] : 32'h0) | (io_sel == 7'h1 ? io_in[7'h1] : 32'h0)
    | (io_sel == 7'h2 ? io_in[7'h2] : 32'h0) | (io_sel == 7'h3 ? io_in[7'h3] : 32'h0)
    | (io_sel == 7'h4 ? io_in[7'h4] : 32'h0) | (io_sel == 7'h5 ? io_in[7'h5] : 32'h0)
// ... snip ...
```

However, the verilator output is still not changed much, still lots of shift and and operations in both versions of Chisel.

But if we manual modify the Verilog to:

```verilog
module Test(
  input  [127:0][31:0] io_in,
  input  [6:0]         io_sel,
  output [31:0]        io_out
);

  assign io_out = io_in[io_sel];

endmodule
```

The verilated output is:
```cpp
void VTest___024root___ico_sequent__TOP__0(VTest___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VTest___024root___ico_sequent__TOP__0\n"); );
    VTest__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.io_out = (((0U == (0x0000001fU & VL_SHIFTL_III(12,12,32, (IData)(vlSelfRef.io_sel), 5U)))
                          ? 0U : (vlSelfRef.io_in[(
                                                   ((IData)(0x0000001fU) 
                                                    + 
                                                    (0x00000fffU 
                                                     & VL_SHIFTL_III(12,12,32, (IData)(vlSelfRef.io_sel), 5U))) 
                                                   >> 5U)] 
                                  << ((IData)(0x00000020U) 
                                      - (0x0000001fU 
                                         & VL_SHIFTL_III(12,12,32, (IData)(vlSelfRef.io_sel), 5U))))) 
                        | (vlSelfRef.io_in[(0x0000007fU 
                                            & (VL_SHIFTL_III(12,12,32, (IData)(vlSelfRef.io_sel), 5U) 
                                               >> 5U))] 
                           >> (0x0000001fU & VL_SHIFTL_III(12,12,32, (IData)(vlSelfRef.io_sel), 5U))));
}
```

And the assembly code is very simple now:
```asm
00000000000004f0 <_Z37VTest___024root___ico_sequent__TOP__0P15VTest___024root>:
 4f0:	0f b6 07             	movzbl (%rdi),%eax
 4f3:	83 e0 7f             	and    $0x7f,%eax
 4f6:	8b 44 87 04          	mov    0x4(%rdi,%rax,4),%eax
 4fa:	89 87 04 02 00 00    	mov    %eax,0x204(%rdi)
 500:	c3                   	ret
```

The code size is only `0x16` bytes, **5 instructions only**. Much much better than Chisel 7 (**12k** instructions) and Chisel 6 (**1.4k** instructions). We can see that the optimal code for Mux1H with large number of inputs should be a simple array lookup.

There should be a **very large** room for Verilator and CIRCT FIRRTL backend to improve the generated code for such patterns to achieve optimal performance.
