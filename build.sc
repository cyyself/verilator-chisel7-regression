import mill._
import mill.define.Sources
import scalalib._

object Test7 extends SbtModule { m =>
  override def millSourcePath = super.millSourcePath / os.up
  override def scalaVersion = "2.13.17"
  def chiselVersion = "7.3.0"
  override def scalacOptions = Seq(
    "-language:reflectiveCalls",
    "-deprecation",
    "-feature",
    "-Xcheckinit",
    "-Ymacro-annotations",
  )
  override def ivyDeps = Agg(
    ivy"org.chipsalliance::chisel:$chiselVersion",
  )
  override def scalacPluginIvyDeps = Agg(
    ivy"org.chipsalliance:::chisel-plugin:$chiselVersion",
  )
}

object Test6 extends SbtModule { m =>
	override def millSourcePath = super.millSourcePath / os.up
	override def scalaVersion = "2.13.16"
	def chiselVersion = "6.7.0"
	override def scalacOptions = Seq(
		"-language:reflectiveCalls",
		"-deprecation",
		"-feature",
		"-Xcheckinit",
		"-Ymacro-annotations",
	)
	override def ivyDeps = Agg(
		ivy"org.chipsalliance::chisel:$chiselVersion",
	)
	override def scalacPluginIvyDeps = Agg(
		ivy"org.chipsalliance:::chisel-plugin:$chiselVersion",
	)
}
