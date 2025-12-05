all: analyze/chisel6.s analyze/chisel7.s

rtl/chisel6/Test.sv: src/main/scala/Test.scala
	mill -i Test6.run rtl/chisel6

rtl/chisel7/Test.sv: src/main/scala/Test.scala
	mill -i Test7.run rtl/chisel7

verilated/chisel6/libVTest.a: rtl/chisel6/Test.sv
	mkdir -p verilated/chisel6
	verilator --build -O3 --cc rtl/chisel6/Test.sv --Mdir verilated/chisel6 --CFLAGS "-O3"

verilated/chisel7/libVTest.a: rtl/chisel7/Test.sv
	mkdir -p verilated/chisel7
	verilator --build -O3 --cc rtl/chisel7/Test.sv --Mdir verilated/chisel7 --CFLAGS "-O3"

analyze/chisel6.s: verilated/chisel6/libVTest.a
	mkdir -p analyze
	objdump --disassemble=_Z37VTest___024root___ico_sequent__TOP__0P15VTest___024root $< > $@

analyze/chisel7.s: verilated/chisel7/libVTest.a
	mkdir -p analyze
	objdump --disassemble=_Z37VTest___024root___ico_sequent__TOP__0P15VTest___024root $< > $@

.PHONY: bsp clean

clean:
	rm -rf rtl verilated analyze

bsp:
	mill -i mill.bsp.BSP/install
